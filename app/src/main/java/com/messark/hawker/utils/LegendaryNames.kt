package com.messark.hawker.utils

import kotlin.random.Random

/**
 * Utility object for retrieving legendary prefixes, suffixes, and constructing stall titles upon reaching upgrade milestones.
 */
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
        ),
        "Boost" to listOf(
            "of Herbal Invigoration",
            "the Essence-Binder",
            "of Supreme Vitality",
            "the Spirit-Lifter",
            "of Eternal Resonance",
            "the Aura-Master",
            "of The Primordial Brew",
            "the Catalyst-King"
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
        ),
        "Boost" to listOf(
            "Invigorating",
            "Herbal-",
            "Resonant",
            "Transcendent",
            "Empowering",
            "The-Sacred-",
            "Vitalizing",
            "Aura-of-"
        )
    )

    fun getRandomSuffix(category: String, random: Random = Random): String? {
        return suffixes[category]?.randomOrNull(random)
    }

    fun getRandomPrefix(category: String, random: Random = Random): String? {
        return prefixes[category]?.randomOrNull(random)
    }

    fun constructName(baseName: String, prefix: String?, suffix: String?): String = buildString {
        if (prefix != null) {
            append(prefix)
            if (!prefix.endsWith('-')) {
                append(' ')
            }
        }
        append(baseName)
        if (suffix != null) {
            append(' ').append(suffix)
        }
    }
}
