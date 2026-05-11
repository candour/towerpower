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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrayUncleDrainTest {
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
    fun `should allow placing Uncle if only DRAIN tile is available`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val uncleCoord = AxialCoordinate(0, 0)
        val drainCoord = AxialCoordinate(1, 0) // Neighbor
        val goalCoord = AxialCoordinate(10, 10)

        // Setup hexes: (0,0) is floor where we want to place Uncle.
        // (1,0) is DRAIN. Others are PILLARS.
        val hexes = mutableMapOf(
            uncleCoord to HexTile(uncleCoord, TileType.FLOOR),
            drainCoord to HexTile(drainCoord, TileType.DRAIN),
            goalCoord to HexTile(goalCoord, TileType.GOAL_TABLE)
        )

        // Fill other neighbors with pillars
        val neighbors = com.messark.hawker.utils.GridUtils.getNeighbors(uncleCoord)
        neighbors.forEach {
            if (it != drainCoord) {
                hexes[it] = HexTile(it, TileType.PILLAR)
            }
        }

        // Fill remaining area to ensure pathfinding doesn't crash
        for (q in -2..11) {
            for (r in -2..11) {
                val c = AxialCoordinate(q, r)
                if (!hexes.containsKey(c)) {
                    hexes[c] = HexTile(c, TileType.FLOOR)
                }
            }
        }

        viewModel._gameState.value = GameState(
            hexes = hexes,
            startPosition = AxialCoordinate(-2, -2),
            endPosition = goalCoord,
            gold = 1000,
            currentScreen = AppScreen.GAME
        )

        // Select Tray Return Uncle
        viewModel.selectStall(StallRegistry.get(StallType.TRAY_RETURN_UNCLE).toStall())

        // Try to place it on uncleCoord (0,0).
        // This SHOULD be allowed because it has a DRAIN neighbor.
        viewModel.onCellClick(uncleCoord)

        val stallAtUncle = viewModel.gameState.value.hexes[uncleCoord]?.stall
        assertNotNull("Stall should be placed because DRAIN tile is a valid release point", stallAtUncle)
    }
}
