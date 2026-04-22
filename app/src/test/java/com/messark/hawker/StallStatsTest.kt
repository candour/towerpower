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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class StallStatsTest {
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
    fun `stalls track unique hits and kills`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        // Initial state with one stall and one enemy
        val stallCoord = AxialCoordinate(0, 0)
        val enemyId = "enemy1"
        val stallId = "s1"
        val stall = Stall(
            id = stallId,
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.CHICKEN_RICE,
            damage = 50
        )

        val enemy = Enemy(
            id = enemyId,
            health = 100,
            maxHealth = 100,
            position = PreciseAxialCoordinate(1f, 0f), // within range
            path = listOf(AxialCoordinate(0, 0), AxialCoordinate(1, 0), AxialCoordinate(2, 0))
        )

        // Set up initial state manually
        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, stall)),
            enemies = listOf(enemy),
            projectiles = listOf(
                Projectile(
                    id = "p1",
                    position = PreciseAxialCoordinate(1f, 0f), // already at enemy
                    targetEnemyId = enemyId,
                    targetPosition = PreciseAxialCoordinate(1f, 0f),
                    damage = 50,
                    color = Color.Yellow,
                    sourceStallCoord = stallCoord,
                    sourceStallId = stallId
                )
            )
        )

        // 1. First hit
        viewModel.updateGame(1000L)
        
        var newState = viewModel.gameState.value
        var updatedStall = newState.hexes[stallCoord]?.stall!!
        assertEquals(1, updatedStall.uniqueTargetIds.size)
        assertTrue("Stall should track specific enemyId", updatedStall.uniqueTargetIds.contains(enemyId))
        assertEquals(0, updatedStall.kills)
        assertEquals(50, newState.enemies[0].health)

        // 2. Second hit on SAME enemy (should NOT increment unique hits, but should kill and increment kills)
        viewModel._gameState.value = newState.copy(
            projectiles = listOf(
                Projectile(
                    id = "p2",
                    position = PreciseAxialCoordinate(1f, 0f),
                    targetEnemyId = enemyId,
                    targetPosition = PreciseAxialCoordinate(1f, 0f),
                    damage = 50,
                    color = Color.Yellow,
                    sourceStallCoord = stallCoord,
                    sourceStallId = stallId
                )
            )
        )

        viewModel.updateGame(1100L)
        
        newState = viewModel.gameState.value
        updatedStall = newState.hexes[stallCoord]?.stall!!
        assertEquals(1, updatedStall.uniqueTargetIds.size) // Still 1
        assertTrue("Stall should still contain enemyId", updatedStall.uniqueTargetIds.contains(enemyId))
        assertEquals(1, updatedStall.kills) // Now 1
        assertEquals(0, newState.enemies.size) // Dead
    }

    @Test
    fun `stalls do not attribute hits if replaced`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        val stallCoord = AxialCoordinate(0, 0)
        val enemyId = "enemy1"
        val oldStallId = "old_s1"
        val newStallId = "new_s1"
        
        val oldStall = Stall(id = oldStallId, name = "Old Stall", cost = 100, color = Color.Yellow)
        val newStall = Stall(id = newStallId, name = "New Stall", cost = 100, color = Color.Green)

        val enemy = Enemy(
            id = enemyId,
            health = 100,
            maxHealth = 100,
            position = PreciseAxialCoordinate(1f, 0f),
            path = listOf(AxialCoordinate(0, 0), AxialCoordinate(1, 0))
        )

        // Projectile from OLD stall
        viewModel._gameState.value = GameState(
            hexes = mapOf(stallCoord to HexTile(stallCoord, TileType.FLOOR, newStall)), // NEW stall already there
            enemies = listOf(enemy),
            projectiles = listOf(
                Projectile(
                    id = "p1",
                    position = PreciseAxialCoordinate(1f, 0f),
                    targetEnemyId = enemyId,
                    targetPosition = PreciseAxialCoordinate(1f, 0f),
                    damage = 50,
                    color = Color.Yellow,
                    sourceStallCoord = stallCoord,
                    sourceStallId = oldStallId
                )
            )
        )

        viewModel.updateGame(1000L)
        
        val newState = viewModel.gameState.value
        val currentStall = newState.hexes[stallCoord]?.stall!!
        
        assertEquals(newStallId, currentStall.id)
        assertEquals("New stall should NOT get hits from old stall projectile", 0, currentStall.uniqueTargetIds.size)
    }
}
