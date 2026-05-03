package com.messark.hawker

import com.messark.hawker.model.AxialCoordinate
import com.messark.hawker.utils.Pathfinding
import org.junit.Assert.*
import org.junit.Test

class PathfindingTest {

    @Test
    fun testSimplePath() {
        val allCoords = setOf(
            AxialCoordinate(0, 0),
            AxialCoordinate(1, 0),
            AxialCoordinate(2, 0)
        )
        val path = Pathfinding.findPath(
            start = AxialCoordinate(0, 0),
            end = AxialCoordinate(2, 0),
            blockedPositions = emptySet(),
            allCoordinates = allCoords
        )
        assertNotNull(path)
        assertEquals(3, path?.size)
        assertEquals(AxialCoordinate(0, 0), path?.get(0))
        assertEquals(AxialCoordinate(1, 0), path?.get(1))
        assertEquals(AxialCoordinate(2, 0), path?.get(2))
    }

    @Test
    fun testBlockedPath() {
        // (0,0) -> (1,0) -> (2,0)
        // Let's block (1,0)
        val allCoords = setOf(
            AxialCoordinate(0, 0),
            AxialCoordinate(1, 0),
            AxialCoordinate(2, 0),
            AxialCoordinate(0, 1),
            AxialCoordinate(1, 1),
            AxialCoordinate(2, 1)
        )
        val blocked = setOf(AxialCoordinate(1, 0), AxialCoordinate(1, 1))

        // With (1,0) and (1,1) blocked, there is no path in this small grid if it's just a line
        // But axial neighbors of (0,0) are (1,0), (1,-1), (0,-1), (-1,0), (-1,1), (0,1)

        val path = Pathfinding.findPath(
            start = AxialCoordinate(0, 0),
            end = AxialCoordinate(2, 0),
            blockedPositions = blocked,
            allCoordinates = allCoords
        )
        assertNull("Path should be null when blocked", path)
    }

    @Test
    fun testEndIsBlockedButReachable() {
        val allCoords = setOf(
            AxialCoordinate(0, 0),
            AxialCoordinate(1, 0)
        )
        // End is (1,0), and it is blocked
        val path = Pathfinding.findPath(
            start = AxialCoordinate(0, 0),
            end = AxialCoordinate(1, 0),
            blockedPositions = setOf(AxialCoordinate(1, 0)),
            allCoordinates = allCoords
        )
        assertNotNull("Path should be found even if end is technically blocked", path)
        assertEquals(2, path?.size)
        assertEquals(AxialCoordinate(1, 0), path?.last())
    }

    @Test
    fun testStartIsEnd() {
        val coord = AxialCoordinate(5, 5)
        val path = Pathfinding.findPath(coord, coord, emptySet(), setOf(coord))
        assertEquals(listOf(coord), path)
    }
}
