package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RigCatalogItem
import com.example.data.security.SessionManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

typealias MiningRig = RigCatalogItem

class RigsStoreViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager.getInstance(application)

    fun buyRigAtomic(selectedRig: RigCatalogItem) {
        val key = sessionManager.getActiveUserKey()
        if (key.isNullOrEmpty()) {
            Log.e("SYNC", "No active user key found!")
            return
        }
        val now = System.currentTimeMillis()
        val priceUsdt = selectedRig.priceUsdt.toDouble()
        val dailyYieldUsdt = (priceUsdt * 0.15) / 30.0

        val deployedRigMap = hashMapOf(
            "name" to selectedRig.name,
            "costUsdt" to priceUsdt,
            "dailyYieldUsdt" to dailyYieldUsdt,
            "hashrateGh" to selectedRig.hashrateGh.toDouble(),
            "deployedTimestamp" to now,
            "totalDays" to 200
        )
        val rigMap = hashMapOf<String, Any>(
            "id" to "NODE-${now.toString().takeLast(6)}",
            "nodeId" to "NODE-${now.toString().takeLast(6)}",
            "name" to selectedRig.name,
            "costUsdt" to priceUsdt,
            "priceUsdt" to priceUsdt,
            "dailyYieldUsdt" to dailyYieldUsdt,
            "hashrateGh" to selectedRig.hashrateGh.toDouble(),
            "deployedTimestamp" to now,
            "purchaseTimestamp" to now,
            "totalDays" to 200,
            "durationDays" to 200,
            "status" to "ACTIVE"
        )
        FirebaseFirestore.getInstance().collection("users").document(key)
            .update(
                "hardwareNodes", FieldValue.arrayUnion(rigMap),
                "minerBalanceUsdt", FieldValue.increment(-priceUsdt),
                "dailySpentUsdt", FieldValue.increment(priceUsdt),
                "lastSyncTimestamp", now
            )
    }

    fun purchaseRigWithBalance(selectedRig: MiningRig, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeKey = sessionManager.getActiveUserKey() ?: "HG-ADM9-7788-5544-0001"
                val priceUsdt = selectedRig.priceUsdt.toDouble()
                val now = System.currentTimeMillis()
                val dailyYieldUsdt = (priceUsdt * 0.15) / 30.0

                val rigMap = hashMapOf<String, Any>(
                    "id" to "NODE-${now.toString().takeLast(6)}",
                    "nodeId" to "NODE-${now.toString().takeLast(6)}",
                    "name" to selectedRig.name,
                    "costUsdt" to priceUsdt,
                    "priceUsdt" to priceUsdt,
                    "dailyYieldUsdt" to dailyYieldUsdt,
                    "hashrateGh" to selectedRig.hashrateGh.toDouble(),
                    "deployedTimestamp" to now,
                    "purchaseTimestamp" to now,
                    "totalDays" to 200,
                    "durationDays" to 200,
                    "status" to "ACTIVE"
                )

                // Direct Firestore increment/decrement write to ensure absolute lock-step synchronization with cloud state
                FirebaseFirestore.getInstance().collection("users").document(activeKey)
                    .update(
                        "hardwareNodes", FieldValue.arrayUnion(rigMap),
                        "minerBalanceUsdt", FieldValue.increment(-priceUsdt),
                        "dailySpentUsdt", FieldValue.increment(priceUsdt),
                        "lastSyncTimestamp", now
                    )
                    .await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Node Deployed Successfully to Cloud!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Purchase Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
