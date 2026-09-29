package com.example.data.firebase

import android.util.Log
import com.example.data.notification.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HashGridMessagingService : FirebaseMessagingService() {

    companion object {
        const val TAG = "HashGridFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM Token: $token")
        val firebaseManager = FirebaseManager(applicationContext)
        firebaseManager.saveFcmTokenToFirestore("HG-USER-8921", token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message Received from: ${remoteMessage.from}")

        // 1. Check Data Payload
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"] ?: ""
            Log.d(TAG, "Message Data Payload type: $type")

            when (type.uppercase()) {
                "SESSION_ENDED", "MINING_END" -> {
                    NotificationHelper.sendMiningSessionEndedNotification(applicationContext)
                }
                "PAYMENT_STATUS", "PAYMENT_CONFIRMED" -> {
                    val status = remoteMessage.data["status"] ?: "confirmed"
                    val amountStr = remoteMessage.data["amount"] ?: "0.0"
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val details = remoteMessage.data["details"]
                        ?: "Your NOWPayments transaction has been confirmed and synced to your Firestore balance."
                    NotificationHelper.sendPaymentStatusNotification(applicationContext, status, amount, details)
                }
                else -> {
                    val title = remoteMessage.data["title"] ?: "HashGrid Pro Alert"
                    val body = remoteMessage.data["body"] ?: "New updates available on your mining matrix."
                    NotificationHelper.sendPaymentStatusNotification(applicationContext, "Update", 0.0, "$title: $body")
                }
            }
        }

        // 2. Check Notification Payload
        remoteMessage.notification?.let { notif ->
            val title = notif.title ?: "HashGrid Pro Alert"
            val body = notif.body ?: ""
            if (title.contains("Session", ignoreCase = true) || body.contains("Session", ignoreCase = true)) {
                NotificationHelper.sendMiningSessionEndedNotification(applicationContext)
            } else {
                NotificationHelper.sendPaymentStatusNotification(applicationContext, "Update", 0.0, "$title: $body")
            }
        }
    }
}
