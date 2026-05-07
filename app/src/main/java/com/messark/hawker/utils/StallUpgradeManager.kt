package com.messark.hawker.utils

import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType
import com.messark.hawker.registry.StallDefinition
import com.messark.hawker.registry.StallRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object StallUpgradeManager {

    private val valueCache = ConcurrentHashMap<CacheKey, Double>()

    private data class CacheKey(
        val stallType: StallType,
        val statName: String,
        val baseValue: Double,
        val level: Int
    )

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
            StallType.ATM -> emptyList()
            else -> if (stall.stallType.isUtility) listOf("Range", "Rate") else listOf("Damage", "Range", "Rate")
        }
    }

    private fun getCanonicalStat(statName: String, stallType: StallType): String = when (statName) {
        "Grab Rate" -> "Rate"
        "Cleaning Time" -> "Duration"
        "Damage" -> if (stallType == StallType.BAK_KUT_TEH) "Boost" else "Damage"
        "Boost" -> "Boost"
        else -> statName
    }

    private fun calculateValue(
        statName: String,
        baseValue: Double,
        level: Int,
        stallType: StallType
    ): Double {
        if (level <= 0) return baseValue
        val canonical = getCanonicalStat(statName, stallType)
        val cacheKey = CacheKey(stallType, canonical, baseValue, level)

        return valueCache.getOrPut(cacheKey) {
            val baseStall = StallRegistry.get(stallType)
            val rateReduction = when (stallType) {
                StallType.TRAY_RETURN_UNCLE -> 100.0
                StallType.CHICKEN_RICE -> 15.0
                StallType.DURIAN -> 50.0
                StallType.SATAY -> 25.0
                else -> baseValue * 0.1
            }
            val rateFloor = when (stallType) {
                StallType.TRAY_RETURN_UNCLE -> 10000.0
                StallType.CHICKEN_RICE -> 200.0
                StallType.DURIAN -> 1000.0
                StallType.SATAY -> 750.0
                else -> 50.0
            }
            val durationIncrement = if (stallType == StallType.TRAY_RETURN_UNCLE) 100.0 else 500.0
            val durationCap = if (stallType == StallType.TRAY_RETURN_UNCLE) 4000.0 else Double.MAX_VALUE

            (1..level).fold(baseValue) { current, l ->
                val isMilestone = l % 10 == 0
                when (canonical) {
                    "Damage" -> {
                        var next = if (stallType == StallType.CHICKEN_RICE && baseStall.cost == 100) current + 6.0
                                   else (current * 1.15).roundToInt().toDouble()
                        if (isMilestone) (next * 1.25).roundToInt().toDouble() else next
                    }
                    "Range" -> {
                        var next = current + 0.5
                        if (isMilestone) next *= 1.25
                        (next * 10).roundToInt() / 10.0
                    }
                    "Rate" -> {
                        if (current <= rateFloor) current
                        else {
                            var potentialRate = current - rateReduction
                            if (isMilestone) potentialRate = (potentialRate * 0.75).roundToLong().toDouble()
                            max(rateFloor, potentialRate)
                        }
                    }
                    "Radius" -> {
                        var next = current + 0.2
                        if (isMilestone) next *= 1.25
                        (next * 10).roundToInt() / 10.0
                    }
                    "Duration" -> {
                        var next = min(durationCap, current + durationIncrement)
                        if (isMilestone) min(durationCap, (next * 1.25).roundToLong().toDouble()) else next
                    }
                    "Effect" -> {
                        val next = current + 100.0
                        if (isMilestone) (next * 1.25).roundToLong().toDouble() else next
                    }
                    "Boost" -> {
                        val next = current + 20.0
                        if (isMilestone) (next * 1.25).roundToInt().toDouble() else next
                    }
                    else -> current
                }
            }
        }
    }

    fun applyUpgrade(stall: Stall, statName: String, upgradeCost: Int, isSpecific: Boolean): Stall {
        val mutableUpgrades = stall.upgrades.toMutableMap()

        // Normalize all possible stat keys (including aliases and stall-specific ones)
        val allStatKeys = (mutableUpgrades.keys + getAvailableUpgradeStats(stall)).toSet()
        allStatKeys.forEach { key ->
            val canonical = getCanonicalStat(key, stall.stallType)
            if (canonical != key) {
                val level = max(mutableUpgrades.getOrDefault(key, 0), mutableUpgrades.getOrDefault(canonical, 0))
                if (level > 0) {
                    mutableUpgrades[key] = level
                    mutableUpgrades[canonical] = level
                }
            }
        }

        val canonicalStat = getCanonicalStat(statName, stall.stallType)
        val newLevelForStat = mutableUpgrades.getOrDefault(statName, 0) + 1
        mutableUpgrades[statName] = newLevelForStat
        if (canonicalStat != statName) mutableUpgrades[canonicalStat] = newLevelForStat

        val baseDef = StallRegistry.get(stall.stallType)
        val damageStat = getCanonicalStat("Damage", stall.stallType)

        // Legendary Naming
        var newPrefix = stall.legendaryPrefix
        var newSuffix = stall.legendarySuffix
        val newNamingCategories = stall.namingCategories.map { getCanonicalStat(it, stall.stallType) }.toMutableList()

        if (newLevelForStat == 10 && !newNamingCategories.contains(canonicalStat)) {
            val legendaryCat = canonicalStat
            if (newNamingCategories.isEmpty()) {
                newSuffix = LegendaryNames.getRandomSuffix(legendaryCat)
                newNamingCategories.add(canonicalStat)
            } else if (newNamingCategories.size == 1) {
                newPrefix = LegendaryNames.getRandomPrefix(legendaryCat)
                newNamingCategories.add(canonicalStat)
            }
        }

        return stall.copy(
            name = LegendaryNames.constructName(stall.baseName, newPrefix, newSuffix),
            damage = calculateValue(damageStat, baseDef.damage.toDouble(), mutableUpgrades.getOrDefault(damageStat, 0), stall.stallType).toInt(),
            range = calculateValue("Range", baseDef.range.toDouble(), mutableUpgrades.getOrDefault("Range", 0), stall.stallType).toFloat(),
            fireRateMs = calculateValue("Rate", baseDef.fireRateMs.toDouble(), mutableUpgrades.getOrDefault("Rate", 0), stall.stallType).toLong(),
            aoeRadius = calculateValue("Radius", baseDef.aoeRadius.toDouble(), mutableUpgrades.getOrDefault("Radius", 0), stall.stallType).toFloat(),
            effectDurationMs = calculateValue("Duration", baseDef.effectDurationMs.toDouble(), mutableUpgrades.getOrDefault("Duration", 0), stall.stallType).toLong(),
            freezeDurationMs = calculateValue("Effect", baseDef.freezeDurationMs.toDouble(), mutableUpgrades.getOrDefault("Effect", 0), stall.stallType).toLong(),
            upgradeCount = stall.upgradeCount + 1,
            totalInvestment = stall.totalInvestment + upgradeCost,
            upgrades = mutableUpgrades,
            legendaryPrefix = newPrefix,
            legendarySuffix = newSuffix,
            namingCategories = newNamingCategories,
            disabledWaves = stall.disabledWaves + if (isSpecific && upgradeCost > 0) 1 else 0
        )
    }

    fun getBenefitString(category: String, level: Int, baseStall: StallDefinition): String {
        if (level <= 0) return ""

        val canonicalStat = getCanonicalStat(category, baseStall.type)
        val baseValue = when (canonicalStat) {
            "Damage", "Boost" -> baseStall.damage.toDouble()
            "Range" -> baseStall.range.toDouble()
            "Rate" -> baseStall.fireRateMs.toDouble()
            "Radius" -> baseStall.aoeRadius.toDouble()
            "Duration" -> baseStall.effectDurationMs.toDouble()
            "Effect" -> baseStall.freezeDurationMs.toDouble()
            else -> 0.0
        }
        val finalValue = calculateValue(canonicalStat, baseValue, level, baseStall.type)

        return when (category) {
            "Damage" -> {
                val percentage = if (baseStall.damage > 0) {
                    ((finalValue - baseStall.damage) / baseStall.damage * 100).roundToInt()
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
