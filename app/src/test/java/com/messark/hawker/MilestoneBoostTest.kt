package com.messark.hawker

import android.app.Application
import androidx.compose.ui.graphics.Color
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MilestoneBoostTest {
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
    fun `getUpgradeBenefit calculates cumulative boost correctly for Damage`() {
        val baseStall = Stall(
            id = "base",
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            damage = 15f
        )

        // Benefit calculation uses StallRegistry.get(stallType). Chicken Rice base damage is 10.
        // Level 9: 10 + 9 * 6 = 64 -> (64-10)/10 = 5.4 -> 540%
        val benefit9 = baseStall.getUpgradeBenefit("Damage", 9)
        assertEquals("+540%", benefit9)

        // Level 10: (10 + 10 * 6) * 1.25 = 70 * 1.25 = 87.5 -> 88. (88-10)/10 = 7.8 -> 780%
        val benefit10 = baseStall.getUpgradeBenefit("Damage", 10)
        assertEquals("+780%", benefit10)
    }

    @Test
    fun `upgradeStall applies 25 percent boost at level 10`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))
        viewModel.gameJob?.cancel()

        val stallCoord = AxialCoordinate(0, 0)
        val stall = Stall(
            id = "s1",
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            damage = 64f, // (10 + 9*6)
            upgrades = mapOf("Damage" to 9),
            upgradeCount = 9
        )

        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, stall)),
            gold = 10000,
            selectedBoardStall = stallCoord
        )

        var attempts = 0
        while (viewModel.gameState.value.hexes[stallCoord]?.stall?.upgrades?.get("Damage") == 9 && attempts < 100) {
            viewModel.upgradeStallRandomly()
            attempts++
        }

        val upgradedStall = viewModel.gameState.value.hexes[stallCoord]?.stall!!
        assertEquals(10, upgradedStall.upgrades["Damage"])
        // (64 + 6) * 1.25 = 87.5 -> 88
        assertEquals(88f, upgradedStall.damage)
    }

    @Test
    fun `upgradeStall allows reaching level 11 Rate even if capped at 50ms`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))
        viewModel.gameJob?.cancel()

        val stallCoord = AxialCoordinate(0, 0)
        // Teh Tarik base rate 1000ms. Reduction 100ms. Floor 50ms.
        val stall = Stall(
            id = "s1",
            name = "Teh Tarik",
            cost = 150,
            color = Color.Blue,
            stallType = StallType.TEH_TARIK,
            fireRateMs = 50,
            upgrades = mapOf("Rate" to 10),
            upgradeCount = 10
        )

        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, stall)),
            gold = 10000,
            selectedBoardStall = stallCoord
        )

        var attempts = 0
        while (viewModel.gameState.value.hexes[stallCoord]?.stall?.upgrades?.get("Rate") == 10 && attempts < 100) {
            viewModel.upgradeStallRandomly()
            attempts++
        }

        val upgradedStall = viewModel.gameState.value.hexes[stallCoord]?.stall!!
        assertEquals(11, upgradedStall.upgrades["Rate"])
        assertEquals(50L, upgradedStall.fireRateMs)
    }
}
