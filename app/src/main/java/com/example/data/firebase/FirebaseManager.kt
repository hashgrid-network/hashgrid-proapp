package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.security.SecretKeyUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseManager(private val context: Context) {

    companion object {
        const val TAG = "FIREBASE_SYNC"
        const val API_KEY = "AIzaSyCMDAfHJ6awiJYRDoJ1PR-UMC7yF8_kauc"
        const val AUTH_DOMAIN = "hashgrid-c7fe4.firebaseapp.com"
        const val PROJECT_ID = "hashgrid-c7fe4"
        const val STORAGE_BUCKET = "hashgrid-c7fe4.firebasestorage.app"
        const val SENDER_ID = "885427334784"
        const val APP_ID = "1:885427334784:android:3b18fbfb14a82cb36e0065"
    }

    private var firestore: FirebaseFirestore? = null

    init {
        try {
            initializeFirebase()
        } catch (e: Throwable) {
            Log.e(TAG, "FirebaseManager init safe catch: ${e.message}", e)
        }
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (e: Throwable) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(API_KEY)
                        .setApplicationId(APP_ID)
                        .setProjectId(PROJECT_ID)
                        .setStorageBucket(STORAGE_BUCKET)
                        .setGcmSenderId(SENDER_ID)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
                Log.d(TAG, "Firebase initialized for hashgrid-c7fe4.")
            }

            try {
                val db = FirebaseFirestore.getInstance()
                try {
                    // Enable Offline Persistence with local cache
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    db.firestoreSettings = settings
                    Log.d(TAG, "Firestore Offline Persistence enabled.")
                } catch (e: Throwable) {
                    Log.d(TAG, "Firestore settings already configured: ${e.message}")
                }
                firestore = db
                Log.d(TAG, "Firestore instance retrieved successfully.")
            } catch (e: Throwable) {
                Log.w(TAG, "Could not initialize Firestore on this environment: ${e.message}")
            }

            // FCM Messaging initialization without creating dummy accounts
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
            } catch (e: Throwable) {
                Log.w(TAG, "FCM messaging note: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}", e)
        }
    }

    fun saveFcmTokenToFirestore(userId: String, token: String) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .set(mapOf("fcmToken" to token, "fcmTokenUpdatedAt" to System.currentTimeMillis()), SetOptions.merge())
            Log.d(TAG, "FCM token saved for user $userId.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save FCM token: ${e.message}", e)
        }
    }

    /**
     * Saves or updates a transaction in both users/{userId}/transactions/{txId} and root transactions/{txId}.
     */
    suspend fun saveOrUpdateTransaction(userId: String, tx: TransactionItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            val txData = hashMapOf(
                "id" to tx.id,
                "userId" to userId,
                "type" to tx.type.name,
                "amount" to tx.amount,
                "currency" to tx.currency,
                "timestamp" to tx.timestamp,
                "status" to tx.status.name.lowercase(),
                "description" to tx.description,
                "network" to (tx.network ?: ""),
                "txHash" to (tx.txHash ?: ""),
                "paymentId" to (tx.paymentId ?: ""),
                "payAddress" to (tx.payAddress ?: ""),
                "payAmount" to (tx.payAmount ?: tx.amount),
                "payCurrency" to (tx.payCurrency ?: "usdt"),
                "nowPaymentsStatus" to (tx.nowPaymentsStatus ?: tx.status.name.lowercase()),
                "targetRigCatalogId" to (tx.targetRigCatalogId ?: ""),
                "lastUpdatedTimestamp" to System.currentTimeMillis()
            )

            // 1. users/{userId}/transactions/{txId}
            db.collection("users").document(userId)
                .collection("transactions").document(tx.id)
                .set(txData, SetOptions.merge())

            // 2. Root transactions/{txId}
            db.collection("transactions").document(tx.id)
                .set(txData, SetOptions.merge())

            Log.d(TAG, "Saved transaction ${tx.id} with status ${tx.status} to Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save transaction: ${e.message}", e)
            false
        }
    }

    /**
     * Automatically updates user balance & adds/deploys the newly purchased hardware node in Firestore.
     */
    suspend fun recordPlanActivation(userId: String, rig: UserRig, newMinerBalance: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()

            // Update user balance & stats
            db.collection("users").document(userId)
                .update(
                    mapOf(
                        "minerBalanceUsdt" to newMinerBalance,
                        "lastUpdatedTimestamp" to System.currentTimeMillis()
                    )
                )

            // Add new deployed rig to rigs/{rigId}
            val rigData = hashMapOf(
                "id" to rig.id,
                "userId" to userId,
                "catalogId" to rig.catalogId,
                "name" to rig.name,
                "priceUsdt" to rig.priceUsdt,
                "hashrateGh" to rig.hashrateGh,
                "purchaseTimestamp" to rig.purchaseTimestamp,
                "durationDays" to rig.durationDays,
                "status" to rig.status.name,
                "totalReceivedUsdt" to rig.totalReceivedUsdt,
                "thisMonthEarnedUsdt" to rig.thisMonthEarnedUsdt,
                "expiryTimestamp" to rig.expiryTimestamp,
                "lastUpdatedTimestamp" to System.currentTimeMillis()
            )
            db.collection("rigs").document(rig.id)
                .set(rigData, SetOptions.merge())

            Log.d(TAG, "Plan activated in Firestore for rig ${rig.id}.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record plan activation: ${e.message}", e)
            false
        }
    }

    suspend fun syncUserStateToFirestore(state: UserMiningState): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()

            // 1. Sync User Document: users/{uid}
            val userData = hashMapOf(
                "uid" to state.uid,
                "email" to state.email,
                "nodeId" to state.nodeId,
                "minerBalanceUsdt" to state.minerBalanceUsdt,
                "gridBalance" to state.gridBalance,
                "baseFreeHashrateGh" to state.baseFreeHashrateGh,
                "referralCount" to state.referralCount,
                "activeReferredMiners" to state.activeReferredMiners,
                "referralCode" to state.referralCode,
                "isFreeMiningActive" to state.isFreeMiningActive,
                "freeMiningSessionStart" to state.freeMiningSessionStart,
                "freeMiningSessionEnd" to state.freeMiningSessionEnd,
                "temporaryBoostHashrateGh" to state.temporaryBoostHashrateGh,
                "temporaryBoostExpiry" to state.temporaryBoostExpiry,
                "dailySpentUsdt" to state.dailySpentUsdt,
                "lastDailySpinTimestamp" to state.lastDailySpinTimestamp,
                "totalAggregateHashrateGh" to state.totalAggregateHashrateGh,
                "isAdmin" to state.isAdmin,
                "role" to state.role,
                "lastUpdatedTimestamp" to System.currentTimeMillis()
            )

            db.collection("users").document(state.uid)
                .set(userData, SetOptions.merge())

            // 2. Sync Deployed Rigs: rigs/{rigId}
            state.userRigs.forEach { rig ->
                val rigData = hashMapOf(
                    "id" to rig.id,
                    "userId" to state.uid,
                    "catalogId" to rig.catalogId,
                    "name" to rig.name,
                    "priceUsdt" to rig.priceUsdt,
                    "hashrateGh" to rig.hashrateGh,
                    "purchaseTimestamp" to rig.purchaseTimestamp,
                    "durationDays" to rig.durationDays,
                    "status" to rig.status.name,
                    "totalReceivedUsdt" to rig.totalReceivedUsdt,
                    "thisMonthEarnedUsdt" to rig.thisMonthEarnedUsdt,
                    "expiryTimestamp" to rig.expiryTimestamp,
                    "lastUpdatedTimestamp" to System.currentTimeMillis()
                )
                db.collection("rigs").document(rig.id)
                    .set(rigData, SetOptions.merge())
            }

            // 3. Sync Transactions: transactions/{txId} & users/{userId}/transactions/{txId}
            state.transactions.take(15).forEach { tx ->
                saveOrUpdateTransaction(state.uid, tx)
            }

            // 4. Sync Lucky Spins: luckySpins/{id}
            state.spinHistory.take(10).forEach { spin ->
                val spinData = hashMapOf(
                    "id" to spin.id,
                    "userId" to state.uid,
                    "rewardTitle" to spin.rewardTitle,
                    "rewardSubtitle" to spin.rewardSubtitle,
                    "timestamp" to spin.timestamp,
                    "rewardType" to spin.rewardType.name,
                    "value" to spin.value
                )
                db.collection("luckySpins").document(spin.id)
                db.collection("luckySpins").document(spin.id)
                    .set(spinData, SetOptions.merge())
            }

            // 5. Sync Promotion Submissions: promotionSubmissions/{id}
            state.microTasks.forEach { task ->
                val taskData = hashMapOf(
                    "id" to task.id,
                    "userId" to state.uid,
                    "platform" to task.platform.name,
                    "submittedAt" to task.submittedAt,
                    "initialViewCount" to task.initialViewCount,
                    "finalViewCount" to task.finalViewCount,
                    "status" to task.status.name,
                    "rewardUsdt" to task.rewardUsdt,
                    "notes" to task.notes
                )
                db.collection("promotionSubmissions").document(task.id)
                    .set(taskData, SetOptions.merge())
            }

            Log.d(TAG, "Successfully synced state to Firestore collections.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync error: ${e.message}", e)
            false
        }
    }

    /**
     * Saves user under users/{secretKey} with strict hardwareNodes array, timestamps, and balances.
     */
    suspend fun saveUserUnderSecretKey(secretKey: String, state: UserMiningState): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()

            val hardwareNodesList = state.userRigs.map { rig ->
                mapOf(
                    "nodeId" to rig.id,
                    "nodeName" to rig.name,
                    "costUsdt" to rig.priceUsdt,
                    "hashrateGh" to rig.hashrateGh,
                    "purchaseTimestamp" to rig.purchaseTimestamp,
                    "totalDays" to rig.durationDays,
                    "daysRemaining" to rig.daysRemaining(),
                    "receivedUsdt" to rig.totalReceivedUsdt,
                    "status" to rig.status.name,
                    "catalogId" to rig.catalogId,
                    "thisMonthEarnedUsdt" to rig.thisMonthEarnedUsdt,
                    "lastYieldCalculatedTimestamp" to rig.lastYieldCalculatedTimestamp,
                    "expiryTimestamp" to rig.expiryTimestamp,
                    // Alias fields for backwards compatibility
                    "rigId" to rig.id,
                    "name" to rig.name,
                    "priceUsdt" to rig.priceUsdt,
                    "hashrate" to rig.hashrateGh,
                    "durationDays" to rig.durationDays,
                    "totalReceivedUsdt" to rig.totalReceivedUsdt,
                    "startTimestamp" to rig.purchaseTimestamp
                )
            }

            val depositsList = state.transactions.filter { it.type == TransactionType.DEPOSIT }.map { tx ->
                mapOf(
                    "id" to tx.id,
                    "amount" to tx.amount,
                    "currency" to tx.currency,
                    "timestamp" to tx.timestamp,
                    "status" to tx.status.name,
                    "description" to tx.description,
                    "txHash" to (tx.txHash ?: ""),
                    "network" to (tx.network ?: "")
                )
            }

            val withdrawalsList = state.transactions.filter { it.type == TransactionType.WITHDRAWAL }.map { tx ->
                mapOf(
                    "id" to tx.id,
                    "amount" to tx.amount,
                    "currency" to tx.currency,
                    "timestamp" to tx.timestamp,
                    "status" to tx.status.name,
                    "description" to tx.description,
                    "address" to (tx.address ?: "")
                )
            }

            val allTransactionsList = state.transactions.map { tx ->
                mapOf(
                    "id" to tx.id,
                    "type" to tx.type.name,
                    "amount" to tx.amount,
                    "currency" to tx.currency,
                    "timestamp" to tx.timestamp,
                    "status" to tx.status.name,
                    "description" to tx.description,
                    "address" to (tx.address ?: ""),
                    "network" to (tx.network ?: ""),
                    "txHash" to (tx.txHash ?: ""),
                    "paymentId" to (tx.paymentId ?: "")
                )
            }

            val spinHistoryList = state.spinHistory.map { spin ->
                mapOf(
                    "id" to spin.id,
                    "rewardTitle" to spin.rewardTitle,
                    "rewardSubtitle" to spin.rewardSubtitle,
                    "timestamp" to spin.timestamp,
                    "rewardType" to spin.rewardType.name,
                    "value" to spin.value
                )
            }

            val microTasksList = state.microTasks.map { task ->
                mapOf(
                    "id" to task.id,
                    "platform" to task.platform.name,
                    "submittedAt" to task.submittedAt,
                    "initialViewCount" to task.initialViewCount,
                    "finalViewCount" to task.finalViewCount,
                    "status" to task.status.name,
                    "rewardUsdt" to task.rewardUsdt,
                    "notes" to task.notes
                )
            }

            val videoPromotionsList = state.videoPromotions.map { promo ->
                mapOf(
                    "id" to promo.id,
                    "platform" to promo.platform.name,
                    "videoUrl" to promo.videoUrl,
                    "channelOrHandle" to promo.channelOrHandle,
                    "submittedAt" to promo.submittedAt,
                    "estimatedViews" to promo.estimatedViews,
                    "status" to promo.status.name,
                    "rewardUsdt" to promo.rewardUsdt,
                    "reviewerFeedback" to (promo.reviewerFeedback ?: "")
                )
            }

            val now = System.currentTimeMillis()
            val docData = hashMapOf<String, Any?>(
                "uid" to secretKey,
                "secretKey" to secretKey,
                "nodeId" to "NODE-WEB3-#${secretKey.takeLast(4)}",
                "isAdmin" to (state.isAdmin || SecretKeyUtils.isMasterAdminKey(secretKey)),
                "minerBalanceUsdt" to state.minerBalanceUsdt,
                "usdtBalance" to state.minerBalanceUsdt,
                "gridBalance" to state.gridBalance,
                "totalAggregateHashrateGh" to state.totalAggregateHashrateGh,
                "totalHashrate" to state.totalAggregateHashrateGh,
                "baseFreeHashrateGh" to state.baseFreeHashrateGh,
                "isFreeMiningActive" to state.isFreeMiningActive,
                "isMiningActive" to state.isFreeMiningActive,
                "freeMiningSessionStart" to state.freeMiningSessionStart,
                "freeMiningSessionEnd" to state.freeMiningSessionEnd,
                "miningStartTime" to state.freeMiningSessionStart,
                "miningEndTime" to state.freeMiningSessionEnd,
                "lastUpdatedTimestamp" to now,
                "lastYieldTimestamp" to state.lastYieldTickTimestamp,
                "lastYieldTickTimestamp" to state.lastYieldTickTimestamp,
                "referralCode" to (if (state.referralCode.isNotBlank()) state.referralCode else secretKey),
                "referredBy" to state.referredBy,
                "referralCount" to state.referralCount,
                "activeReferredMiners" to state.activeReferredMiners,
                "hardwareNodes" to hardwareNodesList,
                "activeMiningRigs" to hardwareNodesList,
                "depositHistory" to depositsList,
                "withdrawalHistory" to withdrawalsList,
                "transactions" to allTransactionsList,
                "spinHistory" to spinHistoryList,
                "microTasks" to microTasksList,
                "videoPromotions" to videoPromotionsList,
                "isOnline" to true,
                "onlineStatus" to "ONLINE",
                "lastOnlineTimestamp" to now,
                "lastSeenTimestamp" to now,
                "email" to state.email,
                "temporaryBoostHashrateGh" to state.temporaryBoostHashrateGh,
                "temporaryBoostExpiry" to state.temporaryBoostExpiry,
                "dailySpentUsdt" to state.dailySpentUsdt,
                "lastDailySpinTimestamp" to state.lastDailySpinTimestamp,
                "isKeyBackedUp" to state.isKeyBackedUp,
                "isPinConfigured" to state.isPinConfigured,
                "isBiometricEnabled" to state.isBiometricEnabled,
                "role" to (if (state.isAdmin || SecretKeyUtils.isMasterAdminKey(secretKey)) "superadmin" else state.role),
                "createdAt" to state.createdAt,
                "lastSyncTimestamp" to now
            )

            suspendCancellableCoroutine<Boolean> { continuation ->
                db.collection("users").document(secretKey)
                    .set(docData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FIREBASE_SYNC", "SUCCESS: User state and all activities saved to Firestore: $secretKey")
                        if (continuation.isActive) continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FIREBASE_SYNC", "FAILED to write user to Firestore", e)
                        if (continuation.isActive) continuation.resume(false)
                    }
            }
        } catch (e: Exception) {
            Log.e("FIREBASE_SYNC", "FAILED to write user to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Records any user activity (both online actions and offline catchup events)
     * to users/{userId}/activity_logs/{logId} and global activity_logs/{logId}.
     */
    suspend fun recordActivityLog(
        userId: String,
        action: String,
        details: Map<String, Any?> = emptyMap()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext false
            val db = firestore ?: FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            val logId = "log-$now-${UUID.randomUUID().toString().take(6)}"
            val logData = hashMapOf<String, Any?>(
                "id" to logId,
                "userId" to userId,
                "action" to action,
                "timestamp" to now,
                "details" to details,
                "platform" to "Android"
            )
            // 1. users/{userId}/activity_logs/{logId}
            db.collection("users").document(userId)
                .collection("activity_logs").document(logId)
                .set(logData, SetOptions.merge())

            // 2. Global activity_logs/{logId}
            db.collection("activity_logs").document(logId)
                .set(logData, SetOptions.merge())

            Log.d("FIREBASE_SYNC", "Activity logged for user $userId: $action")
            true
        } catch (e: Exception) {
            Log.e("FIREBASE_SYNC", "Failed to record activity log: ${e.message}", e)
            false
        }
    }

    /**
     * Updates real-time online/offline presence status in Firestore.
     */
    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext false
            val db = firestore ?: FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            val updateData = hashMapOf<String, Any?>(
                "isOnline" to isOnline,
                "onlineStatus" to (if (isOnline) "ONLINE" else "OFFLINE"),
                "lastSeenTimestamp" to now,
                if (isOnline) "lastOnlineTimestamp" to now else "lastOfflineTimestamp" to now
            )
            db.collection("users").document(userId)
                .set(updateData, SetOptions.merge())
            Log.d("FIREBASE_SYNC", "Presence updated for user $userId: ${if (isOnline) "ONLINE" else "OFFLINE"}")
            true
        } catch (e: Exception) {
            Log.e("FIREBASE_SYNC", "Failed to update online presence: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if a user document exists in users/{secretKey}.
     */
    suspend fun checkUserDocumentExists(secretKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            val cleanKey = secretKey.trim().uppercase()
            val snapshot = suspendCancellableCoroutine<DocumentSnapshot> { continuation ->
                db.collection("users").document(cleanKey).get()
                    .addOnSuccessListener { doc -> continuation.resume(doc) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }
            snapshot.exists()
        } catch (e: Exception) {
            Log.w(TAG, "checkUserDocumentExists error: ${e.message}")
            false
        }
    }

    /**
     * Restores user profile and transaction/rig history from users/{secretKey}.
     */
    suspend fun restoreUserBySecretKey(secretKey: String): Result<UserMiningState?> = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            val cleanKey = secretKey.trim().uppercase()

            val snapshot = suspendCancellableCoroutine<DocumentSnapshot> { continuation ->
                db.collection("users").document(cleanKey).get()
                    .addOnSuccessListener { doc ->
                        continuation.resume(doc)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            }

            if (!snapshot.exists()) {
                return@withContext Result.failure(Exception("No account found matching Secret Key: $cleanKey"))
            }

            val gridBalance = snapshot.getDouble("gridBalance") ?: 0.0
            val usdtBalance = snapshot.getDouble("usdtBalance") ?: snapshot.getDouble("minerBalanceUsdt") ?: 0.0
            val isMiningActive = snapshot.getBoolean("isMiningActive") ?: snapshot.getBoolean("isFreeMiningActive") ?: false
            val sessionStart = snapshot.getLong("miningStartTime") ?: snapshot.getLong("freeMiningSessionStart") ?: 0L
            val sessionEnd = snapshot.getLong("miningEndTime") ?: snapshot.getLong("freeMiningSessionEnd") ?: 0L
            val lastYieldTick = snapshot.getLong("lastYieldTimestamp") ?: snapshot.getLong("lastYieldTickTimestamp") ?: snapshot.getLong("lastSyncTimestamp") ?: System.currentTimeMillis()
            val referralCode = snapshot.getString("referralCode") ?: "HG-${cleanKey.takeLast(4)}"
            val referredBy = snapshot.getString("referredBy")
            val baseFreeHashrate = snapshot.getDouble("baseFreeHashrateGh") ?: 1.0
            val referralCount = (snapshot.getLong("referralCount") ?: 3L).toInt()
            val activeReferredMiners = (snapshot.getLong("activeReferredMiners") ?: 2L).toInt()
            val tempBoostGh = snapshot.getDouble("temporaryBoostHashrateGh") ?: 0.0
            val tempBoostExpiry = snapshot.getLong("temporaryBoostExpiry") ?: 0L
            val lastDailySpin = snapshot.getLong("lastDailySpinTimestamp") ?: 0L
            val createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis()
            val isMasterAdmin = SecretKeyUtils.isMasterAdminKey(cleanKey) || (snapshot.getBoolean("isAdmin") ?: false)
            val userRole = if (isMasterAdmin) "superadmin" else (snapshot.getString("role") ?: "user")

            // Parse Hardware Nodes / Rigs
            val nodesRaw = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)
                ?: (snapshot.get("activeMiningRigs") as? List<Map<String, Any>>)
                ?: emptyList()

            val restoredRigs = nodesRaw.mapNotNull { map ->
                try {
                    val id = (map["nodeId"] as? String) ?: (map["rigId"] as? String) ?: (map["id"] as? String) ?: return@mapNotNull null
                    val name = (map["nodeName"] as? String) ?: (map["name"] as? String) ?: "Mining Node"
                    val catalogId = (map["catalogId"] as? String) ?: "starter_node"
                    val priceUsdt = (map["costUsdt"] as? Number)?.toDouble() ?: (map["priceUsdt"] as? Number)?.toDouble() ?: 10.0
                    val hashrate = (map["hashrateGh"] as? Number)?.toDouble() ?: (map["hashrate"] as? Number)?.toDouble() ?: 2.0
                    val startTimestamp = (map["purchaseTimestamp"] as? Number)?.toLong() ?: (map["startTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val durationDays = (map["totalDays"] as? Number)?.toInt() ?: (map["durationDays"] as? Number)?.toInt() ?: 200
                    val statusStr = (map["status"] as? String)?.uppercase() ?: RigStatus.ACTIVE.name
                    val status = if (statusStr == RigStatus.COMPLETED.name) RigStatus.COMPLETED else RigStatus.ACTIVE
                    val totalReceivedUsdt = (map["receivedUsdt"] as? Number)?.toDouble() ?: (map["totalReceivedUsdt"] as? Number)?.toDouble() ?: 0.0
                    val thisMonthEarnedUsdt = (map["thisMonthEarnedUsdt"] as? Number)?.toDouble() ?: 0.0
                    val lastCalculated = (map["lastYieldCalculatedTimestamp"] as? Number)?.toLong() ?: startTimestamp

                    UserRig(
                        id = id,
                        catalogId = catalogId,
                        name = name,
                        priceUsdt = priceUsdt,
                        hashrateGh = hashrate,
                        purchaseTimestamp = startTimestamp,
                        durationDays = durationDays,
                        status = status,
                        totalReceivedUsdt = totalReceivedUsdt,
                        thisMonthEarnedUsdt = thisMonthEarnedUsdt,
                        lastYieldCalculatedTimestamp = lastCalculated
                    )
                } catch (e: Exception) {
                    null
                }
            }

            // Parse all transactions
            val allTxRaw = snapshot.get("transactions") as? List<Map<String, Any>>
            val depositsRaw = snapshot.get("depositHistory") as? List<Map<String, Any>> ?: emptyList()
            val withdrawalsRaw = snapshot.get("withdrawalHistory") as? List<Map<String, Any>> ?: emptyList()

            val restoredTxList = mutableListOf<TransactionItem>()

            if (!allTxRaw.isNullOrEmpty()) {
                allTxRaw.forEach { map ->
                    try {
                        val typeStr = map["type"] as? String ?: TransactionType.DEPOSIT.name
                        val type = try { TransactionType.valueOf(typeStr) } catch (_: Exception) { TransactionType.DEPOSIT }
                        val statusStr = map["status"] as? String ?: TransactionStatus.COMPLETED.name
                        val status = try { TransactionStatus.valueOf(statusStr) } catch (_: Exception) { TransactionStatus.COMPLETED }
                        restoredTxList.add(
                            TransactionItem(
                                id = map["id"] as? String ?: "tx-${System.currentTimeMillis()}",
                                type = type,
                                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                                currency = map["currency"] as? String ?: "USDT",
                                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                status = status,
                                description = map["description"] as? String ?: "Transaction Record",
                                address = map["address"] as? String,
                                network = map["network"] as? String,
                                txHash = map["txHash"] as? String,
                                paymentId = map["paymentId"] as? String
                            )
                        )
                    } catch (_: Exception) {}
                }
            } else {
                depositsRaw.forEach { map ->
                    try {
                        restoredTxList.add(
                            TransactionItem(
                                id = map["id"] as? String ?: "tx-${System.currentTimeMillis()}",
                                type = TransactionType.DEPOSIT,
                                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                                currency = map["currency"] as? String ?: "USDT",
                                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                status = TransactionStatus.COMPLETED,
                                description = map["description"] as? String ?: "Deposit Confirmed",
                                txHash = map["txHash"] as? String,
                                network = map["network"] as? String
                            )
                        )
                    } catch (_: Exception) {}
                }
                withdrawalsRaw.forEach { map ->
                    try {
                        restoredTxList.add(
                            TransactionItem(
                                id = map["id"] as? String ?: "tx-${System.currentTimeMillis()}",
                                type = TransactionType.WITHDRAWAL,
                                amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
                                currency = map["currency"] as? String ?: "USDT",
                                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                status = TransactionStatus.COMPLETED,
                                description = map["description"] as? String ?: "Withdrawal Completed",
                                address = map["address"] as? String
                            )
                        )
                    } catch (_: Exception) {}
                }
            }

            // Parse spinHistory
            val spinRaw = snapshot.get("spinHistory") as? List<Map<String, Any>> ?: emptyList()
            val restoredSpins = spinRaw.mapNotNull { map ->
                try {
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val title = map["rewardTitle"] as? String ?: "Reward"
                    val subtitle = map["rewardSubtitle"] as? String ?: ""
                    val time = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val typeStr = map["rewardType"] as? String ?: SpinRewardType.GRID_TOKENS.name
                    val rType = try { SpinRewardType.valueOf(typeStr) } catch (_: Exception) { SpinRewardType.GRID_TOKENS }
                    val value = (map["value"] as? Number)?.toDouble() ?: 0.0
                    SpinHistoryRecord(id, title, subtitle, time, rType, value)
                } catch (_: Exception) { null }
            }

            // Parse microTasks
            val microRaw = snapshot.get("microTasks") as? List<Map<String, Any>> ?: emptyList()
            val restoredMicroTasks = microRaw.mapNotNull { map ->
                try {
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val platStr = map["platform"] as? String ?: TaskPlatform.WHATSAPP_STATUS.name
                    val platform = try { TaskPlatform.valueOf(platStr) } catch (_: Exception) { TaskPlatform.WHATSAPP_STATUS }
                    val submittedAt = (map["submittedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val initViews = (map["initialViewCount"] as? Number)?.toInt() ?: 0
                    val finalViews = (map["finalViewCount"] as? Number)?.toInt() ?: 0
                    val statusStr = map["status"] as? String ?: PromoStatus.PENDING_REVIEW.name
                    val status = try { PromoStatus.valueOf(statusStr) } catch (_: Exception) { PromoStatus.PENDING_REVIEW }
                    val rewardUsdt = (map["rewardUsdt"] as? Number)?.toDouble() ?: 0.0
                    val notes = map["notes"] as? String ?: ""
                    MicroTaskSubmission(id, platform, submittedAt, initViews, finalViews, status, rewardUsdt, notes)
                } catch (_: Exception) { null }
            }

            // Parse videoPromotions
            val videoRaw = snapshot.get("videoPromotions") as? List<Map<String, Any>> ?: emptyList()
            val restoredVideos = videoRaw.mapNotNull { map ->
                try {
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val platStr = map["platform"] as? String ?: TaskPlatform.YOUTUBE_VIDEO.name
                    val platform = try { TaskPlatform.valueOf(platStr) } catch (_: Exception) { TaskPlatform.YOUTUBE_VIDEO }
                    val videoUrl = map["videoUrl"] as? String ?: ""
                    val channel = map["channelOrHandle"] as? String ?: ""
                    val submittedAt = (map["submittedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val estViews = (map["estimatedViews"] as? Number)?.toInt() ?: 0
                    val statusStr = map["status"] as? String ?: PromoStatus.PENDING_REVIEW.name
                    val status = try { PromoStatus.valueOf(statusStr) } catch (_: Exception) { PromoStatus.PENDING_REVIEW }
                    val rewardUsdt = (map["rewardUsdt"] as? Number)?.toDouble() ?: 0.0
                    val feedback = map["reviewerFeedback"] as? String
                    VideoPromotionSubmission(id, platform, videoUrl, channel, submittedAt, estViews, status, rewardUsdt, feedback)
                } catch (_: Exception) { null }
            }

            val restored = UserMiningState(
                uid = cleanKey,
                secretKey = cleanKey,
                email = "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
                nodeId = "NODE-WEB3-#${cleanKey.takeLast(4)}",
                gridBalance = gridBalance,
                minerBalanceUsdt = usdtBalance,
                baseFreeHashrateGh = baseFreeHashrate,
                referralCount = referralCount,
                activeReferredMiners = activeReferredMiners,
                temporaryBoostHashrateGh = tempBoostGh,
                temporaryBoostExpiry = tempBoostExpiry,
                referralCode = referralCode,
                referredBy = referredBy,
                lastDailySpinTimestamp = lastDailySpin,
                isFreeMiningActive = isMiningActive,
                freeMiningSessionStart = sessionStart,
                freeMiningSessionEnd = sessionEnd,
                userRigs = restoredRigs,
                transactions = restoredTxList,
                spinHistory = restoredSpins,
                microTasks = restoredMicroTasks,
                videoPromotions = restoredVideos,
                lastYieldTickTimestamp = lastYieldTick,
                createdAt = createdAt,
                isKeyBackedUp = true,
                isAdmin = isMasterAdmin,
                role = userRole
            )

            Result.success(restored)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore user by secret key: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Listens to real-time changes in system_settings/config for dynamic grid_price_usd.
     */
    fun listenToSystemSettings(onPriceUpdated: (Double) -> Unit): ListenerRegistration? {
        return try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            db.collection("system_settings").document("config")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "System settings listener note: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val priceNum = snapshot.get("grid_price_usd") as? Number
                        if (priceNum != null) {
                            val price = priceNum.toDouble()
                            Log.d(TAG, "Real-time GRID token price from Firestore: $price USD")
                            onPriceUpdated(price)
                        }
                    }
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to attach system settings listener: ${e.message}")
            null
        }
    }

    /**
     * Updates grid_price_usd in system_settings/config in Firestore.
     */
    suspend fun updateGridPrice(newPrice: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            val data = mapOf(
                "grid_price_usd" to newPrice,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("system_settings").document("config")
                .set(data, SetOptions.merge())
            Log.d(TAG, "Updated system_settings/config with grid_price_usd = $newPrice")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update grid_price_usd in Firestore: ${e.message}", e)
            false
        }
    }
}

