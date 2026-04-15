package com.messark.hawkerrush.model

import androidx.compose.ui.graphics.Color

data class AxialCoordinate(val q: Int, val r: Int)

data class PreciseAxialCoordinate(val q: Float, val r: Float)

enum class AppScreen {
    LOADING, MAIN_MENU, GAME
}

enum class TileType {
    FLOOR,
    EDGE_NW,
    EDGE_NE,
    EDGE_SW,
    EDGE_SE,
    EDGE_TOP,
    PILLAR,
    GOAL_TABLE,
    START,
    END
}

data class HexTile(
    val coordinate: AxialCoordinate,
    val type: TileType = TileType.FLOOR,
    val stall: Stall? = null,
    val floorVariant: Int = 0
)

enum class StallType {
    TEH_TARIK, SATAY, CHICKEN_RICE, DURIAN, ICE_KACHANG
}

enum class TargetMode {
    FIRST, CLOSEST, STRONGEST, WEAKEST
}

data class Stall(
    val id: String,
    val name: String,
    val cost: Int,
    val color: Color,
    val range: Float = 3f, // Grid units
    val damage: Int = 10,
    val fireRateMs: Long = 1000L,
    val lastFiredMs: Long = 0L,
    val stallType: StallType = StallType.CHICKEN_RICE,
    val rotation: Float = 0f, // For Satay cone direction
    val description: String = "",
    val upgradeCount: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
    val totalInvestment: Int = cost,
    val targetMode: TargetMode = TargetMode.FIRST,
    val aoeRadius: Float = 1.0f,
    val effectDurationMs: Long = 3000L,
    val freezeDurationMs: Long = 1500L
)

enum class EnemyType {
    SALARYMAN, TOURIST, AUNTIE, DELIVERY_RIDER
}

data class Enemy(
    val id: String,
    val type: EnemyType = EnemyType.SALARYMAN,
    val health: Int,
    val maxHealth: Int,
    val position: PreciseAxialCoordinate,
    val baseSpeed: Float = 0.05f, // Grid units per tick
    val currentSpeed: Float = 0.05f,
    val path: List<AxialCoordinate> = emptyList(),
    val currentPathIndex: Int = 0,
    val isDead: Boolean = false,
    val reward: Int = 20,
    val freezeDurationMs: Long = 0L,
    val lastStopMs: Long = 0L,
    val isStopped: Boolean = false,
    val stopDurationMs: Long = 0L
)

data class Projectile(
    val id: String,
    val position: PreciseAxialCoordinate,
    val lastPosition: PreciseAxialCoordinate? = null,
    val targetEnemyId: String?,
    val targetPosition: PreciseAxialCoordinate,
    val damage: Int,
    val speed: Float = 0.2f,
    val color: Color,
    val isFreeze: Boolean = false,
    val aoeRadius: Float = 0f,
    val freezeDurationMs: Long = 0L
)

data class StickyPuddle(
    val id: String,
    val position: PreciseAxialCoordinate,
    val spawnTimeMs: Long,
    val durationMs: Long = 3000L
)

data class VisualEffect(
    val id: String,
    val position: PreciseAxialCoordinate,
    val color: Color,
    val startTimeMs: Long,
    val durationMs: Long = 150L
)

data class GameState(
    val currentScreen: AppScreen = AppScreen.LOADING,
    val hexes: Map<AxialCoordinate, HexTile> = emptyMap(),
    val health: Int = 10, // 10 tables
    val gold: Int = 500,
    val selectedStallType: Stall? = null,
    val selectedBoardStall: AxialCoordinate? = null,
    val enemies: List<Enemy> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val puddles: List<StickyPuddle> = emptyList(),
    val visualEffects: List<VisualEffect> = emptyList(),
    val startPosition: AxialCoordinate? = null,
    val endPosition: AxialCoordinate? = null,
    val waveActive: Boolean = false,
    val currentWave: Int = 0,
    val enemiesToSpawn: Int = 0,
    val lastSpawnTimeMs: Long = 0L,
    val score: Int = 0
)
