package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class RemovePillarTest {
    private lateinit var viewModel: MainViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val gameStateRepository = mockk<GameStateRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock settings to avoid tutorial blocks
        val settings = Settings(showTutorials = false)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(settings)

        viewModel = MainViewModel(application, settingsRepository, gameStateRepository, Random(42))
        // The game loop starts in init, but we want to control it.
        // For unit tests, we usually don't need the loop.
        viewModel.gameJob?.cancel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test remove pillar logic`() = runTest {
        // 1. Setup a state with a pillar and a kitchelin star
        val pillarCoord = AxialCoordinate(2, 2)
        val initialHexes = viewModel.gameState.value.hexes.toMutableMap()
        initialHexes[pillarCoord] = HexTile(coordinate = pillarCoord, type = TileType.PILLAR)

        viewModel._gameState.value = viewModel.gameState.value.copy(
            hexes = initialHexes,
            kitchelinStars = 1,
            isRemovePillarModeActive = false
        )

        // 2. Enter remove pillar mode
        viewModel.enterRemovePillarMode()
        assertTrue(viewModel.gameState.value.isRemovePillarModeActive)
        assertFalse(viewModel.gameState.value.showStarActionOverlay)

        // 3. Click the pillar
        viewModel.onCellClick(pillarCoord)

        // 4. Verify results
        val state = viewModel.gameState.value
        assertEquals(0, state.kitchelinStars)
        assertEquals(TileType.FLOOR, state.hexes[pillarCoord]?.type)
        assertFalse(state.isRemovePillarModeActive)
        assertTrue(state.lastShakeTimeMs > 0)
    }

    @Test
    fun `test cannot remove pillar without stars`() = runTest {
        val pillarCoord = AxialCoordinate(2, 2)
        val initialHexes = viewModel.gameState.value.hexes.toMutableMap()
        initialHexes[pillarCoord] = HexTile(coordinate = pillarCoord, type = TileType.PILLAR)

        viewModel._gameState.value = viewModel.gameState.value.copy(
            hexes = initialHexes,
            kitchelinStars = 0,
            isRemovePillarModeActive = true
        )

        viewModel.onCellClick(pillarCoord)

        val state = viewModel.gameState.value
        assertEquals(0, state.kitchelinStars)
        assertEquals(TileType.PILLAR, state.hexes[pillarCoord]?.type)
        // It stays in mode because nothing happened (though currently removePillar returns early)
        // Actually my implementation of removePillar sets isRemovePillarModeActive = false ONLY if it succeeds.
        // Let's check my code again.
    }

    @Test
    fun `test exit remove pillar mode`() = runTest {
        viewModel.enterRemovePillarMode()
        assertTrue(viewModel.gameState.value.isRemovePillarModeActive)

        viewModel.exitRemovePillarMode()
        assertFalse(viewModel.gameState.value.isRemovePillarModeActive)
    }
}
