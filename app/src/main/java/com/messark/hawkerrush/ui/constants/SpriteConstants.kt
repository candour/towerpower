package com.messark.hawkerrush.ui.constants

import androidx.compose.ui.unit.IntRect
import com.messark.hawkerrush.model.StallType

object SpriteConstants {
    val FLOOR_RECTS = listOf(
        IntRect(8, 33, 109, 124),   // floor01
        IntRect(126, 33, 227, 124), // floor02
        IntRect(244, 33, 345, 124), // floor03
        IntRect(361, 33, 462, 124), // floor04
        IntRect(478, 33, 579, 124), // floor05
        IntRect(595, 33, 696, 124), // floor06
        IntRect(713, 33, 814, 124), // floor07
        IntRect(595, 149, 696, 240) // floor10
    )
    val EDGE_NW_RECT = IntRect(831, 33, 932, 124)
    val EDGE_NE_RECT = IntRect(1064, 33, 1165, 124)
    val EDGE_SW_RECT = IntRect(948, 149, 1049, 240)
    val EDGE_SE_RECT = IntRect(1064, 149, 1165, 240)
    val EDGE_TOP_RECT = IntRect(830, 267, 931, 360)
    val PILLAR_RECT = IntRect(31, 501, 101, 627)
    val GOAL_TABLE_RECT = IntRect(1100, 430, 1363, 628)

    val STALL_RECTS = mapOf(
        StallType.TEH_TARIK to IntRect(28, 678, 93, 743),
        StallType.SATAY to IntRect(116, 678, 181, 743),
        StallType.CHICKEN_RICE to IntRect(204, 678, 269, 743),
        StallType.DURIAN to IntRect(292, 678, 357, 743),
        StallType.ICE_KACHANG to IntRect(382, 678, 447, 743)
    )

    val ENEMY_SALARYMAN_RECT = IntRect(615, 638, 665, 742)
    val ENEMY_TOURIST_RECT = IntRect(679, 638, 729, 742)
    val ENEMY_AUNTIE_RECT = IntRect(745, 638, 795, 742)
    val ENEMY_RIDER_RECT = IntRect(990, 677, 1044, 744)
    val FX_PUDDLE_RECT = IntRect(1078, 679, 1142, 741)
}
