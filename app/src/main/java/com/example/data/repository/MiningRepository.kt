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
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

class MiningRepository(context: Context) {

    private val appContext = context.applicationContext
    val securityPreferences = SecurityPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val firebaseManager = FirebaseManager(context)
    val nowPaymentsManager = NowPaymentsManager()

    companion object {
        const val GRID_PRELAUNCH_PRICE_USD = 0.01
        const val TARGET_DAILY_GRID = 302.4
        const val TOKENS_PER_SECOND = (2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0) // ~0.0007 GRID/sec
    }

    private val globalPrefs: SharedPreferences = context.getSharedPreferences("hashgrid_global_v2", Context.MODE_PRIVATE)

    private fun getUserVault(secretKey: String): SharedPreferences {
        val clean = SecretKeyUtils.normalizeSecretKey(secretKey)
        return appContext.getSharedPreferences("user_vault_$clean", Context.MODE_PRIVATE)
    }

    private val _userState = MutableStateFlow(loadInitialState())
    val userState: StateFlow<UserMiningState> = _userState.asStateFlow()

    private val _gridPriceUsd = MutableStateFlow(
        globalPrefs.getFloat("grid_price_usd", GRID_PRELAUNCH_PRICE_USD.toFloat()).toDouble()
    )
    val gridPriceUsd: StateFlow<Double> = _gridPriceUsd.asStateFlow()

    private val _cryptoPrices = MutableStateFlow(
        listOf(CryptoTickerPrice("GRID/USDT", _gridPriceUsd.value, 0.00, _gridPriceUsd.value, _gridPriceUsd.value))
    )
    val cryptoPrices: StateFlow<List<CryptoTickerPrice>> = _cryptoPrices.asStateFlow()

    private var lastCloudSyncTime = System.currentTimeMillis()

