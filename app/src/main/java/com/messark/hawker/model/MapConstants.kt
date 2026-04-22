package com.messark.hawker.model

object MapConstants {
    val INITIAL_MAP = listOf(
        "11111G22222",
        "1FFFFFFFFF2",
        "1FPPPPPPPF2",
        "1F F  F  F2",
        "1F F  F  F2",
        "1F F  F  F2",
        "3FFFFFFFFF4",
        "33333 44444"
    )

    // Legend:
    // F: Floor
    // P: Pillar
    // G: Goal Table
    // 1: Edge NW
    // 2: Edge NE
    // 3: Edge SW
    // 4: Edge SE
    // T: Edge Top Corner (unused in this map layout)
}
