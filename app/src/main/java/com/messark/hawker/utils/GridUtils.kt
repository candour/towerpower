package com.messark.hawker.utils

import androidx.compose.ui.geometry.Offset
import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.PreciseAxialCoordinate
import kotlin.math.abs

object GridUtils {
    val NEIGHBOR_OFFSETS = listOf(
        AxialCoordinate(1, 0), AxialCoordinate(1, -1), AxialCoordinate(0, -1),
        AxialCoordinate(-1, 0), AxialCoordinate(-1, 1), AxialCoordinate(0, 1)
    )

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
    fun axialDistance(a: PreciseAxialCoordinate, b: PreciseAxialCoordinate): Float {
        return (abs(a.q - b.q) + abs(a.q + a.r - b.q - b.r) + abs(a.r - b.r)) / 2f
    }

    /**
     * Calculates axial distance between two integer coordinates.
     */
    fun axialDistance(a: AxialCoordinate, b: AxialCoordinate): Int {
        return (abs(a.q - b.q) + abs(a.q + a.r - b.q - b.r) + abs(a.r - b.r)) / 2
    }

    /**
     * Returns neighbors of an axial coordinate.
     */
    fun getNeighbors(coord: AxialCoordinate): List<AxialCoordinate> {
        return NEIGHBOR_OFFSETS.map { offset ->
            AxialCoordinate(coord.q + offset.q, coord.r + offset.r)
        }
    }
}
