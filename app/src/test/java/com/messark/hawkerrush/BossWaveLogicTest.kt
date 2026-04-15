package com.messark.hawkerrush

import android.app.Application
import com.messark.hawkerrush.model.EnemyType
import com.messark.hawkerrush.model.GameState
import com.messark.hawkerrush.utils.GameStateRepository
import com.messark.hawkerrush.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BossWaveLogicTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.messark.hawkerrush.model.Settings())

        viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
    }

    @Test
    fun `isBossWave and Delivery Rider logic`() = runBlocking {
        // We will manually advance the wave state to test logic
        for (w in 1..40) {
            setWave(w - 1)
            viewModel.startWave()
            val state = viewModel.gameState.value
            assertEquals("Expected wave $w", w, state.currentWave)

            if (w % 10 == 0) {
                assertTrue("Wave $w should be a boss wave", state.isBossWave)
            } else {
                assertFalse("Wave $w should NOT be a boss wave", state.isBossWave)
            }

            val hasDeliveryRider = state.enemiesToSpawnList.contains(EnemyType.DELIVERY_RIDER)
            if (w < 7) {
                assertFalse("Wave $w should not have Delivery Rider (unlocks at W7 or W10)", hasDeliveryRider)
            } else if (w <= 30) {
                if (w % 10 == 0) {
                    // Boss wave might have it (random)
                } else {
                    assertFalse("Wave $w should not have Delivery Rider (only boss waves until 30)", hasDeliveryRider)
                }
            } else {
                // After 30, it can appear anywhere
            }
        }
    }

    private fun setWave(wave: Int) {
        val field = MainViewModel::class.java.getDeclaredField("_gameState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as MutableStateFlow<GameState>
        stateFlow.value = stateFlow.value.copy(currentWave = wave, waveActive = false, enemies = emptyList(), enemiesToSpawn = 0)
    }
}
