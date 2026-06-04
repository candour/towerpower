package com.messark.hawker.registry

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import com.messark.hawker.model.*
import com.messark.hawker.utils.GridUtils
import com.messark.hawker.utils.StallUpgradeManager
import java.util.*

sealed class FireResult {
    data class NewProjectile(val projectile: Projectile, val updatedStall: Stall? = null) : FireResult()
    data class NewPuddle(val puddle: StickyPuddle) : FireResult()
    data class HoldEnemy(val targetId: String, val releaseTimeMs: Long) : FireResult()
}

interface StallBehavior {
    fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float
    fun getFreezeModifier(enemy: Enemy, baseDuration: Long): Long
    fun getSpeedBoost(enemy: Enemy): Long
    fun selectTarget(
        stall: Stall,
        stallCoord: AxialCoordinate,
        enemiesByMode: Map<TargetMode, List<Enemy>>,
        enemySpatialIndex: com.messark.hawker.utils.SpatialIndex<Enemy>,
        obstructions: List<AxialCoordinate>,
        newlyGrabbedEnemyIds: Set<String>
    ): Enemy?
    fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult
}

interface EnemyBehavior {
    fun getPuddleSlowMultiplier(): Float
    fun updateSpecialBehavior(enemy: Enemy, currentTimeMs: Long): Enemy
}

open class DefaultStallBehavior : StallBehavior {
    override fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float = baseDamage
    override fun getFreezeModifier(enemy: Enemy, baseDuration: Long): Long = 0L
    override fun getSpeedBoost(enemy: Enemy): Long = 0L

    override fun selectTarget(
        stall: Stall,
        stallCoord: AxialCoordinate,
        enemiesByMode: Map<TargetMode, List<Enemy>>,
        enemySpatialIndex: com.messark.hawker.utils.SpatialIndex<Enemy>,
        obstructions: List<AxialCoordinate>,
        newlyGrabbedEnemyIds: Set<String>
    ): Enemy? {
        val stallPos = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat())

        // 1. Efficiently pre-filter enemies within range using Spatial Index
        val nearbyEnemies = enemySpatialIndex.findNearby(stallPos, stall.range)
            .filter { !it.isGrabbed && it.id !in newlyGrabbedEnemyIds }

        if (nearbyEnemies.isEmpty()) return null

        // 2. Filter by Line-of-Sight if the stall is blockable
        val visibleEnemies = if (stall.isBlockable && obstructions.isNotEmpty()) {
            nearbyEnemies.filter { !GridUtils.isLineOfSightBlocked(stallCoord, it.position, obstructions) }
        } else {
            nearbyEnemies
        }

        if (visibleEnemies.isEmpty()) return null

        // 3. Select best target from visible candidates based on target mode
        return when (stall.targetMode) {
            TargetMode.CLOSEST -> visibleEnemies.minByOrNull { GridUtils.axialDistance(it.position, stallPos) }
            TargetMode.FIRST -> {
                val visibleIds = visibleEnemies.mapTo(mutableSetOf()) { it.id }
                enemiesByMode[TargetMode.FIRST]?.firstOrNull { it.id in visibleIds }
            }
            TargetMode.STRONGEST -> visibleEnemies.maxByOrNull { it.health }
            TargetMode.WEAKEST -> visibleEnemies.minByOrNull { it.health }
        }
    }

    override fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        val stallPos = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat())
        return FireResult.NewProjectile(
            projectile = Projectile(
                id = UUID.randomUUID().toString(),
                position = stallPos,
                targetEnemyId = target.id,
                targetPosition = target.position,
                damage = stall.damage,
                color = stall.color,
                isFreeze = false,
                freezeDurationMs = stall.freezeDurationMs,
                aoeRadius = stall.aoeRadius,
                sourceStallType = stallDefinition.type,
                sourceStallCoord = stallCoord,
                sourceStallId = stall.id
            )
        )
    }
}

