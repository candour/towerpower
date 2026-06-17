package com.messark.hawker.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.*
import com.messark.hawker.model.*
import java.io.File
import java.lang.reflect.Type

class ColorTypeAdapter : JsonSerializer<Color>, JsonDeserializer<Color> {
    override fun serialize(src: Color, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toArgb())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Color {
        return Color(json.asInt)
    }
}

class TileTypeAdapter : JsonSerializer<TileType>, JsonDeserializer<TileType> {
    override fun serialize(src: TileType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.name)
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TileType {
        return TileType.valueOf(json.asString)
    }
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

class GameStateRepository(private val context: Context) {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorTypeAdapter())
        .registerTypeAdapter(TileType::class.java, TileTypeAdapter())
        .create()
    private val file = File(context.filesDir, "gamestate.json")

    fun saveGameState(state: GameState) {
        val persistentState = PersistentGameState(
            health = state.health,
            gold = state.gold,
            hexes = state.hexes.values.toList(),
            startPosition = state.startPosition,
            endPosition = state.endPosition,
            currentWave = state.currentWave,
            score = state.score,
            kitchelinStars = state.kitchelinStars,
            currentLevel = state.currentLevel,
            showGraduationOverlay = state.showGraduationOverlay,
            simulationTimeMs = state.simulationTimeMs,
            enemies = state.enemies,
            projectiles = state.projectiles,
            puddles = state.puddles,
            visualEffects = state.visualEffects,
            waveActive = state.waveActive,
            enemiesToSpawn = state.enemiesToSpawn,
            enemiesToSpawnList = state.enemiesToSpawnList,
            isBossWave = state.isBossWave,
            bossWaveTriggerTimeMs = state.bossWaveTriggerTimeMs,
            lastSpawnTimeMs = state.lastSpawnTimeMs,
            goldEarnedThisWave = state.goldEarnedThisWave,
            activeBudgetBonuses = state.activeBudgetBonuses,
            freeSpecificUpgrades = state.freeSpecificUpgrades
        )
        file.writeText(gson.toJson(persistentState))
    }

    fun loadGameState(): GameState? {
        if (!file.exists()) return null
        return try {
            val persistentState = gson.fromJson(file.readText(), PersistentGameState::class.java)
            GameState(
                currentScreen = AppScreen.GAME,
                health = persistentState.health,
                gold = persistentState.gold,
                hexes = persistentState.hexes.associateBy { it.coordinate },
                startPosition = persistentState.startPosition,
                endPosition = persistentState.endPosition,
                currentWave = persistentState.currentWave,
                score = persistentState.score,
                kitchelinStars = persistentState.kitchelinStars ?: 0,
                currentLevel = persistentState.currentLevel ?: 1,
                showGraduationOverlay = persistentState.showGraduationOverlay ?: false,
                simulationTimeMs = persistentState.simulationTimeMs ?: 0L,
                enemies = persistentState.enemies ?: emptyList(),
                projectiles = persistentState.projectiles ?: emptyList(),
                puddles = persistentState.puddles ?: emptyList(),
                visualEffects = persistentState.visualEffects ?: emptyList(),
                waveActive = persistentState.waveActive ?: false,
                enemiesToSpawn = persistentState.enemiesToSpawn ?: 0,
                enemiesToSpawnList = persistentState.enemiesToSpawnList ?: emptyList(),
                isBossWave = persistentState.isBossWave ?: false,
                bossWaveTriggerTimeMs = persistentState.bossWaveTriggerTimeMs ?: 0L,
                lastSpawnTimeMs = persistentState.lastSpawnTimeMs ?: 0L,
                goldEarnedThisWave = persistentState.goldEarnedThisWave ?: 0,
                activeBudgetBonuses = persistentState.activeBudgetBonuses ?: 0,
                freeSpecificUpgrades = persistentState.freeSpecificUpgrades ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    fun deleteGameState() {
        if (file.exists()) {
            file.delete()
        }
    }

    fun hasSavedGame(): Boolean {
        return file.exists()
    }
}
