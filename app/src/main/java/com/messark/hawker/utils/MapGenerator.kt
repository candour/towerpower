package com.messark.hawker.utils

import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.model.HexTile
import com.messark.hawker.model.TileType
import kotlin.random.Random

object MapGenerator {

    private fun offsetToAxial(qOffset: Int, r: Int): AxialCoordinate {
        val q = qOffset - (r - (r and 1)) / 2
        return AxialCoordinate(q, r)
    }

    fun generateRandomVerticalMap(
        width: Int = 8,
        height: Int = 16,
        random: Random = Random.Default
    ): Triple<Map<AxialCoordinate, HexTile>, AxialCoordinate, AxialCoordinate> {
        val hexes = mutableMapOf<AxialCoordinate, HexTile>()
        val startQOffset = random.nextInt(width)
        val endQOffset = random.nextInt(width)

        val startR = height - 1
        val startPos = offsetToAxial(startQOffset, startR)

        val endR = 0
        val endPos = offsetToAxial(endQOffset, endR)

        val allCoords = mutableSetOf<AxialCoordinate>()
        for (r in 0 until height) {
            for (q_offset in 0 until width) {
                allCoords.add(offsetToAxial(q_offset, r))
            }
        }

        // Guaranteed path carving: use A* on an empty grid to find a baseline path
        val guaranteedPath = Pathfinding.findPath(startPos, endPos, emptySet(), allCoords)?.toSet() ?: emptySet()

        // Fallback or warning if no path is found (should not happen on an empty grid)
        if (guaranteedPath.isEmpty()) {
            android.util.Log.e("MapGenerator", "Failed to carve a guaranteed path from $startPos to $endPos")
        }

        allCoords.forEach { coord ->
            val type = when (coord) {
                startPos -> TileType.START
                endPos -> TileType.GOAL_TABLE
                else -> {
                    // Only place pillars if not on the guaranteed path
                    if (coord !in guaranteedPath && random.nextFloat() < 0.12f) {
                        TileType.PILLAR
                    } else {
                        TileType.FLOOR
                    }
                }
            }

            val floorVariant = if (type != TileType.PILLAR) {
                getWeightedFloorVariant(random)
            } else 0

            hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
        }

        // Place a horizontal line of 5 DRAIN tiles on a random row (excluding first and last)
        if (height > 2) {
            val rows = (1 until height - 1).shuffled(random)
            var placed = false
            for (drainRow in rows) {
                val maxStartQOffset = (width - 5).coerceAtLeast(0)
                val offsets = (0..maxStartQOffset).shuffled(random)
                for (startQOffset in offsets) {
                    var allFloor = true
                    for (i in 0 until 5) {
                        val drainCoord = offsetToAxial(startQOffset + i, drainRow)
                        if (hexes[drainCoord]?.type != TileType.FLOOR) {
                            allFloor = false
                            break
                        }
                    }
                    if (allFloor) {
                        for (i in 0 until 5) {
                            val drainCoord = offsetToAxial(startQOffset + i, drainRow)
                            hexes[drainCoord] = hexes[drainCoord]!!.copy(type = TileType.DRAIN)
                        }
                        placed = true
                        break
                    }
                }
                if (placed) break
            }
        }

        return Triple(hexes, startPos, endPos)
    }

    fun generateMap(
        mapData: List<String>,
        random: Random = Random.Default
    ): Map<AxialCoordinate, HexTile> {
        val hexes = mutableMapOf<AxialCoordinate, HexTile>()

        mapData.forEachIndexed { r, row ->
            row.forEachIndexed { q_offset, char ->
                // Convert offset coordinates (from List<String>) to Axial
                // Assuming the input strings represent an odd-r offset grid
                val coord = offsetToAxial(q_offset, r)

                val type = when (char) {
                    'F' -> TileType.FLOOR
                    'P' -> TileType.PILLAR
                    'G' -> TileType.GOAL_TABLE
                    '1', '2', '3', '4', 'T' -> TileType.FLOOR
                    ' ' -> TileType.FLOOR
                    else -> TileType.FLOOR
                }

                // Place the tile based on character type
                if (!hexes.containsKey(coord)) {
                    val floorVariant = if (type == TileType.FLOOR) {
                        getWeightedFloorVariant(random)
                    } else 0
                    hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
                }
            }
        }

        return hexes
    }

    private fun getWeightedFloorVariant(random: Random): Int {
        return if (random.nextFloat() < 0.90f) {
            0
        } else {
            1 + random.nextInt(6) // floor02 to floor07 (floor01 is at index 0)
        }
    }
}
