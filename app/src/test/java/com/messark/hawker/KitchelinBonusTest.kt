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

        viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))
        viewModel.gameJob?.cancel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBudgetBonusFromStarAction() {
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

        viewModel.chooseBudgetBonus()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(1, viewModel.gameState.value.activeBudgetBonuses)

        viewModel.startWave()
        val enemy = Enemy(
            id = "test-enemy",
            health = 10,
            maxHealth = 10,
            position = PreciseAxialCoordinate(0f, 0f),
            reward = 100,
            path = listOf(AxialCoordinate(0,0), AxialCoordinate(0,1), AxialCoordinate(0,2))
        )
        viewModel._gameState.update { it.copy(enemies = listOf(enemy), waveActive = true, enemiesToSpawn = 0) }

        val projectile = Projectile(
            id = "test-proj",
            position = PreciseAxialCoordinate(0f, 0f),
            targetEnemyId = "test-enemy",
            targetPosition = PreciseAxialCoordinate(0f, 0f),
            damage = 100,
            color = androidx.compose.ui.graphics.Color.Red,
            speed = 100.0f
        )
        viewModel._gameState.update { it.copy(projectiles = listOf(projectile)) }

        // Tick once
        viewModel.updateGame(1000L)

        val stateAfterProj = viewModel.gameState.value
        // Gold should be 610 (500 + 100 reward + 10 bonus) because wave completed in the same tick
        assertEquals(610, stateAfterProj.gold)
        assertEquals(100, stateAfterProj.goldEarnedThisWave)
        assertEquals(0, stateAfterProj.activeBudgetBonuses)
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

        viewModel.chooseFreeUpgrade()
        viewModel.chooseFreeUpgrade()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(2, viewModel.gameState.value.freeSpecificUpgrades)

        viewModel.upgradeStallSpecifically("Damage")

        var newState = viewModel.gameState.value
        var updatedStall = newState.hexes[coord]?.stall!!

        assertEquals(50, newState.gold)
        assertEquals(1, newState.freeSpecificUpgrades)
        assertEquals(1, updatedStall.upgradeCount)
        assertEquals(0, updatedStall.disabledWaves)

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

        viewModel.chooseBudgetBonus()
        viewModel.chooseBudgetBonus()
        assertEquals(0, viewModel.gameState.value.kitchelinStars)
        assertEquals(2, viewModel.gameState.value.activeBudgetBonuses)

        viewModel.startWave()
        val enemy = Enemy(id = "e1", health = 1, maxHealth = 1, position = PreciseAxialCoordinate(0f, 0f), reward = 100, path = listOf(AxialCoordinate(0,0), AxialCoordinate(0,1)))
        viewModel._gameState.update { it.copy(enemies = listOf(enemy), waveActive = true, enemiesToSpawn = 0) }

        val projectile = Projectile(id = "p1", position = PreciseAxialCoordinate(0f, 0f), targetEnemyId = "e1", targetPosition = PreciseAxialCoordinate(0f, 0f), damage = 10, color = androidx.compose.ui.graphics.Color.Red, speed = 100f)
        viewModel._gameState.update { it.copy(projectiles = listOf(projectile)) }

        viewModel.updateGame(1000L)

        // reward 100 + bonus (2 * 10% * 100) = 120
        assertEquals(120, viewModel.gameState.value.gold)
        assertEquals(0, viewModel.gameState.value.activeBudgetBonuses)
    }
}
