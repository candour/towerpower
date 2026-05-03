package com.messark.hawker.utils

import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType
import com.messark.hawker.registry.StallDefinition
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.LegendaryNames
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object StallUpgradeManager {

    fun calculateUpgradeCost(stall: Stall, isSpecific: Boolean, hasFreeUpgrade: Boolean): Int {
        val nextUpgradeIndex = stall.upgradeCount + 1
        val baseUpgradeCost = (stall.cost * (0.2f + nextUpgradeIndex * 0.1f)).roundToInt()
        return if (isSpecific) {
            if (hasFreeUpgrade) 0 else baseUpgradeCost * 2
        } else {
            baseUpgradeCost
        }
    }

    fun getAvailableUpgradeStats(stall: Stall): List<String> {
        return when (stall.stallType) {
            StallType.TRAY_RETURN_UNCLE -> listOf("Grab Rate", "Cleaning Time")
            StallType.BAK_KUT_TEH -> listOf("Boost")
            StallType.TEH_TARIK -> listOf("Range", "Rate", "Duration")
            StallType.ICE_KACHANG -> listOf("Range", "Rate", "Effect")
            StallType.SATAY, StallType.DURIAN -> listOf("Damage", "Range", "Rate", "Radius")
            StallType.CHICKEN_RICE -> listOf("Damage", "Range", "Rate")
            else -> if (stall.stallType.isUtility) listOf("Range", "Rate") else listOf("Damage", "Range", "Rate")
        }
    }

    /**
     * Maps UI-friendly or stall-specific stat names to their internal canonical keys.
     */
    private fun getCanonicalStat(statName: String): String {
        return when (statName) {
            "Grab Rate" -> "Rate"
            "Cleaning Time" -> "Duration"
            else -> statName
        }
    }

    /**
     * Calculates the final value for a given stat based on its level and stall type.
     * Note: This recalculates the value from scratch (O(level)) to ensure absolute
     * consistency between UI previews and the actual game state, and to prevent
     * floating-point drift over many upgrades.
     */
    private fun calculateValue(
        statName: String,
        baseValue: Double,
        level: Int,
        stallType: StallType
    ): Double {
        var current = baseValue
        val baseStall = StallRegistry.get(stallType)

        for (l in 1..level) {
            val isMilestone = l % 10 == 0
            when (statName) {
                "Damage" -> {
                    if (stallType == StallType.CHICKEN_RICE && baseStall.cost == 100) {
                        current += 6.0
                    } else {
                        current = (current * 1.15).roundToInt().toDouble()
                    }
                    if (isMilestone) current = (current * 1.25).roundToInt().toDouble()
                }
                "Range" -> {
                    current += 0.5
                    if (isMilestone) current *= 1.25
                    current = (current * 10).roundToInt() / 10.0
                }
                "Rate", "Grab Rate" -> {
                    val rateReduction = when (stallType) {
                        StallType.TRAY_RETURN_UNCLE -> 100.0
                        StallType.CHICKEN_RICE -> 15.0
                        StallType.DURIAN -> 50.0
                        StallType.SATAY -> 25.0
                        else -> baseValue * 0.1
                    }
                    val floor = when (stallType) {
                        StallType.TRAY_RETURN_UNCLE -> 10000.0
                        StallType.CHICKEN_RICE -> 200.0
                        StallType.DURIAN -> 1000.0
                        StallType.SATAY -> 750.0
                        else -> 50.0
                    }

                    if (current > floor) {
                        var potentialRate = current - rateReduction
                        if (isMilestone) potentialRate = (potentialRate * 0.75).roundToLong().toDouble()
                        current = max(floor, potentialRate)
                    }
                }
                "Radius" -> {
                    current += 0.2
                    if (isMilestone) current *= 1.25
                    current = (current * 10).roundToInt() / 10.0
                }
                "Duration", "Cleaning Time" -> {
                    val increment = if (stallType == StallType.TRAY_RETURN_UNCLE) 100.0 else 500.0
                    val cap = if (stallType == StallType.TRAY_RETURN_UNCLE) 4000.0 else Double.MAX_VALUE

                    current = min(cap, current + increment)
                    if (isMilestone) current = min(cap, (current * 1.25).roundToLong().toDouble())
                }
                "Effect" -> {
                    current += 100.0
                    if (isMilestone) current = (current * 1.25).roundToLong().toDouble()
                }
                "Boost" -> {
                    current += 20.0
                    if (isMilestone) current = (current * 1.25).roundToInt().toDouble()
                }
            }
        }
        return current
    }

    fun applyUpgrade(stall: Stall, statName: String, upgradeCost: Int, isSpecific: Boolean): Stall {
        val mutableUpgrades = stall.upgrades.toMutableMap()

        // Normalize: Ensure any existing aliased levels are synced to their canonical keys
        // before we recalculate. This prevents "stat resetting" when canonical keys are missing.
        listOf("Grab Rate", "Cleaning Time").forEach { alias ->
            val canonical = getCanonicalStat(alias)
            val level = max(mutableUpgrades.getOrDefault(alias, 0), mutableUpgrades.getOrDefault(canonical, 0))
            if (level > 0) {
                mutableUpgrades[alias] = level
                mutableUpgrades[canonical] = level
            }
        }

        val canonicalStat = getCanonicalStat(statName)

        // Record the upgrade level
        val newLevelForStat = mutableUpgrades.getOrDefault(statName, 0) + 1
        mutableUpgrades[statName] = newLevelForStat

        // Sync to canonical stat key if it differs (e.g., "Grab Rate" -> "Rate")
        if (canonicalStat != statName) {
            mutableUpgrades[canonicalStat] = newLevelForStat
        }

        val baseDef = StallRegistry.get(stall.stallType)

        // Recalculate all fields from the canonical levels in the map
        val damageStat = if (stall.stallType == StallType.BAK_KUT_TEH) "Boost" else "Damage"
        val newDamage = calculateValue(damageStat, baseDef.damage.toDouble(), mutableUpgrades.getOrDefault(damageStat, 0), stall.stallType).toInt()
        val newRange = calculateValue("Range", baseDef.range.toDouble(), mutableUpgrades.getOrDefault("Range", 0), stall.stallType).toFloat()
        val newFireRate = calculateValue("Rate", baseDef.fireRateMs.toDouble(), mutableUpgrades.getOrDefault("Rate", 0), stall.stallType).toLong()
        val newAoeRadius = calculateValue("Radius", baseDef.aoeRadius.toDouble(), mutableUpgrades.getOrDefault("Radius", 0), stall.stallType).toFloat()
        val newEffectDuration = calculateValue("Duration", baseDef.effectDurationMs.toDouble(), mutableUpgrades.getOrDefault("Duration", 0), stall.stallType).toLong()
        val newFreezeDuration = calculateValue("Effect", baseDef.freezeDurationMs.toDouble(), mutableUpgrades.getOrDefault("Effect", 0), stall.stallType).toLong()

        // Legendary Naming
        var newPrefix = stall.legendaryPrefix
        var newSuffix = stall.legendarySuffix
        val newNamingCategories = stall.namingCategories.toMutableList()

        if (newLevelForStat == 10 && !stall.namingCategories.contains(statName)) {
            val legendaryCat = when (statName) {
                "Grab Rate" -> "Rate"
                "Cleaning Time" -> "Duration"
                else -> statName
            }
            if (stall.namingCategories.isEmpty()) {
                newSuffix = LegendaryNames.getRandomSuffix(legendaryCat)
                newNamingCategories.add(statName)
            } else if (stall.namingCategories.size == 1) {
                newPrefix = LegendaryNames.getRandomPrefix(legendaryCat)
                newNamingCategories.add(statName)
            }
        }
        val newName = LegendaryNames.constructName(stall.baseName, newPrefix, newSuffix)

        var disabledWaves = stall.disabledWaves
        if (isSpecific && upgradeCost > 0) {
            disabledWaves += 1
        }

        return stall.copy(
            name = newName,
            damage = newDamage,
            range = newRange,
            fireRateMs = newFireRate,
            aoeRadius = newAoeRadius,
            effectDurationMs = newEffectDuration,
            freezeDurationMs = newFreezeDuration,
            upgradeCount = stall.upgradeCount + 1,
            totalInvestment = stall.totalInvestment + upgradeCost,
            upgrades = mutableUpgrades,
            legendaryPrefix = newPrefix,
            legendarySuffix = newSuffix,
            namingCategories = newNamingCategories,
            disabledWaves = disabledWaves
        )
    }

    fun getBenefitString(category: String, level: Int, baseStall: StallDefinition): String {
        if (level <= 0) return ""

        val canonicalStat = getCanonicalStat(category)
        val finalValue = calculateValue(canonicalStat, when(canonicalStat) {
            "Damage", "Boost" -> baseStall.damage.toDouble()
            "Range" -> baseStall.range.toDouble()
            "Rate" -> baseStall.fireRateMs.toDouble()
            "Radius" -> baseStall.aoeRadius.toDouble()
            "Duration" -> baseStall.effectDurationMs.toDouble()
            "Effect" -> baseStall.freezeDurationMs.toDouble()
            else -> 0.0
        }, level, baseStall.type)

        return when (category) {
            "Damage" -> {
                val diff = finalValue.roundToInt() - baseStall.damage
                val percentage = if (baseStall.damage > 0) {
                    (diff.toDouble() / baseStall.damage * 100).roundToInt()
                } else 0
                "+$percentage%"
            }
            "Grab Rate", "Rate" -> {
                if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                    "-${(baseStall.fireRateMs - finalValue).roundToLong()}ms"
                } else {
                    val percentage = ((baseStall.fireRateMs - finalValue) / baseStall.fireRateMs * 100).roundToInt()
                    "+$percentage%"
                }
            }
            "Range" -> "+${String.format("%.1f", finalValue - baseStall.range)}"
            "Radius" -> "+${String.format("%.1f", finalValue - baseStall.aoeRadius)}"
            "Cleaning Time", "Duration" -> "+${(finalValue - baseStall.effectDurationMs).roundToLong()}ms"
            "Effect" -> "+${(finalValue - baseStall.freezeDurationMs).roundToLong()}ms"
            "Boost" -> "+${(finalValue - baseStall.damage).roundToInt()}%"
            else -> ""
        }
    }
}
