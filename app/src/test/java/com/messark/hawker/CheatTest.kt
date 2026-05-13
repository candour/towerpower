package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.AppScreen
import com.messark.hawker.model.GameState
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheatTest {
    private val application = mockk<Application>(relaxed = true)
    private val settingsRepo = mockk<SettingsRepository>(relaxed = true)
    private val gameStateRepo = mockk<GameStateRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(application, settingsRepo, gameStateRepo)
    }

    @Test
    fun `applyCheat updates saved game and live state when in game`() {
        val initialSavedState = GameState(gold = 500, kitchelinStars = 0, currentScreen = AppScreen.GAME)
        every { gameStateRepo.loadGameState() } returns initialSavedState

        // Mocking the live state
        viewModel.navigateTo(AppScreen.GAME)
        viewModel._gameState.value = viewModel._gameState.value.copy(gold = 500, kitchelinStars = 0)

        viewModel.applyCheat()

        // Verify repository update
        val stateSlot = slot<GameState>()
        verify { gameStateRepo.saveGameState(capture(stateSlot)) }
        assertEquals(5500, stateSlot.captured.gold)
        assertEquals(1, stateSlot.captured.kitchelinStars)

        // Verify live state update
        assertEquals(5500, viewModel.gameState.value.gold)
        assertEquals(1, viewModel.gameState.value.kitchelinStars)
    }

    @Test
    fun `applyCheat does nothing if no saved game`() {
        every { gameStateRepo.loadGameState() } returns null

        viewModel.applyCheat()

        verify(exactly = 0) { gameStateRepo.saveGameState(any()) }
    }
}
