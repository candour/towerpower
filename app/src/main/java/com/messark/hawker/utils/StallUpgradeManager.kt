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

private interface StatScaler {
    fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double
}

private class DamageScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        val next = if (stallType == StallType.CHICKEN_RICE && baseStall.cost == 100) current + 6.0
        else (current * 1.15).roundToInt().toDouble()
        return if (isMilestone) (next * 1.25).roundToInt().toDouble() else next
    }
}

private class RangeScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        var next = current + 0.5
        if (isMilestone) next *= 1.25
        return (next * 10).roundToInt() / 10.0
    }
}

private class RateScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        val rateReduction = when (stallType) {
            StallType.TRAY_RETURN_UNCLE -> 100.0
            StallType.CHICKEN_RICE -> 15.0
            StallType.DURIAN -> 50.0
            StallType.SATAY -> 25.0
            else -> baseStall.fireRateMs * 0.1
        }
        val rateFloor = when (stallType) {
            StallType.TRAY_RETURN_UNCLE -> 10000.0
            StallType.CHICKEN_RICE -> 200.0
            StallType.DURIAN -> 1000.0
            StallType.SATAY -> 750.0
            else -> 50.0
        }
        if (current <= rateFloor) return current
        var potentialRate = current - rateReduction
        if (isMilestone) potentialRate = (potentialRate * 0.75).roundToLong().toDouble()
        return max(rateFloor, potentialRate)
    }
}

private class RadiusScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        var next = current + 0.2
        if (isMilestone) next *= 1.25
        return (next * 10).roundToInt() / 10.0
    }
}

private class DurationScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        val durationIncrement = if (stallType == StallType.TRAY_RETURN_UNCLE) 100.0 else 500.0
        val durationCap = if (stallType == StallType.TRAY_RETURN_UNCLE) 4000.0 else Double.MAX_VALUE
        val next = min(durationCap, current + durationIncrement)
        return if (isMilestone) min(durationCap, (next * 1.25).roundToLong().toDouble()) else next
    }
}

private class EffectScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        val next = current + 100.0
        return if (isMilestone) (next * 1.25).roundToLong().toDouble() else next
    }
}

private class BoostScaler : StatScaler {
    override fun calculate(current: Double, level: Int, isMilestone: Boolean, stallType: StallType, baseStall: StallDefinition): Double {
        val next = current + 20.0
        return if (isMilestone) (next * 1.25).roundToInt().toDouble() else next
    }
}

object StallUpgradeManager {

    private val scalers = mapOf(
        "Damage" to DamageScaler(),
        "Range" to RangeScaler(),
        "Rate" to RateScaler(),
        "Radius" to RadiusScaler(),
        "Duration" to DurationScaler(),
        "Effect" to EffectScaler(),
        "Boost" to BoostScaler()
    )

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
            val scaler = scalers[canonical]

            (1..level).fold(baseValue) { current, l ->
                val isMilestone = l % 10 == 0
                scaler?.calculate(current, l, isMilestone, stallType, baseStall) ?: current
            }
        }
    }

    fun applyUpgrade(stall: Stall, statName: String, upgradeCost: Int, isSpecific: Boolean): Stall {
        val canonicalStat = getCanonicalStat(statName, stall.stallType)
        val mutableUpgrades = stall.upgrades.toMutableMap()

        // Normalize existing aliases to ensure consistent levels
        (mutableUpgrades.keys + getAvailableUpgradeStats(stall)).forEach { key ->
            val canonical = getCanonicalStat(key, stall.stallType)
            if (canonical != key) {
                val existingLevel = max(mutableUpgrades.getOrDefault(key, 0), mutableUpgrades.getOrDefault(canonical, 0))
                if (existingLevel > 0) {
                    mutableUpgrades[key] = existingLevel
                    mutableUpgrades[canonical] = existingLevel
                }
            }
        }

        val newLevel = max(mutableUpgrades.getOrDefault(statName, 0), mutableUpgrades.getOrDefault(canonicalStat, 0)) + 1
        mutableUpgrades[statName] = newLevel
        if (statName != canonicalStat) mutableUpgrades[canonicalStat] = newLevel

        val baseDef = StallRegistry.get(stall.stallType)

        // Legendary Naming
        var prefix = stall.legendaryPrefix
        var suffix = stall.legendarySuffix
        val categories = stall.namingCategories.toMutableList()

        if (newLevel == 10 && !categories.contains(canonicalStat)) {
            if (categories.isEmpty()) suffix = LegendaryNames.getRandomSuffix(canonicalStat)
            else if (categories.size == 1) prefix = LegendaryNames.getRandomPrefix(canonicalStat)
            categories.add(canonicalStat)
        }

        fun getLevel(stat: String) = mutableUpgrades.getOrDefault(getCanonicalStat(stat, stall.stallType), 0)

        return stall.copy(
            name = LegendaryNames.constructName(stall.baseName, prefix, suffix),
            damage = calculateValue("Damage", baseDef.damage.toDouble(), getLevel("Damage"), stall.stallType).toFloat(),
            range = calculateValue("Range", baseDef.range.toDouble(), getLevel("Range"), stall.stallType).toFloat(),
            fireRateMs = calculateValue("Rate", baseDef.fireRateMs.toDouble(), getLevel("Rate"), stall.stallType).toLong(),
            aoeRadius = calculateValue("Radius", baseDef.aoeRadius.toDouble(), getLevel("Radius"), stall.stallType).toFloat(),
            effectDurationMs = calculateValue("Duration", baseDef.effectDurationMs.toDouble(), getLevel("Duration"), stall.stallType).toLong(),
            freezeDurationMs = calculateValue("Effect", baseDef.freezeDurationMs.toDouble(), getLevel("Effect"), stall.stallType).toLong(),
            upgradeCount = stall.upgradeCount + 1,
            totalInvestment = stall.totalInvestment + upgradeCost,
            upgrades = mutableUpgrades,
            legendaryPrefix = prefix,
            legendarySuffix = suffix,
            namingCategories = categories,
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
