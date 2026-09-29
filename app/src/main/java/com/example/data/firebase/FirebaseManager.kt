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

class FirebaseManager(private val context: Context) {

    companion object {
        const val TAG = "HashGridFirebase"
        const val API_KEY = "AIzaSyD8XqcWgN9EbaUSBVYhf5oBk1qgo3lHnyA"
        const val AUTH_DOMAIN = "hashgrid-b850b.firebaseapp.com"
        const val DATABASE_URL = "https://hashgrid-b850b-default-rtdb.asia-southeast1.firebasedatabase.app"
        const val PROJECT_ID = "hashgrid-b850b"
        const val STORAGE_BUCKET = "hashgrid-b850b.firebasestorage.app"
        const val SENDER_ID = "621346408367"
        const val APP_ID = "1:621346408367:web:7621be60d110b9105a915a"
    }

    private var firestore: FirebaseFirestore? = null

    init {
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APP_ID)
                    .setDatabaseUrl(DATABASE_URL)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .setGcmSenderId(SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized with hashgrid-b850b options.")
            }
            firestore = FirebaseFirestore.getInstance()
            Log.d(TAG, "Firestore instance retrieved successfully.")

            // Fetch and register FCM Token
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result
                    Log.d(TAG, "Initial FCM Registration Token: $token")
                    saveFcmTokenToFirestore("HG-USER-8921", token)
                }
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
}
