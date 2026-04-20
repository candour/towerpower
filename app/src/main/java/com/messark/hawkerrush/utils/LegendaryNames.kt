package com.messark.hawkerrush.utils

import kotlin.random.Random

object LegendaryNames {
    private val suffixes = mapOf(
        "Damage" to listOf(
            "of Obliteration",
            "the Annihilator",
            "of Sovereign Fire",
            "the Judgement Bringer",
            "the Devastator",
            "of Eternal Sear",
            "the Molten Core",
            "of Cataclysm"
        ),
        "Rate" to listOf(
            "of Ten Thousand Strikes",
            "the Swift Executioner",
            "of Endless Barrage",
            "the Zephyr-Blessed",
            "of Hallowed Speed",
            "of The Gales",
            "the Relentless",
            "of The Monsoon"
        ),
        "Radius" to listOf(
            "of the Wide-Reaching Hand",
            "the Saturation Master",
            "of Absolute Contagion",
            "the Tidal Surge",
            "of The Infinite Spill",
            "of The Conflagration",
            "the Echoing Thunder",
            "of the Blanket"
        ),
        "Duration" to listOf(
            "of Timeless Torment",
            "of the Eternal Vigil",
            "of the Final Stand",
            "the Preserver",
            "of Endless Stasis",
            "of Persistent Despair",
            "of The Frost-Sleep",
            "of The Linger"
        ),
        "Effect" to listOf(
            "the Debilitator",
            "of Primal Decay",
            "of Arcane Weakness",
            "of The Creeping Chill",
            "the Catalyst",
            "of the Soul-Breaker",
            "of Prismatic Chaos",
            "the Ruin-Binder"
        ),
        "Range" to listOf(
            "of the All-Seeing Vigil",
            "the Wide-Reaching Eye",
            "of The Horizon",
            "the World-Stalker",
            "of Infinite Perch",
            "the Surveyor",
            "of The Eagle-Heart"
        )
    )

    private val prefixes = mapOf(
        "Damage" to listOf(
            "Colossal",
            "Vanquisher's",
            "Titanic",
            "Overwhelming",
            "Ancient-King's",
            "Annihilation",
            "Dread-Forth",
            "Sovereign"
        ),
        "Rate" to listOf(
            "Zephyr",
            "Rapid-Fire",
            "Stormbringer's",
            "Swift-Vengeance",
            "Hurricane",
            "Avalanche-of-",
            "Ever-Active",
            "Unyielding"
        ),
        "Radius" to listOf(
            "Panoramic",
            "Total-Contagion",
            "Infinite",
            "Vast-Horizon's",
            "Unavoidable",
            "The-Reaching-",
            "Spreading-",
            "All-Seeing"
        ),
        "Duration" to listOf(
            "Enduring",
            "Everlasting",
            "Timeless-",
            "Unending-",
            "Vigilant",
            "The-Perpetual-",
            "Unyielding-",
            "Linger-"
        ),
        "Effect" to listOf(
            "Primeval",
            "Arcane-Bound",
            "Cataclysmic-",
            "Prismatic-",
            "Ethereal-",
            "Status-Master's",
            "The-True-",
            "Venomous-"
        ),
        "Range" to listOf(
            "Panoramic",
            "Far-Reaching",
            "High-Ground's",
            "Long-Watch",
            "Vast-Sight",
            "The-Distant-",
            "Endless-Horizon",
            "Eagle-Eyed"
        )
    )

    fun getRandomSuffix(category: String): String? {
        val list = suffixes[category] ?: return null
        return list[Random.nextInt(list.size)]
    }

    fun getRandomPrefix(category: String): String? {
        val list = prefixes[category] ?: return null
        return list[Random.nextInt(list.size)]
    }

    fun constructName(baseName: String, prefix: String?, suffix: String?): String {
        var name = baseName
        if (prefix != null) {
            name = if (prefix.endsWith("-")) {
                "$prefix$name"
            } else {
                "$prefix $name"
            }
        }
        if (suffix != null) {
            name = "$name $suffix"
        }
        return name
    }
}
