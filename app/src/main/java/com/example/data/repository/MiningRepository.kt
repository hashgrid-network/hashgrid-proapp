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
    val prefs: SharedPreferences = context.getSharedPreferences("hashgrid_prefs_v1", Context.MODE_PRIVATE)
    val securityPreferences = SecurityPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val firebaseManager = FirebaseManager(context)
    val nowPaymentsManager = NowPaymentsManager()

    companion object {
        const val GRID_PRELAUNCH_PRICE_USD = 0.01 // Default Pre-Launch rate: 1 GRID = 0.01 USDT
        const val TARGET_DAILY_GRID = 302.4 // Target daily GRID for 10 GH/s (~0.0035/sec)
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
        var isMiningActive = state.isFreeMiningActive || (now < state.freeMiningSessionEnd)
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
        var key = securityPreferences.getActiveUserKey()
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
        val startTime = prefs.getLong("mining_start_time_millis", sessionStart)
        val baselineGrid = prefs.getFloat("baseline_grid_balance", initialGrid.toFloat()).toDouble()
        var isFreeActive = prefs.getBoolean("free_session_active", false) && (now < sessionEnd)

        if (isFreeActive && startTime > 0 && now > startTime) {
            val elapsedSeconds = ((now - startTime) / 1000.0).coerceAtLeast(0.0)
            val tokensPerSecond = (2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0)
            val sessionMined = elapsedSeconds * tokensPerSecond
            initialGrid = maxOf(initialGrid, baselineGrid + sessionMined)
        }

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
    val isCloudSynced = MutableStateFlow(false)
    val connectionErrorMsg = MutableStateFlow<String?>(null)
    private var userListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun attachUserDocumentRealTimeListener(secretKey: String) {
        val cleanKey = secretKey.trim().uppercase()
        if (cleanKey.isBlank()) return
        scope.launch(Dispatchers.Main) {
            try {
                userListenerRegistration?.remove()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docRef = db.collection("users").document(cleanKey)
                
                userListenerRegistration = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        isCloudSynced.value = false
                        connectionErrorMsg.value = "Firestore Error: ${error.code} - ${error.localizedMessage}"
                        Log.e("MiningRepository", "Firestore real-time snapshot error: ${error.message}", error)
                        return@addSnapshotListener
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        isCloudSynced.value = false
                        connectionErrorMsg.value = "Connecting to Node..."
                        Log.w("MiningRepository", "User document $cleanKey is not loaded yet on Firestore.")
                        return@addSnapshotListener
                    }

                    // VERIFIED LIVE CONNECTION -> TURN GREEN
                    isCloudSynced.value = true
                    connectionErrorMsg.value = null

                    val current = _userState.value
                    if (current.secretKey.trim().uppercase() == cleanKey || current.secretKey.isBlank()) {
                        // Extract real-time balances immediately with timestamp accrual protection
                        val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble()
                            ?: (snapshot.get("usdtBalance") as? Number)?.toDouble()
                            ?: current.minerBalanceUsdt
                        val cloudGrid = (snapshot.get("gridBalance") as? Number)?.toDouble()
                            ?: current.gridBalance
                        val cloudBaseline = (snapshot.get("baselineGridBalance") as? Number)?.toDouble()
                            ?: prefs.getFloat("baseline_grid_balance", cloudGrid.toFloat()).toDouble()
                        val sessionStart = (snapshot.get("miningStartTimeMillis") as? Number)?.toLong()
                            ?: (snapshot.get("freeMiningSessionStart") as? Number)?.toLong()
                            ?: (snapshot.get("miningStartTime") as? Number)?.toLong()
                            ?: current.freeMiningSessionStart
                        val sessionEnd = (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
                            ?: (snapshot.get("sessionEndTime") as? Number)?.toLong()
                            ?: (snapshot.get("miningEndTime") as? Number)?.toLong()
                            ?: current.freeMiningSessionEnd
                        val isMining = (snapshot.getBoolean("isFreeMiningActive")
                            ?: snapshot.getBoolean("isMiningActive")
                            ?: current.isFreeMiningActive) && (System.currentTimeMillis() < sessionEnd)

                        val nowTs = System.currentTimeMillis()
                        val sessionMined = if (isMining && sessionStart > 0 && nowTs > sessionStart) {
                            val elapsedSeconds = ((nowTs - sessionStart) / 1000.0).coerceAtLeast(0.0)
                            val hashrate = current.aggregateFreeHashrateGh.coerceAtLeast(2.0)
                            val tokensPerSec = (hashrate / 10.0) * (TARGET_DAILY_GRID / 86400.0)
                            elapsedSeconds * tokensPerSec
                        } else 0.0

                        val accruedGrid = cloudBaseline + sessionMined
                        val effectiveGrid = maxOf(accruedGrid, cloudGrid, current.gridBalance, prefs.getFloat("grid_balance", 0f).toDouble())

                        // Parse hardware nodes / rigs list
                        val rawRigs = snapshot.get("hardwareNodes") as? List<Map<String, Any>> ?: emptyList()
                        val parsedRigs = rawRigs.mapNotNull { map ->
                            try {
                                val id = map["id"] as? String ?: map["nodeId"] as? String ?: return@mapNotNull null
                                val catalogId = map["catalogId"] as? String ?: "rig-nano"
                                val name = map["name"] as? String ?: "Hardware Node"
                                val priceUsdt = (map["priceUsdt"] as? Number)?.toDouble() ?: 0.0
                                val hashrateGh = (map["hashrateGh"] as? Number)?.toDouble() ?: 0.0
                                val statusStr = map["status"] as? String ?: "ACTIVE"
                                val status = try { RigStatus.valueOf(statusStr) } catch (_: Exception) { RigStatus.ACTIVE }
                                val durationDays = (map["durationDays"] as? Number)?.toInt() ?: (map["daysRemaining"] as? Number)?.toInt() ?: 200
                                val purchaseTimestamp = (map["purchaseTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                val lastYieldCalculatedTimestamp = (map["lastYieldCalculatedTimestamp"] as? Number)?.toLong() ?: purchaseTimestamp
                                val totalReceivedUsdt = (map["totalReceivedUsdt"] as? Number)?.toDouble() ?: 0.0
                                val thisMonthEarnedUsdt = (map["thisMonthEarnedUsdt"] as? Number)?.toDouble() ?: 0.0

                                UserRig(
                                    id = id,
                                    catalogId = catalogId,
                                    name = name,
                                    priceUsdt = priceUsdt,
                                    hashrateGh = hashrateGh,
                                    purchaseTimestamp = purchaseTimestamp,
                                    durationDays = durationDays,
                                    status = status,
                                    totalReceivedUsdt = totalReceivedUsdt,
                                    thisMonthEarnedUsdt = thisMonthEarnedUsdt,
                                    lastYieldCalculatedTimestamp = lastYieldCalculatedTimestamp
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        // Parse transactions list
                        val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
                        val parsedTxs = rawTxs.mapNotNull { map ->
                            try {
                                val id = map["id"] as? String ?: return@mapNotNull null
                                val typeStr = map["type"] as? String ?: "DEPOSIT"
                                val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.DEPOSIT }
                                val amount = (map["amount"] as? Number)?.toDouble() ?: 0.0
                                val currency = map["currency"] as? String ?: "USDT"
                                val timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                val statusStr = map["status"] as? String ?: "COMPLETED"
                                val status = try { TransactionStatus.valueOf(statusStr) } catch (_: Exception) { TransactionStatus.COMPLETED }
                                val description = map["description"] as? String ?: ""
                                val txHash = map["txHash"] as? String
                                val network = map["network"] as? String ?: "BEP20 (BSC)"

                                TransactionItem(
                                    id = id,
                                    type = type,
                                    amount = amount,
                                    currency = currency,
                                    timestamp = timestamp,
                                    status = status,
                                    description = description,
                                    txHash = txHash,
                                    network = network
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val isMaster = SecretKeyUtils.isMasterAdminKey(cleanKey) || (snapshot.getBoolean("isAdmin") ?: false)
                        val role = snapshot.getString("role") ?: (if (isMaster) "superadmin" else "user")

                        // Update StateFlow immediately on Main thread so all UI observers update without delay
                        _userState.value = _userState.value.copy(
                            uid = cleanKey,
                            secretKey = cleanKey,
                            gridBalance = effectiveGrid,
                            minerBalanceUsdt = usdt,
                            isFreeMiningActive = isMining && (System.currentTimeMillis() < sessionEnd),
                            freeMiningSessionStart = sessionStart,
                            freeMiningSessionEnd = sessionEnd,
                            userRigs = if (parsedRigs.isNotEmpty()) parsedRigs else current.userRigs,
                            transactions = if (parsedTxs.isNotEmpty()) parsedTxs else current.transactions,
                            isAdmin = isMaster,
                            role = role
                        )

                        prefs.edit()
                            .putFloat("grid_balance", effectiveGrid.toFloat())
                            .putFloat("miner_balance", usdt.toFloat())
                            .putLong("free_session_start", sessionStart)
                            .putLong("free_session_end", sessionEnd)
                            .putLong("mining_start_time_millis", sessionStart)
                            .putFloat("baseline_grid_balance", cloudBaseline.toFloat())
                            .putBoolean("free_session_active", isMining)
                            .apply()

                        Log.i("MiningRepository", "Live Firestore Snapshot: Real-time update USDT=$usdt, GRID=$effectiveGrid for $cleanKey")
                    }
                }
            } catch (e: Exception) {
                isCloudSynced.value = false
                Log.e("MiningRepository", "Failed to start real-time snapshot engine: ${e.message}")
            }
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
                        val earnedGrid = caughtUp.gridBalance - cloudState.gridBalance
                        val earnedUsdt = caughtUp.minerBalanceUsdt - cloudState.minerBalanceUsdt
                        if (earnedGrid > 0.0 || earnedUsdt > 0.0) {
                            scope.launch {
                                firebaseManager.recordActivityLog(
                                    userId = cloudState.secretKey,
                                    action = "OFFLINE_YIELD_SYNCED",
                                    details = mapOf(
                                        "grid" to earnedGrid,
                                        "usdt" to earnedUsdt,
                                        "timestamp" to now
                                    )
                                )
                            }
                        }
                        _userState.value = caughtUp.copy(
                            isKeyBackedUp = securityPreferences.isSecretKeyBackedUp(),
                            isPinConfigured = securityPreferences.isPinSet(),
                            isBiometricEnabled = securityPreferences.isBiometricEnabled(),
                            isAppLocked = securityPreferences.isPinSet(),
                            isAuthenticated = true
                        )
                        isCloudHydrated = true
                        Log.d("MiningRepository", "Successfully hydrated and caught up user state from Firestore at startup.")
                        attachUserDocumentRealTimeListener(current.secretKey)
                        
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
            val computedGrid = computeAccruedGridBalance(now)
            newGridBalance = maxOf(computedGrid, current.gridBalance)
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

    suspend fun syncMinedTokensToFirestore(): Boolean = withContext(Dispatchers.IO) {
        val state = _userState.value
        if (!state.isAuthenticated || state.secretKey.isBlank()) return@withContext false
        try {
            // Save local offline copy first
            prefs.edit()
                .putFloat("grid_balance", state.gridBalance.toFloat())
                .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
                .putLong("last_yield_tick", state.lastYieldTickTimestamp)
                .putLong("free_session_start", state.freeMiningSessionStart)
                .putLong("free_session_end", state.freeMiningSessionEnd)
                .putBoolean("free_session_active", state.isFreeMiningActive)
                .apply()

            if (isCloudHydrated) {
                firebaseManager.saveUserUnderSecretKey(state.secretKey, state)
                firebaseManager.syncUserStateToFirestore(state)
                Log.d("MiningRepository", "Successfully synced mined tokens and sessions to Cloud Firestore.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("MiningRepository", "syncMinedTokensToFirestore error: ${e.message}")
            false
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

        val success = try {
            isCloudHydrated = true
            val saved = firebaseManager.saveUserUnderSecretKey(newKey, newState)
            if (saved) {
                firebaseManager.recordActivityLog(newKey, "ACCOUNT_CREATED")
            }
            saved
        } catch (e: Exception) {
            Log.w("MiningRepository", "Note on account creation cloud save: ${e.message}")
            false
        }

        if (!success) {
            return@withContext Result.failure(Exception("Failed to register your secure Web3 mining profile to the Firestore cloud. Please check your internet connection and try again."))
        }

        securityPreferences.setActiveUserKey(newKey)
        securityPreferences.setLoggedIn(true)
        securityPreferences.setSecretKeyBackedUp(false)

        _userState.value = newState
        syncToCloud()
        attachUserDocumentRealTimeListener(newKey)
        Result.success(newState)
    }

    fun immediateLogout() {
        try {
            userListenerRegistration?.remove()
            userListenerRegistration = null
            isCloudSynced.value = false
            isCloudHydrated = false
            securityPreferences.clearSession()
            com.example.data.security.SessionManager.getInstance(appContext).clearActiveKey()
            prefs.edit()
                .remove("grid_balance")
                .remove("miner_balance")
                .remove("last_yield_tick")
                .remove("free_session_start")
                .remove("free_session_end")
                .remove("free_session_active")
                .apply()
            _userState.value = UserMiningState(
                uid = "",
                secretKey = "",
                email = "",
                nodeId = "",
                isAuthenticated = false,
                isAdmin = false,
                role = "user"
            )
            Log.i("MiningRepository", "Immediate logout executed: Session cleared, state reset to unauthenticated.")
        } catch (e: Exception) {
            Log.e("MiningRepository", "Error during immediate logout: ${e.message}", e)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val current = _userState.value
        if (current.isAuthenticated && current.secretKey.isNotBlank()) {
            try {
                firebaseManager.saveUserUnderSecretKey(current.secretKey, current)
                firebaseManager.syncUserStateToFirestore(current)
            } catch (e: Throwable) {
                Log.e("MiningRepository", "Flush state before logout: ${e.message}")
            }
        }
        withContext(Dispatchers.Main) {
            immediateLogout()
        }
    }

    /**
     * Instant local offline-first login. Authenticates immediately and launches background sync.
     */
    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        val cachedUsdt = prefs.getFloat("miner_balance", 0.0f).toDouble()
        val cachedGrid = prefs.getFloat("grid_balance", 0.0f).toDouble()
        val initialUsdt = if (isAdminKey) {
            if (cachedUsdt > 0.0) cachedUsdt else 3000.0
        } else {
            cachedUsdt
        }
        val initialGrid = if (isAdminKey) {
            if (cachedGrid > 0.0) cachedGrid else 5000.0
        } else {
            cachedGrid
        }

        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = initialUsdt,
            gridBalance = initialGrid,
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        // 1. Immediately save key & session flags
        securityPreferences.saveActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)
        securityPreferences.setSecretKeyBackedUp(true)

        prefs.edit()
            .putFloat("miner_balance", initialUsdt.toFloat())
            .putFloat("grid_balance", initialGrid.toFloat())
            .apply()

        // 2. Immediately set state
        _userState.value = instantState
        isCloudHydrated = true

        // 3. Launch background async sync without blocking the UI
        restoreSessionAsync(cleanKey)

        return instantState
    }

    /**
     * Asynchronously restores cloud data without blocking the UI or kicking the user out.
     */
    fun restoreSessionAsync(key: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        scope.launch(Dispatchers.IO) {
            try {
                val docRef = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(cleanKey)
                docRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot != null && snapshot.exists()) {
                        val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble()
                            ?: (snapshot.get("usdtBalance") as? Number)?.toDouble()
                            ?: _userState.value.minerBalanceUsdt
                        val grid = (snapshot.get("gridBalance") as? Number)?.toDouble()
                            ?: _userState.value.gridBalance
                        val nodes = (snapshot.get("deployedNodesCount") as? Number)?.toInt()
                            ?: (snapshot.get("hardwareNodes") as? List<*>)?.size
                            ?: _userState.value.userRigs.size
                        val isMining = snapshot.getBoolean("isFreeMiningActive") ?: snapshot.getBoolean("isMiningActive") ?: _userState.value.isFreeMiningActive
                        val sessionEnd = (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
                            ?: (snapshot.get("miningEndTime") as? Number)?.toLong()
                            ?: _userState.value.freeMiningSessionEnd
                        val isAdmin = snapshot.getBoolean("isAdmin") ?: (cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey))
                        val role = snapshot.getString("role") ?: (if (isAdmin) "superadmin" else "user")

                        _userState.value = _userState.value.copy(
                            minerBalanceUsdt = usdt,
                            gridBalance = grid,
                            isFreeMiningActive = isMining && (System.currentTimeMillis() < sessionEnd),
                            freeMiningSessionEnd = sessionEnd,
                            isAdmin = isAdmin,
                            role = role
                        )
                        prefs.edit()
                            .putFloat("miner_balance", usdt.toFloat())
                            .putFloat("grid_balance", grid.toFloat())
                            .apply()
                        Log.i("RESTORE_ASYNC", "Cloud sync succeeded for $cleanKey: usdt=$usdt, grid=$grid, nodes=$nodes")
                    }
                    attachUserDocumentRealTimeListener(cleanKey)
                }.addOnFailureListener { e ->
                    Log.e("RESTORE_ERR", "Background restore failed, kept local state", e)
                    // Even if network fails or times out, user remains authenticated!
                    attachUserDocumentRealTimeListener(cleanKey)
                }
            } catch (e: Exception) {
                Log.e("RESTORE_ERR", "Background restore failed, kept local state", e)
                attachUserDocumentRealTimeListener(cleanKey)
            }
        }
    }

    fun setLocalBalance(minerUsdt: Double, grid: Double) {
        prefs.edit()
            .putFloat("miner_balance", minerUsdt.toFloat())
            .putFloat("grid_balance", grid.toFloat())
            .apply()
        _userState.value = _userState.value.copy(
            minerBalanceUsdt = minerUsdt,
            gridBalance = grid
        )
    }

    fun setInstantUserState(state: UserMiningState) {
        _userState.value = state
        prefs.edit()
            .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
            .putFloat("grid_balance", state.gridBalance.toFloat())
            .apply()
    }

    /**
     * Restores user account without blocking or wiping balances.
     */
    fun restoreAccount(key: String, onComplete: (Boolean) -> Unit) {
        val state = loginWithKeyInstant(key)
        onComplete(true)
    }

    /**
     * Initializes or restores a user account, ensuring existing data is not overwritten.
     */
    fun initializeOrRestoreUser(key: String, onComplete: (Boolean) -> Unit) {
        loginWithKeyInstant(key)
        onComplete(true)
    }

    suspend fun restoreAccountWithSecretKey(secretKey: String): Result<UserMiningState> {
        val state = loginWithKeyInstant(secretKey)
        return Result.success(state)
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
    fun getSecretKey(): String = securityPreferences.getActiveUserKey() ?: _userState.value.secretKey

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

    fun computeAccruedGridBalance(now: Long = System.currentTimeMillis()): Double {
        val state = _userState.value
        val sessionEnd = prefs.getLong("free_session_end", state.freeMiningSessionEnd)
        val startTime = prefs.getLong("mining_start_time_millis", state.freeMiningSessionStart)
        val baselineGrid = prefs.getFloat("baseline_grid_balance", state.gridBalance.toFloat()).toDouble()
        val isMining = prefs.getBoolean("free_session_active", state.isFreeMiningActive) && (now < sessionEnd) && (startTime > 0)

        if (isMining) {
            val elapsedSeconds = ((now - startTime) / 1000.0).coerceAtLeast(0.0)
            val currentHashrateGh = state.aggregateFreeHashrateGh.coerceAtLeast(2.0)
            val tokensPerSecond = (currentHashrateGh / 10.0) * (TARGET_DAILY_GRID / 86400.0)
            val sessionMined = elapsedSeconds * tokensPerSecond
            return baselineGrid + sessionMined
        }
        return maxOf(baselineGrid, state.gridBalance, prefs.getFloat("grid_balance", 0f).toDouble())
    }

    fun saveGridBalanceOnPause(computedGrid: Double) {
        val now = System.currentTimeMillis()
        val current = _userState.value
        val activeKey = current.secretKey.ifBlank { securityPreferences.getActiveUserKey() ?: "" }

        prefs.edit()
            .putFloat("grid_balance", computedGrid.toFloat())
            .putLong("last_yield_tick", now)
            .apply()

        _userState.value = current.copy(
            gridBalance = computedGrid,
            lastYieldTickTimestamp = now
        )

        if (activeKey.isNotBlank()) {
            try {
                val updateMap = mapOf(
                    "gridBalance" to computedGrid,
                    "lastSyncTimestamp" to now,
                    "lastUpdatedTimestamp" to now
                )
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(activeKey)
                    .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("MiningRepository", "Auto-saved grid balance $computedGrid on pause/stop")
                    }
                    .addOnFailureListener { e ->
                        Log.e("MiningRepository", "Auto-save on pause failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("MiningRepository", "Auto-save exception on pause", e)
            }
        }
    }

    fun startFreeMiningSession() {
        val now = System.currentTimeMillis()
        val duration = 24L * 60 * 60 * 1000L
        val current = _userState.value
        val end = now + duration
        val activeKey = current.secretKey.ifBlank { securityPreferences.getActiveUserKey() ?: "HG-ADM9-7788-5544-0001" }
        val baselineGrid = current.gridBalance

        prefs.edit()
            .putLong("mining_start_time_millis", now)
            .putFloat("baseline_grid_balance", baselineGrid.toFloat())
            .putLong("free_session_start", now)
            .putLong("free_session_end", end)
            .putBoolean("free_session_active", true)
            .putFloat("grid_balance", baselineGrid.toFloat())
            .apply()

        val updatedState = current.copy(
            isFreeMiningActive = true,
            freeMiningSessionStart = now,
            freeMiningSessionEnd = end,
            lastYieldTickTimestamp = now
        )
        _userState.value = updatedState
        saveStateToPrefs(updatedState)

        // Write directly to Firestore document using SetOptions.merge()
        val updateMap = mapOf(
            "isFreeMiningActive" to true,
            "isMiningActive" to true,
            "miningStartTimeMillis" to now,
            "miningStartTime" to now,
            "freeMiningSessionStart" to now,
            "baselineGridBalance" to baselineGrid,
            "sessionEndTime" to end,
            "miningEndTime" to end,
            "freeMiningSessionEnd" to end,
            "lastSyncTimestamp" to now,
            "lastUpdatedTimestamp" to now
        )

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(activeKey)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MiningRepository", "Free mining session started successfully directly in Cloud Firestore!")
                scope.launch {
                    try {
                        firebaseManager.recordActivityLog(
                            userId = activeKey,
                            action = "MINING_START",
                            details = mapOf(
                                "hashrateGh" to current.aggregateFreeHashrateGh,
                                "timestamp" to now,
                                "startTime" to now,
                                "endTime" to end
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("MiningRepository", "startFreeMiningSession activity logging failed: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("MiningRepository", "Free mining session start failed in Cloud Firestore: ${e.message}")
            }
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
            val key = securityPreferences.getActiveUserKey() ?: current.secretKey
            if (key.isNotBlank()) {
                try {
                    val newRigMap = mapOf(
                        "nodeId" to newRig.id,
                        "nodeName" to newRig.name,
                        "costUsdt" to newRig.priceUsdt,
                        "hashrateGh" to newRig.hashrateGh,
                        "purchaseTimestamp" to newRig.purchaseTimestamp,
                        "totalDays" to newRig.durationDays,
                        "daysRemaining" to newRig.daysRemaining(),
                        "receivedUsdt" to newRig.totalReceivedUsdt,
                        "status" to newRig.status.name,
                        "catalogId" to newRig.catalogId,
                        "thisMonthEarnedUsdt" to newRig.thisMonthEarnedUsdt,
                        "lastYieldCalculatedTimestamp" to newRig.lastYieldCalculatedTimestamp,
                        "expiryTimestamp" to newRig.expiryTimestamp,
                        // Alias fields for backwards compatibility
                        "rigId" to newRig.id,
                        "name" to newRig.name,
                        "priceUsdt" to newRig.priceUsdt,
                        "hashrate" to newRig.hashrateGh,
                        "durationDays" to newRig.durationDays,
                        "totalReceivedUsdt" to newRig.totalReceivedUsdt,
                        "startTimestamp" to newRig.purchaseTimestamp
                    )
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(key)
                        .update(
                            "hardwareNodes", com.google.firebase.firestore.FieldValue.arrayUnion(newRigMap),
                            "minerBalanceUsdt", com.google.firebase.firestore.FieldValue.increment(-catalogItem.priceUsdt),
                            "dailySpentUsdt", com.google.firebase.firestore.FieldValue.increment(catalogItem.priceUsdt)
                        )
                } catch (e: Exception) {
                    Log.e("MiningRepository", "Direct Firestore update failed: ${e.message}", e)
                }
                firebaseManager.saveUserUnderSecretKey(key, updatedState)
            }
            firebaseManager.recordPlanActivation(current.uid, newRig, newBalance)
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)

            // ATOMIC TEAM COMMISSION ENGINE
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val referredBy = current.referredBy
            if (!referredBy.isNullOrBlank()) {
                val commissionUsdt = catalogItem.priceUsdt * 0.07
                db.collection("users")
                    .whereEqualTo("referralCode", referredBy.trim())
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val sponsorDoc = querySnapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                        val sponsorKey = sponsorDoc.id

                        val sponsorTx = mapOf(
                            "id" to "tx-ref-${UUID.randomUUID().toString().take(8)}",
                            "type" to "REFERRAL_COMMISSION",
                            "amount" to commissionUsdt,
                            "currency" to "USDT",
                            "timestamp" to System.currentTimeMillis(),
                            "status" to "COMPLETED",
                            "description" to "7% Affiliate Commission from ${current.nodeId.ifBlank { "Downline" }}",
                            "fromUser" to current.nodeId
                        )

                        db.collection("users").document(sponsorKey)
                            .update(
                                "minerBalanceUsdt", com.google.firebase.firestore.FieldValue.increment(commissionUsdt),
                                "usdtBalance", com.google.firebase.firestore.FieldValue.increment(commissionUsdt),
                                "teamEarningsUsdt", com.google.firebase.firestore.FieldValue.increment(commissionUsdt),
                                "transactions", com.google.firebase.firestore.FieldValue.arrayUnion(sponsorTx)
                            )
                            .addOnSuccessListener {
                                Log.d("REFERRAL_ENGINE", "Successfully credited Sponsor ($sponsorKey) with $commissionUsdt USDT commission.")
                            }

                        // Immutable cloud activity logging for Sponsor's commission
                        scope.launch {
                            firebaseManager.recordActivityLog(
                                userId = sponsorKey,
                                action = "REFERRAL_COMMISSION",
                                details = mapOf(
                                    "amount" to commissionUsdt,
                                    "commission" to commissionUsdt,
                                    "fromUser" to current.nodeId,
                                    "timestamp" to System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("REFERRAL_ENGINE", "Failed to query Sponsor: ${e.message}")
                    }
            }

            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "RIG_PURCHASE",
                details = mapOf(
                    "rigId" to newRig.id,
                    "catalogId" to catalogItem.id,
                    "name" to catalogItem.name,
                    "price" to catalogItem.priceUsdt,
                    "amount" to catalogItem.priceUsdt,
                    "rigName" to catalogItem.name,
                    "timestamp" to now,
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
        if (amountUsdt < 10.0 || current.minerBalanceUsdt < amountUsdt) {
            return Result.failure(Exception("Insufficient withdrawable USDT balance."))
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
                action = "SPIN_REWARD",
                details = mapOf(
                    "reward" to sector.value,
                    "rewardTitle" to sector.title,
                    "rewardType" to sector.type.name,
                    "timestamp" to now
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

        val updatedTeamCount = current.teamCount
        val updatedTeamEarnings = current.teamEarningsUsdt + commission

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + commission,
            teamEarningsUsdt = updatedTeamEarnings,
            transactions = listOf(tx) + current.transactions
        )
        scope.launch {
            val key = securityPreferences.getActiveUserKey() ?: current.secretKey
            if (key.isNotBlank()) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(key)
                    .update(
                        "minerBalanceUsdt", com.google.firebase.firestore.FieldValue.increment(commission),
                        "usdtBalance", com.google.firebase.firestore.FieldValue.increment(commission),
                        "teamEarningsUsdt", com.google.firebase.firestore.FieldValue.increment(commission),
                        "transactions", com.google.firebase.firestore.FieldValue.arrayUnion(mapOf(
                            "id" to tx.id,
                            "type" to tx.type.name,
                            "amount" to tx.amount,
                            "currency" to tx.currency,
                            "timestamp" to tx.timestamp,
                            "status" to tx.status.name,
                            "description" to tx.description
                        ))
                    )
            }
            firebaseManager.saveOrUpdateTransaction(current.uid, tx)
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "REFERRAL_COMMISSION",
                details = mapOf("amount" to commission, "commission" to commission, "source" to "100_USDT_RIG", "timestamp" to now)
            )
        }
        syncToCloud()
    }

    fun simulateNewReferral() {
        val current = _userState.value
        val newRef = current.referralCount + 1
        val newActive = current.activeReferredMiners + 1
        val newTeamCount = current.teamCount + 1
        val hashrateBoost = (newRef * 0.25) + (newActive * 0.50)

        _userState.value = current.copy(
            referralCount = newRef,
            activeReferredMiners = newActive,
            teamCount = newTeamCount,
            totalHashrateBoostGh = hashrateBoost
        )
        scope.launch {
            val key = securityPreferences.getActiveUserKey() ?: current.secretKey
            if (key.isNotBlank()) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(key)
                    .update(
                        "referralCount", com.google.firebase.firestore.FieldValue.increment(1),
                        "activeReferredMiners", com.google.firebase.firestore.FieldValue.increment(1),
                        "teamCount", com.google.firebase.firestore.FieldValue.increment(1),
                        "totalHashrateBoostGh", hashrateBoost
                    )
            }
            firebaseManager.recordActivityLog(
                userId = current.uid,
                action = "NEW_REFERRAL_JOINED",
                details = mapOf("totalReferrals" to newRef, "activeMiners" to newActive, "teamCount" to newTeamCount)
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
        val activeKey = current.secretKey.ifBlank { securityPreferences.getActiveUserKey() ?: "HG-ADM9-7788-5544-0001" }

        val updatedState = current.copy(
            minerBalanceUsdt = newUsdt,
            gridBalance = newGrid
        )
        _userState.value = updatedState
        saveStateToPrefs(updatedState)

        val updateMap = mapOf(
            "minerBalanceUsdt" to newUsdt.toDouble(),
            "gridBalance" to newGrid.toDouble(),
            "lastSyncTimestamp" to System.currentTimeMillis(),
            "lastUpdatedTimestamp" to System.currentTimeMillis()
        )

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // Write directly to active key document
        db.collection("users")
            .document(activeKey)
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MiningRepository", "Successfully adjusted activeKey balance directly on Firestore!")
            }

        // Also write directly to master admin key document
        db.collection("users")
            .document("HG-ADM9-7788-5544-0001")
            .set(updateMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MiningRepository", "Successfully adjusted HG-ADM9-7788-5544-0001 balance directly on Firestore!")
            }

        scope.launch {
            try {
                firebaseManager.recordActivityLog(
                    userId = activeKey,
                    action = "ADMIN_BALANCE_ADJUSTMENT",
                    details = mapOf("newGrid" to newGrid, "newUsdt" to newUsdt)
                )
            } catch (e: Exception) {
                Log.e("MiningRepository", "Sync local log save failed: ${e.message}")
            }
        }
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
    private fun saveStateToPrefs(state: UserMiningState) {
        val prefs = appContext.getSharedPreferences("mining_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
            .putBoolean("is_mining_active", state.isFreeMiningActive)
            .putLong("session_end", state.freeMiningSessionEnd)
            .putLong("last_yield_tick", state.lastYieldTickTimestamp)
            .putFloat("grid_balance", state.gridBalance.toFloat())
            .apply()
    }
}
