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
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AtmDisabledTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `ATM does not provide income at the end of the wave if it was disabled`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val atmCoord = AxialCoordinate(0, 0)
        // ATM with disabledWaves = 1. It is disabled during this wave.
        // Even if it decrements to 0 at the end, it shouldn't pay for the wave it was disabled in.
        val atm = StallRegistry.get(StallType.ATM).toStall().copy(id = "atm", disabledWaves = 1)

        val hexes = mapOf(
            atmCoord to HexTile(atmCoord, TileType.FLOOR, atm)
        )

        viewModel._gameState.value = GameState(
            waveActive = true,
            currentWave = 1,
            hexes = hexes,
            gold = 0,
            enemies = emptyList(),
            enemiesToSpawn = 0
        )

        viewModel.updateGame(System.currentTimeMillis())

        val state = viewModel.gameState.value
        assertEquals("ATM should not have provided income", 0, state.gold)
        val updatedAtm = state.hexes[atmCoord]?.stall
        assertEquals("ATM should now be enabled", 0, updatedAtm?.disabledWaves)
    }

    @Test
    fun `ATM provides income if it is not disabled`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val atmCoord = AxialCoordinate(0, 0)
        val atm = StallRegistry.get(StallType.ATM).toStall().copy(id = "atm", disabledWaves = 0)

        val hexes = mapOf(
            atmCoord to HexTile(atmCoord, TileType.FLOOR, atm)
        )

        viewModel._gameState.value = GameState(
            waveActive = true,
            currentWave = 1,
            hexes = hexes,
            gold = 0,
            enemies = emptyList(),
            enemiesToSpawn = 0
        )

        viewModel.updateGame(System.currentTimeMillis())

        val state = viewModel.gameState.value
        assertEquals("ATM should have provided income", 100, state.gold)
    }
}
