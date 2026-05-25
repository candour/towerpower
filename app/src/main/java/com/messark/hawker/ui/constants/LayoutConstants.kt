package com.messark.hawker.ui.constants

import androidx.compose.ui.unit.dp

object LayoutConstants {
    val BOARD_BORDER_SIZE = 20.dp
    // These fractions are now deprecated in favor of dynamic calculations in MainActivity
    @Deprecated("Use dynamic height calculation in MainActivity")
    const val BOARD_HEIGHT_FRACTION = 0.75f
    @Deprecated("Use dynamic height calculation in MainActivity")
    const val CONTROL_PANEL_HEIGHT_FRACTION = 0.25f
}
