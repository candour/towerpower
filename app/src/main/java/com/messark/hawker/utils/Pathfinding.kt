package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import java.util.*

/**
 * Finds the shortest path between two axial coordinates using the A* algorithm.
 * Optimized for performance and idiomatic Kotlin.
 */
object Pathfinding {

    fun findPath(
        start: AxialCoordinate,
        end: AxialCoordinate,
        blockedPositions: Set<AxialCoordinate>,
        allCoordinates: Set<AxialCoordinate>
    ): List<AxialCoordinate>? {
        if (start == end) return listOf(start)

        // Maps to track scores and parent nodes for path reconstruction
        val gScores = mutableMapOf<AxialCoordinate, Int>().apply { this[start] = 0 }
        val parents = mutableMapOf<AxialCoordinate, AxialCoordinate>()

        // Priority queue for A* search, prioritized by fScore (gScore + heuristic)
        val openSet = PriorityQueue<Node>(compareBy { it.fScore })
        val closedSet = mutableSetOf<AxialCoordinate>()

        openSet.add(Node(start, GridUtils.axialDistance(start, end)))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll()?.coordinate ?: break

            // Goal reached: reconstruct and return path
            if (current == end) {
                return reconstructPath(parents, end)
            }

            // Skip if already processed (A* optimization with PriorityQueue duplicates)
            if (!closedSet.add(current)) continue

            val currentG = gScores[current] ?: continue

            for (neighbor in GridUtils.getNeighbors(current)) {
                // Bounds and collision checks
                // Note: The destination 'end' is always considered walkable for pathfinding.
                val isBlocked = neighbor in blockedPositions && neighbor != end
                if (neighbor !in allCoordinates || isBlocked || neighbor in closedSet) continue

                val tentativeG = currentG + 1
                if (tentativeG < (gScores[neighbor] ?: Int.MAX_VALUE)) {
                    gScores[neighbor] = tentativeG
                    parents[neighbor] = current
                    val fScore = tentativeG + GridUtils.axialDistance(neighbor, end)
                    openSet.add(Node(neighbor, fScore))
                }
            }
        }

        return null // No path found
    }

    private fun reconstructPath(
        parents: Map<AxialCoordinate, AxialCoordinate>,
        end: AxialCoordinate
    ): List<AxialCoordinate> {
        val path = mutableListOf(end)
        var current = end
        while (parents.containsKey(current)) {
            current = parents[current]!!
            path.add(current)
        }
        return path.asReversed()
    }

    private class Node(val coordinate: AxialCoordinate, val fScore: Int)
}
