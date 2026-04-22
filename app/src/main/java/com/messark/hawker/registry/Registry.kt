package com.messark.hawker.registry

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import com.messark.hawker.model.*
import java.util.*

sealed class FireResult {
    data class NewProjectile(val projectile: Projectile, val updatedStall: Stall? = null) : FireResult()
    data class NewPuddle(val puddle: StickyPuddle) : FireResult()
}

data class StallDefinition(
    val type: StallType,
    val name: String,
    val cost: Int,
    val color: Color,
    val range: Float,
    val damage: Int,
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
    val visualEffectType: VisualEffectType = VisualEffectType.EXPANDING_CIRCLE,
    val visualEffectColor: Color? = null,
    val visualEffectDuration: Long = 150L
) {
    fun getUpgradeDamageIncrease(baseDamage: Int): Int {
        return if (type == StallType.CHICKEN_RICE) {
            (baseDamage * 0.3f).toInt() + 2
        } else {
            (baseDamage * 0.2f).toInt() + 1
        }
    }

    fun applyDamageModifiers(enemy: Enemy, baseDamage: Float): Float {
        return when (type) {
            StallType.SATAY -> when (enemy.type) {
                EnemyType.TOURIST -> baseDamage * 2f
                EnemyType.AUNTIE -> baseDamage * 0.5f
                else -> baseDamage
            }
            StallType.DURIAN -> when (enemy.type) {
                EnemyType.DELIVERY_RIDER -> baseDamage * 1.5f
                else -> baseDamage
            }
            else -> baseDamage
        }
    }

    fun getFreezeModifier(enemy: Enemy, baseDuration: Long): Long {
        if (type != StallType.ICE_KACHANG) return 0L
        return when (enemy.type) {
            EnemyType.SALARYMAN -> baseDuration * 2
            EnemyType.TOURIST -> baseDuration / 2
            else -> baseDuration
        }
    }

    fun getSpeedBoost(enemy: Enemy): Long {
        if (type == StallType.DURIAN && enemy.type == EnemyType.SALARYMAN) {
            return 2000L
        }
        return 0L
    }

    fun fire(
        stall: Stall,
        stallCoord: AxialCoordinate,
        target: Enemy,
        currentTimeMs: Long
    ): FireResult {
        val stallPos = PreciseAxialCoordinate(stallCoord.q.toFloat(), stallCoord.r.toFloat())
        return when (type) {
            StallType.TEH_TARIK -> FireResult.NewPuddle(
                StickyPuddle(
                    id = UUID.randomUUID().toString(),
                    position = target.position,
                    spawnTimeMs = currentTimeMs,
                    durationMs = stall.effectDurationMs,
                    sourceStallCoord = stallCoord,
                    sourceStallId = stall.id
                )
            )
            StallType.SATAY -> {
                val dq = target.position.q - stallCoord.q
                val dr = target.position.r - stallCoord.r
                val angle = Math.atan2(dr.toDouble(), dq.toDouble()).toFloat()
                FireResult.NewProjectile(
                    projectile = Projectile(
                        id = UUID.randomUUID().toString(),
                        position = stallPos,
                        targetEnemyId = null,
                        targetPosition = target.position,
                        damage = stall.damage,
                        color = Color.White,
                        speed = projectileSpeed,
                        aoeRadius = stall.aoeRadius,
                        isArc = true,
                        startPosition = stallPos,
                        sourceStallType = StallType.SATAY,
                        sourceStallCoord = stallCoord,
                        sourceStallId = stall.id
                    ),
                    updatedStall = stall.copy(rotation = angle)
                )
            }
            else -> FireResult.NewProjectile(
                projectile = Projectile(
                    id = UUID.randomUUID().toString(),
                    position = stallPos,
                    targetEnemyId = target.id,
                    targetPosition = target.position,
                    damage = stall.damage,
                    color = stall.color,
                    isFreeze = type == StallType.ICE_KACHANG,
                    freezeDurationMs = stall.freezeDurationMs,
                    aoeRadius = stall.aoeRadius,
                    sourceStallType = type,
                    sourceStallCoord = stallCoord,
                    sourceStallId = stall.id
                )
            )
        }
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
            freezeDurationMs = freezeDurationMs
        )
    }

    fun getUpgradeBenefit(category: String, level: Int, baseStall: StallDefinition): String {
        if (level <= 0) return ""

        return when (category) {
            "Damage" -> {
                var currentDamage = baseStall.damage
                val increasePerLevel = getUpgradeDamageIncrease(baseStall.damage)
                for (l in 1..level) {
                    currentDamage += increasePerLevel
                    if (l % 10 == 0) {
                        currentDamage = Math.round(currentDamage * 1.25f)
                    }
                }
                val percentage = Math.round(((currentDamage - baseStall.damage).toFloat() / baseStall.damage) * 100)
                "+$percentage%"
            }
            "Grab Rate", "Rate" -> {
                var currentRate = baseStall.fireRateMs
                val rateReduction = if (baseStall.type == StallType.TRAY_RETURN_UNCLE) 100L else (baseStall.fireRateMs * 0.1f).toLong()
                for (l in 1..level) {
                    currentRate = if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                        Math.max(10000L, currentRate - rateReduction)
                    } else {
                        Math.max(50L, currentRate - rateReduction)
                    }
                    if (l % 10 == 0) {
                        currentRate = if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                            Math.max(10000L, Math.round(currentRate * 0.75))
                        } else {
                            Math.max(50L, Math.round(currentRate * 0.75))
                        }
                    }
                }
                if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                    currentRate = Math.max(10000L, currentRate)
                }
                if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                    "-${baseStall.fireRateMs - currentRate}ms"
                } else {
                    val percentage = Math.round(((baseStall.fireRateMs - currentRate).toFloat() / baseStall.fireRateMs) * 100)
                    "+$percentage%"
                }
            }
            "Range" -> {
                var currentRange = baseStall.range
                for (l in 1..level) {
                    currentRange += 0.5f
                    if (l % 10 == 0) {
                        currentRange *= 1.25f
                    }
                }
                "+${String.format("%.1f", currentRange - baseStall.range)}"
            }
            "Radius" -> {
                var currentRadius = baseStall.aoeRadius
                for (l in 1..level) {
                    currentRadius += 0.2f
                    if (l % 10 == 0) {
                        currentRadius *= 1.25f
                    }
                }
                "+${String.format("%.1f", currentRadius - baseStall.aoeRadius)}"
            }
            "Cleaning Time", "Duration" -> {
                var currentDuration = baseStall.effectDurationMs
                for (l in 1..level) {
                    if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                        currentDuration = Math.min(4000L, currentDuration + 100)
                    } else {
                        currentDuration += 500
                    }
                    if (l % 10 == 0) {
                        currentDuration = Math.round(currentDuration * 1.25f).toLong()
                    }
                }
                if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                    currentDuration = Math.min(4000L, currentDuration)
                }
                "+${currentDuration - baseStall.effectDurationMs}ms"
            }
            "Effect" -> {
                var currentEffect = baseStall.freezeDurationMs
                for (l in 1..level) {
                    currentEffect += 100
                    if (l % 10 == 0) {
                        currentEffect = Math.round(currentEffect * 1.25f).toLong()
                    }
                }
                "+${currentEffect - baseStall.freezeDurationMs}ms"
            }
            else -> ""
        }
    }
}

