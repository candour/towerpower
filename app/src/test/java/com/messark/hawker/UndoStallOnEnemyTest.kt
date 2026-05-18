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
class UndoStallOnEnemyTest {
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
    fun `undoSell should not be allowed if an enemy is on the tile`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val stallCoord = AxialCoordinate(0, 0)
        val stall = StallRegistry.get(StallType.CHICKEN_RICE).toStall().copy(id = "stall1")

        val hexes = mapOf(
            stallCoord to HexTile(stallCoord, TileType.FLOOR, stall),
            AxialCoordinate(1, 0) to HexTile(AxialCoordinate(1, 0), TileType.FLOOR),
            AxialCoordinate(2, 0) to HexTile(AxialCoordinate(2, 0), TileType.GOAL_TABLE)
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            enemies = emptyList(),
            startPosition = AxialCoordinate(-1, 0),
            endPosition = AxialCoordinate(2, 0),
            gold = 1000
        )

        // 1. Sell the stall
        viewModel.onCellClick(stallCoord) // Select
        viewModel.sellStall()

        assertNull(viewModel.gameState.value.hexes[stallCoord]?.stall)
        assertNotNull(viewModel.gameState.value.lastSoldStall)

        // 2. Move an enemy onto the tile
        val enemy = Enemy(
            id = "enemy1",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(0f, 0f), // Right on stallCoord
            path = listOf(AxialCoordinate(-1, 0), AxialCoordinate(0, 0), AxialCoordinate(1, 0), AxialCoordinate(2, 0))
        )
        viewModel._gameState.value = viewModel.gameState.value.copy(enemies = listOf(enemy))

        // 3. Try to undo sell
        viewModel.undoSell()

        // If the bug exists, the stall will be restored on top of the enemy
        assertNull("Stall should NOT be restored because an enemy is on the tile", viewModel.gameState.value.hexes[stallCoord]?.stall)
        assertNotNull("lastSoldStall should NOT be cleared because undo failed", viewModel.gameState.value.lastSoldStall)
    }

    @Test
    fun `undoSell should not be allowed if an enemy is moving towards the tile`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val stallCoord = AxialCoordinate(1, 0)
        val stall = StallRegistry.get(StallType.CHICKEN_RICE).toStall().copy(id = "stall1")

        val hexes = mapOf(
            AxialCoordinate(0, 0) to HexTile(AxialCoordinate(0, 0), TileType.FLOOR),
            stallCoord to HexTile(stallCoord, TileType.FLOOR, stall),
            AxialCoordinate(2, 0) to HexTile(AxialCoordinate(2, 0), TileType.GOAL_TABLE)
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            enemies = emptyList(),
            startPosition = AxialCoordinate(-1, 0),
            endPosition = AxialCoordinate(2, 0),
            gold = 1000
        )

        // 1. Sell the stall
        viewModel.onCellClick(stallCoord) // Select
        viewModel.sellStall()

        // 2. An enemy is at (0,0) moving towards (1,0)
        val enemy = Enemy(
            id = "enemy1",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(0f, 0f),
            currentPathIndex = 1, // index of (0,0) in path below
            path = listOf(AxialCoordinate(-1, 0), AxialCoordinate(0, 0), AxialCoordinate(1, 0), AxialCoordinate(2, 0))
        )
        viewModel._gameState.value = viewModel.gameState.value.copy(enemies = listOf(enemy))

        // 3. Try to undo sell
        viewModel.undoSell()

        // If the bug exists, the stall will be restored
        assertNull("Stall should NOT be restored because an enemy is moving towards the tile", viewModel.gameState.value.hexes[stallCoord]?.stall)
    }
}
