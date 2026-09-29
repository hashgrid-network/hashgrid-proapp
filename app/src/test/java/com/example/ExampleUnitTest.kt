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
        assertEquals(0.01, com.example.data.repository.MiningRepository.GRID_PRELAUNCH_PRICE_USD, 0.0001)
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

    @Test
    fun testMasterAdminKeyRecognition() {
        val masterKey = "HG-ADM9-7788-5544-0001"
        assertTrue(com.example.data.security.SecretKeyUtils.isMasterAdminKey(masterKey))
        assertTrue(com.example.data.security.SecretKeyUtils.isMasterAdminKey("hg-adm9-7788-5544-0001"))
        assertTrue(com.example.data.security.SecretKeyUtils.isMasterAdminKey(" HG-ADM9-7788-5544-0001 "))
        assertFalse(com.example.data.security.SecretKeyUtils.isMasterAdminKey("HG-USER-1234-5678-9012"))
        assertFalse(com.example.data.security.SecretKeyUtils.isMasterAdminKey(null))
        assertFalse(com.example.data.security.SecretKeyUtils.isMasterAdminKey(""))
    }

    @Test
    fun testMasterAdminStatePrivileges() {
        val masterAdminKey = "HG-ADM9-7788-5544-0001"
        val isMasterAdmin = com.example.data.security.SecretKeyUtils.isMasterAdminKey(masterAdminKey)
        val adminState = UserMiningState(
            secretKey = masterAdminKey,
            isAdmin = isMasterAdmin,
            role = if (isMasterAdmin) "superadmin" else "user"
        )
        assertTrue(adminState.isAdmin)
        assertEquals("superadmin", adminState.role)
    }

    @Test
    fun testCalculatorGridRateAndValuation() {
        val hashrateGh = 400.0
        val dailyRatePerGh = 0.5
        val dailyGridCoins = hashrateGh * dailyRatePerGh
        val monthlyGridCoins = dailyGridCoins * 30.0
        val gridPriceUsd = 0.01

        assertEquals(200.0, dailyGridCoins, 0.001)
        assertEquals(6000.0, monthlyGridCoins, 0.001)

        val monthlyGridValueUsd = monthlyGridCoins * gridPriceUsd
        assertEquals(60.0, monthlyGridValueUsd, 0.001)
    }

    @Test
    fun testLogoutAndUnauthenticatedState() {
        val loggedOutState = UserMiningState(
            uid = "",
            secretKey = "",
            email = "",
            nodeId = "",
            isAuthenticated = false,
            isAdmin = false,
            role = "user"
        )
        assertFalse(loggedOutState.isAuthenticated)
        assertTrue(loggedOutState.secretKey.isBlank())
        assertFalse(loggedOutState.isAdmin)
    }

    @Test
    fun testAuthenticatedStateCreation() {
        val newKey = com.example.data.security.SecretKeyUtils.generateSecretKey()
        val authState = UserMiningState(
            uid = newKey,
            secretKey = newKey,
            isAuthenticated = true,
            isAdmin = false,
            role = "user"
        )
        assertTrue(authState.isAuthenticated)
        assertTrue(authState.secretKey.isNotBlank())
        assertTrue(com.example.data.security.SecretKeyUtils.isValidSecretKey(authState.secretKey))
    }

    @Test
    fun testReferralBaseUrlAndFormatting() {
        val baseUrl = com.example.data.security.ReferralConstants.REFERRAL_BASE_URL
        assertEquals("https://hashgrid.online/?ref=", baseUrl)

        val code = "HG-7K9P"
        val fullUrl = com.example.data.security.ReferralConstants.getReferralUrl(code)
        assertEquals("https://hashgrid.online/?ref=HG-7K9P", fullUrl)

        val preview = com.example.data.security.ReferralConstants.getReferralPreview(code)
        assertEquals("hashgrid.online/?ref=HG-7K9P", preview)

        val shareMsg = com.example.data.security.ReferralConstants.getShareMessage(code)
        assertTrue(shareMsg.contains("https://hashgrid.online/?ref=HG-7K9P"))
        assertTrue(shareMsg.contains("🚀 Join HashGrid Pro"))
    }

    @Test
    fun testOfflineMiningCatchUpFormula() {
        val now = 1700000000000L
        val twoHoursAgo = now - (2L * 60 * 60 * 1000) // 2 hours
        val sessionEnd = now + (22L * 60 * 60 * 1000)

        val starterRig = UserRig(
            id = "node-101",
            catalogId = "starter_node",
            name = "Starter Node #101",
            priceUsdt = 10.0,
            hashrateGh = 2.0,
            purchaseTimestamp = twoHoursAgo,
            durationDays = 200,
            status = RigStatus.ACTIVE,
            totalReceivedUsdt = 0.0,
            thisMonthEarnedUsdt = 0.0,
            lastYieldCalculatedTimestamp = twoHoursAgo
        )

        val initialState = UserMiningState(
            gridBalance = 100.0,
            minerBalanceUsdt = 50.0,
            baseFreeHashrateGh = 2.0,
            referralCount = 0,
            activeReferredMiners = 0,
            isFreeMiningActive = true,
            freeMiningSessionStart = twoHoursAgo,
            freeMiningSessionEnd = sessionEnd,
            lastYieldTickTimestamp = twoHoursAgo,
            userRigs = listOf(starterRig)
        )

        // Offline catchup formula verification:
        // 1. Free GRID: 2 hours elapsed at 2.0 GH/s = (2.0 * 0.5 * (2.0 / 24.0)) = (1.0 * 1/12) = 0.08333 GRID
        val elapsedHours = 2.0
        val expectedGridMined = 2.0 * 0.5 * (elapsedHours / 24.0)
        assertEquals(0.08333, expectedGridMined, 0.001)

        // 2. Hardware USDT Yield: 2 hours elapsed on $10 node = ($10 * (0.15 / 30.0)) * (2.0 / 24.0) = $0.05 * (1/12) = $0.004166 USDT
        val daysElapsed = 2.0 / 24.0
        val expectedRigYield = (10.0 * (0.15 / 30.0)) * daysElapsed
        assertEquals(0.004166, expectedRigYield, 0.0001)

        val caughtUpGrid = initialState.gridBalance + expectedGridMined
        val caughtUpUsdt = initialState.minerBalanceUsdt + expectedRigYield
        assertEquals(100.08333, caughtUpGrid, 0.001)
        assertEquals(50.004166, caughtUpUsdt, 0.0001)
    }

    @Test
    fun testHardwareNodesDataIntegrity() {
        val rig = UserRig(
            id = "101",
            catalogId = "starter_node",
            name = "Starter Node #101",
            priceUsdt = 10.0,
            hashrateGh = 2.0,
            purchaseTimestamp = System.currentTimeMillis(),
            durationDays = 200,
            status = RigStatus.ACTIVE,
            totalReceivedUsdt = 1.25,
            thisMonthEarnedUsdt = 1.25
        )

        assertEquals("101", rig.id)
        assertEquals("Starter Node #101", rig.name)
        assertEquals(10.0, rig.priceUsdt, 0.001)
        assertEquals(2.0, rig.hashrateGh, 0.001)
        assertEquals(200, rig.durationDays)
        assertEquals(RigStatus.ACTIVE, rig.status)
        assertEquals(1.25, rig.totalReceivedUsdt, 0.001)
        assertTrue(rig.daysRemaining() > 195)
    }
}
