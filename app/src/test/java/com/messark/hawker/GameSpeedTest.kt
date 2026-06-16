package com.messark.hawker

import android.app.Application
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSpeedTest {
    private val testDispatcher = StandardTestDispatcher()

    `@Before`
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    `@After`
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `game speed adjustment works correctly`() = runBlocking {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.messark.hawker.model.Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        // Initial speed should be 1.0f
        assertEquals(1.0f, viewModel.gameState.value.gameSpeed)

        // Increase speed
        viewModel.increaseGameSpeed()
        assertEquals(1.5f, viewModel.gameState.value.gameSpeed)

        viewModel.increaseGameSpeed()
        assertEquals(2.0f, viewModel.gameState.value.gameSpeed)

        viewModel.increaseGameSpeed()
        assertEquals(2.5f, viewModel.gameState.value.gameSpeed)

        viewModel.increaseGameSpeed()
        assertEquals(3.0f, viewModel.gameState.value.gameSpeed)

        // Should not exceed 3.0f
        viewModel.increaseGameSpeed()
        assertEquals(3.0f, viewModel.gameState.value.gameSpeed)

        // Decrease speed
        viewModel.decreaseGameSpeed()
        assertEquals(2.5f, viewModel.gameState.value.gameSpeed)

        viewModel.decreaseGameSpeed()
        viewModel.decreaseGameSpeed()
        viewModel.decreaseGameSpeed()
        viewModel.decreaseGameSpeed()
        assertEquals(0.5f, viewModel.gameState.value.gameSpeed)

        // Should not go below 0.5f
        viewModel.decreaseGameSpeed()
        assertEquals(0.5f, viewModel.gameState.value.gameSpeed)
    }
}
