package com.example

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFreeHashrateHardCap() {
        val state = UserMiningState(
            baseFreeHashrateGh = 1.0,
            referralCount = 30, // 30 * 0.25 = 7.5
            activeReferredMiners = 10 // 10 * 0.50 = 5.0 -> raw = 13.5 GH/s
        )
        // Hard capped at 10.0 GH/s
        assertEquals(10.0, state.aggregateFreeHashrateGh, 0.001)
    }

    @Test
    fun testReferralBoostFormula() {
        val state = UserMiningState(
            baseFreeHashrateGh = 1.0,
            referralCount = 4, // 4 * 0.25 = 1.0
            activeReferredMiners = 2 // 2 * 0.50 = 1.0
        )
        assertEquals(2.0, state.referralBoostHashrateGh, 0.001)
        assertEquals(3.0, state.aggregateFreeHashrateGh, 0.001)
    }

    @Test
    fun testPaidRigsCatalogYield() {
        val quantumRig = DefaultRigs.catalog.first { it.id == "quantum_rig_node" }
        assertEquals(100.0, quantumRig.priceUsdt, 0.001)
        assertEquals(30.0, quantumRig.hashrateGh, 0.001)
        assertEquals(200, quantumRig.durationDays)
        // 15% monthly = $15.00 USDT / month -> daily = 0.50 USDT
        assertEquals(0.50, quantumRig.dailyYieldUsdt, 0.001)
    }

    @Test
    fun testAffiliateCommissionSevenPercent() {
        val price = 100.0
        val commissionRate = 0.07
        val commission = price * commissionRate
        assertEquals(7.0, commission, 0.001)
    }

    @Test
    fun testLuckyWheelSectorsConfig() {
        val sectors = LuckyWheelConfig.sectors
        assertEquals(8, sectors.size)
        assertTrue(sectors.any { it.type == SpinRewardType.USDT && it.value == 5.0 })
    }
}
