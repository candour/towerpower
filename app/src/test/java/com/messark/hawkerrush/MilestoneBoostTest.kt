package com.messark.hawkerrush

import android.app.Application
import androidx.compose.ui.graphics.Color
import com.messark.hawkerrush.model.*
import com.messark.hawkerrush.utils.GameStateRepository
import com.messark.hawkerrush.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            damage = 15
        )

        // Level 9: 15 + 9 * ( (15*0.3).toInt() + 2 ) = 15 + 9 * 6 = 69
        // Increase is 54. 54/15 = 3.6 -> 360%
        val benefit9 = baseStall.getUpgradeBenefit("Damage", 9, baseStall)
        assertEquals("+360%", benefit9)

        // Level 10: (15 + 10 * 6) * 1.25 = 75 * 1.25 = 93.75 -> 94
        // Increase is 94 - 15 = 79. 79/15 = 5.266... -> 527%
        val benefit10 = baseStall.getUpgradeBenefit("Damage", 10, baseStall)
        assertEquals("+527%", benefit10)
    }

    @Test
    fun `upgradeStall applies 25 percent boost at level 10`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        val stallCoord = AxialCoordinate(0, 0)
        // Base damage 15. Increase per level 6.
        // Damage at level 9 = 15 + 9 * 6 = 69.
        val stall = Stall(
            id = "s1",
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            damage = 69,
            upgrades = mapOf("Damage" to 9),
            upgradeCount = 9
        )

        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, stall)),
            gold = 10000,
            selectedBoardStall = stallCoord
        )

        // We might need to call it multiple times since category selection is random
        // but since we only care about Damage reaching 10, and it's 1/3 chance roughly.
        // Actually, let's just loop until Damage is upgraded to 10.
        var attempts = 0
        while (viewModel.gameState.value.hexes[stallCoord]?.stall?.upgrades?.get("Damage") == 9 && attempts < 100) {
            viewModel.upgradeStall()
            attempts++
        }

        val upgradedStall = viewModel.gameState.value.hexes[stallCoord]?.stall!!
        assertEquals(10, upgradedStall.upgrades["Damage"])
        // (69 + 6) * 1.25 = 75 * 1.25 = 93.75 -> 94
        assertEquals(94, upgradedStall.damage)

    @Test
    fun `upgradeStall respects 50ms minimum fire rate`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        val stallCoord = AxialCoordinate(0, 0)
        // Chicken Rice base rate 700ms. Reduction 70ms.
        // At level 9: 70ms.
        // Next upgrade would be 0ms (or 50ms minimum, but wait)
        // Level 10 upgrade: (70 - 70) * 0.75 = 0.
        // It should skip Rate and pick something else.
        val stall = Stall(
            id = "s1",
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            fireRateMs = 70,
            upgrades = mapOf("Rate" to 9),
            upgradeCount = 9
        )

        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, stall)),
            gold = 10000,
            selectedBoardStall = stallCoord
        )

        viewModel.upgradeStall()

        val upgradedStall = viewModel.gameState.value.hexes[stallCoord]?.stall!!
        assertEquals(9, upgradedStall.upgrades["Rate"] ?: 0)
        assertTrue(upgradedStall.upgradeCount == 10)
        assertTrue(
            upgradedStall.upgrades.any { (category, level) ->
                category != "Rate" && level > 0
            }
        )
    }
}
