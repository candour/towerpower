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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndoPathBlockTest {
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
    fun `undoSell should not be allowed if it blocks the main path`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        // Create a narrow path that can be blocked by one stall
        val startPos = AxialCoordinate(0, 0)
        val endPos = AxialCoordinate(2, 0)
        val blockCoord = AxialCoordinate(1, 0)

        val hexes = mutableMapOf(
            startPos to HexTile(startPos, TileType.START),
            blockCoord to HexTile(blockCoord, TileType.FLOOR),
            endPos to HexTile(endPos, TileType.GOAL_TABLE)
        )

        val stall = StallRegistry.get(StallType.CHICKEN_RICE).toStall().copy(id = "stall1")

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            startPosition = startPos,
            endPosition = endPos,
            gold = 1000,
            lastSoldStall = blockCoord to stall
        )

        // Try to undo sell - this should fail because it blocks the path from (0,0) to (2,0)
        viewModel.undoSell()

        assertNull("Stall should NOT be restored because it blocks the main path", viewModel.gameState.value.hexes[blockCoord]?.stall)
        assertNotNull("lastSoldStall should NOT be cleared", viewModel.gameState.value.lastSoldStall)
    }

    @Test
    fun `undoSell should not be allowed if it violates Tray Return Uncle rule`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        // Uncle at (0,0), only neighbor is (1,0)
        // We are undoing a stall at (1,0)
        val uncleCoord = AxialCoordinate(0, 0)
        val blockCoord = AxialCoordinate(1, 0)

        val uncle = StallRegistry.get(StallType.TRAY_RETURN_UNCLE).toStall().copy(id = "uncle1")
        val stall = StallRegistry.get(StallType.CHICKEN_RICE).toStall().copy(id = "stall1")

        val hexes = mutableMapOf(
            uncleCoord to HexTile(uncleCoord, TileType.FLOOR, uncle),
            blockCoord to HexTile(blockCoord, TileType.FLOOR),
            AxialCoordinate(0, 1) to HexTile(AxialCoordinate(0, 1), TileType.PILLAR),
            AxialCoordinate(1, -1) to HexTile(AxialCoordinate(1, -1), TileType.PILLAR),
            AxialCoordinate(0, -1) to HexTile(AxialCoordinate(0, -1), TileType.PILLAR),
            AxialCoordinate(-1, 0) to HexTile(AxialCoordinate(-1, 0), TileType.PILLAR),
            AxialCoordinate(-1, 1) to HexTile(AxialCoordinate(-1, 1), TileType.PILLAR)
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            startPosition = AxialCoordinate(-2, 0),
            endPosition = AxialCoordinate(2, 0),
            gold = 1000,
            lastSoldStall = blockCoord to stall
        )

        // Try to undo sell - this should fail because it leaves Uncle with no walkable neighbors
        viewModel.undoSell()

        assertNull("Stall should NOT be restored because it violates Tray Return Uncle rule", viewModel.gameState.value.hexes[blockCoord]?.stall)
        assertNotNull("lastSoldStall should NOT be cleared", viewModel.gameState.value.lastSoldStall)
    }
}
