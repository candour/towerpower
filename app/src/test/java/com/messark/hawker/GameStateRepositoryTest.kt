package com.messark.hawker

import android.content.Context
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GameStateRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var repository: GameStateRepository

    @Before
    fun setup() {
        tempDir = createTempDirectory("gamestate_test").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
        repository = GameStateRepository(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoadGameState() {
        assertFalse(repository.hasSavedGame())
        assertNull(repository.loadGameState())

        val hexes = mapOf(
            AxialCoordinate(0, 0) to HexTile(
                coordinate = AxialCoordinate(0, 0),
                type = TileType.FLOOR
            )
        )
        val originalState = GameState(
            currentScreen = AppScreen.GAME,
            health = 8,
            gold = 250,
            hexes = hexes,
            startPosition = AxialCoordinate(0, 0),
            endPosition = AxialCoordinate(2, 2),
            currentWave = 3,
            score = 1500,
            kitchelinStars = 2,
            currentLevel = 2,
            simulationTimeMs = 12000L,
            waveActive = true
        )

        repository.saveGameState(originalState)

        assertTrue(repository.hasSavedGame())

        val loadedState = repository.loadGameState()
        assertNotNull(loadedState)
        assertEquals(AppScreen.GAME, loadedState!!.currentScreen)
        assertEquals(8, loadedState.health)
        assertEquals(250, loadedState.gold)
        assertEquals(1, loadedState.hexes.size)
        assertEquals(TileType.FLOOR, loadedState.hexes[AxialCoordinate(0, 0)]?.type)
        assertEquals(AxialCoordinate(0, 0), loadedState.startPosition)
        assertEquals(AxialCoordinate(2, 2), loadedState.endPosition)
        assertEquals(3, loadedState.currentWave)
        assertEquals(1500, loadedState.score)
        assertEquals(2, loadedState.kitchelinStars)
        assertEquals(2, loadedState.currentLevel)
        assertEquals(12000L, loadedState.simulationTimeMs)
        assertTrue(loadedState.waveActive)
    }

    @Test
    fun testDeleteGameState() {
        val state = GameState(gold = 100)
        repository.saveGameState(state)
        assertTrue(repository.hasSavedGame())

        repository.deleteGameState()
        assertFalse(repository.hasSavedGame())
        assertNull(repository.loadGameState())
    }

    @Test
    fun testCorruptedFileReturnsNull() {
        val file = File(tempDir, "gamestate.json")
        file.writeText("{ invalid json ...")

        assertTrue(repository.hasSavedGame())
        assertNull(repository.loadGameState())
    }
}