class TehTarikStallBehavior : DefaultStallBehavior() {
    override fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        val targetCoord = GridUtils.hexRound(target.position.q, target.position.r)
        val isOnDrain = hexes[targetCoord]?.type == TileType.DRAIN
        val duration = if (isOnDrain) stall.effectDurationMs / 2 else stall.effectDurationMs
        return FireResult.NewPuddle(
            StickyPuddle(
                id = UUID.randomUUID().toString(),
                position = target.position,
                spawnTimeMs = currentTimeMs,
                durationMs = duration,
                sourceStallCoord = stallCoord,
                sourceStallId = stall.id
            )
        )
    }
}

class SatayStallBehavior : DefaultStallBehavior() {
    override fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float {
        return when (enemy.type) {
            EnemyType.TOURIST -> baseDamage * 2f
            EnemyType.AUNTIE -> baseDamage * 0.5f
            else -> baseDamage
        }
    }

    override fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        val stallPos = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat())
        val dx = (target.position.q + target.position.r / 2f) - (stallCoord.q + stallCoord.r / 2f)
        val dy = (target.position.r - stallCoord.r) * GridUtils.ISOMETRIC_Y_FACTOR
        val angle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
        return FireResult.NewProjectile(
            projectile = Projectile(
                id = UUID.randomUUID().toString(),
                position = stallPos,
                targetEnemyId = null,
                targetPosition = target.position,
                damage = stall.damage,
                color = stallDefinition.projectileColor,
                speed = stallDefinition.projectileSpeed,
                aoeRadius = stall.aoeRadius,
                isArc = true,
                startPosition = stallPos,
                sourceStallType = stallDefinition.type,
                sourceStallCoord = stallCoord,
                sourceStallId = stall.id
            ),
            updatedStall = stall.copy(rotation = angle)
        )
    }
}

class DurianStallBehavior : DefaultStallBehavior() {
    override fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float {
        return when (enemy.type) {
            EnemyType.DELIVERY_RIDER -> baseDamage * 1.5f
            else -> baseDamage
        }
    }

    override fun getSpeedBoost(enemy: Enemy): Long {
        if (enemy.type == EnemyType.SALARYMAN) {
            return 2000L
        }
        return 0L
    }
}

class IceKachangStallBehavior : DefaultStallBehavior() {
    override fun getFreezeModifier(enemy: Enemy, baseDuration: Long): Long {
        return when (enemy.type) {
            EnemyType.SALARYMAN -> baseDuration * 2
            EnemyType.TOURIST -> baseDuration / 2
            else -> baseDuration
        }
    }

    override fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        val stallPos = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat())
        return FireResult.NewProjectile(
            projectile = Projectile(
                id = UUID.randomUUID().toString(),
                position = stallPos,
                targetEnemyId = target.id,
                targetPosition = target.position,
                damage = stall.damage,
                color = stall.color,
                isFreeze = true,
                freezeDurationMs = stall.freezeDurationMs,
                aoeRadius = stall.aoeRadius,
                sourceStallType = stallDefinition.type,
                sourceStallCoord = stallCoord,
                sourceStallId = stall.id
            )
        )
    }
}

class TrayReturnUncleBehavior : DefaultStallBehavior() {
    override fun fire(
        stallDefinition: StallDefinition,
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        return FireResult.HoldEnemy(target.id, currentTimeMs + stall.effectDurationMs)
    }
}

open class DefaultEnemyBehavior : EnemyBehavior {
    override fun getPuddleSlowMultiplier(): Float = 0.6f
    override fun updateSpecialBehavior(enemy: Enemy, currentTimeMs: Long): Enemy = enemy
}

