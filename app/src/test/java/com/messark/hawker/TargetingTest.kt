package com.messark.hawker

import com.messark.hawker.model.*
import com.messark.hawker.registry.*
import com.messark.hawker.utils.*
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class TargetingTest {

    @Test
    fun testFirstTargetModeUsesPathDistance() {
        val behavior = DefaultStallBehavior()
        val stallCoord = AxialCoordinate(0, 0)
        val stall = Stall(
            id = "stall1",
            name = "Chicken Rice",
            cost = 100,
            color = androidx.compose.ui.graphics.Color.Yellow,
            range = 10f,
            targetMode = TargetMode.FIRST
        )

        val endPos = AxialCoordinate(5, 0)

        // Enemy 1: at (2,0), path is (2,0) -> (3,0) -> (4,0) -> (5,0). Dist to end = 3
        val enemy1 = Enemy(
            id = "enemy1",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(2f, 0f),
            path = listOf(AxialCoordinate(2, 0), AxialCoordinate(3, 0), AxialCoordinate(4, 0), AxialCoordinate(5, 0)),
            currentPathIndex = 0
        )

        // Enemy 2: at (1,0), path is (1,0) -> (1,1) -> (2,1) -> (3,1) -> (4,1) -> (5,1) -> (5,0). Dist to end = 6
        val enemy2 = Enemy(
            id = "enemy2",
            health = 100f,
            maxHealth = 100f,
            position = PreciseAxialCoordinate(1f, 0f),
            path = listOf(AxialCoordinate(1, 0), AxialCoordinate(1, 1), AxialCoordinate(2, 1), AxialCoordinate(3, 1), AxialCoordinate(4, 1), AxialCoordinate(5, 1), AxialCoordinate(5, 0)),
            currentPathIndex = 0
        )

        val pathDistances = mapOf(
            AxialCoordinate(5, 0) to 0,
            AxialCoordinate(4, 0) to 1,
            AxialCoordinate(3, 0) to 2,
            AxialCoordinate(2, 0) to 3,
            AxialCoordinate(5, 1) to 1,
            AxialCoordinate(4, 1) to 2,
            AxialCoordinate(3, 1) to 3,
            AxialCoordinate(2, 1) to 4,
            AxialCoordinate(1, 1) to 5,
            AxialCoordinate(1, 0) to 6
        )

        val enemies = listOf(enemy1, enemy2)
        val enemySpatialIndex = SpatialIndex(enemies) { it.position }

        val target = behavior.selectTarget(
            stall = stall,
            stallCoord = stallCoord,
            enemySpatialIndex = enemySpatialIndex,
            obstructions = emptyList(),
            newlyGrabbedEnemyIds = emptySet(),
            pathDistances = pathDistances
        )

        assertEquals("Enemy 1 should be targeted as it is closer to the end along the path", "enemy1", target?.id)
    }
}
