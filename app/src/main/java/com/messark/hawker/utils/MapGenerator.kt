package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.HexTile
import com.messark.hawker.model.TileType
import kotlin.random.Random

/**
 * Procedural map generator for Hawker Rush.
 * Optimized for variety and organic-looking environments using axial coordinate logic.
 */
object MapGenerator {

    /**
     * Converts odd-r offset coordinates (used by the initial map strings) to axial coordinates.
     */
    private fun offsetToAxial(qOffset: Int, r: Int): AxialCoordinate {
        val q = qOffset - (r - (r and 1)) / 2
        return AxialCoordinate(q, r)
    }

    /**
     * Generates a random vertical map with a guaranteed winding path and clumped obstructions.
     */
    fun generateRandomVerticalMap(
        width: Int = 8,
        height: Int = 16,
        random: Random = Random.Default
    ): Triple<Map<AxialCoordinate, HexTile>, AxialCoordinate, AxialCoordinate> {
        require(width > 0) { "Width must be positive" }
        require(height > 1) { "Height must be at least 2 to have a start and end row" }

        val hexes = mutableMapOf<AxialCoordinate, HexTile>()

        // Define bounds in offset-space for consistency with requested dimensions
        // but internal logic will mostly use the resulting axial coordinates.
        val startQOffset = random.nextInt(width)
        val endQOffset = random.nextInt(width)
        val startR = height - 1
        val endR = 0

        val startPos = offsetToAxial(startQOffset, startR)
        val endPos = offsetToAxial(endQOffset, endR)

        var allCoords = mutableSetOf<AxialCoordinate>()
        val rowCounts = mutableMapOf<Int, Int>()
        for (r in 0 until height) {
            for (q_offset in 0 until width) {
                allCoords.add(offsetToAxial(q_offset, r))
            }
            rowCounts[r] = width
        }

        // 0. Remove random edge holes
        val numHoles = height / 2
        val edgeCandidates = mutableListOf<Pair<Int, Int>>() // Pair(qOffset, r)
        for (r in 1 until height - 1) { // Exclude start and end rows
            edgeCandidates.add(0 to r)
            edgeCandidates.add((width - 1) to r)
        }
        edgeCandidates.shuffle(random)

        val holes = mutableSetOf<AxialCoordinate>()
        var holesPlaced = 0
        for ((qOffset, r) in edgeCandidates) {
            if (holesPlaced >= numHoles) break
            if ((rowCounts[r] ?: 0) > 4) {
                val coord = offsetToAxial(qOffset, r)
                holes.add(coord)
                rowCounts[r] = (rowCounts[r] ?: 0) - 1
                holesPlaced++
            }
        }
        allCoords = allCoords.filter { it !in holes }.toMutableSet()

        // Re-calculate start/end in case the original random ones were holes (though they shouldn't be as we excluded rows)
        // But let's make sure start/end are valid for the selected width/height
        val finalStartPos = if (offsetToAxial(startQOffset, startR) in allCoords) {
            offsetToAxial(startQOffset, startR)
        } else {
            allCoords.filter { it.r == startR }.random(random)
        }
        val finalEndPos = if (offsetToAxial(endQOffset, endR) in allCoords) {
            offsetToAxial(endQOffset, endR)
        } else {
            allCoords.filter { it.r == endR }.random(random)
        }

        // 1. Generate a winding path using a biased random walk
        val path = generateWindingPath(finalStartPos, finalEndPos, allCoords, random)

        // 2. Identify potential obstruction coordinates (not on path, not start/end)
        val obstructionCandidates = allCoords.filter {
            it != finalStartPos && it != finalEndPos && it !in path
        }.toMutableSet()

        // 3. Place clumped pillars (obstructions)
        val pillars = generateClumpedPillars(obstructionCandidates, random)

        // 4. Construct the HexTile map
        allCoords.forEach { coord ->
            val type = when (coord) {
                finalStartPos -> TileType.START
                finalEndPos -> TileType.GOAL_TABLE
                in pillars -> TileType.PILLAR
                else -> TileType.FLOOR
            }

            val floorVariant = if (type != TileType.PILLAR) getWeightedFloorVariant(random) else 0
            hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
        }

        // 5. Place DRAIN tiles (horizontal strips)
        placeDrainStrips(hexes, width, height, random)

        return Triple(hexes, finalStartPos, finalEndPos)
    }

