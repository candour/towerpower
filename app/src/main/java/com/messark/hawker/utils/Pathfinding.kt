package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import java.util.*
import kotlin.math.abs

/**
 * Finds the shortest path between two axial coordinates using the A* algorithm.
 * Optimized to avoid O(N) removals from the priority queue and minimize allocations.
 */
object Pathfinding {
    private val NEIGHBOR_OFFSETS = arrayOf(
        AxialCoordinate(1, 0), AxialCoordinate(1, -1), AxialCoordinate(0, -1),
        AxialCoordinate(-1, 0), AxialCoordinate(-1, 1), AxialCoordinate(0, 1)
    )

    fun findPath(
        start: AxialCoordinate,
        end: AxialCoordinate,
        blockedPositions: Set<AxialCoordinate>,
        allCoordinates: Set<AxialCoordinate>
    ): List<AxialCoordinate>? {
        if (start == end) return listOf(start)

        val gScores = mutableMapOf(start to 0)
        val parents = mutableMapOf<AxialCoordinate, AxialCoordinate>()
        // We allow duplicates in the priority queue to avoid O(N) removals.
        // A coordinate is only processed once because of the closedSet.
        val openSet = PriorityQueue<Node>(compareBy { it.fScore })
        val closedSet = mutableSetOf<AxialCoordinate>()

        openSet.add(Node(heuristic(start, end), start))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()?.coordinate ?: break

            if (current == end) {
                return generateSequence(end) { parents[it] }.toList().reversed()
            }

            if (!closedSet.add(current)) continue

            val currentG = gScores[current] ?: continue

            for (offset in NEIGHBOR_OFFSETS) {
                val neighbor = AxialCoordinate(current.q + offset.q, current.r + offset.r)

                // End is never blocked for pathfinding purposes
                val isBlocked = neighbor in blockedPositions && neighbor != end
                if (neighbor !in allCoordinates || isBlocked || neighbor in closedSet) continue

                val tentativeG = currentG + 1
                if (tentativeG < (gScores[neighbor] ?: Int.MAX_VALUE)) {
                    gScores[neighbor] = tentativeG
                    parents[neighbor] = current
                    openSet.add(Node(tentativeG + heuristic(neighbor, end), neighbor))
                }
            }
        }

        return null // No path found
    }

    private fun heuristic(a: AxialCoordinate, b: AxialCoordinate): Int =
        (abs(a.q - b.q) + abs(a.q + a.r - b.q - b.r) + abs(a.r - b.r)) / 2

    private class Node(val fScore: Int, val coordinate: AxialCoordinate)
}