class TouristEnemyBehavior : DefaultEnemyBehavior() {
    override fun updateSpecialBehavior(enemy: Enemy, currentTimeMs: Long): Enemy {
        var isStopped = enemy.isStopped
        var stopDurationMs = enemy.stopDurationMs
        var lastStopMs = if (enemy.lastStopMs == 0L) currentTimeMs else enemy.lastStopMs

        if (isStopped) {
            stopDurationMs -= 32
            if (stopDurationMs <= 0) {
                isStopped = false
                lastStopMs = currentTimeMs
            }
        } else if (currentTimeMs - lastStopMs > 8000) {
            isStopped = true
            stopDurationMs = 2000L
        }
        return enemy.copy(isStopped = isStopped, stopDurationMs = stopDurationMs, lastStopMs = lastStopMs)
    }
}

class DeliveryRiderEnemyBehavior : DefaultEnemyBehavior() {
    override fun getPuddleSlowMultiplier(): Float = 0.2f
}

class AuntieEnemyBehavior : DefaultEnemyBehavior() {
    override fun getPuddleSlowMultiplier(): Float = 0.8f
}

data class StallDefinition(
    val type: StallType,
    val name: String,
    val cost: Int,
    val color: Color,
    val range: Float,
    val damage: Float,
    val fireRateMs: Long,
    val description: String,
    val tutorialTitle: String,
    val signatureMove: String,
    val tutorialDescription: String,
    val spriteRect: IntRect,
    val aoeRadius: Float = 0f,
    val effectDurationMs: Long = 0L,
    val freezeDurationMs: Long = 0L,
    val projectileSpeed: Float = 0.2f,
    val isArc: Boolean = false,
    val projectileColor: Color = color,
    val passiveIncome: Int = 0,
    val visualEffectType: VisualEffectType = VisualEffectType.EXPANDING_CIRCLE,
    val visualEffectColor: Color? = null,
    val visualEffectDuration: Long = 150L,
    val isBlockable: Boolean = true,
    val behavior: StallBehavior = DefaultStallBehavior()
) {
    fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float {
        return behavior.applyDamageModifiers(enemy, baseDamage)
    }

    fun getFreezeModifier(enemy: Enemy, baseDuration: Long): Long {
        return behavior.getFreezeModifier(enemy, baseDuration)
    }

    fun getSpeedBoost(enemy: Enemy): Long {
        return behavior.getSpeedBoost(enemy)
    }

    fun fire(
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long,
        hexes: Map<AxialCoordinate, HexTile>
    ): FireResult {
        return behavior.fire(this, stall, stallCoord, target, currentTimeMs, hexes)
    }

    fun toStall(id: String = UUID.randomUUID().toString()): Stall {
        return Stall(
            id = id,
            name = name,
            baseName = name,
            cost = cost,
            color = color,
            range = range,
            damage = damage,
            fireRateMs = fireRateMs,
            stallType = type,
            description = description,
            aoeRadius = aoeRadius,
            effectDurationMs = effectDurationMs,
            freezeDurationMs = freezeDurationMs,
            isBlockable = isBlockable
        )
    }

    fun getUpgradeBenefit(category: String, level: Int, baseStall: StallDefinition): String {
        return StallUpgradeManager.getBenefitString(category, level, baseStall)
    }
}

data class EnemyDefinition(
    val type: EnemyType,
    val name: String,
    val description: String,
    val baseHp: Float,
    val baseSpeed: Float,
    val reward: Int,
    val spriteRow: Int,
    val behavior: EnemyBehavior = DefaultEnemyBehavior()
) {
    fun getPuddleSlowMultiplier(): Float {
        return behavior.getPuddleSlowMultiplier()
    }

    fun updateSpecialBehavior(enemy: Enemy, currentTimeMs: Long): Enemy {
        return behavior.updateSpecialBehavior(enemy, currentTimeMs)
    }

    fun getHp(wave: Int): Float {
        return (baseHp * Math.pow(1.1, (wave - 1).toDouble())).toFloat()
    }

    fun toEnemy(id: String = UUID.randomUUID().toString(), wave: Int, position: PreciseAxialCoordinate, path: List<AxialCoordinate>, isFacingLeft: Boolean): Enemy {
        val hp = getHp(wave)
        return Enemy(
            id = id,
            type = type,
            health = hp,
            maxHealth = hp,
            position = position,
            baseSpeed = baseSpeed,
            currentSpeed = baseSpeed,
            path = path,
            currentPathIndex = 0,
            reward = reward,
            isFacingLeft = isFacingLeft
        )
    }
}

