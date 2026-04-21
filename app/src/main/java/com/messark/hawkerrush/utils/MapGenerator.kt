package com.messark.hawkerrush.utils

import com.messark.hawkerrush.model.AxialCoordinate
import com.messark.hawkerrush.model.HexTile
import com.messark.hawkerrush.model.TileType
import kotlin.random.Random

object MapGenerator {

    fun generateRandomVerticalMap(width: Int = 8, height: Int = 16): Triple<Map<AxialCoordinate, HexTile>, AxialCoordinate, AxialCoordinate> {
        while (true) {
            val hexes = mutableMapOf<AxialCoordinate, HexTile>()
            val startQOffset = Random.nextInt(width)
            val endQOffset = Random.nextInt(width)

            val startR = height - 1
            val startQ = startQOffset - (startR - (startR and 1)) / 2
            val startPos = AxialCoordinate(startQ, startR)

            val endR = 0
            val endQ = endQOffset - (endR - (endR and 1)) / 2
            val endPos = AxialCoordinate(endQ, endR)

            val allCoords = mutableSetOf<AxialCoordinate>()

            for (r in 0 until height) {
                for (q_offset in 0 until width) {
                    val q = q_offset - (r - (r and 1)) / 2
                    val coord = AxialCoordinate(q, r)
                    allCoords.add(coord)

                    val type = when (coord) {
                        startPos -> TileType.START
                        endPos -> TileType.GOAL_TABLE
                        else -> {
                            if (Random.nextFloat() < 0.10f) TileType.PILLAR else TileType.FLOOR
                        }
                    }

                    val floorVariant = if (type == TileType.FLOOR || type == TileType.GOAL_TABLE || type == TileType.START) {
                        getWeightedFloorVariant()
                    } else 0
                    hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
                }
            }

            // Verify path exists
            val blocked = hexes.values.filter {
                it.type == TileType.PILLAR
            }.map { it.coordinate }.toSet()

            val path = Pathfinding.findPath(startPos, endPos, blocked, allCoords)
            if (path != null) {
                return Triple(hexes, startPos, endPos)
            }
        }
    }

    fun generateMap(mapData: List<String>): Map<AxialCoordinate, HexTile> {
        val hexes = mutableMapOf<AxialCoordinate, HexTile>()

        mapData.forEachIndexed { r, row ->
            row.forEachIndexed { q_offset, char ->
                // Convert offset coordinates (from List<String>) to Axial
                // Assuming the input strings represent an odd-r offset grid
                val q = q_offset - (r - (r and 1)) / 2
                val coord = AxialCoordinate(q, r)

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
                        getWeightedFloorVariant()
                    } else 0
                    hexes[coord] = HexTile(coord, type, floorVariant = floorVariant)
                }
            }
        }

        return hexes
    }

    private fun getWeightedFloorVariant(): Int {
        return if (Random.nextFloat() < 0.90f) {
            0
        } else {
            1 + Random.nextInt(6) // floor01 to floor10 (20% total)
        }
    }

    private fun getNeighbors(coord: AxialCoordinate): List<AxialCoordinate> {
        return listOf(
            AxialCoordinate(coord.q + 1, coord.r),
            AxialCoordinate(coord.q + 1, coord.r - 1),
            AxialCoordinate(coord.q, coord.r - 1),
            AxialCoordinate(coord.q - 1, coord.r),
            AxialCoordinate(coord.q - 1, coord.r + 1),
            AxialCoordinate(coord.q, coord.r + 1)
        )
    }
}
