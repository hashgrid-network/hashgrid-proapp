package com.example.data.security

import java.security.SecureRandom

object SecretKeyUtils {

    // Characters excluding ambiguous ones like 0, O, 1, I
    private const val CHAR_POOL = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    /**
     * Generates a Web3 Secret Key in format: HG-XXXX-XXXX-XXXX-XXXX (16 chars in 4 groups of 4)
     */
    fun generateSecretKey(): String {
        fun randomGroup(): String {
            val sb = StringBuilder(4)
            for (i in 0 until 4) {
                sb.append(CHAR_POOL[random.nextInt(CHAR_POOL.length)])
            }
            return sb.toString()
        }

        return "HG-${randomGroup()}-${randomGroup()}-${randomGroup()}-${randomGroup()}"
    }

    /**
     * Validates secret key format: HG-XXXX-XXXX-XXXX-XXXX
     */
    fun isValidSecretKey(input: String): Boolean {
        val sanitized = input.trim().uppercase()
        val regex = Regex("^HG-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")
        return regex.matches(sanitized)
    }

    /**
     * Sanitizes and normalizes input string into standard uppercase secret key
     */
    fun normalizeSecretKey(input: String): String {
        var clean = input.trim().uppercase().replace(" ", "")
        if (!clean.startsWith("HG-") && clean.startsWith("HG")) {
            clean = "HG-" + clean.removePrefix("HG")
        }
        return clean
    }
}
