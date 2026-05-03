package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrayUncleRuleTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should allow placing stall near Uncle if START or END tile is available`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val uncleCoord = AxialCoordinate(0, 0)
        val floorCoord = AxialCoordinate(1, 0)
        val startCoord = AxialCoordinate(0, 1) // Adjacent to (0,0)

        // Setup hexes: (0,0) has Uncle, (1,0) is Floor, (0,1) is Start.
        // Neighbors of (0,0) in getAdjacentCoordinates are:
        // (1,0), (1,-1), (0,-1), (-1,0), (-1,1), (0,1)
        // So (1,0) and (0,1) are neighbors.
        val hexes = mutableMapOf(
            uncleCoord to HexTile(uncleCoord, TileType.FLOOR, stall = StallRegistry.get(StallType.TRAY_RETURN_UNCLE).toStall(id = "uncle")),
            floorCoord to HexTile(floorCoord, TileType.FLOOR),
            startCoord to HexTile(startCoord, TileType.START),
            AxialCoordinate(1, -1) to HexTile(AxialCoordinate(1, -1), TileType.PILLAR),
            AxialCoordinate(0, -1) to HexTile(AxialCoordinate(0, -1), TileType.PILLAR),
            AxialCoordinate(-1, 0) to HexTile(AxialCoordinate(-1, 0), TileType.PILLAR),
            AxialCoordinate(-1, 1) to HexTile(AxialCoordinate(-1, 1), TileType.PILLAR),
            AxialCoordinate(1, 1) to HexTile(AxialCoordinate(1, 1), TileType.FLOOR),
            AxialCoordinate(10, 10) to HexTile(AxialCoordinate(10, 10), TileType.END)
        )
        // Fill some more floor tiles to ensure path
        for (q in 0..10) {
            for (r in 0..10) {
                val c = AxialCoordinate(q, r)
                if (!hexes.containsKey(c)) {
                    hexes[c] = HexTile(c, TileType.FLOOR)
                }
            }
        }

        viewModel._gameState.value = GameState(
            hexes = hexes,
            startPosition = startCoord,
            endPosition = AxialCoordinate(10, 10), // Far away
            gold = 1000,
            currentScreen = AppScreen.GAME
        )

        // Select a stall to place (Chicken Rice)
        viewModel.selectStall(StallRegistry.get(StallType.CHICKEN_RICE).toStall())

        // Ensure path from start to end exists WITHOUT (1,0) blocked
        val initialPath = com.messark.hawker.utils.Pathfinding.findPath(
            startCoord, AxialCoordinate(10, 10), setOf(uncleCoord), hexes.keys
        )
        assertNotNull("Initial path should exist", initialPath)

        // Try to place it on floorCoord (1,0).
        // Currently this will fail because (0,0) Uncle will only see (1,0) as a free neighbor
        // if it only looks for TileType.FLOOR. Once (1,0) is considered "blocked" by the placement attempt,
        // it will see NO free neighbors because (0,1) is START.
        viewModel.onCellClick(floorCoord)

        val stallAtFloor = viewModel.gameState.value.hexes[floorCoord]?.stall
        assertNotNull("Stall should be placed because START tile is a valid release point", stallAtFloor)
    }
}
