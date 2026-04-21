package com.messark.hawkerrush.model

enum class TutorialType {
    ENEMY, STALL
}

data class TutorialData(
    val id: String,
    val type: TutorialType,
    val title: String,
    val description: String,
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
}
