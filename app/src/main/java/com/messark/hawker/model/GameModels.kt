package com.messark.hawker.model

import androidx.compose.ui.graphics.Color
import com.messark.hawker.registry.StallRegistry

data class AxialCoordinate(val q: Int, val r: Int)

data class PreciseAxialCoordinate(val q: Float, val r: Float)

enum class AppScreen {
    LOADING, MAIN_MENU, GAME, OPTIONS
}

sealed class TileType(val name: String) {
    object FLOOR : TileType("FLOOR")
    object EDGE_NW : TileType("EDGE_NW")
    object EDGE_NE : TileType("EDGE_NE")
    object EDGE_SW : TileType("EDGE_SW")
    object EDGE_SE : TileType("EDGE_SE")
    object EDGE_TOP : TileType("EDGE_TOP")

    sealed class Obstruction(name: String) : TileType(name)
    object PILLAR : Obstruction("PILLAR")

    object GOAL_TABLE : TileType("GOAL_TABLE")
    object START : TileType("START")
    object END : TileType("END")
    object DRAIN : TileType("DRAIN")

    override fun toString(): String = name

    companion object {
        fun values(): Array<TileType> = arrayOf(
            FLOOR, EDGE_NW, EDGE_NE, EDGE_SW, EDGE_SE, EDGE_TOP, PILLAR, GOAL_TABLE, START, END, DRAIN
        )
        fun valueOf(name: String): TileType {
            return values().find { it.name == name } ?: throw IllegalArgumentException("No such TileType: $name")
        }
    }
}

data class HexTile(
    val coordinate: AxialCoordinate,
    val type: TileType = TileType.FLOOR,
    val stall: Stall? = null,
    val floorVariant: Int = 0
)

enum class StallType {
    TEH_TARIK, SATAY, CHICKEN_RICE, DURIAN, ICE_KACHANG, TRAY_RETURN_UNCLE, ATM, BAK_KUT_TEH;

    val isUtility: Boolean
        get() = this == TEH_TARIK || this == ICE_KACHANG || this == TRAY_RETURN_UNCLE || this == ATM || this == BAK_KUT_TEH
}

enum class TargetMode {
    FIRST, CLOSEST, STRONGEST, WEAKEST
}

/**
 * Represents a hawker stall (tower) in the game.
 * Stalls can be placed on the board to attack enemies.
 *
 * @property id Unique identifier for this specific stall instance.
 * @property name Display name of the stall (including prefixes/suffixes).
 * @property baseName The original name of the stall (e.g. "Satay").
 * @property cost Gold cost to purchase the stall.
 * @property color Color used for the stall's projectile and UI elements.
 * @property range Attack range in grid units.
 * @property damage Damage dealt per hit.
 * @property fireRateMs Time between shots in milliseconds.
 * @property lastFiredMs Timestamp of the last shot fired.
 * @property stallType The type of stall, determining its behavior.
 * @property rotation Rotation angle for direction-based attacks.
 * @property description Short flavor text and behavior summary.
 * @property upgradeCount Total number of upgrades applied.
 * @property upgrades Map of specific upgrade categories to their levels.
 * @property totalInvestment Total gold spent on this stall (cost + upgrades).
 * @property targetMode Strategy used to select which enemy to attack.
 * @property aoeRadius Radius for area-of-effect damage.
 * @property effectDurationMs Duration of secondary effects (e.g. puddles).
 * @property freezeDurationMs Duration of freeze effect in milliseconds.
 * @property uniqueTargetIds Set of enemy IDs that this stall has hit.
 * @property kills Total number of enemies killed by this stall.
 * @property legendaryPrefix Assigned legendary prefix, if any.
 * @property legendarySuffix Assigned legendary suffix, if any.
 * @property namingCategories Categories that have already triggered a name change.
 */
data class Stall(
    val id: String,
    val name: String,
    val baseName: String = name,
    val cost: Int,
    val color: Color,
    val range: Float = 3f, // Grid units
    val damage: Float = 10f,
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
    val freezeDurationMs: Long = 500L,
    val uniqueTargetIds: Set<String> = emptySet(),
    val kills: Int = 0,
    val legendaryPrefix: String? = null,
    val legendarySuffix: String? = null,
    val namingCategories: List<String> = emptyList(),
    val heldEnemyId: String? = null,
    val releaseTimeMs: Long = 0L,
    val disabledWaves: Int = 0,
    val isBlockable: Boolean = true
) {
    fun getUpgradeCost(): Int {
        val nextUpgradeIndex = upgradeCount + 1
        return Math.round(cost * (0.2f + nextUpgradeIndex * 0.1f)).toInt()
    }

    fun getUpgradeBenefit(category: String, level: Int): String {
        if (level <= 0) return ""
        val stallDef = StallRegistry.get(stallType)
        return stallDef.getUpgradeBenefit(category, level, stallDef)
    }
}