data class EnemyDefinition(
    val type: EnemyType,
    val name: String,
    val description: String,
    val baseHp: Int,
    val baseSpeed: Float,
    val reward: Int,
    val spriteRow: Int
) {
    fun getPuddleSlowMultiplier(enemyType: EnemyType): Float {
        return when (enemyType) {
            EnemyType.DELIVERY_RIDER -> 0.2f
            EnemyType.AUNTIE -> 0.8f
            else -> 0.6f
        }
    }

    fun updateSpecialBehavior(enemy: Enemy, currentTimeMs: Long): Enemy {
        if (type == EnemyType.TOURIST) {
            var isStopped = enemy.isStopped
            var stopDurationMs = enemy.stopDurationMs
            var lastStopMs = enemy.lastStopMs

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
        return enemy
    }

    fun getHp(wave: Int): Int {
        return (baseHp * Math.pow(1.1, (wave - 1).toDouble())).toInt()
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
            damage = 10,
            fireRateMs = 1000L,
            description = "Creates slowing puddles",
            tutorialTitle = "Teh Tarik Maestro (Movement Slow)",
            signatureMove = "The Perpetual Tarik Puddle",
            tutorialDescription = "Welcome to the Teh Tarik Maestro, where the art of 'pulling' tea is a high-level tactical maneuver. This Maestro doesn't just make your enemies slower; he makes the very ground they walk on sticky. Utilizing a massive pair of custom cups, he performs a continuous, mesmerizing 'tarik' high in the air. Each 'pull' perfectly places a wide, frothy Perpetual Tarik Puddle of viscous, sweet milk tea. The tea is so thick and syrupy that enemies stepping into it are immediately bogged down, their speed cut in half as they struggle through the delicious, sticky mess. A crowd favorite for slowing the rush.",
            spriteRect = IntRect(22, 41, 330, 451),
            effectDurationMs = 3000L
        ),
        StallType.SATAY to StallDefinition(
            type = StallType.SATAY,
            name = "Satay",
            cost = 200,
            color = Color.Red,
            range = 2.5f,
            damage = 20,
            fireRateMs = 1500,
            description = "Area chili sauce damage",
            tutorialTitle = "Uncle's Satay Stall (AoE Damage)",
            signatureMove = "The Chili Conflagration",
            tutorialDescription = "Wah, smells so shiok! Behind this unassuming grill, the Satay Uncle is fanning a fiery revolution. Watch out for his signature Chili Conflagration—the chili isn't just spicy; it's explosive. He loads up a massive spoon and, with a precision usually reserved for satay-counting, launches a gigantic splash of his secret, explosive chili sauce. When it hits, it covers a wide circle, dousing groups of enemies in a sticky, burning chili storm that eats away at their health (and their willpower). If you need a crowd-control burn, this Uncle is the OG.",
            spriteRect = IntRect(358, 41, 666, 451),
            aoeRadius = 1.0f,
            projectileSpeed = 0.3f,
            isArc = true,
            projectileColor = Color.White,
            visualEffectType = VisualEffectType.GAS_CLOUD,
            visualEffectColor = Color.Red.copy(alpha = 0.3f),
            visualEffectDuration = 500L
        ),
        StallType.CHICKEN_RICE to StallDefinition(
            type = StallType.CHICKEN_RICE,
            name = "Chicken Rice",
            cost = 100,
            color = Color.Yellow,
            range = 4f,
            damage = 15,
            fireRateMs = 700,
            description = "High single-target damage",
            tutorialTitle = "Ah Hock’s Chicken Rice Stand (Single-Target DPS)",
            signatureMove = "The Garlic-Ginger Gatling Gun",
            tutorialDescription = "Ah Hock’s Chicken Rice is famous for two things: the tenderest steamed chicken and the single-minded focus of his attacks. Don’t be fooled by the simple setup; this stand is your base single-target workhorse. When an enemy is targeted, Ah Hock deploys his Garlic-Ginger Gatling Gun. Instead of bullets, he’s launching high-velocity, precision-aimed balls of marinated meat, dousing targets in flavor-infused damage. It’s consistent, it’s powerful, and it never runs out of stock. A classic choice that never fails.",
            spriteRect = IntRect(22, 500, 330, 930)
        ),
        StallType.DURIAN to StallDefinition(
            type = StallType.DURIAN,
            name = "Durian",
            cost = 300,
            color = Color(0xFF4CAF50),
            range = 3f,
            damage = 120,
            fireRateMs = 2000,
            description = "Massive damage, slow fire",
            tutorialTitle = "The King Durian Bunker (High Damage/Slight AoE)",
            signatureMove = "The Spiky Cataclysm",
            tutorialDescription = "They call the Durian the King of Fruits, and this stall is the King of Damage. The King Durian Bunker is fortified with armor-plating and smells… well, like a durian. When the King’s crew makes a sale, they aren't selling just fruit; they are deploying a localized explosive. Using a heavy-duty pneumatic launcher, they fire an overripe, spikey Durian bomb into the largest cluster of enemies. Upon impact, it delivers a high-damage, single-target blow, followed immediately by a Spiky Cataclysm AoE explosion as the potent, heavy aroma bursts outward. It’s high-cost and slow-reloading, but the raw damage (and the scent) is devastating.",
            spriteRect = IntRect(33, 961, 341, 1318),
            aoeRadius = 1.0f,
            visualEffectColor = Color(0xFFCDDC39).copy(alpha = 0.5f)
        ),
        StallType.ICE_KACHANG to StallDefinition(
            type = StallType.ICE_KACHANG,
            name = "Ice Kachang",
            cost = 250,
            color = Color.Cyan,
            range = 3.5f,
            damage = 2,
            fireRateMs = 1500,
            description = "Freezes enemies in place",
            tutorialTitle = "Auntie's Ice Kachang Cart (Stun/Freezer)",
            signatureMove = "The Absolute Zero Brain Freeze",
            tutorialDescription = "Want something to really chill out the enemies? Then you need the Auntie at the Ice Kachang Cart! She’s taken traditional dessert techniques to the cryo-level. Her specialized ice shaver can launch a massive, compacted ball of shaved ice, syrup, and cold, cold, red beans, aimed precisely at the lead enemy. Upon impact, it doesn't just damage; it delivers an Absolute Zero Brain Freeze. The target is frozen solid, encased in a giant colorful ice cube, completely immobilized for several precious seconds. A perfect stall for controlling boss units.",
            spriteRect = IntRect(358, 500, 666, 930),
            freezeDurationMs = 500L
        ),
        StallType.TRAY_RETURN_UNCLE to StallDefinition(
            type = StallType.TRAY_RETURN_UNCLE,
            name = "Tray Return Uncle",
            cost = 450,
            color = Color.Gray,
            range = 1.1f,
            damage = 0,
            fireRateMs = 15000,
            description = "Cleans trays, and enemies",
            tutorialTitle = "Tray Return Uncle (Enemy Displacement)",
            signatureMove = "THE GREAT TRAY CLEARANCE",
            tutorialDescription = "Don't leave your trays behind, or this Uncle might just clear YOU! The Tray Return Uncle is the master of order in the hawker center. Every 15 seconds, he spots an enemy and decides they need a good cleaning. He'll grab them, pull them into his stall for a few seconds of 'intensive tray-training', and then place them back on the floor in a random nearby spot. While they're being 'cleaned', they're off the board and can't be touched. Efficient, orderly, and slightly terrifying.",
            spriteRect = IntRect(358, 500, 666, 930), // Placeholder Ice Kachang sprite
            effectDurationMs = 2000L // Cleaning time
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
            baseHp = 50,
            baseSpeed = 0.08f,
            reward = 20,
            spriteRow = 2
        ),
        EnemyType.TOURIST to EnemyDefinition(
            type = EnemyType.TOURIST,
            name = "Tourist",
            description = "A curious visitor who frequently stops to take pictures of the local sights. While stationary, they are easy targets for your stalls, but they have more health than a Salaryman.",
            baseHp = 100,
            baseSpeed = 0.04f,
            reward = 20,
            spriteRow = 1
        ),
        EnemyType.AUNTIE to EnemyDefinition(
            type = EnemyType.AUNTIE,
            name = "Auntie",
            description = "A veteran of the hawker scene. She moves slowly and deliberately, but possesses high health. It takes sustained fire from multiple stalls to stop her progress.",
            baseHp = 150,
            baseSpeed = 0.03f,
            reward = 20,
            spriteRow = 0
        ),
        EnemyType.DELIVERY_RIDER to EnemyDefinition(
            type = EnemyType.DELIVERY_RIDER,
            name = "Delivery Rider",
            description = "A formidable boss on two wheels. He has massive health and moves at a significant speed. He is particularly cautious on wet surfaces, slowing down considerably when passing through sticky puddles.",
            baseHp = 500,
            baseSpeed = 0.06f,
            reward = 100,
            spriteRow = 3
        )
    )

    fun get(type: EnemyType): EnemyDefinition = definitions[type]!!
}