    /**
     * Creates a path from start to end by performing a biased random walk.
     * Ensures the path stays within the grid and eventually reaches the destination.
     */
    private fun generateWindingPath(
        start: AxialCoordinate,
        end: AxialCoordinate,
        bounds: Set<AxialCoordinate>,
        random: Random
    ): Set<AxialCoordinate> {
        val path = mutableSetOf(start)
        var current = start

        // Maximum iterations to prevent infinite loops in degenerate grids
        var iterations = 0
        while (current != end && iterations < 1000) {
            iterations++
            val neighbors = GridUtils.getNeighbors(current).filter { it in bounds }
            if (neighbors.isEmpty()) break

            // Weight neighbors by distance to end; prefer those that move closer
            // but allow some random exploration to create 'winding'.
            val next = neighbors.minByOrNull { neighbor ->
                val dist = GridUtils.axialDistance(neighbor, end)
                val noise = random.nextFloat() * 2.0f // Add noise to encourage winding
                dist + noise
            } ?: break

            current = next
            path.add(current)
        }

        // Final check: if the walk failed to reach the goal, fallback to A* to ensure connectivity
        if (current != end) {
            val fallbackPath = Pathfinding.findPath(start, end, emptySet(), bounds)
            if (fallbackPath != null) path.addAll(fallbackPath)
        }

        return path
    }

    /**
     * Places pillars in organic-looking clumps rather than uniform distribution.
     */
    private fun generateClumpedPillars(
        candidates: Set<AxialCoordinate>,
        random: Random
    ): Set<AxialCoordinate> {
        val pillars = mutableSetOf<AxialCoordinate>()
        val remaining = candidates.toMutableSet()

        // Seed pillar "cores" - Aim for 2-5 clusters depending on map size
        val numCores = (candidates.size * 0.04f).toInt().coerceIn(2, 5)
        val cores = mutableListOf<AxialCoordinate>()
        repeat(numCores) {
            if (remaining.isNotEmpty()) {
                val core = remaining.elementAt(random.nextInt(remaining.size))
                cores.add(core)
                pillars.add(core)
                remaining.remove(core)
            }
        }

        // "Grow" the clumps linearly to create lines instead of blobs
        cores.forEach { core ->
            var current = core
            val lineLength = random.nextInt(1, 3) // Grow by 1 or 2 additional pillars
            val direction = GridUtils.NEIGHBOR_OFFSETS.random(random)

            repeat(lineLength) {
                val next = AxialCoordinate(current.q + direction.q, current.r + direction.r)
                if (next in remaining) {
                    pillars.add(next)
                    remaining.remove(next)
                    current = next
                }
            }
        }

        return pillars
    }

    /**
     * Places horizontal strips of DRAIN tiles to break up the floor.
     */
    private fun placeDrainStrips(
        hexes: MutableMap<AxialCoordinate, HexTile>,
        width: Int,
        height: Int,
        random: Random
    ) {
        if (height <= 4) return

        // Attempt to place 1-2 drain strips
        val numStrips = random.nextInt(1, 3)
        val eligibleRows = (2 until height - 2).shuffled(random)

        var stripsPlaced = 0
        for (row in eligibleRows) {
            if (stripsPlaced >= numStrips) break

            val stripLength = 4
            val maxStartQ = (width - stripLength).coerceAtLeast(0)
            val startQOffset = random.nextInt(maxStartQ + 1)

            val coords = (0 until stripLength).map { offsetToAxial(startQOffset + it, row) }

            // Only place if all tiles in the strip are currently FLOOR
            if (coords.all { hexes[it]?.type == TileType.FLOOR }) {
                coords.forEach { coord ->
                    hexes[coord] = hexes[coord]!!.copy(type = TileType.DRAIN)
                }
                stripsPlaced++
            }
        }
    }

    fun generateMap(
        mapData: List<String>,
        random: Random = Random.Default
    ): Map<AxialCoordinate, HexTile> {
        val hexes = mutableMapOf<AxialCoordinate, HexTile>()

        mapData.forEachIndexed { r, row ->
            row.forEachIndexed { q_offset, char ->
                val coord = offsetToAxial(q_offset, r)

                val type = when (char) {
                    'F' -> TileType.FLOOR
                    'P' -> TileType.PILLAR
                    'G' -> TileType.GOAL_TABLE
                    '1', '2', '3', '4', 'T' -> TileType.FLOOR // Simplification for loader
                    ' ' -> TileType.FLOOR
                    else -> TileType.FLOOR
                }

                if (!hexes.containsKey(coord)) {
                    val floorVariant = if (type == TileType.FLOOR) getWeightedFloorVariant(random) else 0
                    hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
                }
            }
        }

        return hexes
    }

    private fun getWeightedFloorVariant(random: Random): Int {
        return if (random.nextFloat() < 0.90f) 0 else 1 + random.nextInt(6)
    }
}
