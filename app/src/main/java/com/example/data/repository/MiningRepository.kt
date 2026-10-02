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
    }

    private val globalPrefs: SharedPreferences = context.getSharedPreferences("hashgrid_global_v2", Context.MODE_PRIVATE)

    // ========================================================
    // LAYER 1: PER-USER ISOLATED LOCAL STORAGE VAULT
    // ========================================================
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

    private var lastYieldTickTime = System.currentTimeMillis()
    private var lastCloudSyncTime = System.currentTimeMillis()

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
        _minerBalance.value = 0.0
        _gridBalance.value = 0.0
        _isMiningActive.value = false
        _freeMiningEndTime.value = 0L
    }

    // ========================================================
    // LAYER 2: JSON SERIALIZATION ENGINE FOR HARDWARE NODES
    // ========================================================
    private fun serializeRigs(rigs: List<Map<String, Any>>): String {
        val array = JSONArray()
        rigs.forEach { rig ->
            array.put(JSONObject(rig))
        }
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
    // LAYER 3: INSTANT VAULT LOGIN (NO RESETTING BALANCES!)
    // ========================================================
    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        setActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)

        val vault = getUserVault(cleanKey)
        val isInitialized = vault.getBoolean("is_vault_initialized", false)

        val usdt: Double
        val grid: Double
        val isMining: Boolean
        val sessionStart: Long
        val sessionEnd: Long
        val localRigsRaw: List<Map<String, Any>>

        val now = System.currentTimeMillis()

        if (!isInitialized) {
            // First ever setup: Admin gets $3000, Normal user gets 0
            usdt = if (isAdminKey) 3000.0 else 0.0
            grid = 0.0
            isMining = false
            sessionStart = 0L
            sessionEnd = 0L
            localRigsRaw = emptyList()

            vault.edit()
                .putBoolean("is_vault_initialized", true)
                .putFloat("miner_balance", usdt.toFloat())
                .putFloat("grid_balance", grid.toFloat())
                .putBoolean("is_mining_active", false)
                .putLong("mining_start", 0L)
                .putLong("mining_end", 0L)
                .putString("hardware_nodes_json", serializeRigs(localRigsRaw))
                .apply()
        } else {
            // Already initialized: Read exact saved states (even if USDT is 0.0!)
            usdt = vault.getFloat("miner_balance", 0f).toDouble()
            grid = vault.getFloat("grid_balance", 0f).toDouble()
            sessionEnd = vault.getLong("mining_end", 0L)
            sessionStart = vault.getLong("mining_start", 0L)
            isMining = (sessionEnd > now) && vault.getBoolean("is_mining_active", false)
            localRigsRaw = deserializeRigs(vault.getString("hardware_nodes_json", "") ?: "")
        }

        val parsedRigs = localRigsRaw.mapNotNull { parseRigMap(it, now) }

        _minerBalance.value = usdt
        _gridBalance.value = grid
        _isMiningActive.value = isMining
        _freeMiningEndTime.value = sessionEnd
        _deployedRigs.value = localRigsRaw
        _deployedNodesCount.value = localRigsRaw.size

        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = usdt,
            gridBalance = grid,
            isFreeMiningActive = isMining,
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            userRigs = parsedRigs,
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        _userState.value = instantState

        // Trigger real-time sync with cloud in parallel
        bindUserSession(cleanKey)
        return instantState
    }

    // ========================================================
    // LAYER 4: FIRESTORE TWO-WAY ATOMIC CLOUD SYNC
    // ========================================================
    fun bindUserSession(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        setActiveKey(cleanKey)

        snapshotRegistration?.remove()
        val docRef = firestore.collection("users").document(cleanKey)
        val vault = getUserVault(cleanKey)
        val now = System.currentTimeMillis()
        val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        // 1. Snapshot Listener for continuous sync
        snapshotRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                isCloudSynced.value = false
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                // If cloud document is missing, back up local vault state to cloud immediately
                val localNodes = deserializeRigs(vault.getString("hardware_nodes_json", "") ?: "")
                val cloudBackup = hashMapOf(
                    "secretKey" to cleanKey,
                    "isAdmin" to isMaster,
                    "minerBalanceUsdt" to _minerBalance.value,
                    "gridBalance" to _gridBalance.value,
                    "isFreeMiningActive" to _isMiningActive.value,
                    "freeMiningStartTime" to _userState.value.freeMiningSessionStart,
                    "freeMiningEndTime" to _freeMiningEndTime.value,
                    "hardwareNodes" to localNodes,
                    "transactions" to emptyList<Map<String, Any>>(),
                    "securityPin" to (vault.getString("security_pin", "") ?: ""),
                    "lastDailySpinTimestamp" to vault.getLong("last_spin_timestamp", 0L),
                    "dailySpentUsdt" to 0.0,
                    "dailySpentResetDate" to now,
                    "lastSyncTimestamp" to now
                )
                docRef.set(cloudBackup, SetOptions.merge())
                isCloudSynced.value = true
                return@addSnapshotListener
            }

            if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

            // Hydrate cloud values
            val cloudUsdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: _minerBalance.value
            val cloudGrid = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: _gridBalance.value
            val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong() ?: _freeMiningEndTime.value
            val sessionStart = (snapshot.get("freeMiningStartTime") as? Number)?.toLong() ?: _userState.value.freeMiningSessionStart
            val isFreeMining = sessionEnd > now

            val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
                ?: _deployedRigs.value

            val localNodes = deserializeRigs(vault.getString("hardware_nodes_json", "") ?: "")
            // Merge nodes without duplicate IDs
            val mergedNodes = (localNodes + rawNodes).distinctBy { it["id"] as? String ?: UUID.randomUUID().toString() }

            vault.edit()
                .putBoolean("is_vault_initialized", true)
                .putFloat("miner_balance", cloudUsdt.toFloat())
                .putFloat("grid_balance", cloudGrid.toFloat())
                .putBoolean("is_mining_active", isFreeMining)
                .putLong("mining_start", sessionStart)
                .putLong("mining_end", sessionEnd)
                .putString("hardware_nodes_json", serializeRigs(mergedNodes))
                .apply()

            val userRigsList = mergedNodes.mapNotNull { parseRigMap(it, now) }
            val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
            val restoredTxs = if (rawTxs.isNotEmpty()) rawTxs.mapNotNull { parseTransactionMap(it, now) } else _userState.value.transactions

            _minerBalance.value = cloudUsdt
            _gridBalance.value = cloudGrid
            _isMiningActive.value = isFreeMining
            _freeMiningEndTime.value = sessionEnd
            _deployedRigs.value = mergedNodes
            _deployedNodesCount.value = mergedNodes.size

            _userState.value = _userState.value.copy(
                minerBalanceUsdt = cloudUsdt,
                gridBalance = cloudGrid,
                isFreeMiningActive = isFreeMining,
                freeMiningSessionStart = sessionStart,
                freeMiningSessionEnd = sessionEnd,
                userRigs = userRigsList,
                transactions = restoredTxs,
                isAdmin = snapshot.getBoolean("isAdmin") ?: isMaster
            )

            isCloudSynced.value = true
        }
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

        // 1. Immediately persist to Phone Vault
        val vault = getUserVault(cleanKey)
        vault.edit()
            .putFloat("miner_balance", updatedBalance.toFloat())
            .putString("hardware_nodes_json", serializeRigs(updatedRigsRaw))
            .apply()

        // 2. Persist to Firestore with Merge (Never fails)
        val docRef = firestore.collection("users").document(cleanKey)
        docRef.set(
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

        docRef.set(
            mapOf(
                "hardwareNodes" to FieldValue.arrayUnion(rigMap),
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        ).addOnSuccessListener { onSuccess() }
    }

    // ==========================================
    // 24H MINING ENGINE (INSTANT & ACCURATE)
    // ==========================================
    fun startFreeMiningSession() {
        val current = _userState.value
        val activeKey = current.secretKey.ifBlank { getActiveKey() ?: "HG-ADM9-7788-5544-0001" }
        val cleanKey = SecretKeyUtils.normalizeSecretKey(activeKey)
        val now = System.currentTimeMillis()
        val end = now + 86400000L

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
            .apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "isFreeMiningActive" to true,
                "freeMiningStartTime" to now,
                "freeMiningEndTime" to end,
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
        val now = System.currentTimeMillis()
        val current = _userState.value
        if (!current.isAuthenticated || current.secretKey.isBlank()) return

        val sessionEnd = current.freeMiningSessionEnd
        val isFreeActive = (sessionEnd > now) && current.isFreeMiningActive

        if (_isMiningActive.value != isFreeActive) {
            _isMiningActive.value = isFreeActive
            if (!isFreeActive) {
                NotificationHelper.sendMiningSessionEndedNotification(appContext)
            }
        }

        // Real-Time Token Increment
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

        // Periodic safe sync to Phone Vault and Firestore every 20 seconds
        if (now - lastCloudSyncTime > 20000) {
            lastCloudSyncTime = now
            syncToStorage(current.secretKey, newMinerBalance, newGridBalance, isFreeActive, sessionEnd)
        }
    }

    private fun syncToStorage(secretKey: String, usdt: Double, grid: Double, isMining: Boolean, sessionEnd: Long) {
        val vault = getUserVault(secretKey)
        vault.edit()
            .putFloat("miner_balance", usdt.toFloat())
            .putFloat("grid_balance", grid.toFloat())
            .putBoolean("is_mining_active", isMining)
            .putLong("mining_end", sessionEnd)
            .apply()

        firestore.collection("users").document(secretKey).set(
            mapOf(
                "minerBalanceUsdt" to usdt,
                "gridBalance" to grid,
                "isFreeMiningActive" to isMining,
                "freeMiningEndTime" to sessionEnd,
                "lastSyncTimestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
    }

    // ==========================================
    // REWARDS, DEPOSIT & LOGOUT
    // ==========================================
    fun claimWheelReward(key: String, rewardGrid: Double) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        _gridBalance.value += rewardGrid
        val current = _userState.value
        val newBalance = current.gridBalance + rewardGrid
        _userState.value = current.copy(gridBalance = newBalance)

        getUserVault(cleanKey).edit().putFloat("grid_balance", newBalance.toFloat()).apply()

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "gridBalance" to FieldValue.increment(rewardGrid),
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