object StallRegistry {
    private val definitions = mapOf(
        StallType.TEH_TARIK to StallDefinition(
            type = StallType.TEH_TARIK,
            name = "Teh Tarik",
            cost = 150,
            color = Color.Blue,
            range = 3f,
            damage = 0f,
            fireRateMs = 1000L,
            description = "Creates slowing puddles",
            tutorialTitle = "Teh Tarik Maestro (Movement Slow)",
            signatureMove = "The Perpetual Tarik Puddle",
            tutorialDescription = "Welcome to the Teh Tarik Maestro, where the art of 'pulling' tea is a high-level tactical maneuver. This Maestro doesn't just make your enemies slower; he makes the very ground they walk on sticky. Utilizing a massive pair of custom cups, he performs a continuous, mesmerizing 'tarik' high in the air. Each 'pull' perfectly places a wide, frothy Perpetual Tarik Puddle of viscous, sweet milk tea. The tea is so thick and syrupy that enemies stepping into it are immediately bogged down, their speed cut in half as they struggle through the delicious, sticky mess. A crowd favorite for slowing the rush.",
            spriteRect = IntRect(22, 41, 330, 451),
            effectDurationMs = 3000L,
            behavior = TehTarikStallBehavior()
        ),
        StallType.SATAY to StallDefinition(
            type = StallType.SATAY,
            name = "Satay",
            cost = 200,
            color = Color.Red,
            range = 2.5f,
            damage = 30f,
            fireRateMs = 1500L,
            description = "Area chili sauce damage",
            tutorialTitle = "Uncle's Satay Stall (AoE Damage)",
            signatureMove = "The Chili Conflagration",
            tutorialDescription = "Wah, smells so shiok! Behind this unassuming grill, the Satay Uncle is fanning a fiery revolution. Watch out for his signature Chili Conflagration—the chili isn't just spicy; it's explosive. He loads up a massive spoon and, with a precision usually reserved for satay-counting, launches a gigantic splash of his secret, explosive chili sauce. When it hits, it covers a wide circle, dousing groups of enemies in a sticky, burning chili storm that eats away at their health (and their willpower). If you need a crowd-control burn, this Uncle is the OG.",
            spriteRect = IntRect(14, 1541, 322, 1951),
            aoeRadius = 2.0f,
            projectileSpeed = 0.3f,
            isArc = true,
            projectileColor = Color.White,
            visualEffectType = VisualEffectType.GAS_CLOUD,
            visualEffectColor = Color.Red.copy(alpha = 0.3f),
            visualEffectDuration = 500L,
            behavior = SatayStallBehavior()
        ),
        StallType.CHICKEN_RICE to StallDefinition(
            type = StallType.CHICKEN_RICE,
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            range = 4f,
            damage = 20f,
            fireRateMs = 500L,
            description = "High single-target damage",
            tutorialTitle = "Ah Hock’s Chicken Rice Stand (Single-Target DPS)",
            signatureMove = "The Garlic-Ginger Gatling Gun",
            tutorialDescription = "Ah Hock’s Chicken Rice is famous for two things: the tenderest steamed chicken and the single-minded focus of his attacks. Don’t be fooled by the simple setup; this stand is your base single-target workhorse. When an enemy is targeted, Ah Hock deploys his Garlic-Ginger Gatling Gun. Instead of bullets, he’s launching high-velocity, precision-aimed balls of marinated meat, dousing targets in flavor-infused damage. It’s consistent, it’s powerful, and it never runs out of stock. A classic choice that never fails.",
            spriteRect = IntRect(22, 541, 330, 971)
        ),
        StallType.DURIAN to StallDefinition(
            type = StallType.DURIAN,
            name = "Durian",
            cost = 300,
            color = Color(0xFF4CAF50),
            range = 3f,
            damage = 100f,
            fireRateMs = 2000L,
            description = "Massive damage, slow fire",
            tutorialTitle = "The King Durian Bunker (High Damage/Slight AoE)",
            signatureMove = "The Spiky Cataclysm",
            tutorialDescription = "They call the Durian the King of Fruits, and this stall is the King of Damage. The King Durian Bunker is fortified with armor-plating and smells… well, like a durian. When the King’s crew makes a sale, they aren't selling just fruit; they are deploying a localized explosive. Using a heavy-duty pneumatic launcher, they fire an overripe, spikey Durian bomb into the largest cluster of enemies. Upon impact, it delivers a high-damage, single-target blow, followed immediately by a Spiky Cataclysm AoE explosion as the potent, heavy aroma bursts outward. It’s high-cost and slow-reloading, but the raw damage (and the scent) is devastating.",
            spriteRect = IntRect(33, 1041, 341, 1398),
            aoeRadius = 1.0f,
            visualEffectColor = Color(0xFFCDDC39).copy(alpha = 0.5f),
            behavior = DurianStallBehavior()
        ),
        StallType.ICE_KACHANG to StallDefinition(
            type = StallType.ICE_KACHANG,
            name = "Ice Kachang",
            cost = 250,
            color = Color.Cyan,
            range = 3.5f,
            damage = 0f,
            fireRateMs = 1500L,
            description = "Freezes enemies in place",
            tutorialTitle = "Auntie's Ice Kachang Cart (Stun/Freezer)",
            signatureMove = "The Absolute Zero Brain Freeze",
            tutorialDescription = "Want something to really chill out the enemies? Then you need the Auntie at the Ice Kachang Cart! She’s taken traditional dessert techniques to the cryo-level. Her specialized ice shaver can launch a massive, compacted ball of shaved ice, syrup, and cold, cold, red beans, aimed precisely at the lead enemy. Upon impact, it delivers an Absolute Zero Brain Freeze. The target is frozen solid, encased in a giant colorful ice cube, completely immobilized for several precious seconds. A perfect stall for controlling boss units.",
            spriteRect = IntRect(14, 2041, 322, 2471),
            freezeDurationMs = 500L,
            behavior = IceKachangStallBehavior()
        ),
        StallType.TRAY_RETURN_UNCLE to StallDefinition(
            type = StallType.TRAY_RETURN_UNCLE,
            name = "Tray Return Uncle",
            cost = 250,
            color = Color.Gray,
            range = 1.1f,
            damage = 0f,
            fireRateMs = 15000L,
            description = "Cleans trays, and enemies",
            tutorialTitle = "Tray Return Uncle (Enemy Displacement)",
            signatureMove = "THE GREAT TRAY CLEARANCE",
            tutorialDescription = "Don't leave your trays behind, or this Uncle might just clear YOU! The Tray Return Uncle is the master of order in the hawker center. Every 15 seconds, he spots an enemy and decides they need a good cleaning. He'll grab them, pull them into his stall for a few seconds of 'intensive tray-training', and then place them back on the floor in a random nearby spot. While they're being 'cleaned', they're off the board and can't be touched. Efficient, orderly, and slightly terrifying.",
            spriteRect = IntRect(14, 3041, 322, 3471),
            effectDurationMs = 2000L, // Cleaning time
            behavior = TrayReturnUncleBehavior()
        ),
        StallType.ATM to StallDefinition(
            type = StallType.ATM,
            name = "ATM",
            cost = 1000,
            color = Color(0xFF4CAF50), // Green
            range = 0f,
            damage = 0f,
            fireRateMs = 0L,
            description = "Provides $100 every wave",
            tutorialTitle = "The Reliable ATM (Passive Income)",
            signatureMove = "The High-Interest Payday",
            tutorialDescription = "Need a bit more budget for your hawker empire? The ATM is here to help! While it doesn't serve food or clear trays, it provides a steady stream of income. At the end of every wave, the ATM dispenses a crisp $100 directly into your budget. It's the perfect long-term investment for savvy hawker masters.",
            spriteRect = IntRect(14, 3541, 322, 3971),
            passiveIncome = 100
        ),
        StallType.BAK_KUT_TEH to StallDefinition(
            type = StallType.BAK_KUT_TEH,
            name = "Bak Kut Teh",
            cost = 300,
            color = Color(0xFF795548), // Brown
            range = 1.1f,
                damage = 10f, // Used as base boost percentage
            fireRateMs = 0L,
            description = "Boosts adjacent stalls",
            tutorialTitle = "The Herbal Bak Kut Teh Stall (Adjacency Booster)",
            signatureMove = "The Herbal Invigoration",
            tutorialDescription = "The aroma of these herbs doesn't just attract customers; it invigorates your fellow hawkers! Placing this stall next to others will boost their primary stats—like damage, effect duration, or freeze duration—by 10%. It's the perfect herbal pick-me-up for a busy lunch rush. Note: Boosts from multiple Bak Kut Teh stalls stack additively!",
            spriteRect = IntRect(14, 2541, 322, 2971)
        )
    )

