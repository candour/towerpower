package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KitchelinBonusTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.messark.hawker.model.Settings())

        viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
    }

    @Test
    fun testGoldBonusFromKitchelinStars() {
        // Setup state with 2 stars and 500 gold
        val coord = AxialCoordinate(0, 0)
        val enemy = Enemy(
            id = "test-enemy",
            health = 10,
            maxHealth = 10,
            position = PreciseAxialCoordinate(0f, 0f),
            reward = 100,
            path = listOf(AxialCoordinate(0,0), AxialCoordinate(0,1))
        )

        viewModel._gameState.value = GameState(
            gold = 500,
            kitchelinStars = 2,
            enemies = listOf(enemy),
            hexes = mapOf(coord to HexTile(coord))
        )

        // Simulate projectile hitting enemy and killing it
        val projectile = Projectile(
            id = "test-proj",
            position = PreciseAxialCoordinate(0f, 0f),
            targetEnemyId = "test-enemy",
            targetPosition = PreciseAxialCoordinate(0f, 0f),
            damage = 100,
            color = androidx.compose.ui.graphics.Color.Red,
            sourceStallCoord = coord,
            sourceStallId = "some-stall",
            speed = 10.0f // Ensure it hits
        )

        viewModel._gameState.value = viewModel._gameState.value.copy(projectiles = listOf(projectile))

        // Trigger projectile handling via updateGame which calls handleProjectiles
        viewModel.updateGame(System.currentTimeMillis())

        // Reward (100) + Bonus (2 * 5% * 100 = 10) = 110
        // Initial gold (500) + 110 = 610
        assertEquals(610, viewModel.gameState.value.gold)
    }

    @Test
    fun testFreeSpecificUpgradeWithStar() {
        val coord = AxialCoordinate(2, 2)
        val stall = Stall(
            id = "stall-1",
            name = "Chicken Rice",
            cost = 100,
            color = androidx.compose.ui.graphics.Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            totalInvestment = 100
        )

        viewModel._gameState.value = GameState(
            gold = 50, // Not enough for regular specific upgrade ($200)
            kitchelinStars = 1,
            selectedBoardStall = coord,
            hexes = mapOf(coord to HexTile(coord, stall = stall))
        )

        // Apply specific upgrade
        viewModel.upgradeStallSpecifically("Damage")

        val newState = viewModel.gameState.value
        val updatedStall = newState.hexes[coord]?.stall!!

        // Gold should still be 50
        assertEquals(50, newState.gold)
        // Stars should be 0
        assertEquals(0, newState.kitchelinStars)
        // Upgrade count should be 1
        assertEquals(1, updatedStall.upgradeCount)
        // Total investment should still be 100 (since it was free)
        assertEquals(100, updatedStall.totalInvestment)
    }

    @Test
    fun testGoldBonusRoundingDown() {
        // Reward 25, 1 star = 5% of 25 = 1.25 -> 1

        val coord = AxialCoordinate(0, 0)
        val enemy = Enemy(
            id = "test-enemy",
            health = 10,
            maxHealth = 10,
            position = PreciseAxialCoordinate(0f, 0f),
            reward = 25,
            path = listOf(AxialCoordinate(0,0), AxialCoordinate(0,1))
        )

        viewModel._gameState.value = GameState(
            gold = 0,
            kitchelinStars = 1,
            enemies = listOf(enemy),
            hexes = mapOf(coord to HexTile(coord))
        )

        val projectile = Projectile(
            id = "test-proj",
            position = PreciseAxialCoordinate(0f, 0f),
            targetEnemyId = "test-enemy",
            targetPosition = PreciseAxialCoordinate(0f, 0f),
            damage = 100,
            color = androidx.compose.ui.graphics.Color.Red,
            speed = 10.0f // Ensure it hits
        )

        viewModel._gameState.value = viewModel._gameState.value.copy(projectiles = listOf(projectile))
        viewModel.updateGame(System.currentTimeMillis())

        // Reward 25 + floor(25 * 0.05) = 25 + 1 = 26
        assertEquals(26, viewModel.gameState.value.gold)
    }
}
