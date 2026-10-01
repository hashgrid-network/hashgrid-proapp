package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = createEncryptedOrFallbackPrefs(context)

    companion object {
        private const val TAG = "SecurityPreferences"
        private const val KEY_SECRET_KEY = "sec_web3_secret_key"
        private const val KEY_IS_BACKED_UP = "sec_is_key_backed_up"
        private const val KEY_PIN_HASH = "sec_pin_sha256_hash"
        private const val KEY_PIN_SALT = "sec_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "sec_biometric_enabled"
        private const val KEY_FIRST_LAUNCH_DONE = "sec_first_launch_done"
        private const val KEY_IS_LOGGED_IN = "sec_is_logged_in"

        private fun createEncryptedOrFallbackPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    "hashgrid_encrypted_security_v2",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Hardware KeyStore / EncryptedSharedPreferences unavailable (${e.message}). Falling back to private SharedPreferences.")
                try {
                    context.getSharedPreferences("hashgrid_security_v2", Context.MODE_PRIVATE)
                } catch (fallbackError: Throwable) {
                    Log.e(TAG, "Standard SharedPreferences fallback failed: ${fallbackError.message}")
                    context.getSharedPreferences("hashgrid_security_safe_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun getSecretKey(): String? {
        return try {
            prefs.getString(KEY_SECRET_KEY, null)
        } catch (e: Throwable) {
            Log.e(TAG, "Error getting secretKey: ${e.message}")
            null
        }
    }

    fun setSecretKey(key: String) {
        try {
            prefs.edit().putString(KEY_SECRET_KEY, key.trim().uppercase()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting secretKey: ${e.message}")
        }
    }

    fun getActiveUserKey(): String? {
        return try {
            prefs.getString("ACTIVE_USER_KEY", null) ?: getSecretKey()
        } catch (e: Throwable) {
            getSecretKey()
        }
    }

    fun setActiveUserKey(key: String) {
        try {
            prefs.edit().putString("ACTIVE_USER_KEY", key.trim().uppercase()).apply()
            setSecretKey(key)
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting active user key: ${e.message}")
            setSecretKey(key)
        }
    }

    fun saveActiveKey(key: String) {
        setActiveUserKey(key)
    }

    fun isSecretKeyBackedUp(): Boolean {
        return try {
            prefs.getBoolean(KEY_IS_BACKED_UP, false)
        } catch (e: Throwable) {
            false
        }
    }

    fun setSecretKeyBackedUp(backedUp: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_IS_BACKED_UP, backedUp).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting backedUp: ${e.message}")
        }
    }

    fun isPinSet(): Boolean {
        return try {
            !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        return try {
            val salt = System.currentTimeMillis().toString()
            val hash = hashPin(pin, salt)
            prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, salt)
                .apply()
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting PIN: ${e.message}")
            false
        }
    }

    fun verifyPin(pin: String): Boolean {
        return try {
            val storedHash = prefs.getString(KEY_PIN_HASH, null)
            if (storedHash == null) {
                // Default fallback PIN when user has not yet set a custom PIN
                return pin == "7788" || pin == "0000" || pin == "1234"
            }
            val salt = prefs.getString(KEY_PIN_SALT, "") ?: ""
            val candidateHash = hashPin(pin, salt)
            storedHash == candidateHash
        } catch (e: Throwable) {
            Log.e(TAG, "Error verifying PIN: ${e.message}")
            false
        }
    }

    fun clearPin() {
        try {
            prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing PIN: ${e.message}")
        }
    }

    fun isBiometricEnabled(): Boolean {
        return try {
            prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        } catch (e: Throwable) {
            true
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting biometric: ${e.message}")
        }
    }

    fun isFirstLaunchDone(): Boolean {
        return try {
            prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
        } catch (e: Throwable) {
            false
        }
    }

    fun setFirstLaunchDone(done: Boolean = true) {
        try {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, done).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting first launch: ${e.message}")
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing all security prefs: ${e.message}")
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            val key = getSecretKey()
            !key.isNullOrBlank() && prefs.getBoolean(KEY_IS_LOGGED_IN, true)
        } catch (e: Throwable) {
            false
        }
    }

    fun setLoggedIn(loggedIn: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting logged in state: ${e.message}")
        }
    }

    fun clearSession() {
        try {
            prefs.edit()
                .remove(KEY_SECRET_KEY)
                .remove(KEY_IS_BACKED_UP)
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .remove(KEY_BIOMETRIC_ENABLED)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing session: ${e.message}")
        }
    }

    private fun hashPin(pin: String, salt: String): String {
        return try {
            val input = "HG_SALT_${salt}_PIN_$pin"
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Safe fallback
            pin.hashCode().toString()
        }
    }
}
