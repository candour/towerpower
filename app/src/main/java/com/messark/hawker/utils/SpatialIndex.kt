package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.PreciseAxialCoordinate
import kotlin.math.ceil

/**
 * A spatial index for entities on an axial grid.
 * Partitioning entities into buckets based on their integer axial coordinates (q, r).
 */
class SpatialIndex<T>(
    entities: Iterable<T>,
    private val getPosition: (T) -> PreciseAxialCoordinate
) {
    private val buckets = mutableMapOf<AxialCoordinate, MutableList<T>>()

    init {
        entities.forEach { entity ->
            val pos = getPosition(entity)
            val bucketCoord = AxialCoordinate(pos.q.toInt(), pos.r.toInt())
            buckets.getOrPut(bucketCoord) { mutableListOf() }.add(entity)
        }
    }

    /**
     * Finds entities within a specific axial distance of a center point.
     * Iterates through candidate buckets within the radius range.
     */
    fun findNearby(center: PreciseAxialCoordinate, radius: Float): List<T> {
        val result = mutableListOf<T>()
        val cq = center.q.toInt()
        val cr = center.r.toInt()
        val range = ceil(radius).toInt() + 1

        for (dq in -range..range) {
            for (dr in -range..range) {
                buckets[AxialCoordinate(cq + dq, cr + dr)]?.let { bucket ->
                    bucket.forEach { entity ->
                        if (GridUtils.axialDistance(center, getPosition(entity)) <= radius) {
                            result.add(entity)
                        }
                    }
                }
            }
        }
        return result
    }
}
