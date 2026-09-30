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
import com.example.data.security.SecretKeyUtils
import com.example.data.security.SecurityPreferences
import java.util.UUID
import kotlin.random.Random

class MiningRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("hashgrid_prefs_v1", Context.MODE_PRIVATE)
    val securityPreferences = SecurityPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val firebaseManager = FirebaseManager(context)
    val nowPaymentsManager = NowPaymentsManager()

    companion object {
        const val GRID_PRELAUNCH_PRICE_USD = 0.01 // Default Pre-Launch rate: 1 GRID = 0.01 USDT
    }

    private val _userState = MutableStateFlow(loadInitialState())
    val userState: StateFlow<UserMiningState> = _userState.asStateFlow()

    private val _gridPriceUsd = MutableStateFlow(
        prefs.getFloat("grid_price_usd", GRID_PRELAUNCH_PRICE_USD.toFloat()).toDouble()
    )
    val gridPriceUsd: StateFlow<Double> = _gridPriceUsd.asStateFlow()

    private val _cryptoPrices = MutableStateFlow(
        listOf(
            CryptoTickerPrice("GRID/USDT", _gridPriceUsd.value, 0.00, _gridPriceUsd.value, _gridPriceUsd.value)
        )
    )
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = _cryptoPrices.asStateFlow()

    private var lastYieldTickTime = System.currentTimeMillis()
    private var lastCloudSyncTime = System.currentTimeMillis()
    private var isCloudHydrated = false

    init {
        startBackgroundEngine()
        attachSystemSettingsListener()
    }

    /**
     * Continuous offline yield calculation based on system epoch timestamps.
     * Guarantees zero lost mining seconds even when app is killed or device is offline.
     */
    fun applyOfflineCatchUpYield(state: UserMiningState, now: Long = System.currentTimeMillis()): UserMiningState {
        var grid = state.gridBalance
        var usdt = state.minerBalanceUsdt
        var isMiningActive = state.isFreeMiningActive
        val lastTimestamp = if (state.lastYieldTickTimestamp > 0) state.lastYieldTickTimestamp else now

        // 1. Free GRID Core Mining Catch-up (0.5 GRID per GH/s per 24h)
        if (isMiningActive && now > lastTimestamp) {
            val calculationEnd = Math.min(now, state.freeMiningSessionEnd)
            val elapsedMillis = (calculationEnd - lastTimestamp).coerceAtLeast(0L)
            if (elapsedMillis > 0) {
                val elapsedHours = elapsedMillis.toDouble() / (1000.0 * 60.0 * 60.0)
                val minedGrid = (state.aggregateFreeHashrateGh * 0.5 * (elapsedHours / 24.0))
                grid += minedGrid
            }
            if (now >= state.freeMiningSessionEnd) {
                isMiningActive = false
            }
        }

        // 2. Hardware Nodes USDT Yield Catch-up (~15% monthly yield over lifespan)
        val dailyUsdtYieldRate = 0.15 / 30.0
        val updatedRigs = state.userRigs.map { rig ->
            if (rig.status == RigStatus.ACTIVE) {
                val lastRigCalc = if (rig.lastYieldCalculatedTimestamp > 0) rig.lastYieldCalculatedTimestamp else rig.purchaseTimestamp
                val rigEnd = Math.min(now, rig.expiryTimestamp)
                val rigElapsedMillis = (rigEnd - lastRigCalc).coerceAtLeast(0L)
                if (rigElapsedMillis > 0) {
                    val daysElapsed = rigElapsedMillis.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                    val nodeYield = (rig.priceUsdt * dailyUsdtYieldRate) * daysElapsed
                    usdt += nodeYield
                    rig.copy(
                        status = if (now >= rig.expiryTimestamp) RigStatus.COMPLETED else RigStatus.ACTIVE,
                        totalReceivedUsdt = rig.totalReceivedUsdt + nodeYield,
                        thisMonthEarnedUsdt = rig.thisMonthEarnedUsdt + nodeYield,
                        lastYieldCalculatedTimestamp = rigEnd
                    )
                } else {
                    rig
                }
            } else {
                rig
            }
        }

        return state.copy(
            gridBalance = grid,
            minerBalanceUsdt = usdt,
            isFreeMiningActive = isMiningActive,
            userRigs = updatedRigs,
            lastYieldTickTimestamp = now
        )
    }

    private fun attachSystemSettingsListener() {
        firebaseManager.listenToSystemSettings { newPrice ->
            if (newPrice > 0.0) {
                _gridPriceUsd.value = newPrice
                prefs.edit().putFloat("grid_price_usd", newPrice.toFloat()).apply()
                _cryptoPrices.value = listOf(
                    CryptoTickerPrice("GRID/USDT", newPrice, 0.00, newPrice, newPrice)
                )
            }
        }
    }

    suspend fun updateGridPrice(newPrice: Double): Boolean {
        if (newPrice <= 0.0) return false
        _gridPriceUsd.value = newPrice
        prefs.edit().putFloat("grid_price_usd", newPrice.toFloat()).apply()
        _cryptoPrices.value = listOf(
            CryptoTickerPrice("GRID/USDT", newPrice, 0.00, newPrice, newPrice)
        )
        return firebaseManager.updateGridPrice(newPrice)
    }

    private fun loadInitialState(): UserMiningState {
        val now = System.currentTimeMillis()

        val isLoggedIn = securityPreferences.isLoggedIn()
        var key = securityPreferences.getSecretKey()
        if (!isLoggedIn || key.isNullOrBlank()) {
            return UserMiningState(
                uid = "",
                secretKey = "",
                email = "",
                nodeId = "",
                isAuthenticated = false,
                isAdmin = false,
                role = "user"
            )
        }

        val isPinConfigured = securityPreferences.isPinSet()
        val isBiometricEnabled = securityPreferences.isBiometricEnabled()
        val isKeyBackedUp = securityPreferences.isSecretKeyBackedUp()
        val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(key)
        val userRole = if (isMasterAdmin) "superadmin" else "user"

        // 2. Load Local Balances or strict zero defaults
        val defaultGrid = 0.0
        val defaultUsdt = 0.0
        val savedGrid = prefs.getFloat("grid_balance", -1f)
        val savedUsdt = prefs.getFloat("miner_balance", -1f)
        val savedLastTick = prefs.getLong("last_yield_tick", 0L)

        var initialGrid = if (savedGrid >= 0) savedGrid.toDouble() else defaultGrid
        var initialUsdt = if (savedUsdt >= 0) savedUsdt.toDouble() else defaultUsdt

        val sessionStart = prefs.getLong("free_session_start", 0L)
        val sessionEnd = prefs.getLong("free_session_end", 0L)
        var isFreeActive = prefs.getBoolean("free_session_active", false) && (now < sessionEnd)

        val loadedRigs = emptyList<UserRig>()

        val rawState = UserMiningState(
            uid = key,
            secretKey = key,
            email = "miner_${key.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = "NODE-WEB3-#${key.takeLast(4)}",
            isColdStorageSynced = true,
            minerBalanceUsdt = initialUsdt,
            gridBalance = initialGrid,
            baseFreeHashrateGh = 2.0,
            referralCount = 0,
            activeReferredMiners = 0,
            temporaryBoostHashrateGh = 0.0,
            temporaryBoostExpiry = 0L,
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            isFreeMiningActive = isFreeActive,
            referralCode = "HG-${key.takeLast(4)}",
            dailySpentUsdt = 0.0,
            dailySpentResetDate = now,
            lastDailySpinTimestamp = 0L,
            userRigs = loadedRigs,
            transactions = emptyList(),
            microTasks = emptyList(),
            isKeyBackedUp = isKeyBackedUp,
            isPinConfigured = isPinConfigured,
            isBiometricEnabled = isBiometricEnabled,
            isAppLocked = isPinConfigured, // Lock immediately on cold start if PIN is configured
            lastYieldTickTimestamp = if (savedLastTick > 0) savedLastTick else now,
            createdAt = prefs.getLong("account_created_at", now),
            isAdmin = isMasterAdmin,
            role = userRole
        )

        // 3. Apply Offline Mining Catch-Up if time elapsed
        return if (savedLastTick > 0 && now > savedLastTick) {
            applyOfflineCatchUpYield(rawState, now)
        } else {
            rawState
        }
    }

    private fun startBackgroundEngine() {
        scope.launch {
            val current = _userState.value
            if (current.isAuthenticated && current.secretKey.isNotBlank()) {
                try {
                    val res = firebaseManager.restoreUserBySecretKey(current.secretKey)
                    if (res.isSuccess && res.getOrNull() != null) {
                        val cloudState = res.getOrThrow()!!
                        val now = System.currentTimeMillis()
                        val caughtUp = applyOfflineCatchUpYield(cloudState, now)
                        _userState.value = caughtUp.copy(
                            isKeyBackedUp = securityPreferences.isSecretKeyBackedUp(),
                            isPinConfigured = securityPreferences.isPinSet(),
                            isBiometricEnabled = securityPreferences.isBiometricEnabled(),
                            isAppLocked = securityPreferences.isPinSet(),
                            isAuthenticated = true
                        )
                        isCloudHydrated = true
                        Log.d("MiningRepository", "Successfully hydrated and caught up user state from Firestore at startup.")
                        
                        // Save offline local cache of the hydrated state
                        prefs.edit()
                            .putFloat("grid_balance", _userState.value.gridBalance.toFloat())
                            .putFloat("miner_balance", _userState.value.minerBalanceUsdt.toFloat())
                            .putLong("last_yield_tick", _userState.value.lastYieldTickTimestamp)
                            .putLong("free_session_start", _userState.value.freeMiningSessionStart)
                            .putLong("free_session_end", _userState.value.freeMiningSessionEnd)
                            .putBoolean("free_session_active", _userState.value.isFreeMiningActive)
                            .apply()
                    } else {
                        // User key not found on cloud, but already logged in locally.
                        // We mark as hydrated to allow saving local work.
                        isCloudHydrated = true
                    }
                } catch (e: Exception) {
                    Log.w("MiningRepository", "Startup cloud hydrate error: ${e.message}")
                    // On network failure, we check if they have cached data from previous runs to prevent zero overwrite.
                    val savedGrid = prefs.getFloat("grid_balance", -1f)
                    if (savedGrid >= 0) {
                        isCloudHydrated = true
                    }
                }
            } else {
                // No logged-in user, fully hydrated by default
                isCloudHydrated = true
            }

            // Safe sync initial state if hydrated
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
        if (!current.isAuthenticated || current.secretKey.isBlank()) return

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
    }

    private fun syncToCloud() {
        val state = _userState.value
        if (!state.isAuthenticated || state.secretKey.isBlank()) return
        // Save local offline copy to SharedPreferences
        prefs.edit()
            .putFloat("grid_balance", state.gridBalance.toFloat())
            .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
            .putLong("last_yield_tick", state.lastYieldTickTimestamp)
            .putLong("free_session_start", state.freeMiningSessionStart)
            .putLong("free_session_end", state.freeMiningSessionEnd)
            .putBoolean("free_session_active", state.isFreeMiningActive)
            .apply()

        // Prevent race condition: Only sync to Firestore if latest state is already hydrated from cloud
        if (!isCloudHydrated) {
            Log.d("MiningRepository", "Skipping syncToCloud Firestore push: Waiting for cloud hydration.")
            return
        }

        scope.launch {
            try {
                if (state.secretKey.isNotBlank()) {
                    firebaseManager.saveUserUnderSecretKey(state.secretKey, state)
                }
                firebaseManager.syncUserStateToFirestore(state)
            } catch (e: Exception) {
                Log.e("MiningRepository", "Cloud sync exception: ${e.message}")
            }
        }
    }

    suspend fun createNewAccount(): Result<UserMiningState> = withContext(Dispatchers.IO) {
        val newKey = SecretKeyUtils.generateSecretKey()
        val now = System.currentTimeMillis()

        val newState = UserMiningState(
            uid = newKey,
            secretKey = newKey,
            email = "miner_${newKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = "NODE-WEB3-#${newKey.takeLast(4)}",
            minerBalanceUsdt = 0.0,
            gridBalance = 0.0,
            baseFreeHashrateGh = 2.0,
            referralCount = 0,
            activeReferredMiners = 0,
            referralCode = newKey,
            userRigs = emptyList(),
            transactions = emptyList(),
            isKeyBackedUp = false,
            isPinConfigured = false,
            isBiometricEnabled = false,
            isAppLocked = false,
            lastYieldTickTimestamp = now,
            createdAt = now,
            isAdmin = false,
            role = "user",
            isAuthenticated = true
        )

        try {
            isCloudHydrated = true
            firebaseManager.saveUserUnderSecretKey(newKey, newState)
            firebaseManager.recordActivityLog(newKey, "ACCOUNT_CREATED")
        } catch (e: Exception) {
            Log.w("MiningRepository", "Note on account creation cloud save: ${e.message}")
        }

        securityPreferences.setSecretKey(newKey)
        securityPreferences.setLoggedIn(true)
        securityPreferences.setSecretKeyBackedUp(false)

        _userState.value = newState
        syncToCloud()
        Result.success(newState)
    }

    fun logout() {
        val current = _userState.value
        if (current.isAuthenticated && current.secretKey.isNotBlank()) {
            scope.launch {
                try {
                    firebaseManager.saveUserUnderSecretKey(current.secretKey, current)
                    firebaseManager.syncUserStateToFirestore(current)
                } catch (e: Throwable) {
                    Log.e("MiningRepository", "Flush state before logout: ${e.message}")
                }
            }
        }
        isCloudHydrated = false
        securityPreferences.clearSession()
        _userState.value = UserMiningState(
            uid = "",
            secretKey = "",
            email = "",
            nodeId = "",
            isAuthenticated = false,
            isAdmin = false,
            role = "user"
        )
    }

    suspend fun restoreAccountWithSecretKey(secretKey: String): Result<UserMiningState> = withContext(Dispatchers.IO) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(cleanKey)
        if (!isMasterAdmin && !SecretKeyUtils.isValidSecretKey(cleanKey)) {
            return@withContext Result.failure(Exception("Invalid format. Must be HG-XXXX-XXXX-XXXX-XXXX"))
        }

        val res = firebaseManager.restoreUserBySecretKey(cleanKey)
        val now = System.currentTimeMillis()

        val finalState = if (res.isSuccess && res.getOrNull() != null) {
            val restored = res.getOrThrow()!!
            val userRole = if (isMasterAdmin) "superadmin" else restored.role

            // Apply offline continuous yield calculation since last saved timestamp
            val caughtUp = applyOfflineCatchUpYield(restored, now)

            caughtUp.copy(
                secretKey = cleanKey,
                uid = cleanKey,
                isKeyBackedUp = true,
                isPinConfigured = securityPreferences.isPinSet(),
                isBiometricEnabled = securityPreferences.isBiometricEnabled(),
                isAppLocked = false,
                isAdmin = isMasterAdmin || restored.isAdmin,
                role = userRole,
                isAuthenticated = true
            )
        } else if (isMasterAdmin) {
            val adminState = UserMiningState(
                uid = cleanKey,
                secretKey = cleanKey,
                email = "admin@hashgrid.pro",
                nodeId = "NODE-SUPERADMIN-#0001",
                minerBalanceUsdt = 5000.0,
                gridBalance = 10000.0,
                baseFreeHashrateGh = 10.0,
                referralCount = 150,
                activeReferredMiners = 95,
                referralCode = "HG-ADM01",
                userRigs = listOf(
                    UserRig(
                        id = "rig-titan-adm-01",
                        catalogId = "titan_enterprise_node",
                        name = "Titan Enterprise Node #001",
                        priceUsdt = 500.0,
                        hashrateGh = 180.0,
                        purchaseTimestamp = now,
                        durationDays = 200,
                        status = RigStatus.ACTIVE,
                        totalReceivedUsdt = 0.0,
                        thisMonthEarnedUsdt = 0.0,
                        lastYieldCalculatedTimestamp = now
                    )
                ),
                transactions = listOf(
                    TransactionItem(
                        id = "tx-admin-genesis",
                        type = TransactionType.DEPOSIT,
                        amount = 5000.0,
                        currency = "USDT",
                        timestamp = now,
                        status = TransactionStatus.COMPLETED,
                        description = "Master SuperAdmin Genesis Protocol Liquidity",
                        network = "BEP20 (BSC)"
                    )
                ),
                isKeyBackedUp = true,
                isPinConfigured = securityPreferences.isPinSet(),
                isBiometricEnabled = securityPreferences.isBiometricEnabled(),
                isAppLocked = false,
                lastYieldTickTimestamp = now,
                createdAt = now,
                isAdmin = true,
                role = "superadmin",
                isAuthenticated = true
            )
            isCloudHydrated = true
            firebaseManager.saveUserUnderSecretKey(cleanKey, adminState)
            adminState
        } else {
            // STRICT FETCH-FIRST FAILURE:
            // Do NOT generate a fresh 0-balance account. If the key is not found, display an error.
            return@withContext Result.failure(Exception("No registered account found matching this Secret Key. Please check the spelling or create a new account instead."))
        }

        isCloudHydrated = true
        securityPreferences.setSecretKey(cleanKey)
        securityPreferences.setLoggedIn(true)
        securityPreferences.setSecretKeyBackedUp(true)

        _userState.value = finalState
        firebaseManager.saveUserUnderSecretKey(cleanKey, finalState)
        syncToCloud()

        Result.success(finalState)
    }

    fun setAppLocked(locked: Boolean) {
        _userState.value = _userState.value.copy(isAppLocked = locked)
    }

    fun markSecretKeyBackedUp() {
        securityPreferences.setSecretKeyBackedUp(true)
        _userState.value = _userState.value.copy(isKeyBackedUp = true)
    }

    fun setPin(pin: String): Boolean {
        val success = securityPreferences.setPin(pin)
        if (success) {
            _userState.value = _userState.value.copy(isPinConfigured = true, isAppLocked = false)
        }
        return success
    }

    fun verifyPin(pin: String): Boolean {
        return securityPreferences.verifyPin(pin)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityPreferences.setBiometricEnabled(enabled)
        _userState.value = _userState.value.copy(isBiometricEnabled = enabled)
    }

    fun isPinSet(): Boolean = securityPreferences.isPinSet()
    fun isBiometricEnabled(): Boolean = securityPreferences.isBiometricEnabled()
    fun getSecretKey(): String = securityPreferences.getSecretKey() ?: _userState.value.secretKey

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

    fun setUserOnline(isOnline: Boolean) {
        val state = _userState.value
        if (state.isAuthenticated && state.secretKey.isNotBlank()) {
            scope.launch {
                firebaseManager.updateOnlineStatus(state.secretKey, isOnline)
            }
        }
    }

    fun startFreeMiningSession() {
        val now = System.currentTimeMillis()
        val duration = 24L * 60 * 60 * 1000
        val current = _userState.value
        _userState.value = current.copy(
            isFreeMiningActive = true,
            freeMiningSessionStart = now,
            freeMiningSessionEnd = now + duration
        )
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "MINING_SESSION_STARTED",
                details = mapOf(
                    "hashrateGh" to current.aggregateFreeHashrateGh,
                    "startTime" to now,
                    "endTime" to (now + duration)
                )
            )
        }
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

        val updatedState = current.copy(
            minerBalanceUsdt = newBalance,
            dailySpentUsdt = current.dailySpentUsdt + catalogItem.priceUsdt,
            userRigs = listOf(newRig) + current.userRigs,
            transactions = listOf(tx) + current.transactions,
            lastYieldTickTimestamp = now
        )

        _userState.value = updatedState

        scope.launch {
            if (current.secretKey.isNotBlank()) {
                firebaseManager.saveUserUnderSecretKey(current.secretKey, updatedState)
            }
            firebaseManager.recordPlanActivation(current.uid, newRig, newBalance)
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "NODE_PURCHASED",
                details = mapOf(
                    "rigId" to newRig.id,
                    "catalogId" to catalogItem.id,
                    "name" to catalogItem.name,
                    "priceUsdt" to catalogItem.priceUsdt,
                    "hashrateGh" to catalogItem.hashrateGh,
                    "remainingBalanceUsdt" to newBalance
                )
            )
            syncToCloud()
        }
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
        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "DEPOSIT_CONFIRMED",
                details = mapOf("amount" to amountUsdt, "network" to network, "txHash" to txHash)
            )
        }
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
        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "WITHDRAWAL_REQUESTED",
                details = mapOf("amount" to amountUsdt, "address" to address, "network" to network)
            )
        }
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

        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "LUCKY_WHEEL_SPIN",
                details = mapOf(
                    "rewardTitle" to sector.title,
                    "rewardType" to sector.type.name,
                    "value" to sector.value
                )
            )
        }
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
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "MICRO_TASK_SUBMITTED",
                details = mapOf("taskId" to sub.id, "platform" to platform.name, "initialViews" to initialViews, "finalViews" to finalViews)
            )
        }
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
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "VIDEO_PROMO_SUBMITTED",
                details = mapOf("promoId" to sub.id, "platform" to platform.name, "url" to url, "channel" to channel)
            )
        }
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
        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "REFERRAL_COMMISSION_RECEIVED",
                details = mapOf("commissionUsdt" to commission, "source" to "100_USDT_RIG")
            )
        }
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
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "NEW_REFERRAL_JOINED",
                details = mapOf("totalReferrals" to newRef, "activeMiners" to newActive)
            )
        }
        syncToCloud()
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
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "TASKS_APPROVED_REWARD",
                details = mapOf("totalRewardedUsdt" to rewardSum)
            )
        }
        syncToCloud()
    }

    // ==========================================
    // SUPER ADMIN ACTIONS
    // ==========================================

    fun adminApproveWithdrawal(txId: String) {
        val current = _userState.value
        val updatedTx = current.transactions.map { tx ->
            if (tx.id == txId) {
                tx.copy(
                    status = TransactionStatus.COMPLETED,
                    description = "${tx.description} (Approved by Super Admin)"
                )
            } else tx
        }
        _userState.value = current.copy(transactions = updatedTx)
        val approvedTx = updatedTx.find { it.id == txId }
        scope.launch {
            if (approvedTx != null) {
                firebaseManager.saveOrUpdateTransaction(current.uid, approvedTx)
                firebaseManager.recordActivityLog(
                    userId = current.uid,
                    action = "ADMIN_WITHDRAWAL_APPROVED",
                    details = mapOf("txId" to txId, "amount" to approvedTx.amount)
                )
            }
        }
        syncToCloud()
    }

    fun adminRejectWithdrawal(txId: String) {
        val current = _userState.value
        val txToReject = current.transactions.find { it.id == txId }
        val refundAmount = if (txToReject != null && txToReject.status != TransactionStatus.COMPLETED) txToReject.amount else 0.0
        val updatedTx = current.transactions.map { tx ->
            if (tx.id == txId) {
                tx.copy(
                    status = TransactionStatus.FAILED,
                    description = "${tx.description} (Rejected - Funds Refunded)"
                )
            } else tx
        }
        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + refundAmount,
            transactions = updatedTx
        )
        val rejectedTx = updatedTx.find { it.id == txId }
        scope.launch {
            if (rejectedTx != null) {
                firebaseManager.saveOrUpdateTransaction(current.uid, rejectedTx)
                firebaseManager.recordActivityLog(
                    userId = current.uid,
                    action = "ADMIN_WITHDRAWAL_REJECTED",
                    details = mapOf("txId" to txId, "refundedAmount" to refundAmount)
                )
            }
        }
        syncToCloud()
    }

    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {
        val current = _userState.value
        _userState.value = current.copy(
            gridBalance = newGrid.coerceAtLeast(0.0),
            minerBalanceUsdt = newUsdt.coerceAtLeast(0.0)
        )
        scope.launch {
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "ADMIN_BALANCE_ADJUSTMENT",
                details = mapOf("newGrid" to newGrid, "newUsdt" to newUsdt)
            )
        }
        syncToCloud()
    }

    fun adminCreateTestPendingWithdrawal(amount: Double = 25.0, address: String = "0x71C...B42a", network: String = "BEP20 (BSC)") {
        val current = _userState.value
        val tx = TransactionItem(
            id = "tx-admin-wd-${UUID.randomUUID().toString().take(6)}",
            type = TransactionType.WITHDRAWAL,
            amount = amount,
            currency = "USDT",
            timestamp = System.currentTimeMillis(),
            status = TransactionStatus.PENDING_REVIEW,
            description = "Withdrawal to $address ($network)",
            address = address,
            network = network
        )
        _userState.value = current.copy(
            transactions = listOf(tx) + current.transactions
        )
        scope.launch {
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "ADMIN_TEST_WITHDRAWAL_CREATED",
                details = mapOf("amount" to amount, "address" to address, "network" to network)
            )
        }
        syncToCloud()
    }
}
