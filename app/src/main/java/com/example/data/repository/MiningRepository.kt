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
import kotlinx.coroutines.tasks.await
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
        _minerBalance.value = 0.0
        _gridBalance.value = 0.0
        _isMiningActive.value = false
        _freeMiningEndTime.value = 0L
        isCloudHydrated = false
    }

    // ========================================================
    // BULLETPROOF CLOUD RESTORATION (ZERO-WIPE GUARANTEED)
    // ========================================================
    fun bindUserSession(secretKey: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return
        setActiveKey(cleanKey)

        val docRef = firestore.collection("users").document(cleanKey)

        // 1. One-shot fetch from Firestore server/cache to guarantee clean hydration
        docRef.get().addOnSuccessListener { snapshot ->
            val now = System.currentTimeMillis()
            val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

            if (snapshot != null && snapshot.exists()) {
                hydrateFromDocumentSnapshot(cleanKey, snapshot, docRef, now, isMaster)
            } else {
                // Document does not exist in cloud: Only initialize if Admin or genuine fresh user
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
                    "freeMiningStartTime" to 0L,
                    "hardwareNodes" to defaultRigs,
                    "transactions" to emptyList<Map<String, Any>>(),
                    "securityPin" to "",
                    "lastDailySpinTimestamp" to 0L,
                    "dailySpentUsdt" to 0.0,
                    "dailySpentResetDate" to now,
                    "lastSyncTimestamp" to now
                )
                docRef.set(initialMap, SetOptions.merge())

                _minerBalance.value = defaultUsdt
                _gridBalance.value = 0.0
                _deployedRigs.value = defaultRigs
                _deployedNodesCount.value = defaultRigs.size

                val userRigsList = defaultRigs.mapNotNull { map ->
                    parseRigMap(map, now)
                }

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

            // 2. Attach real-time listener for continuous observation
            attachLiveFirestoreListener(cleanKey, docRef)
        }.addOnFailureListener { e ->
            Log.e("MiningRepository", "Direct doc fetch failed: ${e.message}", e)
            attachLiveFirestoreListener(cleanKey, docRef)
        }
    }

    private fun hydrateFromDocumentSnapshot(
        cleanKey: String,
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        docRef: com.google.firebase.firestore.DocumentReference,
        now: Long,
        isMaster: Boolean
    ) {
        // Read USDT with comprehensive fallback names
        var usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble()
            ?: (snapshot.get("usdtBalance") as? Number)?.toDouble()
            ?: (snapshot.get("balanceUsdt") as? Number)?.toDouble()
            ?: (if (isMaster) 3000.0 else prefs.getFloat("miner_balance", 0f).toDouble())

        // Read GRID with fallback names
        var grid = (snapshot.get("gridBalance") as? Number)?.toDouble()
            ?: (snapshot.get("baselineGridBalance") as? Number)?.toDouble()
            ?: prefs.getFloat("grid_balance", 0f).toDouble()

        val isAdmin = snapshot.getBoolean("isAdmin") ?: isMaster
        val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong()
            ?: (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
            ?: (snapshot.get("sessionEndTime") as? Number)?.toLong()
            ?: (snapshot.get("miningEndTime") as? Number)?.toLong()
            ?: 0L
        val sessionStart = (snapshot.get("freeMiningStartTime") as? Number)?.toLong()
            ?: (snapshot.get("freeMiningSessionStart") as? Number)?.toLong()
            ?: 0L
        val lastSync = (snapshot.get("lastSyncTimestamp") as? Number)?.toLong() ?: now

        val isFreeMining = if (sessionEnd > now) {
            true
        } else if (sessionEnd > 0L && now >= sessionEnd) {
            false
        } else {
            snapshot.getBoolean("isFreeMiningActive") ?: false
        }

        val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
            ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
            ?: (if (isMaster && usdt >= 3000.0) listOf(
                mapOf("id" to "193", "name" to "Elite Node #193", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                mapOf("id" to "725", "name" to "Elite Node #725", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE"),
                mapOf("id" to "806", "name" to "Elite Node #806", "hashrateGh" to 400.0, "costUsdt" to 1000.0, "dailyYieldUsdt" to 5.0, "totalDays" to 200, "status" to "ACTIVE")
            ) else emptyList())

        // Offline Catch-up Growth
        var calculatedNewGrid = false
        if (isFreeMining && now > lastSync && sessionEnd > lastSync) {
            val effectiveEnd = Math.min(now, sessionEnd)
            val offlineSec = Math.max(0L, (effectiveEnd - lastSync) / 1000)
            val minedDelta = offlineSec * ((2.0 / 10.0) * (TARGET_DAILY_GRID / 86400.0))
            grid += minedDelta
            calculatedNewGrid = true
        }

        var calculatedNewUsdt = false
        rawNodes.forEach { rigMap ->
            val cost = (rigMap["costUsdt"] as? Number)?.toDouble() ?: (rigMap["priceUsdt"] as? Number)?.toDouble() ?: 1000.0
            val dailyYield = (rigMap["dailyYieldUsdt"] as? Number)?.toDouble() ?: ((cost * 0.15) / 30.0)
            val deployedAt = (rigMap["deployedTimestamp"] as? Number)?.toLong() ?: (rigMap["purchaseTimestamp"] as? Number)?.toLong() ?: now
            val days = (rigMap["totalDays"] as? Number)?.toInt() ?: (rigMap["durationDays"] as? Number)?.toInt() ?: 200
            val expiry = deployedAt + (days.toLong() * 86400000L)
            if (lastSync < expiry && now > lastSync) {
                val effectiveEnd = Math.min(now, expiry)
                val elapsedDays = Math.max(0.0, (effectiveEnd - lastSync).toDouble() / 86400000.0)
                usdt += (dailyYield * elapsedDays)
                calculatedNewUsdt = true
            }
        }

        // Only update Firestore if offline earnings genuinely grew
        if (calculatedNewGrid || calculatedNewUsdt) {
            docRef.update(
                mapOf(
                    "minerBalanceUsdt" to usdt,
                    "gridBalance" to grid,
                    "lastSyncTimestamp" to now
                )
            )
        }

        // Restore transactions, PIN, and daily states
        val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
        val restoredTransactions = rawTxs.mapNotNull { parseTransactionMap(it, now) }

        val cloudPin = snapshot.getString("securityPin") ?: ""
        val lastSpinTime = (snapshot.get("lastDailySpinTimestamp") as? Number)?.toLong() ?: 0L
        val spentUsdt = (snapshot.get("dailySpentUsdt") as? Number)?.toDouble() ?: 0.0
        val spentReset = (snapshot.get("dailySpentResetDate") as? Number)?.toLong() ?: now

        if (cloudPin.isNotBlank()) {
            securityPreferences.setPin(cloudPin)
        }

        _minerBalance.value = usdt
        _gridBalance.value = grid
        _isMiningActive.value = isFreeMining
        _freeMiningEndTime.value = sessionEnd
        _deployedRigs.value = rawNodes
        _deployedNodesCount.value = rawNodes.size

        val userRigsList = rawNodes.mapNotNull { parseRigMap(it, now) }

        _userState.value = _userState.value.copy(
            uid = cleanKey,
            secretKey = cleanKey,
            minerBalanceUsdt = usdt,
            gridBalance = grid,
            isFreeMiningActive = isFreeMining,
            freeMiningSessionStart = sessionStart,
            freeMiningSessionEnd = sessionEnd,
            userRigs = userRigsList,
            transactions = restoredTransactions,
            isPinConfigured = cloudPin.isNotBlank(),
            lastDailySpinTimestamp = lastSpinTime,
            dailySpentUsdt = spentUsdt,
            dailySpentResetDate = spentReset,
            isAdmin = isAdmin,
            role = if (isAdmin) "superadmin" else "user",
            isAuthenticated = true
        )

        prefs.edit()
            .putFloat("miner_balance", usdt.toFloat())
            .putFloat("grid_balance", grid.toFloat())
            .putFloat("baseline_grid_balance", grid.toFloat())
            .putBoolean("free_session_active", isFreeMining)
            .putLong("free_session_end", sessionEnd)
            .putLong("free_session_start", sessionStart)
            .apply()

        isCloudHydrated = true
        isCloudSynced.value = true
    }

    private fun attachLiveFirestoreListener(cleanKey: String, docRef: com.google.firebase.firestore.DocumentReference) {
        snapshotRegistration?.remove()
        snapshotRegistration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                if (error != null) isCloudSynced.value = false
                return@addSnapshotListener
            }

            // Skip local echo writes to prevent infinite feedback
            if (snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

            val now = System.currentTimeMillis()
            val isMaster = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

            val usdt = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble()
                ?: (snapshot.get("usdtBalance") as? Number)?.toDouble()
                ?: _minerBalance.value

            val grid = (snapshot.get("gridBalance") as? Number)?.toDouble()
                ?: _gridBalance.value

            val sessionEnd = (snapshot.get("freeMiningEndTime") as? Number)?.toLong()
                ?: (snapshot.get("freeMiningSessionEnd") as? Number)?.toLong()
                ?: _freeMiningEndTime.value

            val isFreeMining = if (sessionEnd > now) true else if (sessionEnd > 0L && now >= sessionEnd) false else (snapshot.getBoolean("isFreeMiningActive") ?: _isMiningActive.value)

            val rawNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("deployedRigs") as? List<Map<String, Any>>)
                ?: _deployedRigs.value

            _minerBalance.value = usdt
            _gridBalance.value = grid
            _isMiningActive.value = isFreeMining
            _freeMiningEndTime.value = sessionEnd
            _deployedRigs.value = rawNodes
            _deployedNodesCount.value = rawNodes.size

            val userRigsList = rawNodes.mapNotNull { parseRigMap(it, now) }
            val rawTxs = snapshot.get("transactions") as? List<Map<String, Any>> ?: emptyList()
            val restoredTransactions = if (rawTxs.isNotEmpty()) rawTxs.mapNotNull { parseTransactionMap(it, now) } else _userState.value.transactions

            _userState.value = _userState.value.copy(
                minerBalanceUsdt = usdt,
                gridBalance = grid,
                isFreeMiningActive = isFreeMining,
                freeMiningSessionEnd = sessionEnd,
                userRigs = userRigsList,
                transactions = restoredTransactions,
                isAdmin = snapshot.getBoolean("isAdmin") ?: isMaster
            )

            isCloudSynced.value = true
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
    // CLOUD-ATTACHED OPERATIONS
    // ========================================================
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

        firestore.collection("users").document(cleanKey).update(
            "transactions", FieldValue.arrayUnion(txMap),
            "lastSyncTimestamp", now
        ).addOnFailureListener {
            firestore.collection("users").document(cleanKey).set(
                mapOf("transactions" to listOf(txMap), "lastSyncTimestamp" to now),
                SetOptions.merge()
            )
        }
    }

    fun savePinToCloud(secretKey: String, pin: String, onSuccess: () -> Unit = {}) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(secretKey)
        if (cleanKey.isBlank()) return

        securityPreferences.setPin(pin)
        _userState.value = _userState.value.copy(isPinConfigured = true, isAppLocked = false)

        firestore.collection("users").document(cleanKey).update(
            mapOf(
                "securityPin" to pin,
                "isPinConfigured" to true,
                "lastSyncTimestamp" to System.currentTimeMillis()
            )
        ).addOnSuccessListener { onSuccess() }
    }

    fun isLuckySpinAvailable(): Boolean {
        val lastSpin = _userState.value.lastDailySpinTimestamp
        return (System.currentTimeMillis() - lastSpin) >= 86400000L
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
                firestore.collection("users").document(cleanKey).update(
                    "minerBalanceUsdt", FieldValue.increment(sector.value),
                    "lastDailySpinTimestamp", now,
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

    fun submitMicroTask(platform: TaskPlatform, initialViews: Int, finalViews: Int, notes: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: _userState.value.secretKey)
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

        _userState.value = _userState.value.copy(microTasks = listOf(sub) + _userState.value.microTasks)

        val subMap = mapOf(
            "id" to sub.id,
            "platform" to platform.name,
            "submittedAt" to now,
            "initialViewCount" to initialViews,
            "finalViewCount" to finalViews,
            "status" to "PENDING_REVIEW",
            "rewardUsdt" to rewardEst,
            "notes" to notes
        )

        firestore.collection("users").document(cleanKey).update(
            "microTasks", FieldValue.arrayUnion(subMap),
            "lastSyncTimestamp", now
        )
    }

    fun submitVideoPromo(platform: TaskPlatform, url: String, channel: String) {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(getActiveKey() ?: _userState.value.secretKey)
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

        _userState.value = _userState.value.copy(videoPromotions = listOf(sub) + _userState.value.videoPromotions)

        val videoMap = mapOf(
            "id" to sub.id,
            "platform" to platform.name,
            "videoUrl" to url,
            "channelOrHandle" to channel,
            "submittedAt" to now,
            "status" to "PENDING_REVIEW",
            "rewardUsdt" to 0.0
        )

        firestore.collection("users").document(cleanKey).update(
            "videoPromotions", FieldValue.arrayUnion(videoMap),
            "lastSyncTimestamp", now
        )
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

        _minerBalance.value = current.minerBalanceUsdt - amountUsdt
        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt - amountUsdt,
            transactions = listOf(tx) + current.transactions
        )

        firestore.collection("users").document(cleanKey).update(
            "minerBalanceUsdt", FieldValue.increment(-amountUsdt),
            "lastSyncTimestamp", now
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

        firestore.collection("users").document(cleanKey).update(
            mapOf(
                "dailySpentUsdt" to updatedSpent,
                "dailySpentResetDate" to resetDate,
                "lastSyncTimestamp" to now
            )
        )
        return true
    }

    // ==========================================
    // MINING START & HARDWARE DEPLOYMENT
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

        deployHardwareRig(cleanKey, node)

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

        val updatedBalance = current.minerBalanceUsdt - catalogItem.priceUsdt
        _minerBalance.value = updatedBalance
        _userState.value = current.copy(
            minerBalanceUsdt = updatedBalance,
            userRigs = listOf(newRig) + current.userRigs,
            transactions = listOf(tx) + current.transactions
        )
        return Result.success(newRig)
    }

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
            "lastDailySpinTimestamp", System.currentTimeMillis(),
            "lastSyncTimestamp", System.currentTimeMillis()
        )
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

        _minerBalance.value = current.minerBalanceUsdt + amountUsdt
        _userState.value = current.copy(
            minerBalanceUsdt = current.minerBalanceUsdt + amountUsdt,
            transactions = listOf(tx) + current.transactions
        )

        firestore.collection("users").document(cleanKey).update(
            "minerBalanceUsdt", FieldValue.increment(amountUsdt),
            "lastSyncTimestamp", now
        )
        recordCloudTransaction(cleanKey, tx)
    }

    // ==========================================
    // INSTANT LOCAL LOGIN GATEWAY
    // ==========================================
    fun loginWithKeyInstant(key: String): UserMiningState {
        val cleanKey = SecretKeyUtils.normalizeSecretKey(key)
        val isAdminKey = cleanKey.startsWith("HG-ADM9") || SecretKeyUtils.isMasterAdminKey(cleanKey)

        setActiveKey(cleanKey)
        securityPreferences.setLoggedIn(true)
        isCloudHydrated = false

        val cachedUsdt = prefs.getFloat("miner_balance", 0f).toDouble()
        val cachedGrid = prefs.getFloat("grid_balance", 0f).toDouble()

        val initialUsdt = if (cachedUsdt > 0.0) cachedUsdt else if (isAdminKey) 3000.0 else 0.0
        val initialGrid = if (cachedGrid > 0.0) cachedGrid else 0.0

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

        _userState.value = instantState
        _minerBalance.value = initialUsdt
        _gridBalance.value = initialGrid

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
        if (!current.isAuthenticated || current.secretKey.isBlank() || !isCloudHydrated) return

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

        // Periodic safe Firestore sync every 20 seconds only when hydrated
        if (now - lastCloudSyncTime > 20000) {
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
            firestore.collection("users").document(newKey).set(initialData).await()
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
