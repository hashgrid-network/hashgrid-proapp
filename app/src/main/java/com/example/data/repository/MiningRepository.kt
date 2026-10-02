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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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
        const val GRID_PRELAUNCH_PRICE_USD = 0.01
        const val TARGET_DAILY_GRID = 302.4
    }

    private val _userState = MutableStateFlow(loadInitialState())
    val userState: StateFlow<UserMiningState> = _userState.asStateFlow()

    private val _gridPriceUsd = MutableStateFlow(
        prefs.getFloat("grid_price_usd", GRID_PRELAUNCH_PRICE_USD.toFloat()).toDouble()
    )
    val gridPriceUsd: StateFlow<Double> = _gridPriceUsd.asStateFlow()

    private val _cryptoPrices = MutableStateFlow(
        listOf(CryptoTickerPrice("GRID/USDT", _gridPriceUsd.value, 0.00, _gridPriceUsd.value, _gridPriceUsd.value))
    )
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = _cryptoPrices.asStateFlow()

    private var lastYieldTickTime = System.currentTimeMillis()
    private var lastCloudSyncTime = System.currentTimeMillis()
    private var isCloudHydrated = false

    private val firestore = FirebaseFirestore.getInstance()
    private val vaultPrefs = context.getSharedPreferences("hashgrid_secure_vault", Context.MODE_PRIVATE)

    val activeAccount = MutableStateFlow<UserCloudAccount?>(null)
    val deployedRigs = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val _deployedRigs = deployedRigs
    val hardwareNodes = deployedRigs
    val _hardwareNodes = deployedRigs
    val deployedNodesCount = MutableStateFlow(0)
    val _deployedNodesCount = deployedNodesCount
    val isMiningActive = MutableStateFlow(false)
    val _isMiningActive = isMiningActive
    val freeMiningEndTime = MutableStateFlow(0L)
    val _freeMiningEndTime = freeMiningEndTime
    val gridBalance = MutableStateFlow(0.0)
    val _gridBalance = gridBalance
    val minerBalance = MutableStateFlow(0.0)
    val _minerBalance = minerBalance
    
    val isCloudSynced = MutableStateFlow(false)
    val connectionErrorMsg = MutableStateFlow<String?>(null)
    private var snapshotRegistration: ListenerRegistration? = null

    fun getActiveKey(): String? = vaultPrefs.getString("ACTIVE_SECRET_KEY", null) ?: securityPreferences.getActiveUserKey()

    fun setActiveKey(key: String) {
        vaultPrefs.edit().putString("ACTIVE_SECRET_KEY", key).apply()
        securityPreferences.setActiveUserKey(key)
    }

    fun clearActiveKey() {
        snapshotRegistration?.remove()
        snapshotRegistration = null
        vaultPrefs.edit().remove("ACTIVE_SECRET_KEY").apply()
        securityPreferences.clearSession()
        activeAccount.value = null
        _deployedRigs.value = emptyList()
        _deployedNodesCount.value = 0
        isCloudHydrated = false
    }

    // ========================================================
    // UNIFIED FIRESTORE REAL-TIME SYNC & ZERO-OVERWRITE BIND
    // ========================================================
    fun bindUserSession(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        setActiveKey(cleanKey)

        snapshotRegistration?.remove()
        val docRef = firestore.collection("users").document(cleanKey)

        snapshotRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                isCloudSynced.value = false
                Log.e("MiningRepository", "Firestore snapshot error: ${error.message}", error)
                return@addSnapshotListener
            }

            val now = System.currentTimeMillis()

            if (snapshot == null || !snapshot.exists()) {
                // Only create default if completely new user. SUPERADMIN gets $3000 fallback
                val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)
                val defaultUsdt = if (isMaster) 3000.0 else 0.0
                val defaultRigs = if (isMaster) listOf(
                    mapOf("id" to "193", "name" to "Elite Node #193", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                    mapOf("id" to "725", "name" to "Elite Node #725", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                    mapOf("id" to "806", "name" to "Elite Node #806", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE")
                ) else emptyList()

                val initialMap = hashMapOf(
                    "secretKey" to cleanKey,
                    "isAdmin" to isMaster,
                    "minerBalanceUsdt" to defaultUsdt,
                    "gridBalance" to 0.0,
                    "isFreeMiningActive" to false,
                    "freeMiningEndTime" to 0L,
                    "hardwareNodes" to defaultRigs,
                    "lastSyncTimestamp" to now
                )
                docRef.set(initialMap)
                isCloudHydrated = true
                isCloudSynced.value = true
                return@addSnapshotListener
            }

            // DOCUMENT EXISTS: READ REAL CLOUD DATA (NEVER OVERWRITE WITH ZERO)
            isCloudSynced.value = true
            isCloudHydrated = true

            var usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: 0.0
            var grid = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: 0.0
            val isAdmin = snapshot.getBoolean("isAdmin") ?: (cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey))
            val isFreeMining = snapshot.getBoolean("isFreeMiningActive") ?: false
            val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong() ?: 0L
            val sessionStart = (snapshot.get("freeMiningStartTime") as? Number)?.toLong() ?: 0L
            val lastSync = (snapshot.get("lastSyncTimestamp") as? Number)?.toLong() ?: now

            val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
                ?: emptyList()

            // Calculate Offline Catch-Up for Free Mining
            val isMiningRunning = isFreeMining && (now < sessionEnd)
            if (isFreeMining && lastSync < sessionEnd) {
                val effectiveEnd = Math.min(now, sessionEnd)
                val offlineSec = Math.max(0L, (effectiveEnd - lastSync) / 1000)
                val minedDelta = offlineSec * ((2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0))
                grid += minedDelta
            }

            // Calculate Offline Yield for Hardware Rigs
            rawNodes.forEach { rig ->
                val dailyYield = (rig["dailyYieldUsdt"] as? Number)?.toDouble() ?: 0.0
                val deployedAt = (rig["deployedTimestamp"] as? Number)?.toLong() ?: now
                val days = (rig["totalDays"] as? Number)?.toInt() ?: 200
                val expiry = deployedAt + (days.toLong() * 86400000L)
                if (lastSync < expiry) {
                    val effectiveEnd = Math.min(now, expiry)
                    val elapsedDays = Math.max(0.0, (effectiveEnd - lastSync).toDouble() / 86400000.0)
                    usdt += (dailyYield * elapsedDays)
                }
            }

            // Sync States
            _minerBalance.value = usdt
            _gridBalance.value = grid
            _isMiningActive.value = isMiningRunning
            _freeMiningEndTime.value = sessionEnd
            _deployedRigs.value = rawNodes
            _deployedNodesCount.value = rawNodes.size

            val userRigsList = rawNodes.mapNotNull { map ->
                try {
                    val id = map["id"] as? String ?: map["nodeId"] as? String ?: UUID.randomUUID().toString()
                    val name = map["name"] as? String ?: "Hardware Node"
                    val cost = (map["costUsdt"] as? Number)?.toDouble() ?: (map["priceUsdt"] as? Number)?.toDouble() ?: 1000.0
                    val hashrate = (map["hashrateGh"] as? Number)?.toDouble() ?: 400.0
                    val totalDays = (map["totalDays"] as? Number)?.toInt() ?: 200
                    val deployedAt = (map["deployedTimestamp"] as? Number)?.toLong() ?: now

                    UserRig(
                        id = id,
                        catalogId = "rig-custom",
                        name = name,
                        priceUsdt = cost,
                        hashrateGh = hashrate,
                        purchaseTimestamp = deployedAt,
                        durationDays = totalDays,
                        status = if (now >= deployedAt + totalDays * 86400000L) RigStatus.COMPLETED else RigStatus.ACTIVE,
                        totalReceivedUsdt = (map["totalReceivedUsdt"] as? Number)?.toDouble() ?: 0.0,
                        thisMonthEarnedUsdt = (map["thisMonthEarnedUsdt"] as? Number)?.toDouble() ?: 0.0,
                        lastYieldCalculatedTimestamp = now
                    )
                } catch (e: Exception) { null }
            }

            _userState.value = _userState.value.copy(
                uid = cleanKey,
                secretKey = cleanKey,
                minerBalanceUsdt = usdt,
                gridBalance = grid,
                isFreeMiningActive = isMiningRunning,
                freeMiningSessionStart = sessionStart,
                freeMiningSessionEnd = sessionEnd,
                userRigs = userRigsList,
                isAdmin = isAdmin,
                role = if (isAdmin) "superadmin" else "user",
                isAuthenticated = true
            )

            // Cache locally
            prefs.edit()
                .putFloat("miner_balance", usdt.toFloat())
                .putFloat("grid_balance", grid.toFloat())
                .putFloat("baseline_grid_balance", grid.toFloat())
                .putBoolean("free_session_active", isMiningRunning)
                .putLong("free_session_end", sessionEnd)
                .putLong("free_session_start", sessionStart)
                .apply()
        }
    }

    // ==========================================
    // MINING START (24H ATOMIC EXTENSION)
    // ==========================================
    fun startFreeMiningSession() {
        val current = _userState.value
        val activeKey = current.secretKey.ifBlank { getActiveKey() ?: "HG-ADM9-7788-5544-0001" }
        val cleanKey = SecretKeyUtils.normalizeSecretKey(activeKey)
        val now = System.currentTimeMillis()
        val end = now + 86400000L // 24 Hours

        _isMiningActive.value = true
        _freeMiningEndTime.value = end

        _userState.value = current.copy(
            isFreeMiningActive = true,
            freeMiningSessionStart = now,
            freeMiningSessionEnd = end,
            lastYieldTickTimestamp = now
        )

        prefs.edit()
            .putBoolean("free_session_active", true)
            .putLong("free_session_start", now)
            .putLong("free_session_end", end)
            .putFloat("baseline_grid_balance", current.gridBalance.toFloat())
            .apply()

        firestore.collection("users").document(cleanKey).update(
            mapOf(
                "isFreeMiningActive" to true,
                "freeMiningStartTime" to now,
                "freeMiningEndTime" to end,
                "lastSyncTimestamp" to now
            )
        ).addOnFailureListener {
            firestore.collection("users").document(cleanKey).set(
                mapOf(
                    "isFreeMiningActive" to true,
                    "freeMiningStartTime" to now,
                    "freeMiningEndTime" to end,
                    "lastSyncTimestamp" to now
                ), SetOptions.merge()
            )
        }
    }

    fun startFreeMiningCore(secretKey: String) {
        startFreeMiningSession()
    }

    // ==========================================
    // HARDWARE RIG ATOMIC DEPLOYMENT
    // ==========================================
    fun deployHardwareRig(secretKey: String, rig: HardwareNode, onSuccess: () -> Unit = {}) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        val docRef = firestore.collection("users").document(cleanKey)
        val now = System.currentTimeMillis()
        val rigMap = mapOf(
            "id" to rig.id,
            "nodeId" to rig.id,
            "name" to rig.name,
            "hashrateGh" to rig.hashrateGh,
            "costUsdt" to rig.costUsdt,
            "dailyYieldUsdt" to rig.dailyYieldUsdt,
            "deployedTimestamp" to now,
            "totalDays" to rig.totalDays,
            "status" to "ACTIVE"
        )

        docRef.update(
            "minerBalanceUsdt", FieldValue.increment(-rig.costUsdt),
            "hardwareNodes", FieldValue.arrayUnion(rigMap),
            "lastSyncTimestamp", now
        ).addOnSuccessListener { onSuccess() }
    }

    fun buyRig(catalogItem: RigCatalogItem, useWalletBalance: Boolean = true): Result<UserRig> {
        val current = _userState.value
        val now = System.currentTimeMillis()

        if (useWalletBalance && current.minerBalanceUsdt < catalogItem.priceUsdt) {
            return Result.failure(Exception("Insufficient Miner Balance ($${String.format("%.2f", current.minerBalanceUsdt)} USDT)."))
        }

        val node = HardwareNode(
            id = UUID.randomUUID().toString().take(6),
            name = "${catalogItem.name} #${Random.nextInt(100, 999)}",
            hashrateGh = catalogItem.hashrateGh,
            costUsdt = catalogItem.priceUsdt,
            dailyYieldUsdt = (catalogItem.priceUsdt * 0.15) / 30.0,
            deployedTimestamp = now,
            totalDays = catalogItem.durationDays
        )

        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: "" })
        deployHardwareRig(cleanKey, node)

        val newRig = UserRig(
            id = node.id,
            catalogId = catalogItem.id,
            name = node.name,
            priceUsdt = node.costUsdt,
            hashrateGh = node.hashrateGh,
            purchaseTimestamp = now,
            durationDays = node.totalDays,
            status = RigStatus.ACTIVE,
            totalReceivedUsdt = 0.0,
            thisMonthEarnedUsdt = 0.0,
            lastYieldCalculatedTimestamp = now
        )

        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt - catalogItem.priceUsdt,
            userRigs = listOf(newRig) + current.userRigs
        )
        return Result.success(newRig)
    }

    // ==========================================
    // LUCKY WHEEL REWARD (INCREMENT DIRECTLY)
    // ==========================================
    fun claimWheelReward(key: String, rewardGrid: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        _gridBalance.value += rewardGrid
        val current = _userState.value
        val newBalance = current.gridBalance + rewardGrid
        _userState.value = current.copy(gridBalance = newBalance)

        prefs.edit().putFloat("grid_balance", newBalance.toFloat()).apply()
        prefs.edit().putFloat("baseline_grid_balance", newBalance.toFloat()).apply()

        firestore.collection("users").document(cleanKey).update(
            "gridBalance", FieldValue.increment(rewardGrid),
            "lastSyncTimestamp", System.currentTimeMillis()
        )
    }

    fun executeLuckySpin(sector: SpinSector): SpinHistoryRecord {
        val current = _userState.value
        val now = System.currentTimeMillis()
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: "" })

        var newMinerBal = current.minerBalanceUsdt
        var newGridBal = current.gridBalance

        when (sector.type) {
            SpinRewardType.GRID_TOKENS -> {
                newGridBal += sector.value
                claimWheelReward(cleanKey, sector.value)
            }
            SpinRewardType.USDT -> {
                newMinerBal += sector.value
                firestore.collection("users").document(cleanKey).update(
                    "minerBalanceUsdt", FieldValue.increment(sector.value),
                    "lastSyncTimestamp", now
                )
            }
            else -> {}
        }

        val record = SpinHistoryRecord(
            id = "spin-${UUID.randomUUID().toString().take(6)}",
            rewardTitle = sector.title,
            rewardSubtitle = sector.subtitle,
            timestamp = now,
            rewardType = sector.type,
            value = sector.value
        )

        _userState.value = current.copy(
            minerBalanceUsdt = newMinerBal,
            gridBalance = newGridBal,
            lastDailySpinTimestamp = now,
            spinHistory = listOf(record) + current.spinHistory
        )
        return record
    }

    // ==========================================
    // INSTANT LOCAL LOGIN (NO OVERWRITING ZEROES)
    // ==========================================
    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        setActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)

        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = if (isAdminKey) 3000.0 else prefs.getFloat("miner_balance", 0f).toDouble(),
            gridBalance = prefs.getFloat("grid_balance", 0f).toDouble(),
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        _userState.value = instantState
        // Bind Firestore listener immediately (Hydration happens inside listener safely)
        bindUserSession(cleanKey)
        return instantState
    }

    fun restoreAccount(key: String, onComplete: (Boolean) -> Unit) {
        loginWithKeyInstant(key)
        onComplete(true)
    }

    fun initializeOrRestoreUser(key: String, onComplete: (Boolean) -> Unit) {
        loginWithKeyInstant(key)
        onComplete(true)
    }

    suspend fun restoreAccountWithSecretKey(secretKey: String): Result<UserMiningState> {
        val state = loginWithKeyInstant(secretKey)
        return Result.success(state)
    }

    fun adminSetBalance(targetKey: String, usdt: Double, grid: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(targetKey)
        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to usdt,
                "gridBalance" to grid,
                "lastSyncTimestamp" to System.currentTimeMillis()
            ), SetOptions.merge()
        )
    }

    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: "HG-ADM9-7788-5544-0001")
        adminSetBalance(cleanKey, newUsdt, newGrid)
        _minerBalance.value = newUsdt
        _gridBalance.value = newGrid
        _userState.value = _userState.value.copy(minerBalanceUsdt = newUsdt, gridBalance = newGrid)
    }

    init {
        startBackgroundEngine()
        attachSystemSettingsListener()
        val savedKey = getActiveKey()
        if (!savedKey.isNullOrBlank()) {
            bindUserSession(savedKey)
        }
    }

    private fun startBackgroundEngine() {
        scope.launch {
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

        val sessionEnd = _freeMiningEndTime.value
        val isFreeActive = _isMiningActive.value && (now < sessionEnd)

        if (_isMiningActive.value && !isFreeActive) {
            _isMiningActive.value = false
            NotificationHelper.sendMiningSessionEndedNotification(appContext)
        }

        var newGridBalance = current.gridBalance
        if (isFreeActive) {
            val tokensPerSec = (2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0)
            newGridBalance += tokensPerSec
            _gridBalance.value = newGridBalance
        }

        val elapsedSec = ((now - lastYieldTickTime) / 1000.0).coerceAtLeast(0.0)
        lastYieldTickTime = now

        var additionalUsdtYield = 0.0
        val updatedRigs = current.userRigs.map { rig ->
            if (rig.status == RigStatus.ACTIVE) {
                val dailyYield = (rig.priceUsdt * 0.15) / 30.0
                val yieldPerSec = dailyYield / 86400.0
                val rigYield = yieldPerSec * elapsedSec
                additionalUsdtYield += rigYield
                rig.copy(
                    totalReceivedUsdt = rig.totalReceivedUsdt + rigYield,
                    thisMonthEarnedUsdt = rig.thisMonthEarnedUsdt + rigYield,
                    lastYieldCalculatedTimestamp = now
                )
            } else rig
        }

        val newMinerBalance = current.minerBalanceUsdt + additionalUsdtYield
        _minerBalance.value = newMinerBalance

        _userState.value = current.copy(
            isFreeMiningActive = isFreeActive,
            gridBalance = newGridBalance,
            minerBalanceUsdt = newMinerBalance,
            userRigs = updatedRigs
        )

        // Periodic safe Firestore sync every 20 seconds
        if (now - lastCloudSyncTime > 20000 && isCloudHydrated) {
            lastCloudSyncTime = now
            syncToCloud()
        }
    }

    private fun syncToCloud() {
        val state = _userState.value
        if (!state.isAuthenticated || state.secretKey.isBlank() || !isCloudHydrated) return

        prefs.edit()
            .putFloat("grid_balance", state.gridBalance.toFloat())
            .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
            .putBoolean("free_session_active", state.isFreeMiningActive)
            .putLong("free_session_end", state.freeMiningSessionEnd)
            .apply()

        firestore.collection("users").document(state.secretKey).update(
            mapOf(
                "minerBalanceUsdt" to state.minerBalanceUsdt,
                "gridBalance" to state.gridBalance,
                "isFreeMiningActive" to state.isFreeMiningActive,
                "freeMiningEndTime" to state.freeMiningSessionEnd,
                "lastSyncTimestamp" to System.currentTimeMillis()
            )
        )
    }

    fun computeAccruedGridBalance(now: Long = System.currentTimeMillis()): Double = _gridBalance.value

    fun saveGridBalanceOnPause(computedGrid: Double) {
        val now = System.currentTimeMillis()
        val key = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: "")
        if (key.isNotBlank()) {
            firestore.collection("users").document(key).update(
                mapOf("gridBalance" to computedGrid, "lastSyncTimestamp" to now)
            )
        }
    }

    fun immediateLogout() {
        clearActiveKey()
        _userState.value = UserMiningState(
            uid = "", secretKey = "", email = "", nodeId = "", isAuthenticated = false, isAdmin = false, role = "user"
        )
    }

    suspend fun logout() = withContext(Dispatchers.Main) {
        immediateLogout()
    }

    fun setLocalBalance(minerUsdt: Double, grid: Double) {
        _minerBalance.value = minerUsdt
        _gridBalance.value = grid
        _userState.value = _userState.value.copy(minerBalanceUsdt = minerUsdt, gridBalance = grid)
    }

    fun setInstantUserState(state: UserMiningState) {
        _userState.value = state
    }

    fun setAppLocked(locked: Boolean) { _userState.value = _userState.value.copy(isAppLocked = locked) }
    fun markSecretKeyBackedUp() { securityPreferences.setSecretKeyBackedUp(true); _userState.value = _userState.value.copy(isKeyBackedUp = true) }
    fun setPin(pin: String): Boolean = securityPreferences.setPin(pin)
    fun verifyPin(pin: String): Boolean = securityPreferences.verifyPin(pin)
    fun setBiometricEnabled(enabled: Boolean) { securityPreferences.setBiometricEnabled(enabled); _userState.value = _userState.value.copy(isBiometricEnabled = enabled) }
    fun isPinSet(): Boolean = securityPreferences.isPinSet()
    fun isBiometricEnabled(): Boolean = securityPreferences.isBiometricEnabled()
    fun getSecretKey(): String = securityPreferences.getActiveUserKey() ?: _userState.value.secretKey

    private fun attachSystemSettingsListener() {
        firebaseManager.listenToSystemSettings { newPrice ->
            if (newPrice > 0.0) {
                _gridPriceUsd.value = newPrice
                prefs.edit().putFloat("grid_price_usd", newPrice.toFloat()).apply()
            }
        }
    }

    private fun loadInitialState(): UserMiningState {
        val now = System.currentTimeMillis()
        val key = securityPreferences.getActiveUserKey() ?: ""
        val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(key) || key.startsWith("HG-ADM9")

        return UserMiningState(
            uid = key,
            secretKey = key,
            email = if (isMasterAdmin) "admin@hashgrid.pro" else "miner_${key.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isMasterAdmin) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${key.takeLast(4)}",
            minerBalanceUsdt = prefs.getFloat("miner_balance", if (isMasterAdmin) 3000f else 0f).toDouble(),
            gridBalance = prefs.getFloat("grid_balance", 0f).toDouble(),
            baseFreeHashrateGh = 2.0,
            isAuthenticated = key.isNotBlank(),
            isAdmin = isMasterAdmin,
            role = if (isMasterAdmin) "superadmin" else "user",
            userRigs = emptyList(),
            createdAt = now
        )
    }

    suspend fun createNewAccount(): Result<UserMiningState> = withContext(Dispatchers.IO) {
        val newKey = SecretKeyUtils.generateSecretKey()
        val now = System.currentTimeMillis()
        val newState = UserMiningState(
            uid = newKey, secretKey = newKey,
            email = "miner_${newKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = "NODE-WEB3-#${newKey.takeLast(4)}",
            minerBalanceUsdt = 0.0, gridBalance = 0.0, baseFreeHashrateGh = 2.0,
            referralCode = newKey, isAuthenticated = true
        )
        loginWithKeyInstant(newKey)
        Result.success(newState)
    }

    fun syncAndCatchUpOfflineGrowth(secretKey: String) { bindUserSession(secretKey) }
    fun restoreSessionAsync(key: String) { bindUserSession(key) }
    fun attachUserDocumentRealTimeListener(secretKey: String) { bindUserSession(secretKey) }
    fun loadStateIntoApp(usdt: Double, grid: Double, isMining: Boolean, end: Long, rigs: List<Map<String, Any>>) {}
    fun applyOfflineCatchUpYield(state: UserMiningState, now: Long = System.currentTimeMillis()): UserMiningState = state
    suspend fun updateGridPrice(newPrice: Double): Boolean = true
    suspend fun syncMinedTokensToFirestore(): Boolean = true
    fun setUserOnline(isOnline: Boolean) {}
    fun depositFunds(amountUsdt: Double, network: String, txHash: String = "") {}
    fun requestWithdrawal(amountUsdt: Double, address: String, network: String): Result<TransactionItem> = Result.failure(Exception())
    fun submitMicroTask(platform: TaskPlatform, initialViews: Int, finalViews: Int, notes: String) {}
    fun submitVideoPromo(platform: TaskPlatform, url: String, channel: String) {}
    fun simulateDownlinePurchase() {}
    fun simulateNewReferral() {}
    fun approvePendingTasksSimulation() {}
    fun adminApproveWithdrawal(txId: String) {}
    fun adminRejectWithdrawal(txId: String) {}
    fun adminCreateTestPendingWithdrawal(amount: Double = 25.0, address: String = "", network: String = "") {}
    suspend fun createNowPaymentsDeposit(priceAmountUsd: Double, payCurrency: String): Result<NowPaymentResponse> = Result.failure(Exception())
    suspend fun createNowPaymentsRigPurchase(rigItem: RigCatalogItem, payCurrency: String): Result<NowPaymentResponse> = Result.failure(Exception())
    suspend fun verifyAndSyncPaymentStatus(paymentId: String): Result<Pair<NowPaymentResponse, Boolean>> = Result.failure(Exception())
    fun simulateInstantPaymentConfirmation(paymentId: String): Boolean = true
}
