package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import java.util.*

object Pathfinding {
    private val NEIGHBOR_OFFSETS = arrayOf(
        AxialCoordinate(1, 0), AxialCoordinate(1, -1), AxialCoordinate(0, -1),
        AxialCoordinate(-1, 0), AxialCoordinate(-1, 1), AxialCoordinate(0, 1)
    )

    /**
     * Finds the shortest path between two axial coordinates using the A* algorithm.
     * Optimized to avoid O(N) removals from the priority queue and minimize allocations.
     */
    fun findPath(
        start: AxialCoordinate,
        end: AxialCoordinate,
        blockedPositions: Set<AxialCoordinate>,
        allCoordinates: Set<AxialCoordinate>
    ): List<AxialCoordinate>? {
        if (start == end) return listOf(start)

        val openSet = PriorityQueue<ScoredCoordinate>()
        val gScores = mutableMapOf<AxialCoordinate, Int>()
        val parents = mutableMapOf<AxialCoordinate, AxialCoordinate>()
        val closedSet = mutableSetOf<AxialCoordinate>()

        gScores[start] = 0
        openSet.add(ScoredCoordinate(heuristic(start, end), start))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()?.coordinate ?: break

            if (current == end) return reconstructPath(end, parents)
            if (!closedSet.add(current)) continue

            val currentG = gScores[current] ?: continue

            for (offset in NEIGHBOR_OFFSETS) {
                val neighborPos = AxialCoordinate(current.q + offset.q, current.r + offset.r)

                // End is never blocked for pathfinding purposes
                if (neighborPos !in allCoordinates || (neighborPos in blockedPositions && neighborPos != end) || neighborPos in closedSet) continue

                val tentativeGScore = currentG + 1
                if (tentativeGScore < (gScores[neighborPos] ?: Int.MAX_VALUE)) {
                    gScores[neighborPos] = tentativeGScore
                    parents[neighborPos] = current
                    openSet.add(ScoredCoordinate(tentativeGScore + heuristic(neighborPos, end), neighborPos))
                }
            }
        }

        return null // No path found
    }

    private fun heuristic(a: AxialCoordinate, b: AxialCoordinate): Int {
        return (Math.abs(a.q - b.q) + Math.abs(a.q + a.r - b.q - b.r) + Math.abs(a.r - b.r)) / 2
    }

    private fun reconstructPath(end: AxialCoordinate, parents: Map<AxialCoordinate, AxialCoordinate>): List<AxialCoordinate> {
        val path = mutableListOf<AxialCoordinate>()
        var current: AxialCoordinate? = end
        while (current != null) {
            path.add(current)
            current = parents[current]
        }
        return path.reversed()
    }

    private class ScoredCoordinate(val fScore: Int, val coordinate: AxialCoordinate) : Comparable<ScoredCoordinate> {
        override fun compareTo(other: ScoredCoordinate): Int = fScore.compareTo(other.fScore)
    }
}
