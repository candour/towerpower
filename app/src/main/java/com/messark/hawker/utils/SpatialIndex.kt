package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.PreciseAxialCoordinate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A spatial index for entities on an axial grid.
 * Partitioning entities into buckets based on their integer axial coordinates (q, r).
 * Optimized for transient frame-by-frame usage in a hexagonal game engine.
 */
class SpatialIndex<T>(
    entities: Iterable<T>,
    private val getPosition: (T) -> PreciseAxialCoordinate
) {
    private val buckets = mutableMapOf<AxialCoordinate, MutableList<T>>()

    init {
        entities.forEach { entity ->
            val pos = getPosition(entity)
            // Use floor for consistent bucketing across the origin and negative coordinates
            val bucketCoord = AxialCoordinate(floor(pos.q.toDouble()).toInt(), floor(pos.r.toDouble()).toInt())
            buckets.getOrPut(bucketCoord) { mutableListOf() }.add(entity)
        }
    }

    /**
     * Finds entities within a specific axial distance of a center point.
     * Iterates only through buckets that can potentially contain entities within the radius.
     */
    fun findNearby(center: PreciseAxialCoordinate, radius: Float): List<T> {
        val result = mutableListOf<T>()
        // Radius + 1 to account for the fact that an entity at (1.9, 0) is in bucket (1,0)
        // but could be within distance 1 of a center at (0.9, 0).
        // Actually, since we bucket by floor(q), floor(r),
        // an entity at pos (q, r) is in bucket (floor(q), floor(r)).
        // Distance between (cq, cr) and (q, r) <= radius.
        // We need to check buckets (bq, br) such that there exists (q, r) in bucket (bq, br)
        // with dist((cq, cr), (q, r)) <= radius.
        // The maximum distance between any point in bucket (bq, br) and the integer coordinate (bq, br) is 1.0 in axial?
        // Let's be safe and use ceil(radius) + 1.
        val range = ceil(radius.toDouble()).toInt() + 1

        val cq = floor(center.q.toDouble()).toInt()
        val cr = floor(center.r.toDouble()).toInt()

        for (dq in -range..range) {
            val drMin = max(-range, -dq - range)
            val drMax = min(range, -dq + range)
            for (dr in drMin..drMax) {
                buckets[AxialCoordinate(cq + dq, cr + dr)]?.let { bucket ->
                    // Use indexed loop to avoid iterator allocation in hot path
                    for (i in bucket.indices) {
                        val entity = bucket[i]
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
