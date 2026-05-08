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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BakKutTehSameTickTest {
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
    fun `ATM receives boosted income when adjacent Bak Kut Teh is re-enabled in the same tick`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
        viewModel.gameJob?.cancel() // Stop the loop

        val atmCoord = AxialCoordinate(0, 0)
        val bktCoord = AxialCoordinate(1, 0) // Adjacent

        val atmStall = StallRegistry.get(StallType.ATM).toStall(UUID.randomUUID().toString())
        val bktStall = StallRegistry.get(StallType.BAK_KUT_TEH).toStall(UUID.randomUUID().toString()).copy(
            disabledWaves = 1
        )

        val hexes = mapOf(
            atmCoord to HexTile(atmCoord, TileType.FLOOR, stall = atmStall),
            bktCoord to HexTile(bktCoord, TileType.FLOOR, stall = bktStall)
        )

        val initialState = GameState(
            hexes = hexes,
            gold = 500,
            waveActive = true,
            enemiesToSpawn = 0,
            enemies = emptyList(),
            currentWave = 1
        )

        viewModel._gameState.value = initialState

        // Trigger updateGame which should handle wave completion
        viewModel.updateGame(System.currentTimeMillis())

        val finalState = viewModel.gameState.value

        // ATM income is 100. BKT boost is 20%. Total should be 120.
        // Initial gold 500 + 120 = 620.
        assertEquals("Gold should reflect boosted ATM income", 620, finalState.gold)
        assertEquals("BKT should be re-enabled", 0, finalState.hexes[bktCoord]?.stall?.disabledWaves)
    }
}
