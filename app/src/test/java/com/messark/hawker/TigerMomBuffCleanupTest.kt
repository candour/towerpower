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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TigerMomBuffCleanupTest {
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
    fun `Tiger Mom buff should be removed when she is grabbed even if no projectiles hit`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val momId = "mom"
        val targetId = "target"
        val uncleCoord = AxialCoordinate(2, 2)

        val path = listOf(AxialCoordinate(0, 0), AxialCoordinate(1, 0), AxialCoordinate(2, 0), AxialCoordinate(3, 0))

        val mom = Enemy(
            id = momId,
            type = EnemyType.TIGER_MOM,
            health = 80f,
            maxHealth = 80f,
            position = PreciseAxialCoordinate(2f, 2f),
            path = path,
            currentPathIndex = 0,
            buffingTargetId = targetId,
            isStopped = true,
            hasActivatedBuff = true
        )

        val target = Enemy(
            id = targetId,
            type = EnemyType.SALARYMAN,
            health = 50f,
            maxHealth = 50f,
            position = PreciseAxialCoordinate(0f, 0f),
            path = path,
            currentPathIndex = 0,
            buffs = listOf(Buff(BuffType.ARMOR, momId, 0.9f))
        )

        val uncleStall = Stall(
            id = "uncle",
            name = "Tray Return Uncle",
            cost = 250,
            color = androidx.compose.ui.graphics.Color.Gray,
            stallType = StallType.TRAY_RETURN_UNCLE,
            heldEnemyId = momId,
            releaseTimeMs = 5000L
        )

        val hexes = viewModel.gameState.value.hexes.toMutableMap()
        hexes[uncleCoord] = hexes[uncleCoord]!!.copy(stall = uncleStall)

        viewModel._gameState.value = viewModel.gameState.value.copy(
            currentScreen = AppScreen.GAME,
            enemies = listOf(mom, target),
            hexes = hexes,
            waveActive = true
        )

        // Run update. No projectiles are present.
        viewModel.updateGame(1000L)

        val newState = viewModel.gameState.value
        val updatedTarget = newState.enemies.find { it.id == targetId }
        assertNotNull("Target enemy should still exist", updatedTarget)

        assertFalse("Buff should be removed when source is grabbed",
            updatedTarget!!.buffs.any { it.sourceId == momId && it.type == BuffType.ARMOR })
    }

    @Test
    fun `Tiger Mom should reset hasActivatedBuff and resume movement when target dies`() {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository, kotlin.random.Random(42))

        val momId = "mom"
        val targetId = "target"
        val path = listOf(AxialCoordinate(0, 0), AxialCoordinate(1, 0), AxialCoordinate(2, 0), AxialCoordinate(3, 0))

        val mom = Enemy(
            id = momId,
            type = EnemyType.TIGER_MOM,
            health = 80f,
            maxHealth = 80f,
            position = PreciseAxialCoordinate(0f, 0f),
            path = path,
            currentPathIndex = 0,
            buffingTargetId = targetId,
            isStopped = true,
            hasActivatedBuff = true
        )

        // Target enemy is dead or missing from state
        viewModel._gameState.value = viewModel.gameState.value.copy(
            currentScreen = AppScreen.GAME,
            enemies = listOf(mom),
            waveActive = true
        )

        viewModel.updateGame(1000L)

        val newState = viewModel.gameState.value
        val updatedMom = newState.enemies.find { it.id == momId }
        assertNotNull("Tiger Mom should still exist", updatedMom)
        assertFalse("hasActivatedBuff should be reset to false when target is gone", updatedMom!!.hasActivatedBuff)
        org.junit.Assert.assertNull("buffingTargetId should be cleared", updatedMom.buffingTargetId)
        assertFalse("Tiger Mom should no longer be stopped", updatedMom.isStopped)
    }
}
