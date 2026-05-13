package com.messark.hawker

import android.app.Application
import androidx.compose.ui.graphics.Color
import com.messark.hawker.model.*
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.GameStateRepository
import com.messark.hawker.utils.SettingsRepository
import com.messark.hawker.utils.GridUtils
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class UndoUncleTeleportTest {
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
    fun `undoSell should clear heldEnemyId to prevent teleportation`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(Settings(showTutorials = false))

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val uncleCoord = AxialCoordinate(0, 0)
        val enemyId = "victim"
        val releaseTime = 10000L

        val uncle = StallRegistry.get(StallType.TRAY_RETURN_UNCLE).toStall().copy(
            id = "uncle",
            heldEnemyId = enemyId,
            releaseTimeMs = releaseTime
        )

        val enemy = Enemy(
            id = enemyId,
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(0f, 0f),
            isGrabbed = true,
            path = listOf(AxialCoordinate(0, 0))
        )

        val hexes = mapOf(
            uncleCoord to HexTile(uncleCoord, TileType.FLOOR, uncle),
            AxialCoordinate(1, 0) to HexTile(AxialCoordinate(1, 0), TileType.FLOOR),
            AxialCoordinate(2, 0) to HexTile(AxialCoordinate(2, 0), TileType.GOAL_TABLE)
        )

        viewModel._gameState.value = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexes,
            enemies = listOf(enemy),
            startPosition = AxialCoordinate(-1, 0),
            endPosition = AxialCoordinate(2, 0)
        )

        // 1. Sell the Uncle
        viewModel.onCellClick(uncleCoord) // Select
        viewModel.sellStall()

        var state = viewModel.gameState.value
        assertNull(state.hexes[uncleCoord]?.stall)
        val releasedEnemy = state.enemies.find { it.id == enemyId }!!
        assertFalse(releasedEnemy.isGrabbed)

        // Move the enemy away manually to simulate some game ticks
        val newPos = PreciseAxialCoordinate(2f, 0f)
        viewModel._gameState.value = state.copy(
            enemies = listOf(releasedEnemy.copy(position = newPos))
        )

        // 2. Undo the sell
        viewModel.undoSell()

        state = viewModel.gameState.value
        val restoredUncle = state.hexes[uncleCoord]?.stall!!
        assertEquals("Uncle should be restored", StallType.TRAY_RETURN_UNCLE, restoredUncle.stallType)

        // If the bug exists, heldEnemyId is still "victim"
        // assertEquals(null, restoredUncle.heldEnemyId)

        // 3. Trigger the release time
        viewModel.updateGame(releaseTime + 32)

        state = viewModel.gameState.value
        val finallyReleasedEnemy = state.enemies.find { it.id == enemyId }!!

        // If the bug exists, the enemy is teleported back to a neighbor of (0,0)
        val distToUncle = GridUtils.axialDistance(finallyReleasedEnemy.position, PreciseAxialCoordinate(0f, 0f))

        // We expect the enemy to STAY at its far position, not be teleported back
        assertNotEquals("Enemy should NOT be teleported back to the uncle", 0f, distToUncle, 0.1f)
        assertNotEquals("Enemy should NOT be at a neighbor of the uncle", 1f, distToUncle, 0.1f)
    }
}
