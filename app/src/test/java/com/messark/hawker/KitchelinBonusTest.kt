package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBudgetBonusFromStarAction() {
        // Setup state with 1 star
        val coord = AxialCoordinate(0, 0)

        viewModel._gameState.update { it.copy(
            gold = 500,
            kitchelinStars = 1,
            hexes = mapOf(coord to HexTile(coord)),
            waveActive = false,
            enemies = emptyList(),
            projectiles = emptyList(),
            goldEarnedThisWave = 0,
            activeBudgetBonuses = 0
        ) }

        // Choose Budget Bonus action
        viewModel.chooseBudgetBonus()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(1, viewModel.gameState.value.activeBudgetBonuses)

        // Start wave
        viewModel.startWave()
        testDispatcher.scheduler.advanceUntilIdle()

        val enemy = Enemy(
            id = "test-enemy",
            health = 10,
            maxHealth = 10,
            position = PreciseAxialCoordinate(0f, 0f),
            reward = 100,
            path = listOf(AxialCoordinate(0,0), AxialCoordinate(0,1))
        )
        viewModel._gameState.update { it.copy(enemies = listOf(enemy), waveActive = true, enemiesToSpawn = 0) }

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

        viewModel._gameState.update { it.copy(projectiles = listOf(projectile)) }

        // Trigger projectile handling
        viewModel.updateGame(1000L)

        // Gold should be 600 (500 + 100) now, but bonus not yet awarded
        assertEquals(600, viewModel.gameState.value.gold)
        assertEquals(100, viewModel.gameState.value.goldEarnedThisWave)

        // End wave
        viewModel._gameState.update { it.copy(enemies = emptyList(), enemiesToSpawn = 0, waveActive = true) }
        viewModel.updateGame(2000L)

        // Bonus should be 10% of 100 = 10
        // Total gold = 600 + 10 = 610
        assertEquals(610, viewModel.gameState.value.gold)
        assertEquals(0, viewModel.gameState.value.activeBudgetBonuses)
    }

    @Test
    fun testFreeSpecificUpgradeAction() {
        val coord = AxialCoordinate(2, 2)
        val stall = Stall(
            id = "stall-1",
            name = "Chicken Rice",
            cost = 100,
            color = androidx.compose.ui.graphics.Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            totalInvestment = 100
        )

        viewModel._gameState.update { it.copy(
            gold = 50,
            kitchelinStars = 2,
            selectedBoardStall = coord,
            hexes = mapOf(coord to HexTile(coord, stall = stall)),
            waveActive = false,
            freeSpecificUpgrades = 0
        ) }

        // Choose Free Upgrade action twice
        viewModel.chooseFreeUpgrade()
        viewModel.chooseFreeUpgrade()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(2, viewModel.gameState.value.freeSpecificUpgrades)

        // Apply first specific upgrade
        viewModel.upgradeStallSpecifically("Damage")

        var newState = viewModel.gameState.value
        var updatedStall = newState.hexes[coord]?.stall!!

        // Gold should still be 50
        assertEquals(50, newState.gold)
        // freeSpecificUpgrades should be 1 now
        assertEquals(1, newState.freeSpecificUpgrades)
        // Upgrade count should be 1
        assertEquals(1, updatedStall.upgradeCount)
        // Should NOT be disabled
        assertEquals(0, updatedStall.disabledWaves)

        // Apply second specific upgrade
        viewModel.upgradeStallSpecifically("Range")

        newState = viewModel.gameState.value
        updatedStall = newState.hexes[coord]?.stall!!

        assertEquals(50, newState.gold)
        assertEquals(0, newState.freeSpecificUpgrades)
        assertEquals(2, updatedStall.upgradeCount)
        assertEquals(0, updatedStall.disabledWaves)
    }

    @Test
    fun testStackedBudgetBonuses() {
        val coord = AxialCoordinate(0, 0)

        viewModel._gameState.update { it.copy(
            gold = 0,
            kitchelinStars = 2,
            hexes = mapOf(coord to HexTile(coord)),
            waveActive = false,
            enemies = emptyList(),
            projectiles = emptyList(),
            goldEarnedThisWave = 0,
            activeBudgetBonuses = 0
        ) }

        // Choose Budget Bonus twice
        viewModel.chooseBudgetBonus()
        viewModel.chooseBudgetBonus()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(2, viewModel.gameState.value.activeBudgetBonuses)

        // Start wave and earn gold
        viewModel.startWave()
        testDispatcher.scheduler.advanceUntilIdle()
        val enemy = Enemy(id = "e1", health = 1, maxHealth = 1, position = PreciseAxialCoordinate(0f, 0f), reward = 100, path = listOf(AxialCoordinate(0,0)))
        viewModel._gameState.update { it.copy(enemies = listOf(enemy), waveActive = true, enemiesToSpawn = 0) }

        val projectile = Projectile(id = "p1", position = PreciseAxialCoordinate(0f, 0f), targetEnemyId = "e1", targetPosition = PreciseAxialCoordinate(0f, 0f), damage = 10, color = androidx.compose.ui.graphics.Color.Red)
        viewModel._gameState.update { it.copy(projectiles = listOf(projectile)) }
        viewModel.updateGame(1000L)

        assertEquals(100, viewModel.gameState.value.gold)

        // End wave
        viewModel._gameState.update { it.copy(enemies = emptyList(), enemiesToSpawn = 0, waveActive = true) }
        viewModel.updateGame(2000L)

        // Bonus: 2 * 10% * 100 = 20
        assertEquals(120, viewModel.gameState.value.gold)
    }
}
