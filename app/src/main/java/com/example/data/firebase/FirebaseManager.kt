package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseManager(private val context: Context) {

    companion object {
        const val TAG = "HashGridFirebase"
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

            // Safely attempt FCM token retrieval with fallback to avoid hard failure exceptions
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = false
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful && task.result != null) {
                            val token = task.result
                            Log.d(TAG, "FCM Registration Token: $token")
                            saveFcmTokenToFirestore("HG-USER-8921", token)
                        } else {
                            Log.w(TAG, "FCM token not available. Using local device token identifier.")
                            saveFcmTokenToFirestore("HG-USER-8921", "fcm_token_device_${System.currentTimeMillis().toString().takeLast(6)}")
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Handled FCM token callback: ${e.message}")
                        saveFcmTokenToFirestore("HG-USER-8921", "fcm_token_device_hg8921")
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "FCM not supported on this device/environment: ${e.message}")
                saveFcmTokenToFirestore("HG-USER-8921", "fcm_token_device_hg8921")
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
     * Saves user under users/{secretKey} with activeMiningRigs, depositHistory, withdrawalHistory, and balances.
     */
    suspend fun saveUserUnderSecretKey(secretKey: String, state: UserMiningState): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()

            val activeRigsList = state.userRigs.map { rig ->
                mapOf(
                    "rigId" to rig.id,
                    "catalogId" to rig.catalogId,
                    "name" to rig.name,
                    "hashrate" to rig.hashrateGh,
                    "priceUsdt" to rig.priceUsdt,
                    "startTimestamp" to rig.purchaseTimestamp,
                    "expiryTimestamp" to rig.expiryTimestamp,
                    "durationDays" to rig.durationDays,
                    "dailyEarning" to (rig.priceUsdt * 0.15 / 30.0),
                    "status" to rig.status.name,
                    "totalReceivedUsdt" to rig.totalReceivedUsdt,
                    "thisMonthEarnedUsdt" to rig.thisMonthEarnedUsdt,
                    "lastYieldCalculatedTimestamp" to rig.lastYieldCalculatedTimestamp
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

            val docData = hashMapOf(
                "secretKey" to secretKey,
                "uid" to secretKey,
                "gridBalance" to state.gridBalance,
                "usdtBalance" to state.minerBalanceUsdt,
                "activeMiningRigs" to activeRigsList,
                "depositHistory" to depositsList,
                "withdrawalHistory" to withdrawalsList,
                "isFreeMiningActive" to state.isFreeMiningActive,
                "freeMiningSessionStart" to state.freeMiningSessionStart,
                "freeMiningSessionEnd" to state.freeMiningSessionEnd,
                "aggregateFreeHashrateGh" to state.aggregateFreeHashrateGh,
                "lastYieldTickTimestamp" to state.lastYieldTickTimestamp,
                "createdAt" to state.createdAt,
                "lastSyncTimestamp" to System.currentTimeMillis()
            )

            suspendCancellableCoroutine<Boolean> { continuation ->
                db.collection("users").document(secretKey)
                    .set(docData, SetOptions.merge())
                    .addOnSuccessListener {
                        continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save user under secret key: ${e.message}", e)
                        continuation.resume(false)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user under secret key: ${e.message}", e)
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
            val usdtBalance = snapshot.getDouble("usdtBalance") ?: 0.0
            val isFreeActive = snapshot.getBoolean("isFreeMiningActive") ?: false
            val sessionStart = snapshot.getLong("freeMiningSessionStart") ?: 0L
            val sessionEnd = snapshot.getLong("freeMiningSessionEnd") ?: 0L
            val lastYieldTick = snapshot.getLong("lastYieldTickTimestamp") ?: snapshot.getLong("lastSyncTimestamp") ?: System.currentTimeMillis()
            val createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis()

            // Parse Rigs
            val rigsRaw = snapshot.get("activeMiningRigs") as? List<Map<String, Any>> ?: emptyList()
            val restoredRigs = rigsRaw.mapNotNull { map ->
                try {
                    val id = map["rigId"] as? String ?: return@mapNotNull null
                    val name = map["name"] as? String ?: "Mining Node"
                    val catalogId = map["catalogId"] as? String ?: "starter_node"
                    val priceUsdt = (map["priceUsdt"] as? Number)?.toDouble() ?: 10.0
                    val hashrate = (map["hashrate"] as? Number)?.toDouble() ?: 2.0
                    val startTimestamp = (map["startTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val durationDays = (map["durationDays"] as? Number)?.toInt() ?: 200
                    val statusStr = map["status"] as? String ?: RigStatus.ACTIVE.name
                    val status = if (statusStr == RigStatus.COMPLETED.name) RigStatus.COMPLETED else RigStatus.ACTIVE
                    val totalReceivedUsdt = (map["totalReceivedUsdt"] as? Number)?.toDouble() ?: 0.0
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

            // Parse transactions
            val depositsRaw = snapshot.get("depositHistory") as? List<Map<String, Any>> ?: emptyList()
            val withdrawalsRaw = snapshot.get("withdrawalHistory") as? List<Map<String, Any>> ?: emptyList()

            val restoredTxList = mutableListOf<TransactionItem>()
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

            val restored = UserMiningState(
                uid = cleanKey,
                secretKey = cleanKey,
                email = "miner_${cleanKey.takeLast(4).lowercase()}@hashgrid.pro",
                nodeId = "NODE-WEB3-#${cleanKey.takeLast(4)}",
                gridBalance = gridBalance,
                minerBalanceUsdt = usdtBalance,
                isFreeMiningActive = isFreeActive,
                freeMiningSessionStart = sessionStart,
                freeMiningSessionEnd = sessionEnd,
                userRigs = restoredRigs,
                transactions = restoredTxList,
                lastYieldTickTimestamp = lastYieldTick,
                createdAt = createdAt,
                isKeyBackedUp = true
            )

            Result.success(restored)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore user by secret key: ${e.message}", e)
            Result.failure(e)
        }
    }
}
