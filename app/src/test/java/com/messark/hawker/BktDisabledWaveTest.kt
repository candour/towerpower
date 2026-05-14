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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BktDisabledWaveTest {
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
    fun `Bak Kut Teh does not provide wave buff if disabled`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
        viewModel.gameJob?.cancel()

        val bktCoord = AxialCoordinate(1, 0)
        val bktStall = StallRegistry.get(StallType.BAK_KUT_TEH).toStall().copy(disabledWaves = 1)

        viewModel._gameState.value = GameState(
            hexes = mapOf(bktCoord to HexTile(bktCoord, TileType.FLOOR, stall = bktStall)),
            currentWave = 0,
            waveActive = false
        )

        viewModel.startWave()

        val state = viewModel.gameState.value
        assertNull("Toast message should be null when BKT is disabled", state.bktToastMessage)
    }

    @Test
    fun `Bak Kut Teh provides wave buff if enabled`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
        viewModel.gameJob?.cancel()

        val bktCoord = AxialCoordinate(1, 0)
        val bktStall = StallRegistry.get(StallType.BAK_KUT_TEH).toStall().copy(disabledWaves = 0)

        viewModel._gameState.value = GameState(
            hexes = mapOf(bktCoord to HexTile(bktCoord, TileType.FLOOR, stall = bktStall)),
            currentWave = 0,
            waveActive = false
        )

        viewModel.startWave()

        val state = viewModel.gameState.value
        assertNotNull("Toast message should not be null when BKT is enabled", state.bktToastMessage)
    }
}