    fun get(type: StallType): StallDefinition = definitions[type]!!
    fun all(): List<StallDefinition> = definitions.values.toList()
}

object EnemyRegistry {
    private val definitions = mapOf(
        EnemyType.SALARYMAN to EnemyDefinition(
            type = EnemyType.SALARYMAN,
            name = "Salaryman",
            description = "The fast-paced office worker. They move quickly across the grid, eager to reach their destination. Their high speed makes them difficult to hit, but they don't have much health.",
            baseHp = 50f,
            baseSpeed = 0.08f,
            reward = 10,
            spriteRow = 2
        ),
        EnemyType.TOURIST to EnemyDefinition(
            type = EnemyType.TOURIST,
            name = "Tourist",
            description = "A curious visitor who frequently stops to take pictures of the local sights. While stationary, they are easy targets for your stalls, but they have more health than a Salaryman.",
            baseHp = 100f,
            baseSpeed = 0.04f,
            reward = 20,
            spriteRow = 1,
            behavior = TouristEnemyBehavior()
        ),
        EnemyType.AUNTIE to EnemyDefinition(
            type = EnemyType.AUNTIE,
            name = "Auntie",
            description = "A veteran of the hawker scene. She moves slowly and deliberately, but possesses high health. It takes sustained fire from multiple stalls to stop her progress.",
            baseHp = 150f,
            baseSpeed = 0.03f,
            reward = 30,
            spriteRow = 0,
            behavior = AuntieEnemyBehavior()
        ),
        EnemyType.DELIVERY_RIDER to EnemyDefinition(
            type = EnemyType.DELIVERY_RIDER,
            name = "Delivery Rider",
            description = "A formidable boss on two wheels. He has massive health and moves at a significant speed. He is particularly cautious on wet surfaces, slowing down considerably when passing through sticky puddles.",
            baseHp = 300f,
            baseSpeed = 0.06f,
            reward = 60,
            spriteRow = 3,
            behavior = DeliveryRiderEnemyBehavior()
        ),
        EnemyType.TIGER_MOM to EnemyDefinition(
            type = EnemyType.TIGER_MOM,
            name = "Tiger Mom",
            description = "She's not just here for the food; she's here to ensure success! The Tiger Mom is a formidable force who occasionally stops to give another customer an 'encouraging' lecture, providing them with a 90% armor buff until she's fully fed. Only one Tiger Mom can be on the board at a time.",
            baseHp = 60f,
            baseSpeed = 0.05f,
            reward = 40,
            spriteRow = 4
        )
    )

    fun get(type: EnemyType): EnemyDefinition = definitions[type]!!
}
