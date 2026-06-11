package com.messark.hawker

import com.messark.hawker.model.EnemyType
import com.messark.hawker.model.StallType
import com.messark.hawker.registry.EnemyRegistry
import com.messark.hawker.registry.StallRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistryStatsTest {

    @Test
    fun testChickenRiceBaseDamage() {
        val def = StallRegistry.get(StallType.CHICKEN_RICE)
        // STALL_STATS.md says Chicken Rice base damage should be 10
        assertEquals("Chicken Rice base damage should be 10", 10f, def.damage)
    }

    @Test
    fun testDurianBaseDamage() {
        val def = StallRegistry.get(StallType.DURIAN)
        // STALL_STATS.md says Durian base damage should be 150
        assertEquals("Durian base damage should be 150", 150f, def.damage)
    }

    @Test
    fun testTigerMomBaseHp() {
        val def = EnemyRegistry.get(EnemyType.TIGER_MOM)
        // CUSTOMER_STATS.md says Tiger Mom base HP should be 80
        assertEquals("Tiger Mom base HP should be 80", 80f, def.baseHp)
    }
}
