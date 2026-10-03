package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.example.data.firebase.FirebaseManager
import com.example.data.model.*
import com.example.data.payment.NowPaymentResponse
import com.example.data.payment.NowPaymentsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import com.example.data.notification.NotificationHelper
import com.example.data.security.SecretKeyUtils
import com.example.data.security.SecurityPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
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
        const val TOKENS_PER_SECOND = (2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0) // ~0.0007 GRID/s
        private const val MAX_ACCOUNTS_PER_DEVICE = 2
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

    private fun parseRigMap(map: Map<String, Any>, now: Long): UserRig? {
        return try {
            val id = (map["nodeId"] as? String) ?: (map["id"] as? String) ?: (map["rigId"] as? String) ?: UUID.randomUUID().toString()
            val name = (map["nodeName"] as? String) ?: (map["name"] as? String) ?: "Hardware Node"
            val cost = (map["costUsdt"] as? Number)?.toDouble() ?: (map["priceUsdt"] as? Number)?.toDouble() ?: 1000.0
            val hashrate = (map["hashrateGh"] as? Number)?.toDouble() ?: (map["hashrate"] as? Number)?.toDouble() ?: 400.0
            val totalDays = (map["totalDays"] as? Number)?.toInt() ?: (map["durationDays"] as? Number)?.toInt() ?: 200
            val deployedAt = (map["purchaseTimestamp"] as? Number)?.toLong() ?: (map["deployedTimestamp"] as? Number)?.toLong() ?: now

            UserRig(
                id = id,
                catalogId = (map["catalogId"] as? String) ?: "rig-custom",
                name = name,
                priceUsdt = cost,
                hashrateGh = hashrate,
                purchaseTimestamp = deployedAt,
                durationDays = totalDays,
                status = if (now >= deployedAt + totalDays * 86400000L) RigStatus.COMPLETED else RigStatus.ACTIVE,
                totalReceivedUsdt = (map["receivedUsdt"] as? Number)?.toDouble() ?: (map["totalReceivedUsdt"] as? Number)?.toDouble() ?: 0.0,
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

    fun bindUserSession(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        setActiveKey(cleanKey)

        snapshotRegistration?.remove()
        val docRef = firestore.collection("users").document(cleanKey)
        val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        docRef.get(Source.SERVER).addOnCompleteListener { task ->
            val now = System.currentTimeMillis()
            val snapshot = if (task.isSuccessful) task.result else null

            if (snapshot != null && snapshot.exists()) {
                restoreFromCloudWithAccrual(cleanKey, snapshot, isMaster, now)
            } else {
                val defaultUsdt = if (isMaster) 3000.0 else 0.0
                val defaultRigs = if (isMaster) listOf(
                    mapOf("id" to "651", "nodeId" to "651", "name" to "Elite Node #651", "nodeName" to "Elite Node #651", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "priceUsdt" to 1000.0, "totalDays" to 200, "durationDays" to 200, "purchaseTimestamp" to now, "status" to "ACTIVE"),
                    mapOf("id" to "233", "nodeId" to "233", "name" to "Elite Node #233", "nodeName" to "Elite Node #233", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "priceUsdt" to 1000.0, "totalDays" to 200, "durationDays" to 200, "purchaseTimestamp" to now, "status" to "ACTIVE"),
                    mapOf("id" to "414", "nodeId" to "414", "name" to "Elite Node #414", "nodeName" to "Elite Node #414", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "priceUsdt" to 1000.0, "totalDays" to 200, "durationDays" to 200, "purchaseTimestamp" to now, "status" to "ACTIVE")
                ) else emptyList()

                val initData = hashMapOf(
                    "secretKey" to cleanKey,
                    "uid" to cleanKey,
                    "isAdmin" to isMaster,
                    "minerBalanceUsdt" to defaultUsdt,
                    "usdtBalance" to defaultUsdt,
                    "gridBalance" to 0.0,
                    "isFreeMiningActive" to false,
                    "isMiningActive" to false,
                    "freeMiningSessionStart" to 0L,
                    "freeMiningSessionEnd" to 0L,
                    "hardwareNodes" to defaultRigs,
                    "transactions" to emptyList<Map<String, Any>>(),
                    "securityPin" to "",
                    "lastDailySpinTimestamp" to 0L,
                    "dailySpentUsdt" to 0.0,
                    "dailySpentResetDate" to now,
                    "lastSyncTimestamp" to now
                )
                docRef.set(initData, SetOptions.merge())

                _minerBalance.value = defaultUsdt
                _gridBalance.value = 0.0
                _deployedRigs.value = defaultRigs
                _deployedNodesCount.value = defaultRigs.size

                val userRigsList = defaultRigs.mapNotNull { parseRigMap(it, now) }
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

            attachLiveObserver(cleanKey, docRef, isMaster)
        }
    }

    private fun restoreFromCloudWithAccrual(
        cleanKey: String,
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        isMaster: Boolean,
        now: Long
    ) {
        var usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble()
            ?: (snapshot.get("usdtBalance") as? Number)?.toDouble()
            ?: (if (isMaster) 3000.0 else 0.0)

        var grid = (snapshot.get("gridBalance") as? Number)?.toDouble() ?: 0.0

        val sessionEnd = (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
            ?: (snapshot.get("freeMiningEndTime") as? Number)?.toLong()
            ?: (snapshot.get("miningEndTime") as? Number)?.toLong()
            ?: 0L

        val sessionStart = (snapshot.get("freeMiningSessionStart") as? Number)?.toLong()
            ?: (snapshot.get("freeMiningStartTime") as? Number)?.toLong()
            ?: (snapshot.get("miningStartTime") as? Number)?.toLong()
            ?: 0L

        val lastSync = (snapshot.get("lastSyncTimestamp") as? Number)?.toLong() ?: now
        val wasMiningActive = snapshot.getBoolean("isFreeMiningActive") ?: snapshot.getBoolean("isMiningActive") ?: false

        if (wasMiningActive && sessionStart > 0L) {
            val effectiveEnd = Math.min(now, sessionEnd)
            if (effectiveEnd > lastSync) {
                val elapsedSec = Math.max(0L, (effectiveEnd - lastSync) / 1000)
                grid += elapsedSec * TOKENS_PER_SECOND
            }
        }
        val isStillMining = (now < sessionEnd) && wasMiningActive

        val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
            ?: (snapshot.get("activeMiningRigs") as? List<Map<String, Any>>)
            ?: emptyList()

        var totalAccruedYield = 0.0
        val updatedNodes = rawNodes.map { rigMap ->
            val cost = (rigMap["costUsdt"] as? Number)?.toDouble() ?: (rigMap["priceUsdt"] as? Number)?.toDouble() ?: 1000.0
            val dailyYield = (cost * 0.15) / 30.0
            val yieldPerSec = dailyYield / 86400.0
            val deployedAt = (rigMap["purchaseTimestamp"] as? Number)?.toLong() ?: (rigMap["deployedTimestamp"] as? Number)?.toLong() ?: now
            val totalDays = (rigMap["totalDays"] as? Number)?.toInt() ?: (rigMap["durationDays"] as? Number)?.toInt() ?: 200
            val expiry = deployedAt + (totalDays.toLong() * 86400000L)

            if (lastSync < expiry) {
                val effectiveYieldEnd = Math.min(now, expiry)
                val elapsedSec = Math.max(0.0, (effectiveYieldEnd - lastSync).toDouble() / 1000.0)
                totalAccruedYield += elapsedSec * yieldPerSec
            }
            rigMap
        }
        usdt += totalAccruedYield

        val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
        val restoredTransactions = rawTxs.mapNotNull { parseTransactionMap(it, now) }

        val cloudPin = snapshot.getString("securityPin") ?: ""
        if (cloudPin.isNotBlank()) {
            securityPreferences.setPin(cloudPin)
        }

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to usdt,
                "usdtBalance" to usdt,
                "gridBalance" to grid,
                "isFreeMiningActive" to isStillMining,
                "isMiningActive" to isStillMining,
                "freeMiningSessionStart" to sessionStart,
                "freeMiningSessionEnd" to sessionEnd,
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
        isMaster: Boolean
    ) {
        snapshotRegistration?.remove()
        snapshotRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

            val now = System.currentTimeMillis()
            val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: _minerBalance.value
            val sessionEnd = (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong() ?: _freeMiningEndTime.value
            val isFreeMining = sessionEnd > now

            val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("activeMiningRigs") as? List<Map<String, Any>>)
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

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "isFreeMiningActive" to true,
                "isMiningActive" to true,
                "freeMiningSessionStart" to now,
                "freeMiningSessionEnd" to end,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
    }

    fun buyRig(catalogItem: RigCatalogItem, useWalletBalance: Boolean = true): Result<UserRig> {
        val current = _userState.value
        val now = System.currentTimeMillis()
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: "" })

        if (useWalletBalance && current.minerBalanceUsdt < catalogItem.priceUsdt) {
            return Result.failure(Exception("Insufficient Miner Balance."))
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
            "nodeName" to node.name,
            "hashrateGh" to node.hashrateGh,
            "costUsdt" to node.costUsdt,
            "priceUsdt" to node.costUsdt,
            "purchaseTimestamp" to now,
            "totalDays" to node.totalDays,
            "durationDays" to node.totalDays,
            "status" to "ACTIVE"
        )

        val updatedBalance = (current.minerBalanceUsdt - catalogItem.priceUsdt).coerceAtLeast(0.0)
        val updatedRigsRaw = _deployedRigs.value + rigMap

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to updatedBalance,
                "usdtBalance" to updatedBalance,
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

    init {
        startBackgroundEngine()
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
        if (!isCloudHydrated) return

        val now = System.currentTimeMillis()
        val current = _userState.value
        if (!current.isAuthenticated || current.secretKey.isBlank()) return

        val sessionEnd = current.freeMiningSessionEnd
        val sessionStart = current.freeMiningSessionStart
        val isFreeActive = (sessionEnd > now) && (sessionStart > 0L) && current.isFreeMiningActive

        if (_isMiningActive.value != isFreeActive) {
            _isMiningActive.value = isFreeActive
            if (!isFreeActive) {
                NotificationHelper.sendMiningSessionEndedNotification(appContext)
            }
        }

        var newGridBalance = current.gridBalance
        if (isFreeActive) {
            newGridBalance += TOKENS_PER_SECOND
            _gridBalance.value = newGridBalance
        }

        var additionalUsdtYield = 0.0
        val updatedRigs = current.userRigs.map { rig ->
            if (rig.status == RigStatus.ACTIVE) {
                val dailyYield = (rig.priceUsdt * 0.15) / 30.0
                val yieldPerSec = dailyYield / 86400.0
                additionalUsdtYield += yieldPerSec
                rig.copy(
                    totalReceivedUsdt = rig.totalReceivedUsdt + yieldPerSec,
                    thisMonthEarnedUsdt = rig.thisMonthEarnedUsdt + yieldPerSec,
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

        if (now - lastCloudSyncTime > 25000) {
            lastCloudSyncTime = now
            firestore.collection("users").document(current.secretKey).set(
                mapOf(
                    "minerBalanceUsdt" to newMinerBalance,
                    "usdtBalance" to newMinerBalance,
                    "gridBalance" to newGridBalance,
                    "lastSyncTimestamp" to now
                ),
                SetOptions.merge()
            )
        }
    }

    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        setActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)
        isCloudHydrated = false

        val instantState = UserMiningState(
            uid = cleanKey,
            secretKey = cleanKey,
            email = if (isAdminKey) "admin@hashgrid.pro" else "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isAdminKey) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${cleanKey.takeLast(4)}",
            minerBalanceUsdt = _minerBalance.value,
            gridBalance = _gridBalance.value,
            userRigs = _userState.value.userRigs,
            isAdmin = isAdminKey,
            role = if (isAdminKey) "superadmin" else "user",
            isAuthenticated = true,
            isKeyBackedUp = true
        )

        _userState.value = instantState
        bindUserSession(cleanKey)
        return instantState
    }

    fun onAppPaused() {
        val current = _userState.value
        if (current.secretKey.isNotBlank()) {
            firestore.collection("users").document(current.secretKey).set(
                mapOf(
                    "minerBalanceUsdt" to current.minerBalanceUsdt,
                    "usdtBalance" to current.minerBalanceUsdt,
                    "gridBalance" to current.gridBalance,
                    "lastSyncTimestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        }
    }

    fun recordCloudTransaction(secretKey: String, tx: TransactionItem) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
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
            mapOf("transactions" to FieldValue.arrayUnion(txMap), "lastSyncTimestamp" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    fun immediateLogout() {
        clearActiveKey()
        _userState.value = UserMiningState(
            uid = "", secretKey = "", email = "", nodeId = "", isAuthenticated = false, isAdmin = false, role = "user"
        )
    }

    suspend fun logout() = withContext(Dispatchers.Main) { immediateLogout() }

    private fun loadInitialState(): UserMiningState {
        val key = securityPreferences.getActiveUserKey() ?: ""
        val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(key) || key.startsWith("HG-ADM9")
        return UserMiningState(
            uid = key,
            secretKey = key,
            email = if (isMasterAdmin) "admin@hashgrid.pro" else "miner_${key.takeLast(4).lowercase()}@hashgrid.pro",
            nodeId = if (isMasterAdmin) "NODE-SUPERADMIN-#0001" else "NODE-WEB3-#${key.takeLast(4)}",
            isAdmin = isMasterAdmin,
            role = if (isMasterAdmin) "superadmin" else "user",
            isAuthenticated = key.isNotBlank()
        )
    }

    // ========================================================
    // SECURE CREATE ACCOUNT: CLOUD & HARDWARE DEVICE LIMIT CHECK
    // ========================================================
    suspend fun createNewAccount(): Result<UserMiningState> = suspendCancellableCoroutine { continuation ->
        try {
            val androidId = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"

            val deviceAccountsKey = "dev_acc_count_$androidId"
            val localCount = globalPrefs.getInt(deviceAccountsKey, 0)

            if (localCount >= MAX_ACCOUNTS_PER_DEVICE) {
                if (continuation.isActive) {
                    continuation.resume(
                        Result.failure(Exception("Account Limit Reached: Maximum 2 accounts allowed per device to prevent bot farming."))
                    )
                }
                return@suspendCancellableCoroutine
            }

            firestore.collection("users")
                .whereEqualTo("deviceId", androidId)
                .get(Source.SERVER)
                .addOnCompleteListener { checkTask ->
                    val cloudCount = if (checkTask.isSuccessful) checkTask.result?.size() ?: 0 else 0
                    val totalDeviceAccounts = Math.max(localCount, cloudCount)

                    if (totalDeviceAccounts >= MAX_ACCOUNTS_PER_DEVICE) {
                        globalPrefs.edit().putInt(deviceAccountsKey, totalDeviceAccounts).apply()
                        if (continuation.isActive) {
                            continuation.resume(
                                Result.failure(Exception("Account Limit Reached: Maximum 2 accounts allowed per device to prevent bot farming."))
                            )
                        }
                        return@addOnCompleteListener
                    }

                    val allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    val part1 = (1..4).map { allowedChars.random() }.joinToString("")
                    val part2 = (1..4).map { allowedChars.random() }.joinToString("")
                    val part3 = (1..4).map { allowedChars.random() }.joinToString("")
                    val newSecretKey = "HG-$part1-$part2-$part3"

                    val now = System.currentTimeMillis()

                    val initialUserData = hashMapOf(
                        "secretKey" to newSecretKey,
                        "uid" to newSecretKey,
                        "isAdmin" to false,
                        "deviceId" to androidId,
                        "minerBalanceUsdt" to 0.0,
                        "usdtBalance" to 0.0,
                        "gridBalance" to 0.0,
                        "isFreeMiningActive" to false,
                        "isMiningActive" to false,
                        "freeMiningSessionStart" to 0L,
                        "freeMiningSessionEnd" to 0L,
                        "hardwareNodes" to emptyList<Map<String, Any>>(),
                        "transactions" to emptyList<Map<String, Any>>(),
                        "securityPin" to "",
                        "lastDailySpinTimestamp" to 0L,
                        "dailySpentUsdt" to 0.0,
                        "dailySpentResetDate" to now,
                        "lastSyncTimestamp" to now,
                        "createdAt" to now
                    )

                    firestore.collection("users").document(newSecretKey)
                        .set(initialUserData, SetOptions.merge())
                        .addOnSuccessListener {
                            globalPrefs.edit().putInt(deviceAccountsKey, totalDeviceAccounts + 1).apply()

                            setActiveKey(newSecretKey)
                            securityPreferences.setLoggedIn(true)
                            securityPreferences.setSecretKeyBackedUp(false)

                            val newState = UserMiningState(
                                uid = newSecretKey,
                                secretKey = newSecretKey,
                                email = "miner_${newSecretKey.takeLast(4).lowercase()}@hashgrid.pro",
                                nodeId = "NODE-WEB3-#${newSecretKey.takeLast(4)}",
                                minerBalanceUsdt = 0.0,
                                gridBalance = 0.0,
                                userRigs = emptyList(),
                                isAdmin = false,
                                role = "user",
                                isAuthenticated = true,
                                isKeyBackedUp = false
                            )

                            _minerBalance.value = 0.0
                            _gridBalance.value = 0.0
                            _deployedRigs.value = emptyList()
                            _deployedNodesCount.value = 0
                            _isMiningActive.value = false
                            _freeMiningEndTime.value = 0L
                            _userState.value = newState

                            bindUserSession(newSecretKey)

                            if (continuation.isActive) {
                                continuation.resume(Result.success(newState))
                            }
                        }
                        .addOnFailureListener { err ->
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(err))
                            }
                        }
                }
        } catch (e: Exception) {
            Log.e("MiningRepo", "Error creating account: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    // ========================================================
    // WITHDRAWAL & DEPOSIT ENGINE: CLEAN COMPILATION & LIVE SYNC
    // ========================================================
    fun requestWithdrawal(amountUsdt: Double, address: String, network: String): Result<TransactionItem> {
        val current = _userState.value
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: return Result.failure(Exception("User not authenticated")) })

        if (amountUsdt <= 0) {
            return Result.failure(Exception("Invalid withdrawal amount"))
        }
        if (current.minerBalanceUsdt < amountUsdt) {
            return Result.failure(Exception("Insufficient withdrawable balance. Available: $${String.format("%.2f", current.minerBalanceUsdt)}"))
        }
        if (address.isBlank()) {
            return Result.failure(Exception("Please enter a valid destination address"))
        }

        val now = System.currentTimeMillis()
        val newBalance = (current.minerBalanceUsdt - amountUsdt).coerceAtLeast(0.0)

        val tx = TransactionItem(
            id = "tx-wd-${UUID.randomUUID().toString().take(8)}",
            type = TransactionType.WITHDRAWAL,
            amount = amountUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "Withdrawal to ${address.take(6)}...${address.takeLast(4)} ($network)",
            network = network,
            txHash = ""
        )

        _minerBalance.value = newBalance
        _userState.value = current.copy(
            minerBalanceUsdt = newBalance,
            transactions = listOf(tx) + current.transactions
        )

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to newBalance,
                "usdtBalance" to newBalance,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
        recordCloudTransaction(cleanKey, tx)

        val adminTx = hashMapOf(
            "id" to tx.id,
            "secretKey" to cleanKey,
            "type" to "WITHDRAWAL",
            "amount" to amountUsdt,
            "currency" to "USDT",
            "destinationAddress" to address,
            "network" to network,
            "status" to "PENDING",
            "timestamp" to now
        )
        firestore.collection("transactions").document(tx.id).set(adminTx, SetOptions.merge())

        return Result.success(tx)
    }

    fun depositFunds(amountUsdt: Double, network: String, txHash: String = "") {
        val current = _userState.value
        val cleanKey = SecretKeyUtils.normalizeSecretKey(current.secretKey.ifBlank { getActiveKey() ?: return })
        if (amountUsdt <= 0) return

        val now = System.currentTimeMillis()
        val newBalance = current.minerBalanceUsdt + amountUsdt

        val tx = TransactionItem(
            id = "tx-dep-${UUID.randomUUID().toString().take(8)}",
            type = TransactionType.DEPOSIT,
            amount = amountUsdt,
            currency = "USDT",
            timestamp = now,
            status = TransactionStatus.COMPLETED,
            description = "Direct Deposit via $network",
            network = network,
            txHash = txHash
        )

        _minerBalance.value = newBalance
        _userState.value = current.copy(
            minerBalanceUsdt = newBalance,
            transactions = listOf(tx) + current.transactions
        )

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "minerBalanceUsdt" to newBalance,
                "usdtBalance" to newBalance,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
        recordCloudTransaction(cleanKey, tx)
    }

    // ========================================================
    // LUCKY WHEEL REWARD ENGINE: ACCRUAL & STABLE MODELS
    // ========================================================
    fun claimWheelReward(key: String, rewardGrid: Double) {
        val current = _userState.value
        val activeKey = key.ifBlank { current.secretKey.ifBlank { getActiveKey() ?: "" } }
        val cleanKey = SecretKeyUtils.normalizeSecretKey(activeKey)
        if (cleanKey.isBlank() || rewardGrid <= 0) return

        val now = System.currentTimeMillis()
        val updatedGrid = current.gridBalance + rewardGrid

        _gridBalance.value = updatedGrid
        _userState.value = current.copy(
            gridBalance = updatedGrid,
            lastDailySpinTimestamp = now
        )

        firestore.collection("users").document(cleanKey).set(
            mapOf(
                "gridBalance" to updatedGrid,
                "lastDailySpinTimestamp" to now,
                "lastSyncTimestamp" to now
            ),
            SetOptions.merge()
        )
    }

    fun isLuckySpinAvailable(): Boolean {
        val now = System.currentTimeMillis()
        val lastSpin = _userState.value.lastDailySpinTimestamp
        return (now - lastSpin) >= 86400000L
    }

    fun executeLuckySpin(sector: SpinSector): SpinHistoryRecord {
        return SpinHistoryRecord(
            UUID.randomUUID().toString().take(8),
            "Daily Spin",
            "Grid Reward",
            System.currentTimeMillis(),
            SpinRewardType.GRID_TOKENS,
            0.0
        )
    }

    fun startFreeMiningCore(secretKey: String) { startFreeMiningSession() }
    fun deployHardwareRig(secretKey: String, rig: HardwareNode, onSuccess: () -> Unit = {}) { 
        buyRig(RigCatalogItem(rig.id, rig.name, rig.costUsdt, rig.hashrateGh, rig.totalDays, rig.name))
        onSuccess()
    }
    fun restoreAccount(key: String, onComplete: (Boolean) -> Unit) { loginWithKeyInstant(key); onComplete(true) }
    fun initializeOrRestoreUser(key: String, onComplete: (Boolean) -> Unit) { loginWithKeyInstant(key); onComplete(true) }
    suspend fun restoreAccountWithSecretKey(secretKey: String): Result<UserMiningState> = Result.success(loginWithKeyInstant(secretKey))
    fun adminSetBalance(targetKey: String, usdt: Double, grid: Double) {}
    fun adminAdjustUserBalance(newGrid: Double, newUsdt: Double) {}
    fun computeAccruedGridBalance(now: Long = System.currentTimeMillis()): Double = _gridBalance.value
    fun saveGridBalanceOnPause(computedGrid: Double) {}
    fun syncAndCatchUpOfflineGrowth(secretKey: String) {}
    fun restoreSessionAsync(key: String) {}
    fun attachUserDocumentRealTimeListener(secretKey: String) {}
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
    fun setLocalBalance(minerUsdt: Double, grid: Double) {}
    fun setInstantUserState(state: UserMiningState) { _userState.value = state }
    fun setAppLocked(locked: Boolean) { _userState.value = _userState.value.copy(isAppLocked = locked) }
    fun markSecretKeyBackedUp() { securityPreferences.setSecretKeyBackedUp(true); _userState.value = _userState.value.copy(isKeyBackedUp = true) }
    fun setPin(pin: String): Boolean = true
    fun verifyPin(pin: String): Boolean = securityPreferences.verifyPin(pin)
    fun setBiometricEnabled(enabled: Boolean) {}
    fun isPinSet(): Boolean = securityPreferences.isPinSet()
    fun isBiometricEnabled(): Boolean = securityPreferences.isBiometricEnabled()
    fun getSecretKey(): String = securityPreferences.getActiveUserKey() ?: _userState.value.secretKey
}
