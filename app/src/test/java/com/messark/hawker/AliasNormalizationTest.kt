package com.messark.hawker

import com.messark.hawker.model.Stall
import com.messark.hawker.model.StallType
import com.messark.hawker.registry.StallRegistry
import com.messark.hawker.utils.StallUpgradeManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class AliasNormalizationTest {

    @Test
    fun `upgrading Cleaning Time does not reset Grab Rate if canonical key is missing`() {
        val baseUncle = StallRegistry.get(StallType.TRAY_RETURN_UNCLE).toStall()

        // Simulate legacy state: Grab Rate has been upgraded to level 5, but canonical "Rate" is missing
        val legacyStall = baseUncle.copy(
            upgrades = mapOf("Grab Rate" to 5),
            fireRateMs = 14500L // level 5 value
        )

        // Act: Upgrade Cleaning Time
        val upgradedStall = StallUpgradeManager.applyUpgrade(
            stall = legacyStall,
            statName = "Cleaning Time",
            upgradeCost = 100,
            isSpecific = true
        )

        // Assert:
        // 1. Grab Rate should still be 5
        // 2. Canonical Rate should now be 5 (normalized)
        // 3. FireRateMs should NOT be reset to 15000L
        assertEquals(5, upgradedStall.upgrades["Grab Rate"])
        assertEquals(5, upgradedStall.upgrades["Rate"])
        assertEquals(1, upgradedStall.upgrades["Cleaning Time"])
        assertEquals(1, upgradedStall.upgrades["Duration"])

        assertEquals(14500L, upgradedStall.fireRateMs)
        assertEquals(2100L, upgradedStall.effectDurationMs) // base 2000 + 100
    }
}
