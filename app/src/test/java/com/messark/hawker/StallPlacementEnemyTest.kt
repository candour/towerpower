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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StallPlacementEnemyTest {
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
    fun `cannot place stall on a tile occupied by an enemy`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val start = AxialCoordinate(0, 0)
        val end = AxialCoordinate(4, 0)
        val coord = AxialCoordinate(2, 0)

        val hexes = mapOf(
            start to HexTile(start, TileType.START),
            AxialCoordinate(1, 0) to HexTile(AxialCoordinate(1, 0), TileType.FLOOR),
            coord to HexTile(coord, TileType.FLOOR),
            AxialCoordinate(3, 0) to HexTile(AxialCoordinate(3, 0), TileType.FLOOR),
            end to HexTile(end, TileType.GOAL_TABLE)
        )

        val enemy = Enemy(
            id = "e1",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(2f, 0f),
            path = listOf(start, AxialCoordinate(1, 0), coord, AxialCoordinate(3, 0), end)
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            gold = 1000,
            selectedStallType = StallRegistry.get(StallType.CHICKEN_RICE).toStall(),
            enemies = listOf(enemy),
            startPosition = start,
            endPosition = end
        )

        viewModel.onCellClick(coord)

        assertNull("Stall should not be placed on occupied tile", viewModel.gameState.value.hexes[coord]?.stall)
    }

    @Test
    fun `cannot place stall on a tile that is the next target of an enemy`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val start = AxialCoordinate(0, 0)
        val end = AxialCoordinate(4, 0)
        val currentCoord = AxialCoordinate(1, 0)
        val nextCoord = AxialCoordinate(2, 0)

        val hexes = mapOf(
            start to HexTile(start, TileType.START),
            currentCoord to HexTile(currentCoord, TileType.FLOOR),
            nextCoord to HexTile(nextCoord, TileType.FLOOR),
            AxialCoordinate(3, 0) to HexTile(AxialCoordinate(3, 0), TileType.FLOOR),
            end to HexTile(end, TileType.GOAL_TABLE)
        )

        val enemy = Enemy(
            id = "e1",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(1f, 0f),
            path = listOf(start, currentCoord, nextCoord, AxialCoordinate(3, 0), end),
            currentPathIndex = 1 // At currentCoord, next is nextCoord
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            gold = 1000,
            selectedStallType = StallRegistry.get(StallType.CHICKEN_RICE).toStall(),
            enemies = listOf(enemy),
            startPosition = start,
            endPosition = end
        )

        viewModel.onCellClick(nextCoord)

        assertNull("Stall should not be placed on next target tile", viewModel.gameState.value.hexes[nextCoord]?.stall)
    }
}