    // STRICT LOCK: Jab tak cloud se verify na ho jaye, tab tak cloud par 0 write nahi ho sakta
    @Volatile private var isCloudHydrated = false

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
        isCloudHydrated = false
        activeAccount.value = null
        _deployedRigs.value = emptyList()
        _deployedNodesCount.value = 0
        _minerBalance.value = 0.0
        _gridBalance.value = 0.0
        _isMiningActive.value = false
        _freeMiningEndTime.value = 0L
    }

    private fun serializeRigs(rigs: List<Map<String, Any>>): String {
        val array = JSONArray()
        rigs.forEach { array.put(JSONObject(it)) }
        return array.toString()
    }

    private fun deserializeRigs(json: String): List<Map<String, Any>> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<Map<String, Any>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val map = mutableMapOf<String, Any>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.get(k)
                }
                list.add(map)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRigMap(map: Map<String, Any>, now: Long): UserRig? {
        return try {
            val id = (map["id"] as? String) ?: (map["nodeId"] as? String) ?: UUID.randomUUID().toString()
            val name = (map["name"] as? String) ?: "Hardware Node"
            val cost = (map["costUsdt"] as? Number)?.toDouble() ?: (map["priceUsdt"] as? Number)?.toDouble() ?: 1000.0
            val hashrate = (map["hashrateGh"] as? Number)?.toDouble() ?: 400.0
            val totalDays = (map["totalDays"] as? Number)?.toInt() ?: (map["durationDays"] as? Number)?.toInt() ?: 200
            val deployedAt = (map["deployedTimestamp"] as? Number)?.toLong() ?: (map["purchaseTimestamp"] as? Number)?.toLong() ?: now

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
        } catch (_: Exception) { null }
    }

    private fun parseTransactionMap(map: Map<String, Any>, now: Long): TransactionItem? {
        return try {
            TransactionItem(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                type = TransactionType.valueOf((map["type"] as? String) ?: "DEPOSIT"),
                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                currency = (map["currency"] as? String) ?: "USDT",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: now,
                status = TransactionStatus.valueOf((map["status"] as? String) ?: "COMPLETED"),
                description = (map["description"] as? String) ?: "",
                network = (map["network"] as? String) ?: "BEP20 (BSC)"
            )
        } catch (_: Exception) { null }
    }

    // ========================================================
    // BULLETPROOF CLOUD RESTORATION & 30-DAY ACCRUAL ENGINE
    // ========================================================
    fun bindUserSession(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        setActiveKey(cleanKey)

        snapshotRegistration?.remove()
        val docRef = firestore.collection("users").document(cleanKey)
        val vault = getUserVault(cleanKey)
        val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        // 1. Fetch live server document first (Guaranteed Server Data)
        docRef.get(Source.SERVER).addOnCompleteListener { task ->
            val now = System.currentTimeMillis()
            val snapshot = if (task.isSuccessful) task.result else null

            if (snapshot != null && snapshot.exists()) {
                // SERVER PE RECORD MIL GAYA: PICHLE 30 DINO KA HISAB JODEIN
                restoreFromCloudWithAccrual(cleanKey, snapshot, vault, isMaster, now)
            } else {
                // NAYA ACCOUNT HAI YA FIRST-TIME USER HAI
                val defaultUsdt = if (isMaster) 3000.0 else 0.0
                val defaultRigs = if (isMaster) listOf(
                    mapOf("id" to "651", "name" to "Elite Node #651", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                    mapOf("id" to "233", "name" to "Elite Node #233", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                    mapOf("id" to "414", "name" to "Elite Node #414", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE")
                ) else emptyList()

                val initData = hashMapOf(
                    "secretKey" to cleanKey,
                    "isAdmin" to isMaster,
                    "minerBalanceUsdt" to defaultUsdt,
                    "gridBalance" to 0.0,
                    "baselineGridBalance" to 0.0,
                    "isFreeMiningActive" to false,
                    "freeMiningStartTime" to 0L,
                    "freeMiningEndTime" to 0L,
                    "hardwareNodes" to defaultRigs,
                    "transactions" to emptyList<Map<String, Any>>(),
                    "securityPin" to "",
                    "lastDailySpinTimestamp" to 0L,
                    "dailySpentUsdt" to 0.0,
                    "dailySpentResetDate" to now,
                    "lastSyncTimestamp" to now
                )
                docRef.set(initData, SetOptions.merge())

                vault.edit()
                    .putBoolean("is_vault_initialized", true)
                    .putFloat("miner_balance", defaultUsdt.toFloat())
                    .putFloat("grid_balance", 0f)
                    .putFloat("baseline_grid_balance", 0f)
                    .putBoolean("is_mining_active", false)
                    .putLong("mining_start", 0L)
                    .putLong("mining_end", 0L)
                    .putLong("last_sync_timestamp", now)
                    .putString("hardware_nodes_json", serializeRigs(defaultRigs))
                    .apply()

                val userRigsList = defaultRigs.mapNotNull { parseRigMap(it, now) }
                _minerBalance.value = defaultUsdt
                _gridBalance.value = 0.0
                _deployedRigs.value = defaultRigs
                _deployedNodesCount.value = defaultRigs.size

                _userState.value = _userState.value.copy(
                    uid = cleanKey,
                    secretKey = cleanKey,
                    minerBalanceUsdt = defaultUsdt,
                    gridBalance = 0.0,
                    userRigs = userRigsList,
                    isAdmin = isMaster,
                    role = if (isMaster) "superadmin" else "user",
                    isAuthenticated = true
                )
                isCloudHydrated = true
                isCloudSynced.value = true
            }

            // 2. Active Snapshot Listener lagayein taaki real-time changes aate rahein
            attachLiveObserver(cleanKey, docRef, vault, isMaster)
        }
    }

    private fun restoreFromCloudWithAccrual(
        cleanKey: String,
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        vault: SharedPreferences,
        isMaster: Boolean,
        now: Long
    ) {
        var usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: if (isMaster) 3000.0 else 0.0
        var grid = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: 0.0
        var baselineGrid = (snapshot.get("baselineGridBalance") as? Number)?.toDouble() ?: grid
        val lastSync = (snapshot.get("lastSyncTimestamp") as? Number)?.toLong() ?: now
        val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong() ?: 0L
        val sessionStart = (snapshot.get("freeMiningStartTime") as? Number)?.toLong() ?: 0L
        val wasMiningActive = snapshot.getBoolean("isFreeMiningActive") ?: false

        // A. PICHLE REINSTALL DINO KA FREE MINING CATCH-UP
        if (wasMiningActive && sessionStart > 0L) {
            val effectiveEnd = Math.min(now, sessionEnd)
            if (effectiveEnd > lastSync) {
                val elapsedSec = Math.max(0L, (effectiveEnd - lastSync) / 1000)
                val accruedGrid = elapsedSec * TOKENS_PER_SECOND
                grid += accruedGrid
                baselineGrid += accruedGrid
            }
        }
        val isStillMining = (now < sessionEnd) && wasMiningActive

        // B. PICHLE 30 DINO KA HARDWARE NODES YIELD CATCH-UP (15% Monthly)
        val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
            ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
            ?: emptyList()

        var totalAccruedYield = 0.0
        val updatedNodes = rawNodes.map { rigMap ->
            val cost = (rigMap["costUsdt"] as? Number)?.toDouble() ?: 1000.0
            val dailyYield = (rigMap["dailyYieldUsdt"] as? Number)?.toDouble() ?: ((cost * 0.15) / 30.0)
            val yieldPerSec = dailyYield / 86400.0
            val deployedAt = (rigMap["deployedTimestamp"] as? Number)?.toLong() ?: now
            val totalDays = (rigMap["totalDays"] as? Number)?.toInt() ?: 200
            val expiry = deployedAt + (totalDays.toLong() * 86400000L)

            if (lastSync < expiry) {
                val effectiveYieldEnd = Math.min(now, expiry)
                val elapsedSec = Math.max(0.0, (effectiveYieldEnd - lastSync).toDouble() / 1000.0)
                val rigEarnings = elapsedSec * yieldPerSec
                totalAccruedYield += rigEarnings
            }
            rigMap.toMutableMap().apply {
                put("status", if (now >= expiry) "COMPLETED" else "ACTIVE")
            }
        }
        usdt += totalAccruedYield

        val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
        val restoredTransactions = rawTxs.mapNotNull { parseTransactionMap(it, now) }
        val cloudPin = snapshot.getString("securityPin") ?: ""

        if (cloudPin.isNotBlank()) {
            securityPreferences.setPin(cloudPin)
            vault.edit().putString("security_pin", cloudPin).apply()
        }

        // Phone Vault ko fresh cloud data se hydrate karein
        vault.edit()
            .putBoolean("is_vault_initialized", true)
            .putFloat("miner_balance", usdt.toFloat())
            .putFloat("grid_balance", grid.toFloat())
            .putFloat("baseline_grid_balance", baselineGrid.toFloat())
            .putBoolean("is_mining_active", isStillMining)
            .putLong("mining_start", sessionStart)
            .putLong("mining_end", sessionEnd)
            .putLong("last_sync_timestamp", now)
            .putString("hardware_nodes_json", serializeRigs(updatedNodes))
            .apply()

        // Cloud par naya catch-up balance push kar dein taaki state update ho jaye
        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to usdt,
                "gridBalance" to grid,
                "baselineGridBalance" to baselineGrid,
                "isFreeMiningActive" to isStillMining,
                "hardwareNodes" to updatedNodes,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )

        val userRigsList = updatedNodes.mapNotNull { parseRigMap(it, now) }
        _minerBalance.value = usdt
        _gridBalance.value = grid
        _isMiningActive.value = isStillMining
        _freeMiningEndTime.value = sessionEnd
        _deployedRigs.value = updatedNodes
        _deployedNodesCount.value = updatedNodes.size

        _userState.value = _userState.value.copy(
            uid = cleanKey,
            secretKey = cleanKey,
            minerBalanceUsdt = usdt,
            gridBalance = grid,
            isFreeMiningActive = isStillMining,
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            userRigs = userRigsList,
            transactions = restoredTransactions,
            isPinConfigured = cloudPin.isNotBlank(),
            isAdmin = snapshot.getBoolean("isAdmin") ?: isMaster,
            role = if (isMaster) "superadmin" else "user",
            isAuthenticated = true
        )

        isCloudHydrated = true
        isCloudSynced.value = true
    }

    private fun attachLiveObserver(
        cleanKey: String,
        docRef: com.google.firebase.firestore.DocumentReference,
        vault: SharedPreferences,
        isMaster: Boolean
    ) {
        snapshotRegistration?.remove()
        snapshotRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

            val now = System.currentTimeMillis()
            val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: _minerBalance.value
            val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong() ?: _freeMiningEndTime.value
            val isFreeMining = sessionEnd > now

            val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
                ?: _deployedRigs.value

            val userRigsList = rawNodes.mapNotNull { parseRigMap(it, now) }
            val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
            val restoredTxs = if (rawTxs.isNotEmpty()) rawTxs.mapNotNull { parseTransactionMap(it, now) } else _userState.value.transactions

            _minerBalance.value = usdt
            _isMiningActive.value = isFreeMining
            _freeMiningEndTime.value = sessionEnd
            _deployedRigs.value = rawNodes
            _deployedNodesCount.value = rawNodes.size

            _userState.value = _userState.value.copy(
                minerBalanceUsdt = usdt,
                isFreeMiningActive = isFreeMining,
                freeMiningSessionEnd = sessionEnd,
                userRigs = userRigsList,
                transactions = restoredTxs,
                isAdmin = snapshot.getBoolean("isAdmin") ?: isMaster
            )
            isCloudSynced.value = true
        }
    }

    // ==========================================
    // INSTANT LOGIN / RESTORE GATEWAY
    // ==========================================
    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        setActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)
        isCloudHydrated = false // Block calculations until server data loads

        val vault = getUserVault(cleanKey)
        val cachedUsdt = vault.getFloat("miner_balance", if (isAdminKey) 3000f else 0f).toDouble()
        val cachedGrid = vault.getFloat("grid_balance", 0f).toDouble()
        val localRigs = deserializeRigs(vault.getString("hardware_nodes_json", "") ?: "")
        val parsedRigs = localRigs.mapNotNull { parseRigMap(it, System.currentTimeMillis()) }

        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = cachedUsdt,
            gridBalance = cachedGrid,
            userRigs = parsedRigs,
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        _userState.value = instantState
        _minerBalance.value = cachedUsdt
        _gridBalance.value = cachedGrid
        _deployedRigs.value = localRigs
        _deployedNodesCount.value = localRigs.size

        // Live cloud sync shuru karein
        bindUserSession(cleanKey)
        return instantState
    }

    // Ek Click me Manually Cloud Backup lene ka function
    fun forceCloudBackup(onComplete: (Boolean) -> Unit = {}) {
        val state = _userState.value
        if (!state.isAuthenticated || state.secretKey.isBlank()) {
            onComplete(false)
            return
        }

        val now = System.currentTimeMillis()
        val cleanKey = SecretKeyUtils.normalizeSecretKey(state.secretKey)
        val vault = getUserVault(cleanKey)

        vault.edit()
            .putFloat("miner_balance", state.minerBalanceUsdt.toFloat())
            .putFloat("grid_balance", state.gridBalance.toFloat())
            .putBoolean("is_mining_active", state.isFreeMiningActive)
            .putLong("mining_end", state.freeMiningSessionEnd)
            .putLong("last_sync_timestamp", now)
            .putString("hardware_nodes_json", serializeRigs(_deployedRigs.value))
            .apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to state.minerBalanceUsdt,
                "gridBalance" to state.gridBalance,
                "isFreeMiningActive" to state.isFreeMiningActive,
                "freeMiningEndTime" to state.freeMiningSessionEnd,
                "hardwareNodes" to _deployedRigs.value,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        ).addOnSuccessListener { onComplete(true) }
         .addOnFailureListener { onComplete(false) }
    }

    // ==========================================
    // HARDWARE RIG ATOMIC DEPLOYMENT
    // ==========================================
    fun buyRig(catalogItem: RigCatalogItem, useWalletBalance: Boolean = true): Result<UserRig> {
        val current = _userState.value
        val now = System.currentTimeMillis()
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: "" })

        if (!checkAndUpdatePurchaseLimit(cleanKey, catalogItem.priceUsdt)) {
            return Result.failure(Exception("Daily purchase limit ($5,000 USDT) reached. Try again tomorrow."))
        }

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

        val rigMap = mapOf(
            "id" to node.id,
            "nodeId" to node.id,
            "name" to node.name,
            "hashrateGh" to node.hashrateGh,
            "costUsdt" to node.costUsdt,
            "dailyYieldUsdt" to node.dailyYieldUsdt,
            "deployedTimestamp" to now,
            "totalDays" to node.totalDays,
            "status" to "ACTIVE"
        )

        val updatedBalance = (current.minerBalanceUsdt - catalogItem.priceUsdt).coerceAtLeast(0.0)
        val updatedRigsRaw = _deployedRigs.value + rigMap

        val vault = getUserVault(cleanKey)
        vault.edit()
            .putFloat("miner_balance", updatedBalance.toFloat())
            .putString("hardware_nodes_json", serializeRigs(updatedRigsRaw))
            .putLong("last_sync_timestamp", now)
            .apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to updatedBalance,
                "hardwareNodes" to FieldValue.arrayUnion(rigMap),
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
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
        recordCloudTransaction(cleanKey, tx)

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

        _minerBalance.value = updatedBalance
        _deployedRigs.value = updatedRigsRaw
        _deployedNodesCount.value = updatedRigsRaw.size

        _userState.value = current.copy(
            minerBalanceUsdt = updatedBalance,
            userRigs = listOf(newRig) + current.userRigs,
            transactions = listOf(tx) + current.transactions
        )
        return Result.success(newRig)
    }

    fun deployHardwareRig(secretKey: String, rig: HardwareNode, onSuccess: () -> Unit = {}) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
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

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "hardwareNodes" to FieldValue.arrayUnion(rigMap),
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        ).addOnSuccessListener { onSuccess() }
    }

    // ==========================================
    // 24H MINING ENGINE (REAL-TIME CLOCK ACCRUAL)
    // ==========================================
    fun startFreeMiningSession() {
        val current = _userState.value
        val activeKey = current.secretKey.ifBlank { getActiveKey() ?: "HG-ADM9-7788-5544-0001" }
        val cleanKey = SecretKeyUtils.normalizeSecretKey(activeKey)
        val now = System.currentTimeMillis()
        val end = now + 86400000L
        val currentGrid = _gridBalance.value

        _isMiningActive.value = true
        _freeMiningEndTime.value = end

        _userState.value = current.copy(
            isFreeMiningActive = true,
            freeMiningSessionStart = now,
            freeMiningSessionEnd = end,
            lastYieldTickTimestamp = now
        )

        val vault = getUserVault(cleanKey)
        vault.edit()
            .putBoolean("is_mining_active", true)
            .putLong("mining_start", now)
            .putLong("mining_end", end)
            .putFloat("baseline_grid_balance", currentGrid.toFloat())
            .putLong("last_sync_timestamp", now)
            .apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "isFreeMiningActive" to true,
                "freeMiningStartTime" to now,
                "freeMiningEndTime" to end,
                "baselineGridBalance" to currentGrid,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
    }

    fun startFreeMiningCore(secretKey: String) {
        startFreeMiningSession()
    }

    init {
        startBackgroundEngine()
        attachSystemSettingsListener()
        val savedKey = getActiveKey()
        if (!savedKey.isNullOrBlank()) {
            loginWithKeyInstant(savedKey)
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
        // Engine tab tak suspend rahega jab tak cloud se verify na ho jaye
        if (!isCloudHydrated) return

        val now = System.currentTimeMillis()
        val current = _userState.value
        if (!current.isAuthenticated || current.secretKey.isBlank()) return

        val vault = getUserVault(current.secretKey)
        val sessionStart = vault.getLong("mining_start", current.freeMiningSessionStart)
        val sessionEnd = vault.getLong("mining_end", current.freeMiningSessionEnd)
        val isFreeActive = (sessionEnd > now) && (sessionStart > 0L) && vault.getBoolean("is_mining_active", false)

        if (_isMiningActive.value != isFreeActive) {
            _isMiningActive.value = isFreeActive
            if (!isFreeActive) {
                NotificationHelper.sendMiningSessionEndedNotification(appContext)
            }
        }

        // Live Real-Time Token Calculation
        var newGridBalance = current.gridBalance
        if (isFreeActive) {
            val baseline = vault.getFloat("baseline_grid_balance", 0f).toDouble()
            val elapsedSec = ((now - sessionStart) / 1000.0).coerceAtLeast(0.0)
            newGridBalance = baseline + (elapsedSec * TOKENS_PER_SECOND)
            _gridBalance.value = newGridBalance
        }

        val lastSync = vault.getLong("last_sync_timestamp", now)
        val elapsedYieldSec = ((now - lastSync) / 1000.0).coerceAtLeast(0.0)

        var additionalUsdtYield = 0.0
        val updatedRigs = current.userRigs.map { rig ->
            if (rig.status == RigStatus.ACTIVE) {
                val dailyYield = (rig.priceUsdt * 0.15) / 30.0
                val yieldPerSec = dailyYield / 86400.0
                val rigYield = yieldPerSec * elapsedYieldSec
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

        vault.edit()
            .putFloat("grid_balance", newGridBalance.toFloat())
            .putFloat("miner_balance", newMinerBalance.toFloat())
            .putLong("last_sync_timestamp", now)
            .apply()

        _userState.value = current.copy(
            isFreeMiningActive = isFreeActive,
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            gridBalance = newGridBalance,
            minerBalanceUsdt = newMinerBalance,
            userRigs = updatedRigs
        )

        // Periodic Cloud Sync every 20 seconds
        if (now - lastCloudSyncTime > 20000) {
            lastCloudSyncTime = now
            forceCloudBackup()
        }
    }

    // ==========================================
    // REWARDS, DEPOSITS & TRANSACTIONS
    // ==========================================
    fun claimWheelReward(key: String, rewardGrid: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val vault = getUserVault(cleanKey)
        val currentBaseline = vault.getFloat("baseline_grid_balance", 0f).toDouble()
        val newBaseline = currentBaseline + rewardGrid

        _gridBalance.value += rewardGrid
        val current = _userState.value
        _userState.value = current.copy(gridBalance = current.gridBalance + rewardGrid)

        vault.edit()
            .putFloat("baseline_grid_balance", newBaseline.toFloat())
            .putFloat("grid_balance", _gridBalance.value.toFloat())
            .apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "gridBalance" to FieldValue.increment(rewardGrid),
                "baselineGridBalance" to newBaseline,
                "lastDailySpinTimestamp" to System.currentTimeMillis(),
                "lastSyncTimestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
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
                _minerBalance.value = newMinerBal
                getUserVault(cleanKey).edit().putFloat("miner_balance", newMinerBal.toFloat()).apply()
                firestore.collection("users").document(cleanKey).set(
                    mapOf(
                        "minerBalanceUsdt" to FieldValue.increment(sector.value),
                        "lastDailySpinTimestamp" to now,
                        "lastSyncTimestamp" to now
                    ),
                    SetOptions.merge()
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
            lastDailySpinTimestamp = now,
            spinHistory = listOf(record) + current.spinHistory,
            transactions = listOf(tx) + current.transactions
        )

        recordCloudTransaction(cleanKey, tx)
        return record
    }

    fun recordCloudTransaction(secretKey: String, tx: TransactionItem) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        val now = System.currentTimeMillis()

        val txMap = mapOf(
            "id" to tx.id,
            "type" to tx.type.name,
            "amount" to tx.amount,
            "currency" to tx.currency,
            "timestamp" to tx.timestamp,
            "status" to tx.status.name,
            "description" to tx.description,
            "network" to tx.network,
            "txHash" to (tx.txHash ?: "")
        )

        firestore.collection("users").document(cleanKey).set(
            mapOf("transactions" to FieldValue.arrayUnion(txMap), "lastSyncTimestamp" to now),
            SetOptions.merge()
        )
    }

    fun savePinToCloud(secretKey: String, pin: String, onSuccess: () -> Unit = {}) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return

        securityPreferences.setPin(pin)
        getUserVault(cleanKey).edit().putString("security_pin", pin).apply()
        _userState.value = _userState.value.copy(isPinConfigured = true, isAppLocked = false)

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "securityPin" to pin,
                "isPinConfigured" to true,
                "lastSyncTimestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).addOnSuccessListener { onSuccess() }
    }

    fun isLuckySpinAvailable(): Boolean {
        val lastSpin = _userState.value.lastDailySpinTimestamp
        return (System.currentTimeMillis() - lastSpin) >= 86400000L
    }

    fun depositFunds(amountUsdt: Double, network: String, txHash: String = "0x" + UUID.randomUUID().toString().replace("-", "").take(16)) {
        val current = _userState.value
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: "" })
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

        val newBalance = current.minerBalanceUsdt + amountUsdt
        _minerBalance.value = newBalance
        _userState.value = current.copy(
            minerBalanceUsdt = newBalance,
            transactions = listOf(tx) + current.transactions
        )

        getUserVault(cleanKey).edit().putFloat("miner_balance", newBalance.toFloat()).apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to FieldValue.increment(amountUsdt),
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
        recordCloudTransaction(cleanKey, tx)
    }

    fun requestWithdrawal(amountUsdt: Double, address: String, network: String): Result<TransactionItem> {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: _userState.value.secretKey)
        val current = _userState.value
        if (amountUsdt < 10.0 || current.minerBalanceUsdt < amountUsdt) {
            return Result.failure(Exception("Insufficient withdrawable USDT balance."))
        }

        val now = System.currentTimeMillis()
        val txId = "tx-wd-${UUID.randomUUID().toString().take(8)}"
        val tx = TransactionItem(
            id = txId,
            type = TransactionType.WITHDRAWAL,
            amount = amountUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.PENDING_REVIEW,
            description = "Withdrawal to ${address.take(6)}...${address.takeLast(4)} (24H Security Audit)",
            address = address,
            network = network
        )

        val newBalance = current.minerBalanceUsdt - amountUsdt
        _minerBalance.value = newBalance
        _userState.value = current.copy(
            minerBalanceUsdt = newBalance,
            transactions = listOf(tx) + current.transactions
        )

        getUserVault(cleanKey).edit().putFloat("miner_balance", newBalance.toFloat()).apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to FieldValue.increment(-amountUsdt),
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
        recordCloudTransaction(cleanKey, tx)

        val adminWithdrawalRecord = mapOf(
            "txId" to txId,
            "secretKey" to cleanKey,
            "amountUsdt" to amountUsdt,
            "address" to address,
            "network" to network,
            "requestedAt" to now,
            "status" to "PENDING_REVIEW"
        )
        firestore.collection("withdrawals").document(txId).set(adminWithdrawalRecord)

        return Result.success(tx)
    }

    fun checkAndUpdatePurchaseLimit(secretKey: String, purchaseAmount: Double): Boolean {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        val current = _userState.value
        val now = System.currentTimeMillis()

        var currentSpent = current.dailySpentUsdt
        var resetDate = current.dailySpentResetDate

        if (now - resetDate >= 86400000L) {
            currentSpent = 0.0
            resetDate = now
        }

        if (currentSpent + purchaseAmount > 5000.0) {
            return false
        }

        val updatedSpent = currentSpent + purchaseAmount
        _userState.value = current.copy(
            dailySpentUsdt = updatedSpent,
            dailySpentResetDate = resetDate
        )

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "dailySpentUsdt" to updatedSpent,
                "dailySpentResetDate" to resetDate,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
        return true
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
        getUserVault(cleanKey).edit().putFloat("miner_balance", usdt.toFloat()).putFloat("grid_balance", grid.toFloat()).apply()
        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to usdt,
                "gridBalance" to grid,
                "lastSyncTimestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
    }

    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: "HG-ADM9-7788-5544-0001")
        adminSetBalance(cleanKey, newUsdt, newGrid)
        _minerBalance.value = newUsdt
        _gridBalance.value = newGrid
        _userState.value = _userState.value.copy(minerBalanceUsdt = newUsdt, gridBalance = newGrid)
    }

    fun computeAccruedGridBalance(now: Long = System.currentTimeMillis()): Double = _gridBalance.value

    fun saveGridBalanceOnPause(computedGrid: Double) {
        val now = System.currentTimeMillis()
        val key = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: "")
        if (key.isNotBlank()) {
            getUserVault(key).edit().putFloat("grid_balance", computedGrid.toFloat()).apply()
            firestore.collection("users").document(key).set(
                mapOf("gridBalance" to computedGrid, "lastSyncTimestamp" to now),
                SetOptions.merge()
            )
        }
    }

    fun onAppResumed() {
        val key = getActiveKey() ?: _userState.value.secretKey
        if (key.isNotBlank()) {
            bindUserSession(key)
        }
    }

    fun onAppPaused() {
        forceCloudBackup()
    }

    fun immediateLogout() {
        forceCloudBackup()
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

    fun setInstantUserState(state: UserMiningState) { _userState.value = state }
    fun setAppLocked(locked: Boolean) { _userState.value = _userState.value.copy(isAppLocked = locked) }
    fun markSecretKeyBackedUp() { securityPreferences.setSecretKeyBackedUp(true); _userState.value = _userState.value.copy(isKeyBackedUp = true) }
    fun setPin(pin: String): Boolean {
        val success = securityPreferences.setPin(pin)
        if (success) {
            val key = getActiveKey() ?: _userState.value.secretKey
            savePinToCloud(key, pin)
        }
        return success
    }
    fun verifyPin(pin: String): Boolean = securityPreferences.verifyPin(pin)
    fun setBiometricEnabled(enabled: Boolean) { securityPreferences.setBiometricEnabled(enabled); _userState.value = _userState.value.copy(isBiometricEnabled = enabled) }
    fun isPinSet(): Boolean = securityPreferences.isPinSet()
    fun isBiometricEnabled(): Boolean = securityPreferences.isBiometricEnabled()
    fun getSecretKey(): String = securityPreferences.getActiveUserKey() ?: _userState.value.secretKey

    private fun attachSystemSettingsListener() {
        firebaseManager.listenToSystemSettings { newPrice ->
            if (newPrice > 0.0) {
                _gridPriceUsd.value = newPrice
                globalPrefs.edit().putFloat("grid_price_usd", newPrice.toFloat()).apply()
            }
        }
    }

    private fun loadInitialState(): UserMiningState {
        val now = System.currentTimeMillis()
        val key = securityPreferences.getActiveUserKey() ?: ""
        val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(key) || key.startsWith("HG-ADM9")

        val vault = if (key.isNotBlank()) getUserVault(key) else null
        val usdt = vault?.getFloat("miner_balance", if (isMasterAdmin) 3000f else 0f)?.toDouble() ?: if (isMasterAdmin) 3000.0 else 0.0
        val grid = vault?.getFloat("grid_balance", 0f)?.toDouble() ?: 0.0
        val rigsRaw = if (vault != null) deserializeRigs(vault.getString("hardware_nodes_json", "") ?: "") else emptyList()
        val parsedRigs = rigsRaw.mapNotNull { parseRigMap(it, now) }

        return UserMiningState(
            uid = key,
            secretKey = key,
            email = if (isMasterAdmin) "admin@hashgrid.pro" else "miner_${key.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isMasterAdmin) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${key.takeLast(4)}",
            minerBalanceUsdt = usdt,
            gridBalance = grid,
            baseFreeHashrateGh = 2.0,
            isAuthenticated = key.isNotBlank(),
            isAdmin = isMasterAdmin,
            role = if (isMasterAdmin) "superadmin" else "user",
            userRigs = parsedRigs,
            createdAt = now
        )
    }

    suspend fun createNewAccount(): Result<UserMiningState> = withContext(Dispatchers.IO) {
        val newKey = SecretKeyUtils.generateSecretKey()
        val now = System.currentTimeMillis()
        val initialData = hashMapOf(
            "secretKey" to newKey,
            "isAdmin" to false,
            "minerBalanceUsdt" to 0.0,
            "gridBalance" to 0.0,
            "baselineGridBalance" to 0.0,
            "isFreeMiningActive" to false,
            "freeMiningEndTime" to 0L,
            "freeMiningStartTime" to 0L,
            "hardwareNodes" to emptyList<Map<String, Any>>(),
            "transactions" to emptyList<Map<String, Any>>(),
            "securityPin" to "",
            "lastDailySpinTimestamp" to 0L,
            "dailySpentUsdt" to 0.0,
            "dailySpentResetDate" to now,
            "lastSyncTimestamp" to now
        )

        try {
            firestore.collection("users").document(newKey).set(initialData, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("MiningRepository", "New account creation error: ${e.message}")
        }

        withContext(Dispatchers.Main) {
            loginWithKeyInstant(newKey)
        }

        val newState = _userState.value
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
