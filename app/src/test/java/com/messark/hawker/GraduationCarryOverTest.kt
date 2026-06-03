package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.GameState
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GraduationCarryOverTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `graduateToNextLevel carries over score, kitchelinStars and 10 percent gold`() = runBlocking {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.messark.hawker.model.Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        // Set initial state
        viewModel._gameState.value = GameState(
            currentLevel = 1,
            gold = 1000,
            score = 5000,
            kitchelinStars = 3,
            health = 10
        )

        viewModel.graduateToNextLevel()

        val newState = viewModel.gameState.value

        assertEquals(2, newState.currentLevel)
        assertEquals(5000, newState.score)
        assertEquals(3, newState.kitchelinStars)
        // 500 (base) + 100 (10% of 1000) = 600
        assertEquals(600, newState.gold)
    }
}
