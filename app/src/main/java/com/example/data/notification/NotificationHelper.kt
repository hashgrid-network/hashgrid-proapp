package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_MINING = "hashgrid_mining_channel"
    const val CHANNEL_PAYMENT = "hashgrid_payment_channel"

    private const val NOTIF_ID_MINING_SESSION = 1001
    private const val NOTIF_ID_PAYMENT_STATUS = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val miningChannel = NotificationChannel(
                CHANNEL_MINING,
                "Mining Session Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed 24H free mining sessions and yield cycles"
                enableVibration(true)
                setShowBadge(true)
            }

            val paymentChannel = NotificationChannel(
                CHANNEL_PAYMENT,
                "Payment & Gateway Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates on NOWPayments invoices, deposit confirmations, and node activations"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(miningChannel)
            notificationManager.createNotificationChannel(paymentChannel)
        }
    }

    fun sendMiningSessionEndedNotification(context: Context) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MINING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚡ Free Mining Session Ended")
            .setContentText("Your 24-Hour Free GRID session has completed. Tap to restart your quantum core!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your 24-Hour Free GRID mining session has finished. Tap now to restart your mining core and continue earning continuous GRID & USDT yield!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIF_ID_MINING_SESSION, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    fun sendPaymentStatusNotification(
        context: Context,
        status: String,
        amountUsd: Double,
        details: String
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val isConfirmed = status.equals("confirmed", ignoreCase = true) || status.equals("finished", ignoreCase = true)
        val title = if (isConfirmed) "💎 Payment Confirmed ($${String.format("%.2f", amountUsd)} USDT)" else "⏳ Payment Update: ${status.uppercase()}"

        val builder = NotificationCompat.Builder(context, CHANNEL_PAYMENT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(details)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIF_ID_PAYMENT_STATUS, builder.build())
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }
}
