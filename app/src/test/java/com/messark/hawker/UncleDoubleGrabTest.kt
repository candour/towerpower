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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UncleDoubleGrabTest {

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
    fun testTwoUnclesShouldNotGrabSameEnemy() {
        val uncle1Coord = AxialCoordinate(0, 0)
        val uncle2Coord = AxialCoordinate(1, 0)
        val enemyCoord = AxialCoordinate(0, 1)

        val uncle1 = Stall(
            id = "uncle-1",
            name = "Tray Return Uncle",
            cost = 250,
            color = androidx.compose.ui.graphics.Color.Gray,
            stallType = StallType.TRAY_RETURN_UNCLE,
            range = 2.0f,
            fireRateMs = 1000L,
            effectDurationMs = 5000L
        )

        val uncle2 = Stall(
            id = "uncle-2",
            name = "Tray Return Uncle",
            cost = 250,
            color = androidx.compose.ui.graphics.Color.Gray,
            stallType = StallType.TRAY_RETURN_UNCLE,
            range = 2.0f,
            fireRateMs = 1000L,
            effectDurationMs = 5000L
        )

        val enemy = Enemy(
            id = "target-enemy",
            health = 100,
            maxHealth = 100,
            position = PreciseAxialCoordinate(0f, 1f),
            path = listOf(AxialCoordinate(0,1), AxialCoordinate(0,2), AxialCoordinate(0,3), AxialCoordinate(0,4))
        )

        viewModel._gameState.update { it.copy(
            hexes = mapOf(
                uncle1Coord to HexTile(uncle1Coord, stall = uncle1),
                uncle2Coord to HexTile(uncle2Coord, stall = uncle2),
                enemyCoord to HexTile(enemyCoord)
            ),
            enemies = listOf(enemy),
            waveActive = true
        ) }

        // Tick to trigger firing
        viewModel.updateGame(1000L)

        val state = viewModel.gameState.value
        val stall1 = state.hexes[uncle1Coord]?.stall
        val stall2 = state.hexes[uncle2Coord]?.stall

        // If the bug is present, both will have "target-enemy"
        // We expect only one to have it.
        val grabCount = listOf(stall1?.heldEnemyId, stall2?.heldEnemyId).count { it == "target-enemy" }
        assertEquals("Only one uncle should grab the enemy", 1, grabCount)
    }
}
