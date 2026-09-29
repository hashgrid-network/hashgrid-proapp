package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("hashgrid_security_v2", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SECRET_KEY = "sec_web3_secret_key"
        private const val KEY_IS_BACKED_UP = "sec_is_key_backed_up"
        private const val KEY_PIN_HASH = "sec_pin_sha256_hash"
        private const val KEY_PIN_SALT = "sec_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "sec_biometric_enabled"
        private const val KEY_FIRST_LAUNCH_DONE = "sec_first_launch_done"
    }

    fun getSecretKey(): String? {
        return prefs.getString(KEY_SECRET_KEY, null)
    }

    fun setSecretKey(key: String) {
        prefs.edit().putString(KEY_SECRET_KEY, key.trim().uppercase()).apply()
    }

    fun isSecretKeyBackedUp(): Boolean {
        return prefs.getBoolean(KEY_IS_BACKED_UP, false)
    }

    fun setSecretKeyBackedUp(backedUp: Boolean) {
        prefs.edit().putBoolean(KEY_IS_BACKED_UP, backedUp).apply()
    }

    fun isPinSet(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        val salt = System.currentTimeMillis().toString()
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        val candidateHash = hashPin(pin, salt)
        return storedHash == candidateHash
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isFirstLaunchDone(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
    }

    fun setFirstLaunchDone(done: Boolean = true) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, done).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val input = "HG_SALT_${salt}_PIN_$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
