package com.messark.hawkerrush.utils

import com.messark.hawkerrush.model.AxialCoordinate
import java.util.*

object Pathfinding {
    fun findPath(
        start: AxialCoordinate,
        end: AxialCoordinate,
        blockedPositions: Set<AxialCoordinate>,
        allCoordinates: Set<AxialCoordinate>
    ): List<AxialCoordinate>? {
        if (start == end) return listOf(start)

        val openSet = PriorityQueue<Node>(compareBy { it.fScore })
        val openSetPositions = mutableSetOf<AxialCoordinate>()
        val closedSet = mutableSetOf<AxialCoordinate>()
        val nodes = mutableMapOf<AxialCoordinate, Node>()

        val startNode = Node(start, gScore = 0, hScore = heuristic(start, end))
        openSet.add(startNode)
        openSetPositions.add(start)
        nodes[start] = startNode

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            openSetPositions.remove(current.coordinate)

            if (current.coordinate == end) {
                return reconstructPath(current)
            }

            closedSet.add(current.coordinate)

            for (neighborPos in getNeighbors(current.coordinate)) {
                // The destination 'end' should not be considered blocked for the purpose of finding a path to it.
                if (neighborPos !in allCoordinates || (neighborPos in blockedPositions && neighborPos != end) || neighborPos in closedSet) continue

                val tentativeGScore = current.gScore + 1
                val neighborNode = nodes.getOrPut(neighborPos) { Node(neighborPos) }

                if (tentativeGScore < neighborNode.gScore) {
                    neighborNode.parent = current
                    neighborNode.gScore = tentativeGScore
                    neighborNode.hScore = heuristic(neighborPos, end)

                    if (neighborPos in openSetPositions) {
                        openSet.remove(neighborNode)
                    }
                    openSet.add(neighborNode)
                    openSetPositions.add(neighborPos)
                }
            }
        }

        return null // No path found
    }

    private fun heuristic(a: AxialCoordinate, b: AxialCoordinate): Int {
        return (Math.abs(a.q - b.q) + Math.abs(a.q + a.r - b.q - b.r) + Math.abs(a.r - b.r)) / 2
    }

    private fun getNeighbors(coord: AxialCoordinate): List<AxialCoordinate> {
        // Axial neighbors:
        // (q+1, r), (q+1, r-1), (q, r-1), (q-1, r), (q-1, r+1), (q, r+1)
        return listOf(
            AxialCoordinate(coord.q + 1, coord.r),
            AxialCoordinate(coord.q + 1, coord.r - 1),
            AxialCoordinate(coord.q, coord.r - 1),
            AxialCoordinate(coord.q - 1, coord.r),
            AxialCoordinate(coord.q - 1, coord.r + 1),
            AxialCoordinate(coord.q, coord.r + 1)
        )
    }

    private fun reconstructPath(node: Node): List<AxialCoordinate> {
        val path = mutableListOf<AxialCoordinate>()
        var current: Node? = node
        while (current != null) {
            path.add(current.coordinate)
            current = current.parent
        }
        return path.reversed()
    }

    private class Node(
        val coordinate: AxialCoordinate,
        var parent: Node? = null,
        var gScore: Int = Int.MAX_VALUE,
        var hScore: Int = 0
    ) {
        val fScore: Int get() = gScore + hScore
    }
}
