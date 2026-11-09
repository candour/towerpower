package com.messark.hawker.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.messark.hawker.model.*
import java.io.File
import java.lang.reflect.Type

class ColorTypeAdapter : JsonSerializer<Color>, JsonDeserializer<Color> {
    override fun serialize(src: Color, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src.toArgb())

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Color =
        Color(json.asInt)
}

class TileTypeAdapter : JsonSerializer<TileType>, JsonDeserializer<TileType> {
    override fun serialize(src: TileType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src.name)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TileType =
        TileType.valueOf(json.asString)
}

data class PersistentGameState(
    val health: Int,
    val gold: Int,
    val hexes: List<HexTile>,
    val startPosition: AxialCoordinate?,
    val endPosition: AxialCoordinate?,
    val currentWave: Int,
    val score: Int,
    val kitchelinStars: Int? = 0,
    val currentLevel: Int? = 1,
    val showGraduationOverlay: Boolean? = false,
    val simulationTimeMs: Long? = 0L,
    val enemies: List<Enemy>? = emptyList(),
    val projectiles: List<Projectile>? = emptyList(),
    val puddles: List<StickyPuddle>? = emptyList(),
    val visualEffects: List<VisualEffect>? = emptyList(),
    val waveActive: Boolean? = false,
    val enemiesToSpawn: Int? = 0,
    val enemiesToSpawnList: List<EnemyType>? = emptyList(),
    val isBossWave: Boolean? = false,
    val bossWaveTriggerTimeMs: Long? = 0L,
    val lastSpawnTimeMs: Long? = 0L,
    val goldEarnedThisWave: Int? = 0,
    val activeBudgetBonuses: Int? = 0,
    val freeSpecificUpgrades: Int? = 0
)

fun GameState.toPersistent(): PersistentGameState = PersistentGameState(
    health = health,
    gold = gold,
    hexes = hexes.values.toList(),
    startPosition = startPosition,
    endPosition = endPosition,
    currentWave = currentWave,
    score = score,
    kitchelinStars = kitchelinStars,
    currentLevel = currentLevel,
    showGraduationOverlay = showGraduationOverlay,
    simulationTimeMs = simulationTimeMs,
    enemies = enemies,
    projectiles = projectiles,
    puddles = puddles,
    visualEffects = visualEffects,
    waveActive = waveActive,
    enemiesToSpawn = enemiesToSpawn,
    enemiesToSpawnList = enemiesToSpawnList,
    isBossWave = isBossWave,
    bossWaveTriggerTimeMs = bossWaveTriggerTimeMs,
    lastSpawnTimeMs = lastSpawnTimeMs,
    goldEarnedThisWave = goldEarnedThisWave,
    activeBudgetBonuses = activeBudgetBonuses,
    freeSpecificUpgrades = freeSpecificUpgrades
)

fun PersistentGameState.toGameState(): GameState = GameState(
    currentScreen = AppScreen.GAME,
    health = health,
    gold = gold,
    hexes = hexes.associateBy { it.coordinate },
    startPosition = startPosition,
    endPosition = endPosition,
    currentWave = currentWave,
    score = score,
    kitchelinStars = kitchelinStars ?: 0,
    currentLevel = currentLevel ?: 1,
    showGraduationOverlay = showGraduationOverlay ?: false,
    simulationTimeMs = simulationTimeMs ?: 0L,
    enemies = enemies ?: emptyList(),
    projectiles = projectiles ?: emptyList(),
    puddles = puddles ?: emptyList(),
    visualEffects = visualEffects ?: emptyList(),
    waveActive = waveActive ?: false,
    enemiesToSpawn = enemiesToSpawn ?: 0,
    enemiesToSpawnList = enemiesToSpawnList ?: emptyList(),
    isBossWave = isBossWave ?: false,
    bossWaveTriggerTimeMs = bossWaveTriggerTimeMs ?: 0L,
    lastSpawnTimeMs = lastSpawnTimeMs ?: 0L,
    goldEarnedThisWave = goldEarnedThisWave ?: 0,
    activeBudgetBonuses = activeBudgetBonuses ?: 0,
    freeSpecificUpgrades = freeSpecificUpgrades ?: 0
)

class GameStateRepository(private val context: Context) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorTypeAdapter())
        .registerTypeAdapter(TileType::class.java, TileTypeAdapter())
        .create()

    private val file: File
        get() = File(context.filesDir, "gamestate.json")

    @Synchronized
    fun saveGameState(state: GameState) {
        file.bufferedWriter().use { writer ->
            gson.toJson(state.toPersistent(), writer)
        }
    }

    @Synchronized
    fun loadGameState(): GameState? {
        val currentFile = file
        if (!currentFile.exists()) return null
        return try {
            currentFile.bufferedReader().use { reader ->
                val persistentState = gson.fromJson(reader, PersistentGameState::class.java)
                persistentState?.toGameState()
            }
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun deleteGameState() {
        val currentFile = file
        if (currentFile.exists()) {
            currentFile.delete()
        }
    }

    fun hasSavedGame(): Boolean = file.exists()
}
