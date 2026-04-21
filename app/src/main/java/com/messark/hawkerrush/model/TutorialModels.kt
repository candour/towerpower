package com.messark.hawkerrush.model

enum class TutorialType {
    ENEMY, STALL
}

data class TutorialData(
    val id: String,
    val type: TutorialType,
    val title: String,
    val description: String,
    val signatureMove: String? = null,
    val enemyType: EnemyType? = null,
    val stallType: StallType? = null
)

object TutorialContent {
    val ENEMY_TUTORIALS = mapOf(
        EnemyType.SALARYMAN to TutorialData(
            id = "enemy_salaryman",
            type = TutorialType.ENEMY,
            title = "Salaryman",
            description = "The fast-paced office worker. They move quickly across the grid, eager to reach their destination. Their high speed makes them difficult to hit, but they don't have much health.",
            enemyType = EnemyType.SALARYMAN
        ),
        EnemyType.TOURIST to TutorialData(
            id = "enemy_tourist",
            type = TutorialType.ENEMY,
            title = "Tourist",
            description = "A curious visitor who frequently stops to take pictures of the local sights. While stationary, they are easy targets for your stalls, but they have more health than a Salaryman.",
            enemyType = EnemyType.TOURIST
        ),
        EnemyType.AUNTIE to TutorialData(
            id = "enemy_auntie",
            type = TutorialType.ENEMY,
            title = "Auntie",
            description = "A veteran of the hawker scene. She moves slowly and deliberately, but possesses high health. It takes sustained fire from multiple stalls to stop her progress.",
            enemyType = EnemyType.AUNTIE
        ),
        EnemyType.DELIVERY_RIDER to TutorialData(
            id = "enemy_delivery_rider",
            type = TutorialType.ENEMY,
            title = "Delivery Rider",
            description = "A formidable boss on two wheels. He has massive health and moves at a significant speed. He is particularly cautious on wet surfaces, slowing down considerably when passing through sticky puddles.",
            enemyType = EnemyType.DELIVERY_RIDER
        )
    )

    val STALL_TUTORIALS = mapOf(
        StallType.SATAY to TutorialData(
            id = "stall_satay",
            type = TutorialType.STALL,
            title = "Uncle's Satay Stall (AoE Damage)",
            signatureMove = "The Chili Conflagration",
            description = "Wah, smells so shiok! Behind this unassuming grill, the Satay Uncle is fanning a fiery revolution. Watch out for his signature Chili Conflagration—the chili isn't just spicy; it's explosive. He loads up a massive spoon and, with a precision usually reserved for satay-counting, launches a gigantic splash of his secret, explosive chili sauce. When it hits, it covers a wide circle, dousing groups of enemies in a sticky, burning chili storm that eats away at their health (and their willpower). If you need a crowd-control burn, this Uncle is the OG.",
            stallType = StallType.SATAY
        ),
        StallType.CHICKEN_RICE to TutorialData(
            id = "stall_chicken_rice",
            type = TutorialType.STALL,
            title = "Ah Hock’s Chicken Rice Stand (Single-Target DPS)",
            signatureMove = "The Garlic-Ginger Gatling Gun",
            description = "Ah Hock’s Chicken Rice is famous for two things: the tenderest steamed chicken and the single-minded focus of his attacks. Don’t be fooled by the simple setup; this stand is your base single-target workhorse. When an enemy is targeted, Ah Hock deploys his Garlic-Ginger Gatling Gun. Instead of bullets, he’s launching high-velocity, precision-aimed balls of marinated meat, dousing targets in flavor-infused damage. It’s consistent, it’s powerful, and it never runs out of stock. A classic choice that never fails.",
            stallType = StallType.CHICKEN_RICE
        ),
        StallType.ICE_KACHANG to TutorialData(
            id = "stall_ice_kachang",
            type = TutorialType.STALL,
            title = "Auntie's Ice Kachang Cart (Stun/Freezer)",
            signatureMove = "The Absolute Zero Brain Freeze",
            description = "Want something to really chill out the enemies? Then you need the Auntie at the Ice Kachang Cart! She’s taken traditional dessert techniques to the cryo-level. Her specialized ice shaver can launch a massive, compacted ball of shaved ice, syrup, and cold, cold, red beans, aimed precisely at the lead enemy. Upon impact, it doesn't just damage; it delivers an Absolute Zero Brain Freeze. The target is frozen solid, encased in a giant colorful ice cube, completely immobilized for several precious seconds. A perfect stall for controlling boss units.",
            stallType = StallType.ICE_KACHANG
        ),
        StallType.TEH_TARIK to TutorialData(
            id = "stall_teh_tarik",
            type = TutorialType.STALL,
            title = "Teh Tarik Maestro (Movement Slow)",
            signatureMove = "The Perpetual Tarik Puddle",
            description = "Welcome to the Teh Tarik Maestro, where the art of 'pulling' tea is a high-level tactical maneuver. This Maestro doesn't just make your enemies slower; he makes the very ground they walk on sticky. Utilizing a massive pair of custom cups, he performs a continuous, mesmerizing 'tarik' high in the air. Each 'pull' perfectly places a wide, frothy Perpetual Tarik Puddle of viscous, sweet milk tea. The tea is so thick and syrupy that enemies stepping into it are immediately bogged down, their speed cut in half as they struggle through the delicious, sticky mess. A crowd favorite for slowing the rush.",
            stallType = StallType.TEH_TARIK
        ),
        StallType.DURIAN to TutorialData(
            id = "stall_durian",
            type = TutorialType.STALL,
            title = "The King Durian Bunker (High Damage/Slight AoE)",
            signatureMove = "The Spiky Cataclysm",
            description = "They call the Durian the King of Fruits, and this stall is the King of Damage. The King Durian Bunker is fortified with armor-plating and smells… well, like a durian. When the King’s crew makes a sale, they aren't selling just fruit; they are deploying a localized explosive. Using a heavy-duty pneumatic launcher, they fire an overripe, spikey Durian bomb into the largest cluster of enemies. Upon impact, it delivers a high-damage, single-target blow, followed immediately by a Spiky Cataclysm AoE explosion as the potent, heavy aroma bursts outward. It’s high-cost and slow-reloading, but the raw damage (and the scent) is devastating.",
            stallType = StallType.DURIAN
        )
    )
}
