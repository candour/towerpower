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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UndeadEnemyTest {
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
    fun `enemy with zero health should be dead`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val enemyId = "enemy1"
        val enemy = Enemy(
            id = enemyId,
            health = 1f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(1f, 0f),
            path = listOf(AxialCoordinate(0, 0), AxialCoordinate(1, 0))
        )

        // Projectile that deals 0.5 damage
        // We need a buff that reduces damage. Tiger Mom buff is 0.9f reduction.
        // 5 * (1 - 0.9) = 0.5 damage.
        val buffedEnemy = enemy.copy(buffs = listOf(Buff(BuffType.ARMOR, "source", 0.9f)))

        viewModel._gameState.value = GameState(
            enemies = listOf(buffedEnemy),
            projectiles = listOf(
                Projectile(
                    id = "p1",
                    position = PreciseAxialCoordinate(1f, 0f), // already at enemy
                    targetEnemyId = enemyId,
                    targetPosition = PreciseAxialCoordinate(1f, 0f),
                    damage = 5f,
                    color = Color.Yellow
                )
            )
        )

        viewModel.updateGame(1000L)

        val newState = viewModel.gameState.value
        val updatedEnemy = newState.enemies.find { it.id == enemyId }
        assertNull("Enemy with health <= 0 should be dead and removed from list", updatedEnemy)
    }
}
