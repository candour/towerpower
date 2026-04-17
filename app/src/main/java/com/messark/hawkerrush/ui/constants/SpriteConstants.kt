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
        StallType.TEH_TARIK to IntRect(22, 41, 330, 451),
        StallType.SATAY to IntRect(358, 41, 666, 451),
        StallType.CHICKEN_RICE to IntRect(22, 500, 330, 930),
        StallType.ICE_KACHANG to IntRect(358, 500, 666, 930),
        StallType.DURIAN to IntRect(33, 961, 341, 1318)
    )

    // Enemies (from drawable-nodpi/enemies.png)
    const val ENEMY_SPRITE_WIDTH = 100
    const val ENEMY_SPRITE_HEIGHT = 125
    const val ENEMY_SPRITE_FRAMES = 3

    val ENEMY_ROW_INDICES = mapOf(
        com.messark.hawkerrush.model.EnemyType.AUNTIE to 0,
        com.messark.hawkerrush.model.EnemyType.TOURIST to 1,
        com.messark.hawkerrush.model.EnemyType.SALARYMAN to 2,
        com.messark.hawkerrush.model.EnemyType.DELIVERY_RIDER to 3
    )

    val FX_PUDDLE_RECT = IntRect(1078, 679, 1142, 741)

    // Buttons (from drawable-nodpi/buttons.png)
    val BTN_RESUME_RECT = IntRect(0, 1, 350, 96)
    val BTN_RESUME_CLICK_RECT = IntRect(0, 95, 350, 183)
    val BTN_NEWGAME_RECT = IntRect(0, 182, 350, 277)
    val BTN_NEWGAME_CLICK_RECT = IntRect(0, 276, 350, 370)
    val BTN_OPTIONS_RECT = IntRect(0, 369, 350, 463)
    val BTN_OPTIONS_CLICK_RECT = IntRect(0, 462, 350, 556)
    val BTN_MAINMENU_RECT = IntRect(0, 555, 350, 650)
    val BTN_SAVE_RECT = IntRect(0, 649, 350, 744)
    val BTN_CANCEL_RECT = IntRect(0, 743, 350, 838)
    val BTN_CANCEL_CLICK_RECT = IntRect(0, 837, 350, 932)
}
