package com.example.data.security

import android.content.Context

class SessionManager(private val context: Context) {
    private val securityPreferences = SecurityPreferences(context)

    fun getActiveUserKey(): String? {
        return securityPreferences.getActiveUserKey()
    }

    fun setActiveUserKey(key: String) {
        securityPreferences.setActiveUserKey(key)
    }

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
