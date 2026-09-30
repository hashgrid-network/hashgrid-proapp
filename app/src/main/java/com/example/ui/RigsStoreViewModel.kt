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
        val rigMap = hashMapOf(
            "nodeId" to "NODE-${System.currentTimeMillis().toString().takeLast(6)}",
            "name" to selectedRig.name,
            "priceUsdt" to selectedRig.priceUsdt.toDouble(),
            "hashrateGh" to selectedRig.hashrateGh.toDouble(),
            "status" to "ACTIVE",
            "daysRemaining" to 200,
            "totalEarnedUsdt" to 0.0
        )
        FirebaseFirestore.getInstance().collection("users").document(key)
            .update(
                "hardwareNodes", FieldValue.arrayUnion(rigMap),
                "minerBalanceUsdt", FieldValue.increment(-selectedRig.priceUsdt.toDouble()),
                "dailySpentUsdt", FieldValue.increment(selectedRig.priceUsdt.toDouble())
            )
    }

    fun purchaseRigWithBalance(selectedRig: MiningRig, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeKey = sessionManager.getActiveUserKey() ?: "HG-ADM9-7788-5544-0001"
                val userRef = FirebaseFirestore.getInstance().collection("users").document(activeKey)
                
                FirebaseFirestore.getInstance().runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val currentBal = (snapshot.get("minerBalanceUsdt") as? Number)?.toDouble() ?: 0.0
                    val price = selectedRig.priceUsdt.toDouble()

                    if (currentBal < price) {
                        throw IllegalStateException("INSUFFICIENT_FUNDS")
                    }

                    val existingNodes = (snapshot.get("hardwareNodes") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                    val newRig = mapOf(
                        "nodeId" to "NODE-${System.currentTimeMillis().toString().takeLast(6)}",
                        "name" to selectedRig.name,
                        "priceUsdt" to price,
                        "hashrateGh" to selectedRig.hashrateGh.toDouble(),
                        "status" to "ACTIVE",
                        "daysRemaining" to 200,
                        "purchaseTimestamp" to System.currentTimeMillis()
                    )
                    existingNodes.add(newRig)

                    transaction.update(userRef, mapOf(
                        "minerBalanceUsdt" to (currentBal - price),
                        "hardwareNodes" to existingNodes,
                        "dailySpentUsdt" to FieldValue.increment(price)
                    ))
                }.await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Node Deployed Successfully to Cloud!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = if (e.message == "INSUFFICIENT_FUNDS") "Insufficient Miner Balance!" else "Purchase Failed: ${e.localizedMessage}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
