package com.messark.hawker

import com.messark.hawker.model.*
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.MapGenerator
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DrainTileTest {

    @Test
    fun testDrainTileMapGeneration() {
        // Use a fixed seed for deterministic map generation
        val random = Random(42)
        val (hexes, _, _) = MapGenerator.generateRandomVerticalMap(width = 8, height = 16, random = random)

        val drainTiles = hexes.values.filter { it.type == TileType.DRAIN }

        // We expect up to 10 drain tiles (2 lines of 5)
        assertTrue("Should have some drain tiles", drainTiles.isNotEmpty())
        assertTrue("Should have no more than 10 drain tiles", drainTiles.size <= 10)

        // Verify they are within rows 2 to 14
        drainTiles.forEach { tile ->
            // MapGenerator uses offsetToAxial, so we need to check the 'r' coordinate
            assertTrue("Drain tile at row ${tile.coordinate.r} is out of bounds", tile.coordinate.r in 2..14)
        }
    }

    @Test
    fun testTehTarikPuddleDurationOnDrain() {
        val tehTarikDef = StallRegistry.get(StallType.TEH_TARIK)
        val stall = tehTarikDef.toStall(id = "teh_tarik_1")
        val stallCoord = AxialCoordinate(0, 0)

        val enemyPos = PreciseAxialCoordinate(2f, 2f)
        val targetEnemy = com.messark.hawker.model.Enemy(
            id = "enemy_1",
            health = 100,
            maxHealth = 100,
            position = enemyPos
        )

        // Case 1: Target is on FLOOR
        val floorHexes = mapOf(
            AxialCoordinate(2, 2) to HexTile(AxialCoordinate(2, 2), TileType.FLOOR)
        )
        val resultFloor = tehTarikDef.fire(stall, stallCoord, targetEnemy, 1000L, floorHexes)
        assertTrue(resultFloor is com.messark.hawker.registry.FireResult.NewPuddle)
        val puddleFloor = (resultFloor as com.messark.hawker.registry.FireResult.NewPuddle).puddle
        assertEquals("Normal duration on floor", stall.effectDurationMs, puddleFloor.durationMs)

        // Case 2: Target is on DRAIN
        val drainHexes = mapOf(
            AxialCoordinate(2, 2) to HexTile(AxialCoordinate(2, 2), TileType.DRAIN)
        )
        val resultDrain = tehTarikDef.fire(stall, stallCoord, targetEnemy, 1000L, drainHexes)
        assertTrue(resultDrain is com.messark.hawker.registry.FireResult.NewPuddle)
        val puddleDrain = (resultDrain as com.messark.hawker.registry.FireResult.NewPuddle).puddle
        assertEquals("Half duration on drain", stall.effectDurationMs / 2, puddleDrain.durationMs)
    }
}
