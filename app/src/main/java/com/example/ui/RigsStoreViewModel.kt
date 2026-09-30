package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.RigCatalogItem
import com.example.data.security.SessionManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

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
}
