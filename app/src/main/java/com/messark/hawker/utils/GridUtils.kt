package com.messark.hawker.utils

import androidx.compose.ui.geometry.Offset

import com.messark.hawker.model.AxialCoordinate

object GridUtils {
    private val NEIGHBOR_OFFSETS = listOf(
        AxialCoordinate(1, 0),
        AxialCoordinate(1, -1),
        AxialCoordinate(0, -1),
        AxialCoordinate(-1, 0),
        AxialCoordinate(-1, 1),
        AxialCoordinate(0, 1)
    )

    /**
     * Returns all adjacent axial coordinates for a given coordinate.
     */
    fun getAdjacentCoordinates(coord: AxialCoordinate): List<AxialCoordinate> {
        return NEIGHBOR_OFFSETS.map { offset ->
            AxialCoordinate(coord.q + offset.q, coord.r + offset.r)
        }
    }

    /**
     * Converts axial coordinates (q, r) to screen coordinates.
     */
    fun toScreenPrecise(
        q: Float,
        r: Float,
        hexWidthPx: Float,
        hexHeightPx: Float,
        rowSpacingFactor: Float,
        borderPx: Float
    ): Offset {
        val x = (q + r / 2f) * hexWidthPx + borderPx + hexWidthPx / 2f
        val y = r * (hexHeightPx * rowSpacingFactor) + borderPx + hexHeightPx / 2f
        return Offset(x, y)
    }

    /**
     * Calculates axial distance between two precise coordinates.
     */
    fun axialDistance(a: com.messark.hawker.model.PreciseAxialCoordinate, b: com.messark.hawker.model.PreciseAxialCoordinate): Float {
        return (Math.abs(a.q - b.q) + Math.abs(a.q + a.r - b.q - b.r) + Math.abs(a.r - b.r)) / 2f
    }
}
