package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.example.data.model.*
import com.example.data.payment.NowPaymentResponse
import com.example.data.payment.NowPaymentsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.data.notification.NotificationHelper
import java.util.UUID
import kotlin.random.Random

class MiningRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("hashgrid_prefs_v1", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val firebaseManager = FirebaseManager(context)
    val nowPaymentsManager = NowPaymentsManager()

    private val _userState = MutableStateFlow(loadInitialState())
    val userState: StateFlow<UserMiningState> = _userState.asStateFlow()

    private val _cryptoPrices = MutableStateFlow(
        listOf(
            CryptoTickerPrice("BTC/USDT", 98250.40, +3.42, 99100.0, 96800.0),
            CryptoTickerPrice("ETH/USDT", 3420.85, +2.15, 3480.0, 3350.0),
            CryptoTickerPrice("GRID/USDT", 0.145, +8.90, 0.160, 0.130)
        )
    )
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = _cryptoPrices.asStateFlow()

    private var lastYieldTickTime = System.currentTimeMillis()
    private var lastCloudSyncTime = System.currentTimeMillis()

    init {
        startBackgroundEngine()
    }

    private fun loadInitialState(): UserMiningState {
        val now = System.currentTimeMillis()
        val defaultRigs = listOf(
            UserRig(
                id = "rig-usr-101",
                catalogId = "starter_node",
                name = "Starter Node #101",
                priceUsdt = 10.0,
                hashrateGh = 2.0,
                purchaseTimestamp = now - (12L * 24 * 60 * 60 * 1000), // 12 days ago
                durationDays = 200,
                status = RigStatus.ACTIVE,
                totalReceivedUsdt = 0.60,
                thisMonthEarnedUsdt = 0.60,
                lastYieldCalculatedTimestamp = now
            ),
            UserRig(
                id = "rig-usr-102",
                catalogId = "pro_miner_node",
                name = "Pro Miner Node #102",
                priceUsdt = 25.0,
                hashrateGh = 6.0,
                purchaseTimestamp = now - (25L * 24 * 60 * 60 * 1000), // 25 days ago
                durationDays = 200,
                status = RigStatus.ACTIVE,
                totalReceivedUsdt = 3.12,
                thisMonthEarnedUsdt = 3.12,
                lastYieldCalculatedTimestamp = now
            )
        )

        val defaultTx = listOf(
            TransactionItem(
                id = "tx-001",
                type = TransactionType.REFERRAL_COMMISSION,
                amount = 7.00,
                currency = "USDT",
                timestamp = now - 3600000 * 4,
                status = TransactionStatus.COMPLETED,
                description = "7% Affiliate Commission: Downline Node Purchase (Quantum Rig $100)"
            ),
            TransactionItem(
                id = "tx-002",
                type = TransactionType.MINING_PAYOUT_USDT,
                amount = 0.38,
                currency = "USDT",
                timestamp = now - 3600000 * 8,
                status = TransactionStatus.COMPLETED,
                description = "Automated Hardware Yield Credit"
            ),
            TransactionItem(
                id = "tx-003",
                type = TransactionType.DEPOSIT,
                amount = 25.00,
                currency = "USDT",
                timestamp = now - 86400000 * 2,
                status = TransactionStatus.COMPLETED,
                description = "Deposit confirmed via NOWPayments Gateway",
                network = "BEP20 (BSC)",
                txHash = "0x89f4b...39d1"
            )
        )

        val defaultTasks = listOf(
            MicroTaskSubmission(
                id = "task-001",
                platform = TaskPlatform.WHATSAPP_STATUS,
                submittedAt = now - 86400000,
                initialViewCount = 42,
                finalViewCount = 185,
                status = PromoStatus.APPROVED,
                rewardUsdt = 3.50,
                notes = "High engagement status verified"
            )
        )

        val sessionStart = now - (6L * 60 * 60 * 1000)
        val sessionEnd = sessionStart + (24L * 60 * 60 * 1000)

        return UserMiningState(
            uid = "HG-USER-8921",
            email = "miner8921@hashgrid.pro",
            nodeId = "NODE-US-EAST-#8921",
            isColdStorageSynced = true,
            minerBalanceUsdt = 48.50,
            gridBalance = 412.850,
            baseFreeHashrateGh = 1.0,
            referralCount = 3,
            activeReferredMiners = 2,
            temporaryBoostHashrateGh = 0.5,
            temporaryBoostExpiry = now + (14L * 60 * 60 * 1000),
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            isFreeMiningActive = true,
            referralCode = "HG-8921",
            dailySpentUsdt = 0.0,
            dailySpentResetDate = now,
            lastDailySpinTimestamp = now - (20L * 60 * 60 * 1000),
            userRigs = defaultRigs,
            transactions = defaultTx,
            microTasks = defaultTasks
        )
    }

    private fun startBackgroundEngine() {
        scope.launch {
            // Initial sync to Firestore
            syncToCloud()
            while (isActive) {
                delay(1000)
                tickSecond()
            }
        }
    }

    private fun tickSecond() {
        val now = System.currentTimeMillis()
        val current = _userState.value

        val wasFreeActive = current.isFreeMiningActive
        val isFreeActive = current.isFreeMiningActive && (now < current.freeMiningSessionEnd)
        
        // If session just ended, send push notification to user
        if (wasFreeActive && !isFreeActive) {
            NotificationHelper.sendMiningSessionEndedNotification(appContext)
        }
        
        var newGridBalance = current.gridBalance
        if (isFreeActive) {
            val hashrate = current.aggregateFreeHashrateGh
            val deltaGrid = (hashrate * 0.00035)
            newGridBalance += deltaGrid
        }

        val elapsedSec = ((now - lastYieldTickTime) / 1000.0).coerceAtLeast(0.0)
        lastYieldTickTime = now

        var additionalUsdtYield = 0.0
        val updatedRigs = current.userRigs.map { rig ->
            if (rig.status == RigStatus.ACTIVE) {
                if (now >= rig.expiryTimestamp) {
                    rig.copy(status = RigStatus.COMPLETED)
                } else {
                    val dailyYield = (rig.priceUsdt * 0.15) / 30.0
                    val yieldPerSec = dailyYield / 86400.0
                    val rigYield = yieldPerSec * elapsedSec
                    additionalUsdtYield += rigYield
                    rig.copy(
                        totalReceivedUsdt = rig.totalReceivedUsdt + rigYield,
                        thisMonthEarnedUsdt = rig.thisMonthEarnedUsdt + rigYield,
                        lastYieldCalculatedTimestamp = now
                    )
                }
            } else {
                rig
            }
        }

        val newMinerBalance = current.minerBalanceUsdt + additionalUsdtYield

        _userState.value = current.copy(
            isFreeMiningActive = isFreeActive,
            gridBalance = newGridBalance,
            minerBalanceUsdt = newMinerBalance,
            userRigs = updatedRigs
        )

        // Periodic sync to Firestore every 20 seconds
        if (now - lastCloudSyncTime > 20000) {
            lastCloudSyncTime = now
            syncToCloud()
        }

        if (Random.nextInt(5) == 0) {
            val prices = _cryptoPrices.value.map { ticker ->
                val jitter = (Random.nextDouble() - 0.49) * 0.0008 * ticker.price
                val newPrice = (ticker.price + jitter).coerceAtLeast(0.01)
                ticker.copy(price = newPrice)
            }
            _cryptoPrices.value = prices
        }
    }

    private fun syncToCloud() {
        scope.launch {
            try {
                firebaseManager.syncUserStateToFirestore(_userState.value)
            } catch (e: Exception) {
                Log.e("MiningRepository", "Cloud sync exception: ${e.message}")
            }
        }
    }

    // ==========================================
    // NOWPAYMENTS GATEWAY & IPN INTEGRATION
    // ==========================================

    /**
     * Initiates a crypto deposit payment through NOWPayments API (/v1/payment).
     * Saves a transaction record in Firestore with status: "waiting_payment".
     */
    suspend fun createNowPaymentsDeposit(
        priceAmountUsd: Double,
        payCurrency: String
    ): Result<NowPaymentResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val orderId = "DEP-HG-${UUID.randomUUID().toString().take(6).uppercase()}"
        val description = "Deposit $priceAmountUsd USD into HashGrid Pro Wallet"

        val result = nowPaymentsManager.createPayment(
            priceAmountUsd = priceAmountUsd,
            payCurrency = payCurrency,
            orderId = orderId,
            orderDescription = description
        )

        if (result.isSuccess) {
            val payment = result.getOrThrow()
            val tx = TransactionItem(
                id = "tx-pay-${payment.paymentId}",
                type = TransactionType.DEPOSIT,
                amount = priceAmountUsd,
                currency = "USDT",
                timestamp = now,
                status = TransactionStatus.WAITING_PAYMENT,
                description = "NOWPayments Deposit (${payment.payCurrency.uppercase()} - Waiting)",
                paymentId = payment.paymentId,
                payAddress = payment.payAddress,
                payAmount = payment.payAmount,
                payCurrency = payment.payCurrency,
                nowPaymentsStatus = payment.paymentStatus,
                network = payCurrency.uppercase()
            )

            // Update in-memory state & sync to Firestore
            val current = _userState.value
            _userState.value = current.copy(
                transactions = listOf(tx) + current.transactions
            )
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            Log.d("MiningRepository", "Created deposit transaction for payment ${payment.paymentId}")
        }
        result
    }

    /**
     * Initiates a direct Mining Plan/Rig purchase through NOWPayments API (/v1/payment).
     * Saves transaction in Firestore under users/{userId}/transactions with status: "waiting_payment".
     */
    suspend fun createNowPaymentsRigPurchase(
        rigItem: RigCatalogItem,
        payCurrency: String
    ): Result<NowPaymentResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val orderId = "RIG-${rigItem.id.take(6).uppercase()}-${UUID.randomUUID().toString().take(4).uppercase()}"
        val description = "Direct Node Purchase: ${rigItem.name} (${rigItem.hashrateGh} GH/s)"

        val result = nowPaymentsManager.createPayment(
            priceAmountUsd = rigItem.priceUsdt,
            payCurrency = payCurrency,
            orderId = orderId,
            orderDescription = description
        )

        if (result.isSuccess) {
            val payment = result.getOrThrow()
            val tx = TransactionItem(
                id = "tx-rigpay-${payment.paymentId}",
                type = TransactionType.RIG_PURCHASE,
                amount = rigItem.priceUsdt,
                currency = "USDT",
                timestamp = now,
                status = TransactionStatus.WAITING_PAYMENT,
                description = "Node Deployment Payment: ${rigItem.name} (${payment.payCurrency.uppercase()})",
                paymentId = payment.paymentId,
                payAddress = payment.payAddress,
                payAmount = payment.payAmount,
                payCurrency = payment.payCurrency,
                nowPaymentsStatus = payment.paymentStatus,
                targetRigCatalogId = rigItem.id,
                network = payCurrency.uppercase()
            )

            val current = _userState.value
            _userState.value = current.copy(
                transactions = listOf(tx) + current.transactions
            )
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            Log.d("MiningRepository", "Created plan purchase transaction for payment ${payment.paymentId}")
        }
        result
    }

    /**
     * Checks status via GET /v1/payment/{payment_id} or processes IPN notification callback.
     * When status becomes "confirmed" or "finished":
     *   a) Updates transaction status to "completed" in Firestore.
     *   b) Automatically activates/upgrades the user's mining hash rate and updates their wallet balance.
     *   c) Returns wasJustCompleted = true for showing UI celebratory feedback.
     */
    suspend fun verifyAndSyncPaymentStatus(paymentId: String): Result<Pair<NowPaymentResponse, Boolean>> = withContext(Dispatchers.IO) {
        val checkRes = nowPaymentsManager.getPaymentStatus(paymentId)
        if (checkRes.isSuccess) {
            val payment = checkRes.getOrThrow()
            val current = _userState.value
            val existingTx = current.transactions.find { it.paymentId == paymentId }

            val isConfirmed = payment.isSuccessOrConfirmed

            if (isConfirmed && existingTx != null && existingTx.status != TransactionStatus.COMPLETED) {
                // 1. Mark transaction COMPLETED
                val completedTx = existingTx.copy(
                    status = TransactionStatus.COMPLETED,
                    nowPaymentsStatus = payment.paymentStatus,
                    description = existingTx.description.replace("Waiting", "Confirmed (NOWPayments)")
                )

                // 2. Handle Plan Purchase vs Deposit
                var updatedBalance = current.minerBalanceUsdt
                var updatedRigs = current.userRigs

                if (existingTx.type == TransactionType.DEPOSIT) {
                    updatedBalance += existingTx.amount
                } else if (existingTx.type == TransactionType.RIG_PURCHASE && existingTx.targetRigCatalogId != null) {
                    val catalogItem = DefaultRigs.catalog.find { it.id == existingTx.targetRigCatalogId }
                        ?: DefaultRigs.catalog.first()

                    val now = System.currentTimeMillis()
                    val newRig = UserRig(
                        id = "rig-usr-${UUID.randomUUID().toString().take(6)}",
                        catalogId = catalogItem.id,
                        name = "${catalogItem.name} #${Random.nextInt(100, 999)}",
                        priceUsdt = catalogItem.priceUsdt,
                        hashrateGh = catalogItem.hashrateGh,
                        purchaseTimestamp = now,
                        durationDays = catalogItem.durationDays,
                        status = RigStatus.ACTIVE,
                        totalReceivedUsdt = 0.0,
                        thisMonthEarnedUsdt = 0.0,
                        lastYieldCalculatedTimestamp = now
                    )
                    updatedRigs = listOf(newRig) + updatedRigs

                    // Record to Firestore
                    firebaseManager.recordPlanActivation(current.uid, newRig, updatedBalance)
                }

                val updatedTxList = current.transactions.map { if (it.id == completedTx.id) completedTx else it }

                _userState.value = current.copy(
                    minerBalanceUsdt = updatedBalance,
                    userRigs = updatedRigs,
                    transactions = updatedTxList
                )

                firebaseManager.saveOrUpdateTransaction(current.uid, completedTx)
                syncToCloud()

                // Send Push Notification
                val notifDetails = if (existingTx.type == TransactionType.DEPOSIT) {
                    "Deposit of $${String.format("%.2f", existingTx.amount)} USDT confirmed! Your miner balance has been updated."
                } else {
                    "Mining node payment confirmed! Your new hardware hashrate is deployed and active."
                }
                NotificationHelper.sendPaymentStatusNotification(appContext, "Confirmed", existingTx.amount, notifDetails)

                Result.success(Pair(payment, true))
            } else {
                Result.success(Pair(payment, false))
            }
        } else {
            Result.failure(checkRes.exceptionOrNull() ?: Exception("Failed to check status"))
        }
    }

    /**
     * Instant manual verification / sandbox trigger to simulate blockchain confirmation.
     */
    fun simulateInstantPaymentConfirmation(paymentId: String): Boolean {
        val current = _userState.value
        val existingTx = current.transactions.find { it.paymentId == paymentId } ?: return false
        if (existingTx.status == TransactionStatus.COMPLETED) return true

        val now = System.currentTimeMillis()
        val completedTx = existingTx.copy(
            status = TransactionStatus.COMPLETED,
            nowPaymentsStatus = "finished",
            description = existingTx.description.replace("Waiting", "Confirmed (Instant Gateway Verified)")
        )

        var updatedBalance = current.minerBalanceUsdt
        var updatedRigs = current.userRigs

        if (existingTx.type == TransactionType.DEPOSIT) {
            updatedBalance += existingTx.amount
        } else if (existingTx.type == TransactionType.RIG_PURCHASE && existingTx.targetRigCatalogId != null) {
            val catalogItem = DefaultRigs.catalog.find { it.id == existingTx.targetRigCatalogId }
                ?: DefaultRigs.catalog.first()

            val newRig = UserRig(
                id = "rig-usr-${UUID.randomUUID().toString().take(6)}",
                catalogId = catalogItem.id,
                name = "${catalogItem.name} #${Random.nextInt(100, 999)}",
                priceUsdt = catalogItem.priceUsdt,
                hashrateGh = catalogItem.hashrateGh,
                purchaseTimestamp = now,
                durationDays = catalogItem.durationDays,
                status = RigStatus.ACTIVE,
                totalReceivedUsdt = 0.0,
                thisMonthEarnedUsdt = 0.0,
                lastYieldCalculatedTimestamp = now
            )
            updatedRigs = listOf(newRig) + updatedRigs
            scope.launch {
                firebaseManager.recordPlanActivation(current.uid, newRig, updatedBalance)
            }
        }

        val updatedTxList = current.transactions.map { if (it.id == completedTx.id) completedTx else it }

        _userState.value = current.copy(
            minerBalanceUsdt = updatedBalance,
            userRigs = updatedRigs,
            transactions = updatedTxList
        )

        // Push Notification
        val notifDetails = if (existingTx.type == TransactionType.DEPOSIT) {
            "Deposit of $${String.format("%.2f", existingTx.amount)} USDT confirmed! Your miner balance has been updated."
        } else {
            "Mining node payment confirmed! Your new hardware hashrate is deployed and active."
        }
        NotificationHelper.sendPaymentStatusNotification(appContext, "Confirmed", existingTx.amount, notifDetails)

        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, completedTx)
            syncToCloud()
        }
        return true
    }

    // ==========================================
    // OTHER PROTOCOL METHODS
    // ==========================================

    fun startFreeMiningSession() {
        val now = System.currentTimeMillis()
        val duration = 24L * 60 * 60 * 1000
        val current = _userState.value
        _userState.value = current.copy(
            isFreeMiningActive = true,
            freeMiningSessionStart = now,
            freeMiningSessionEnd = now + duration
        )
        syncToCloud()
    }

    fun buyRig(catalogItem: RigCatalogItem, useWalletBalance: Boolean = true): Result<UserRig> {
        val current = _userState.value
        val now = System.currentTimeMillis()

        if (current.dailySpentUsdt + catalogItem.priceUsdt > 5000.0) {
            return Result.failure(Exception("Daily purchase limit ($5,000 USDT) reached. Try again tomorrow."))
        }

        if (useWalletBalance && current.minerBalanceUsdt < catalogItem.priceUsdt) {
            return Result.failure(Exception("Insufficient Miner Balance ($${String.format("%.2f", current.minerBalanceUsdt)} USDT). Deposit funds or choose a smaller node."))
        }

        val newBalance = if (useWalletBalance) current.minerBalanceUsdt - catalogItem.priceUsdt else current.minerBalanceUsdt
        val newRig = UserRig(
            id = "rig-usr-${UUID.randomUUID().toString().take(6)}",
            catalogId = catalogItem.id,
            name = "${catalogItem.name} #${Random.nextInt(100, 999)}",
            priceUsdt = catalogItem.priceUsdt,
            hashrateGh = catalogItem.hashrateGh,
            purchaseTimestamp = now,
            durationDays = catalogItem.durationDays,
            status = RigStatus.ACTIVE,
            totalReceivedUsdt = 0.0,
            thisMonthEarnedUsdt = 0.0,
            lastYieldCalculatedTimestamp = now
        )

        val tx = TransactionItem(
            id = "tx-${UUID.randomUUID().toString().take(8)}",
            type = TransactionType.RIG_PURCHASE,
            amount = catalogItem.priceUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "Deployed ${catalogItem.name} (${catalogItem.hashrateGh} GH/s) for ${catalogItem.durationDays} Days"
        )

        _userState.value = current.copy(
            minerBalanceUsdt = newBalance,
            dailySpentUsdt = current.dailySpentUsdt + catalogItem.priceUsdt,
            userRigs = listOf(newRig) + current.userRigs,
            transactions = listOf(tx) + current.transactions
        )

        syncToCloud()
        return Result.success(newRig)
    }

    fun depositFunds(amountUsdt: Double, network: String, txHash: String = "0x" + UUID.randomUUID().toString().replace("-", "").take(16)) {
        val current = _userState.value
        val now = System.currentTimeMillis()

        val tx = TransactionItem(
            id = "tx-${UUID.randomUUID().toString().take(8)}",
            type = TransactionType.DEPOSIT,
            amount = amountUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "Direct Deposit via $network",
            network = network,
            txHash = txHash
        )

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + amountUsdt,
            transactions = listOf(tx) + current.transactions
        )
        syncToCloud()
    }

    fun requestWithdrawal(amountUsdt: Double, address: String, network: String): Result<TransactionItem> {
        val current = _userState.value
        if (amountUsdt < 10.0) {
            return Result.failure(Exception("Minimum withdrawal threshold is 10.00 USDT."))
        }
        if (current.minerBalanceUsdt < amountUsdt) {
            return Result.failure(Exception("Insufficient Miner Balance."))
        }
        if (address.isBlank() || address.length < 10) {
            return Result.failure(Exception("Please enter a valid $network wallet address."))
        }

        val now = System.currentTimeMillis()
        val tx = TransactionItem(
            id = "tx-wd-${UUID.randomUUID().toString().take(8)}",
            type = TransactionType.WITHDRAWAL,
            amount = amountUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.PENDING_REVIEW,
            description = "Withdrawal request to ${address.take(6)}...${address.takeLast(4)} (24H Security Audit)",
            address = address,
            network = network
        )

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt - amountUsdt,
            transactions = listOf(tx) + current.transactions
        )
        syncToCloud()
        return Result.success(tx)
    }

    fun executeLuckySpin(sector: SpinSector): SpinHistoryRecord {
        val current = _userState.value
        val now = System.currentTimeMillis()

        var newMinerBal = current.minerBalanceUsdt
        var newGridBal = current.gridBalance
        var newBoostGh = current.temporaryBoostHashrateGh
        var newBoostExpiry = current.temporaryBoostExpiry

        when (sector.type) {
            SpinRewardType.GRID_TOKENS -> {
                newGridBal += sector.value
            }
            SpinRewardType.HASHRATE_BOOST -> {
                newBoostGh = sector.value
                newBoostExpiry = now + (24L * 60 * 60 * 1000)
            }
            SpinRewardType.USDT -> {
                newMinerBal += sector.value
            }
        }

        val record = SpinHistoryRecord(
            id = "spin-${UUID.randomUUID().toString().take(6)}",
            rewardTitle = sector.title,
            rewardSubtitle = sector.subtitle,
            timestamp = now,
            rewardType = sector.type,
            value = sector.value
        )

        val tx = TransactionItem(
            id = "tx-spin-${UUID.randomUUID().toString().take(6)}",
            type = TransactionType.LUCKY_SPIN_REWARD,
            amount = sector.value,
            currency = if (sector.type == SpinRewardType.USDT) "USDT" else if (sector.type == SpinRewardType.GRID_TOKENS) "GRID" else "GH/s",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "Daily Lucky Wheel Prize: ${sector.title}"
        )

        _userState.value = current.copy(
            minerBalanceUsdt = newMinerBal,
            gridBalance = newGridBal,
            temporaryBoostHashrateGh = newBoostGh,
            temporaryBoostExpiry = newBoostExpiry,
            lastDailySpinTimestamp = now,
            spinHistory = listOf(record) + current.spinHistory,
            transactions = listOf(tx) + current.transactions
        )

        syncToCloud()
        return record
    }

    fun submitMicroTask(platform: TaskPlatform, initialViews: Int, finalViews: Int, notes: String) {
        val current = _userState.value
        val now = System.currentTimeMillis()
        val rewardEst = 2.50 + Random.nextDouble(1.0, 15.0)

        val sub = MicroTaskSubmission(
            id = "task-${UUID.randomUUID().toString().take(6)}",
            platform = platform,
            submittedAt = now,
            initialViewCount = initialViews,
            finalViewCount = finalViews,
            status = PromoStatus.PENDING_REVIEW,
            rewardUsdt = rewardEst,
            notes = notes
        )

        _userState.value = current.copy(
            microTasks = listOf(sub) + current.microTasks
        )
        syncToCloud()
    }

    fun submitVideoPromo(platform: TaskPlatform, url: String, channel: String) {
        val current = _userState.value
        val now = System.currentTimeMillis()

        val sub = VideoPromotionSubmission(
            id = "vid-${UUID.randomUUID().toString().take(6)}",
            platform = platform,
            videoUrl = url,
            channelOrHandle = channel,
            submittedAt = now,
            status = PromoStatus.PENDING_REVIEW,
            rewardUsdt = 0.0
        )

        _userState.value = current.copy(
            videoPromotions = listOf(sub) + current.videoPromotions
        )
        syncToCloud()
    }

    fun simulateDownlinePurchase() {
        val current = _userState.value
        val now = System.currentTimeMillis()
        val rigBoughtPrice = 100.0
        val commission = rigBoughtPrice * 0.07

        val tx = TransactionItem(
            id = "comm-${UUID.randomUUID().toString().take(6)}",
            type = TransactionType.REFERRAL_COMMISSION,
            amount = commission,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "7% Direct Commission: Downline Node Deployment ($100 Quantum Rig)"
        )

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + commission,
            transactions = listOf(tx) + current.transactions
        )
        syncToCloud()
    }

    fun simulateNewReferral() {
        val current = _userState.value
        val newRef = current.referralCount + 1
        val newActive = current.activeReferredMiners + 1
        _userState.value = current.copy(
            referralCount = newRef,
            activeReferredMiners = newActive
        )
    }

    fun approvePendingTasksSimulation() {
        val current = _userState.value
        val now = System.currentTimeMillis()
        var rewardSum = 0.0
        val updatedTasks = current.microTasks.map { task ->
            if (task.status == PromoStatus.PENDING_REVIEW) {
                rewardSum += task.rewardUsdt
                task.copy(status = PromoStatus.APPROVED)
            } else task
        }

        val updatedTx = if (rewardSum > 0) {
            val tx = TransactionItem(
                id = "tx-task-${UUID.randomUUID().toString().take(6)}",
                type = TransactionType.TASK_PROMOTION_REWARD,
                amount = rewardSum,
                currency = "USDT",
                timestamp = now,
                status = TransactionStatus.COMPLETED,
                description = "Admin Approved Micro-Task Bounty"
            )
            listOf(tx) + current.transactions
        } else current.transactions

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + rewardSum,
            microTasks = updatedTasks,
            transactions = updatedTx
        )
        syncToCloud()
    }
}