enum class EnemyType {
    SALARYMAN, TOURIST, AUNTIE, DELIVERY_RIDER, TIGER_MOM
}

enum class BuffType {
    ARMOR
}

data class Buff(
    val type: BuffType,
    val sourceId: String,
    val value: Float = 0f
)

data class Enemy(
    val id: String,
    val type: EnemyType = EnemyType.SALARYMAN,
    val health: Float,
    val maxHealth: Float,
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
    val stopDurationMs: Long = 0L,
    val speedBoostDurationMs: Long = 0L,
    val animationTimeMs: Long = 0L,
    val isFacingLeft: Boolean = false,
    val isGrabbed: Boolean = false,
    val buffs: List<Buff> = emptyList(),
    val buffingTargetId: String? = null,
    val hasActivatedBuff: Boolean = false
)

/**
 * Represents a projectile fired by a stall.
 *
 * @property id Unique identifier for the projectile.
 * @property position Current precise axial coordinate.
 * @property lastPosition Previous position for interpolation.
 * @property targetEnemyId ID of the target enemy, if any.
 * @property targetPosition Target coordinates.
 * @property damage Damage to deal on impact.
 * @property speed Movement speed in grid units per tick.
 * @property color Color of the projectile.
 * @property isFreeze Whether this projectile freezes enemies.
 * @property aoeRadius Radius of area-of-effect damage.
 * @property freezeDurationMs Duration of freeze effect.
 * @property isArc Whether the projectile follows an arc path.
 * @property startPosition Initial firing position.
 * @property sourceStallType Type of the stall that fired this.
 * @property sourceStallCoord Coordinate of the stall that fired this.
 * @property sourceStallId Unique ID of the stall that fired this.
 */
data class Projectile(
    val id: String,
    val position: PreciseAxialCoordinate,
    val lastPosition: PreciseAxialCoordinate? = null,
    val targetEnemyId: String?,
    val targetPosition: PreciseAxialCoordinate,
    val damage: Float,
    val speed: Float = 0.2f,
    val color: Color,
    val isFreeze: Boolean = false,
    val aoeRadius: Float = 0f,
    val freezeDurationMs: Long = 0L,
    val isArc: Boolean = false,
    val startPosition: PreciseAxialCoordinate? = null,
    val sourceStallType: StallType? = null,
    val sourceStallCoord: AxialCoordinate? = null,
    val sourceStallId: String? = null
)

enum class VisualEffectType {
    EXPANDING_CIRCLE, GAS_CLOUD, MONEY_SPRAY
}

/**
 * Represents a sticky puddle (e.g. Teh Tarik) that slows enemies.
 *
 * @property id Unique identifier for the puddle.
 * @property position Precise axial coordinate on the grid.
 * @property spawnTimeMs Timestamp when the puddle was created.
 * @property durationMs Total lifespan of the puddle.
 * @property sourceStallCoord Coordinate of the stall that created this puddle.
 * @property sourceStallId Unique ID of the stall that created this puddle.
 */
data class StickyPuddle(
    val id: String,
    val position: PreciseAxialCoordinate,
    val spawnTimeMs: Long,
    val durationMs: Long = 3000L,
    val sourceStallCoord: AxialCoordinate? = null,
    val sourceStallId: String? = null
)

data class VisualEffect(
    val id: String,
    val position: PreciseAxialCoordinate,
    val color: Color,
    val startTimeMs: Long,
    val durationMs: Long = 150L,
    val type: VisualEffectType = VisualEffectType.EXPANDING_CIRCLE
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
    val enemiesToSpawnList: List<EnemyType> = emptyList(),
    val isBossWave: Boolean = false,
    val bossWaveTriggerTimeMs: Long = 0L,
    val lastSpawnTimeMs: Long = 0L,
    val score: Int = 0,
    val activeTutorial: TutorialData? = null,
    val kitchelinStars: Int = 0,
    val showUpgradeOverlay: Boolean = false,
    val goldEarnedThisWave: Int = 0,
    val activeBudgetBonuses: Int = 0,
    val freeSpecificUpgrades: Int = 0,
    val showStarActionOverlay: Boolean = false,
    val lastWaveBonusGold: Int = 0,
    val showBonusMessage: Boolean = false,
    val isRemovePillarModeActive: Boolean = false,
    val lastShakeTimeMs: Long = 0
)
