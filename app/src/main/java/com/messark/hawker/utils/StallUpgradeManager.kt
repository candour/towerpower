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

    fun applyUpgrade(stall: Stall, statName: String, upgradeCost: Int, isSpecific: Boolean): Stall {
        val mutableUpgrades = stall.upgrades.toMutableMap()
        val newLevel = mutableUpgrades.getOrDefault(statName, 0) + 1
        mutableUpgrades[statName] = newLevel

        var newDamage = stall.damage
        var newRange = stall.range
        var newFireRate = stall.fireRateMs
        var newAoeRadius = stall.aoeRadius
        var newEffectDuration = stall.effectDurationMs
        var newFreezeDuration = stall.freezeDurationMs
        var disabledWaves = stall.disabledWaves

        val isMilestone = newLevel % 10 == 0

        when (statName) {
            "Damage" -> {
                if (stall.stallType == StallType.CHICKEN_RICE && stall.cost == 100) {
                     newDamage += 6
                } else {
                    newDamage = (newDamage * 1.15f).roundToInt()
                }
                if (isMilestone) newDamage = (newDamage * 1.25f).roundToInt()
            }
            "Range" -> {
                newRange += 0.5f
                if (isMilestone) newRange *= 1.25f
            }
            "Rate", "Grab Rate" -> {
                val baseStall = StallRegistry.get(stall.stallType)
                val rateReduction = when (stall.stallType) {
                    StallType.TRAY_RETURN_UNCLE -> 100L
                    StallType.CHICKEN_RICE -> 15L
                    StallType.DURIAN -> 50L
                    StallType.SATAY -> 25L
                    else -> (baseStall.fireRateMs * 0.1f).toLong()
                }
                var potentialRate = stall.fireRateMs - rateReduction
                if (isMilestone) potentialRate = (potentialRate * 0.75).roundToLong()

                val floor = when (stall.stallType) {
                    StallType.TRAY_RETURN_UNCLE -> 10000L
                    StallType.CHICKEN_RICE -> 200L
                    StallType.DURIAN -> 1000L
                    StallType.SATAY -> 750L
                    else -> 50L
                }

                if (stall.fireRateMs <= floor && statName == "Rate") {
                    newFireRate = stall.fireRateMs
                } else {
                    newFireRate = max(floor, potentialRate)
                }

                if (stall.stallType == StallType.TRAY_RETURN_UNCLE) mutableUpgrades["Rate"] = newLevel
            }
            "Radius" -> {
                newAoeRadius += 0.2f
                if (isMilestone) newAoeRadius *= 1.25f
            }
            "Duration", "Cleaning Time" -> {
                val increment = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) 100L else 500L
                var potentialDuration = stall.effectDurationMs + increment
                if (isMilestone) potentialDuration = (potentialDuration * 1.25).roundToLong()

                val cap = if (stall.stallType == StallType.TRAY_RETURN_UNCLE) 4000L else Long.MAX_VALUE
                newEffectDuration = min(cap, potentialDuration)
                mutableUpgrades["Duration"] = newLevel
            }
            "Effect" -> {
                newFreezeDuration += 100L
                if (isMilestone) newFreezeDuration = (newFreezeDuration * 1.25).roundToLong()
            }
            "Boost" -> {
                newDamage += 20
                if (isMilestone) newDamage = (newDamage * 1.25f).roundToInt()
            }
        }

        // Legendary Naming
        var newPrefix = stall.legendaryPrefix
        var newSuffix = stall.legendarySuffix
        val newNamingCategories = stall.namingCategories.toMutableList()

        if (newLevel == 10 && !stall.namingCategories.contains(statName)) {
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

        return when (category) {
            "Damage" -> {
                var currentDamage = baseStall.damage.toFloat()
                for (l in 1..level) {
                    if (baseStall.type == StallType.CHICKEN_RICE && baseStall.cost == 100) {
                        currentDamage += 6
                    } else {
                        currentDamage *= 1.15f
                    }
                    if (l % 10 == 0) currentDamage *= 1.25f
                }
                val diff = currentDamage.roundToInt() - baseStall.damage
                val percentage = if (baseStall.damage > 0) {
                    (diff.toFloat() / baseStall.damage * 100).roundToInt()
                } else 0
                "+$percentage%"
            }
            "Grab Rate", "Rate" -> {
                var currentRate = baseStall.fireRateMs
                val rateReduction = when (baseStall.type) {
                    StallType.TRAY_RETURN_UNCLE -> 100L
                    StallType.CHICKEN_RICE -> 15L
                    StallType.DURIAN -> 50L
                    StallType.SATAY -> 25L
                    else -> (baseStall.fireRateMs * 0.1f).toLong()
                }
                val floor = when (baseStall.type) {
                    StallType.TRAY_RETURN_UNCLE -> 10000L
                    StallType.CHICKEN_RICE -> 200L
                    StallType.DURIAN -> 1000L
                    StallType.SATAY -> 750L
                    else -> 50L
                }

                for (l in 1..level) {
                    var potentialRate = currentRate - rateReduction
                    if (l % 10 == 0) potentialRate = (potentialRate * 0.75).roundToLong()
                    currentRate = max(floor, potentialRate)
                }
                if (baseStall.type == StallType.TRAY_RETURN_UNCLE) {
                    "-${baseStall.fireRateMs - currentRate}ms"
                } else {
                    val percentage = ((baseStall.fireRateMs - currentRate).toFloat() / baseStall.fireRateMs * 100).roundToInt()
                    "+$percentage%"
                }
            }
            "Range" -> {
                var currentRange = baseStall.range
                for (l in 1..level) {
                    currentRange += 0.5f
                    if (l % 10 == 0) currentRange *= 1.25f
                }
                "+${String.format("%.1f", currentRange - baseStall.range)}"
            }
            "Radius" -> {
                var currentRadius = baseStall.aoeRadius
                for (l in 1..level) {
                    currentRadius += 0.2f
                    if (l % 10 == 0) currentRadius *= 1.25f
                }
                "+${String.format("%.1f", currentRadius - baseStall.aoeRadius)}"
            }
            "Cleaning Time", "Duration" -> {
                var currentDuration = baseStall.effectDurationMs
                val increment = if (baseStall.type == StallType.TRAY_RETURN_UNCLE) 100L else 500L
                val cap = if (baseStall.type == StallType.TRAY_RETURN_UNCLE) 4000L else Long.MAX_VALUE

                for (l in 1..level) {
                    currentDuration = min(cap, currentDuration + increment)
                    if (l % 10 == 0) currentDuration = min(cap, (currentDuration * 1.25).roundToLong())
                }
                "+${currentDuration - baseStall.effectDurationMs}ms"
            }
            "Effect" -> {
                var currentEffect = baseStall.freezeDurationMs
                for (l in 1..level) {
                    currentEffect += 100
                    if (l % 10 == 0) currentEffect = (currentEffect * 1.25).roundToLong()
                }
                "+${currentEffect - baseStall.freezeDurationMs}ms"
            }
            "Boost" -> {
                var currentBoost = baseStall.damage.toFloat()
                for (l in 1..level) {
                    currentBoost += 20f
                    if (l % 10 == 0) currentBoost *= 1.25f
                }
                "+${(currentBoost - baseStall.damage).roundToInt()}%"
            }
            else -> ""
        }
    }
}
