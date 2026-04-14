package com.messark.hawkerrush.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.*
import com.messark.hawkerrush.model.*
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

data class PersistentGameState(
    val health: Int,
    val gold: Int,
    val hexes: List<HexTile>,
    val startPosition: AxialCoordinate?,
    val endPosition: AxialCoordinate?,
    val currentWave: Int,
    val score: Int
)

class GameStateRepository(private val context: Context) {
    private val gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorTypeAdapter())
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
            score = state.score
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
                waveActive = false
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
