package com.example.data.security

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

object DeviceIdentifierHelper {

    private const val PREFS_DEVICE_ID_KEY = "device_fingerprint_uuid"
    private const val PREFS_ACCOUNT_CREATION_COUNT = "device_created_accounts_count"
    const val MAX_ACCOUNTS_PER_DEVICE = 5

    @SuppressLint("HardwareIds")
    fun getHashedDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("hashgrid_device_security", Context.MODE_PRIVATE)
        val rawAndroidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }

        val rawId = if (!rawAndroidId.isNullOrBlank() && rawAndroidId != "9774d56d682e549c") {
            rawAndroidId
        } else {
            // Fallback to persisted unique hardware UUID
            var storedUuid = prefs.getString(PREFS_DEVICE_ID_KEY, null)
            if (storedUuid.isNullOrBlank()) {
                storedUuid = UUID.randomUUID().toString()
                prefs.edit().putString(PREFS_DEVICE_ID_KEY, storedUuid).apply()
            }
            storedUuid
        }

        return sha256("HG_DEV_${rawId}")
    }

    fun getLocalCreatedAccountCount(context: Context): Int {
        val prefs = context.getSharedPreferences("hashgrid_device_security", Context.MODE_PRIVATE)
        return prefs.getInt(PREFS_ACCOUNT_CREATION_COUNT, 0)
    }

    fun incrementLocalCreatedAccountCount(context: Context): Int {
        val prefs = context.getSharedPreferences("hashgrid_device_security", Context.MODE_PRIVATE)
        val current = prefs.getInt(PREFS_ACCOUNT_CREATION_COUNT, 0)
        val updated = current + 1
        prefs.edit().putInt(PREFS_ACCOUNT_CREATION_COUNT, updated).apply()
        return updated
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
