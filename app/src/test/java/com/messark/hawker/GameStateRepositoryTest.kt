package com.messark.hawker

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.messark.hawker.model.*
import com.messark.hawker.utils.GameStateRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameStateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var repository: GameStateRepository
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        mockContext = mockk<Context>()
        every { mockContext.filesDir } returns filesDir
        repository = GameStateRepository(mockContext)
    }

    @Test
    fun `hasSavedGame returns false when file does not exist`() {
        assertFalse(repository.hasSavedGame())
        assertNull(repository.loadGameState())
    }

    @Test
    fun `save and load round trip preserves GameState correctly`() {
        val stall = Stall(
            id = "stall_1",
            name = "Super Teh Tarik",
            baseName = "Teh Tarik",
            cost = 100,
            color = Color.Yellow,
            stallType = StallType.TEH_TARIK,
            upgrades = mapOf("Duration" to 2),
            upgradeCount = 2,
            totalInvestment = 140
        )

        val hexMap = mapOf(
            AxialCoordinate(0, 0) to HexTile(
                coordinate = AxialCoordinate(0, 0),
                type = TileType.FLOOR,
                stall = stall,
                floorVariant = 1,
                isPermanentlyWet = true
            ),
            AxialCoordinate(1, 0) to HexTile(
                coordinate = AxialCoordinate(1, 0),
                type = TileType.DRAIN,
                stall = null
            )
        )

        val enemy = Enemy(
            id = "enemy_1",
            type = EnemyType.AUNTIE,
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(0.5f, 0.5f)
        )

        val projectile = Projectile(
            id = "proj_1",
            position = PreciseAxialCoordinate(0.2f, 0.2f),
            targetEnemyId = "enemy_1",
            targetPosition = PreciseAxialCoordinate(0.5f, 0.5f),
            damage = 15f,
            color = Color.Red
        )

        val puddle = StickyPuddle(
            id = "puddle_1",
            position = PreciseAxialCoordinate(0.1f, 0.1f),
            spawnTimeMs = 1000L,
            durationMs = 2000L
        )

        val visualEffect = VisualEffect(
            id = "fx_1",
            position = PreciseAxialCoordinate(0.3f, 0.3f),
            color = Color.Blue,
            startTimeMs = 500L,
            durationMs = 200L,
            type = VisualEffectType.EXPANDING_CIRCLE
        )

        val state = GameState(
            currentScreen = AppScreen.GAME,
            hexes = hexMap,
            health = 8,
            gold = 750,
            enemies = listOf(enemy),
            projectiles = listOf(projectile),
            puddles = listOf(puddle),
            visualEffects = listOf(visualEffect),
            startPosition = AxialCoordinate(0, -1),
            endPosition = AxialCoordinate(2, 2),
            waveActive = true,
            currentWave = 3,
            enemiesToSpawn = 5,
            enemiesToSpawnList = listOf(EnemyType.SALARYMAN, EnemyType.AUNTIE),
            isBossWave = false,
            bossWaveTriggerTimeMs = 0L,
            lastSpawnTimeMs = 1234L,
            score = 1200,
            kitchelinStars = 2,
            currentLevel = 2,
            showGraduationOverlay = false,
            simulationTimeMs = 45000L,
            goldEarnedThisWave = 150,
            activeBudgetBonuses = 1,
            freeSpecificUpgrades = 0
        )

        repository.saveGameState(state)
        assertTrue(repository.hasSavedGame())

        val loadedState = repository.loadGameState()
        assertNotNull(loadedState)
        loadedState!!

        assertEquals(AppScreen.GAME, loadedState.currentScreen)
        assertEquals(state.health, loadedState.health)
        assertEquals(state.gold, loadedState.gold)
        assertEquals(state.currentWave, loadedState.currentWave)
        assertEquals(state.score, loadedState.score)
        assertEquals(state.kitchelinStars, loadedState.kitchelinStars)
        assertEquals(state.currentLevel, loadedState.currentLevel)
        assertEquals(state.showGraduationOverlay, loadedState.showGraduationOverlay)
        assertEquals(state.simulationTimeMs, loadedState.simulationTimeMs)
        assertEquals(state.startPosition, loadedState.startPosition)
        assertEquals(state.endPosition, loadedState.endPosition)
        assertEquals(state.waveActive, loadedState.waveActive)
        assertEquals(state.enemiesToSpawn, loadedState.enemiesToSpawn)
        assertEquals(state.enemiesToSpawnList, loadedState.enemiesToSpawnList)
        assertEquals(state.isBossWave, loadedState.isBossWave)
        assertEquals(state.bossWaveTriggerTimeMs, loadedState.bossWaveTriggerTimeMs)
        assertEquals(state.lastSpawnTimeMs, loadedState.lastSpawnTimeMs)
        assertEquals(state.goldEarnedThisWave, loadedState.goldEarnedThisWave)
        assertEquals(state.activeBudgetBonuses, loadedState.activeBudgetBonuses)
        assertEquals(state.freeSpecificUpgrades, loadedState.freeSpecificUpgrades)

        // Check hexes
        assertEquals(state.hexes.size, loadedState.hexes.size)
        val loadedTile0 = loadedState.hexes[AxialCoordinate(0, 0)]
        assertNotNull(loadedTile0)
        assertEquals(TileType.FLOOR, loadedTile0?.type)
        assertEquals(1, loadedTile0?.floorVariant)
        assertTrue(loadedTile0?.isPermanentlyWet == true)
        assertEquals(stall.id, loadedTile0?.stall?.id)
        assertEquals(stall.name, loadedTile0?.stall?.name)
        assertEquals(stall.stallType, loadedTile0?.stall?.stallType)
        assertEquals(stall.totalInvestment, loadedTile0?.stall?.totalInvestment)

        val loadedTile1 = loadedState.hexes[AxialCoordinate(1, 0)]
        assertNotNull(loadedTile1)
        assertEquals(TileType.DRAIN, loadedTile1?.type)
        assertNull(loadedTile1?.stall)

        // Check enemies, projectiles, puddles, visualEffects
        assertEquals(1, loadedState.enemies.size)
        assertEquals("enemy_1", loadedState.enemies[0].id)
        assertEquals(1, loadedState.projectiles.size)
        assertEquals("proj_1", loadedState.projectiles[0].id)
        assertEquals(1, loadedState.puddles.size)
        assertEquals("puddle_1", loadedState.puddles[0].id)
        assertEquals(1, loadedState.visualEffects.size)
        assertEquals("fx_1", loadedState.visualEffects[0].id)
    }

    @Test
    fun `deleteGameState removes saved file`() {
        val state = GameState(health = 10, gold = 500)
        repository.saveGameState(state)
        assertTrue(repository.hasSavedGame())

        repository.deleteGameState()
        assertFalse(repository.hasSavedGame())
        assertNull(repository.loadGameState())
    }

    @Test
    fun `loadGameState returns null when file is corrupted`() {
        val saveFile = File(filesDir, "gamestate.json")
        saveFile.writeText("{ invalid json ...")
        assertNull(repository.loadGameState())
    }
}
