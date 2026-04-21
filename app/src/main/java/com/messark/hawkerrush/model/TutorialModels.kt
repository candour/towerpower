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
