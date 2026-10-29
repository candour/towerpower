package com.messark.hawker

import com.messark.hawker.model.EnemyType
import com.messark.hawker.registry.EnemyRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class EnemyHpScalingTest {

    @Test
    fun `test enemy hp scaling formula`() {
        val waveNumbers = listOf(1, 2, 5, 10, 20, 50)

        for (type in EnemyType.values()) {
            val def = EnemyRegistry.get(type)
            val baseHp = def.baseHp

            for (wave in waveNumbers) {
                val expectedHp = (baseHp * 1.07.pow((wave - 1).toDouble())).toFloat()
                val actualHp = def.getHp(wave)

                assertEquals(
                    "HP for ${type.name} at wave $wave should match 1.07 scaling formula",
                    expectedHp,
                    actualHp,
                    0.001f
                )
            }
        }
    }
}
