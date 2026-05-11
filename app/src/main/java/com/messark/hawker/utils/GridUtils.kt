package com.messark.hawker.utils

import androidx.compose.ui.geometry.Offset
import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.PreciseAxialCoordinate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object GridUtils {
    const val ISOMETRIC_Y_FACTOR = (91f / 101f) * 0.69f

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

    /**
     * Rounds fractional axial coordinates to the nearest hex coordinate.
     */
    fun hexRound(q: Float, r: Float): AxialCoordinate {
        var rq = q.roundToInt()
        var rr = r.roundToInt()
        var rs = (-q - r).roundToInt()

        val qDiff = abs(rq - q)
        val rDiff = abs(rr - r)
        val sDiff = abs(rs - (-q - r))

        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs
        } else if (rDiff > sDiff) {
            rr = -rq - rs
        }

        return AxialCoordinate(rq, rr)
    }

    fun lineIntersectsCircle(x1: Float, y1: Float, x2: Float, y2: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1

        val fx = x1 - cx
        val fy = y1 - cy

        val a = dx * dx + dy * dy
        if (a < 0.0001f) return false // Essentially same point

        val b = 2 * (fx * dx + fy * dy)
        val c = (fx * fx + fy * fy) - r * r

        var discriminant = b * b - 4 * a * c
        if (discriminant < 0) {
            return false
        } else {
            discriminant = sqrt(discriminant.toDouble()).toFloat()
            val t1 = (-b - discriminant) / (2 * a)
            val t2 = (-b + discriminant) / (2 * a)

            if ((t1 in 0f..1f) || (t2 in 0f..1f)) {
                return true
            }
            if (t1 < 0 && t2 > 1) return true
        }
        return false
    }

    fun isLineOfSightBlocked(
        stallCoord: AxialCoordinate,
        enemyPos: PreciseAxialCoordinate,
        obstructions: List<AxialCoordinate>
    ): Boolean {
        if (obstructions.isEmpty()) return false

        val x1 = stallCoord.q + stallCoord.r / 2f
        val y1 = stallCoord.r * ISOMETRIC_Y_FACTOR

        val x2 = enemyPos.q + enemyPos.r / 2f
        val y2 = enemyPos.r * ISOMETRIC_Y_FACTOR

        val radius = 0.25f // Blocked area is half diameter (0.5), so radius is 0.25

        for (pc in obstructions) {
            val px = pc.q + pc.r / 2f
            val py = pc.r * ISOMETRIC_Y_FACTOR

            if (lineIntersectsCircle(x1, y1, x2, y2, px, py, radius)) {
                return true
            }
        }
        return false
    }
}
