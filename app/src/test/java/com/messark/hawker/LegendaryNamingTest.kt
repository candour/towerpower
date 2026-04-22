package com.messark.hawker

import android.app.Application
import com.messark.hawker.model.*
import com.messark.hawker.utils.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LegendaryNamingTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `stall gets suffix on first category reaching level 10`() = runBlocking {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        // Initial state
        viewModel.resetGame()
        var state = viewModel.gameState.first()
        val coord = state.hexes.keys.first { state.hexes[it]?.type == TileType.FLOOR }

        // Select Chicken Rice and place it
        val chickenRice = viewModel.availableStalls.value.find { it.baseName == "Chicken Rice" }!!
        viewModel.selectStall(chickenRice)
        viewModel.onCellClick(coord)

        state = viewModel.gameState.first()
        val placedStallCoord = coord
        assertNotNull(state.hexes[placedStallCoord]?.stall)

        // Force gold for upgrades
        val field = MainViewModel::class.java.getDeclaredField("_gameState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<GameState>
        stateFlow.value = state.copy(gold = 100000)

        // Select the placed stall
        viewModel.onCellClick(placedStallCoord)

        // Upgrade until a category hits 10.
        var namingCategories = emptyList<String>()
        var iterations = 0
        while (namingCategories.isEmpty() && iterations < 500) {
            viewModel.upgradeStall()
            state = viewModel.gameState.first()
            namingCategories = state.hexes[placedStallCoord]?.stall?.namingCategories ?: emptyList()
            iterations++
        }

        val stall = viewModel.gameState.first().hexes[placedStallCoord]?.stall!!
        assertEquals(1, stall.namingCategories.size)
        assertNotNull(stall.legendarySuffix)
        assertNull(stall.legendaryPrefix)
        assertTrue(stall.name.contains(stall.baseName))
        assertTrue(stall.name.endsWith(stall.legendarySuffix!!))
    }

    @Test
    fun `stall gets prefix on second different category reaching level 10`() = runBlocking {
        val application = mockk<Application>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>()
        val gameStateRepository = mockk<GameStateRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns kotlinx.coroutines.flow.flowOf(Settings())

        val viewModel = MainViewModel(application, settingsRepository, gameStateRepository)

        viewModel.resetGame()
        var state = viewModel.gameState.first()
        val coord = state.hexes.keys.first { state.hexes[it]?.type == TileType.FLOOR }

        val chickenRice = viewModel.availableStalls.value.find { it.baseName == "Chicken Rice" }!!
        viewModel.selectStall(chickenRice)
        viewModel.onCellClick(coord)

        val field = MainViewModel::class.java.getDeclaredField("_gameState")
        field.isAccessible = true
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<GameState>
        stateFlow.value = stateFlow.value.copy(gold = 1000000)

        viewModel.onCellClick(coord)

        var namingCategories = emptyList<String>()
        var iterations = 0
        while (namingCategories.size < 2 && iterations < 2000) {
            viewModel.upgradeStall()
            state = viewModel.gameState.first()
            namingCategories = state.hexes[coord]?.stall?.namingCategories ?: emptyList()
            iterations++
        }

        val stall = viewModel.gameState.first().hexes[coord]?.stall!!
        assertEquals(2, stall.namingCategories.size)
        assertNotNull(stall.legendarySuffix)
        assertNotNull(stall.legendaryPrefix)

        // Name should contain both
        assertTrue("Name should contain prefix: ${stall.name}", stall.name.contains(stall.legendaryPrefix!!.removeSuffix("-")))
        assertTrue("Name should contain base name: ${stall.name}", stall.name.contains(stall.baseName))
        assertTrue("Name should contain suffix: ${stall.name}", stall.name.contains(stall.legendarySuffix!!))
    }
}
