package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.registry.EnemyRegistry
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import com.messark.hawker.utils.SpatialIndex
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SpatialIndexIntegrationTest {

    private lateinit var viewModel: MainViewModel
    private val application = mockk<Application>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val gameStateRepository = mockk<GameStateRepository>(relaxed = true)

    @Before
    fun setup() {
        viewModel = MainViewModel(application, settingsRepository, gameStateRepository)
    }

    @Test
    fun `test SpatialIndex findNearby`() {
        val enemies = listOf(
            createEnemy("1", 0f, 0f),
            createEnemy("2", 1f, 0f),
            createEnemy("3", 5f, 5f)
        )
        val index = SpatialIndex(enemies) { it.position }

        val nearby = index.findNearby(PreciseAxialCoordinate(0.5f, 0f), 1.0f)
        assertEquals(2, nearby.size)
        assertTrue(nearby.any { it.id == "1" })
        assertTrue(nearby.any { it.id == "2" })
    }

    @Test
    fun `test game loop with spatial index logic`() = runBlocking {
        // Prepare a state with a projectile and enemies
        val enemy1 = createEnemy("E1", 0f, 0f)
        val enemy2 = createEnemy("E2", 1f, 0f)

        val projectile = Projectile(
            id = "P1",
            position = PreciseAxialCoordinate(0.1f, 0f),
            targetEnemyId = "E1",
            targetPosition = PreciseAxialCoordinate(0f, 0f),
            damage = 100f, // Instant kill
            color = androidx.compose.ui.graphics.Color.Red,
            aoeRadius = 2.0f, // Should hit both
            sourceStallType = StallType.CHICKEN_RICE,
            sourceStallCoord = AxialCoordinate(0, -2),
            sourceStallId = "S1"
        )

        val initialHexes = mapOf(
            AxialCoordinate(0, -2) to HexTile(AxialCoordinate(0, -2), stall = StallRegistry.get(StallType.CHICKEN_RICE).toStall("S1"))
        )

        viewModel._gameState.value = GameState(
            enemies = listOf(enemy1, enemy2),
            projectiles = listOf(projectile),
            hexes = initialHexes,
            waveActive = true
        )

        // Run updateGame
        val fixedNow = 1_700_000_000_000L
        viewModel.updateGame(fixedNow)

        val state = viewModel.gameState.value
        // Both enemies should be hit and killed by AoE
        assertTrue("Enemies should be dead", state.enemies.isEmpty())
        assertTrue("Gold should be awarded", state.gold > 500)
    }

    private fun createEnemy(id: String, q: Float, r: Float): Enemy {
        val def = EnemyRegistry.get(EnemyType.SALARYMAN)
        return Enemy(
            id = id,
            type = EnemyType.SALARYMAN,
            health = def.baseHp,
            maxHealth = def.baseHp,
            position = PreciseAxialCoordinate(q, r),
            baseSpeed = def.baseSpeed,
            currentSpeed = def.baseSpeed,
            path = listOf(AxialCoordinate(0, 0), AxialCoordinate(0, 10)),
            currentPathIndex = 0
        )
    }
}
