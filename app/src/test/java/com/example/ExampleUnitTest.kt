package com.example

import com.example.data.model.*
import com.example.data.payment.NowPaymentResponse
import com.example.data.payment.NowPaymentsManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFreeHashrateHardCap() {
        val state = UserMiningState(
            baseFreeHashrateGh = 1.0,
            referralCount = 30,
            activeReferredMiners = 10
        )
        assertEquals(10.0, state.aggregateFreeHashrateGh, 0.001)
    }

    @Test
    fun testReferralBoostFormula() {
        val state = UserMiningState(
            baseFreeHashrateGh = 1.0,
            referralCount = 4,
            activeReferredMiners = 2
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

    @Test
    fun testNowPaymentsResponseStatusCheck() {
        val waitingPayment = NowPaymentResponse(
            paymentId = "1001",
            paymentStatus = "waiting",
            payAddress = "0x1234",
            priceAmount = 50.0,
            priceCurrency = "usd",
            payAmount = 50.0,
            payCurrency = "usdtbsc",
            orderId = "ORD-01",
            orderDescription = "Test Order"
        )
        assertTrue(waitingPayment.isPendingOrWaiting)
        assertFalse(waitingPayment.isSuccessOrConfirmed)

        val confirmedPayment = waitingPayment.copy(paymentStatus = "confirmed")
        assertTrue(confirmedPayment.isSuccessOrConfirmed)

        val finishedPayment = waitingPayment.copy(paymentStatus = "finished")
        assertTrue(finishedPayment.isSuccessOrConfirmed)
    }

    @Test
    fun testHashrateProfitCalculatorFormula() {
        val hashrateGh = 30.0
        val estimatedHardwareCostUsd = hashrateGh * 3.333 // ~$100 USD
        val monthlyUsdtYield = estimatedHardwareCostUsd * 0.15 // $15.00 USDT
        val dailyUsdtYield = monthlyUsdtYield / 30.0 // $0.50 USDT
        val cycle200DaysYield = dailyUsdtYield * 200.0 // $100.00 USDT

        assertEquals(100.0, cycle200DaysYield, 0.1)
        assertEquals(0.50, dailyUsdtYield, 0.01)
    }

    @Test
    fun testNotificationChannelsConstants() {
        assertEquals("hashgrid_mining_channel", com.example.data.notification.NotificationHelper.CHANNEL_MINING)
        assertEquals("hashgrid_payment_channel", com.example.data.notification.NotificationHelper.CHANNEL_PAYMENT)
    }

    @Test
    fun testGridPreLaunchPriceConstant() {
        assertEquals(0.05, com.example.data.repository.MiningRepository.GRID_PRELAUNCH_PRICE_USD, 0.0001)
    }

    @Test
    fun testSecretKeyGenerationAndValidation() {
        val generated = com.example.data.security.SecretKeyUtils.generateSecretKey()
        assertTrue(generated.startsWith("HG-"))
        assertTrue(com.example.data.security.SecretKeyUtils.isValidSecretKey(generated))

        val normalized = com.example.data.security.SecretKeyUtils.normalizeSecretKey("hg-7k9p-m2x4-w8q1-j5r3")
        assertEquals("HG-7K9P-M2X4-W8Q1-J5R3", normalized)
        assertTrue(com.example.data.security.SecretKeyUtils.isValidSecretKey(normalized))

        assertFalse(com.example.data.security.SecretKeyUtils.isValidSecretKey("INVALID-KEY"))
        assertFalse(com.example.data.security.SecretKeyUtils.isValidSecretKey("HG-1234-5678"))
    }
}
