package com.example.data.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

object ReferralConstants {
    const val REFERRAL_BASE_URL = "https://hashgrid.online/?ref="

    fun getReferralUrl(referralCode: String): String {
        return "$REFERRAL_BASE_URL$referralCode"
    }

    fun getReferralPreview(referralCode: String): String {
        return "hashgrid.online/?ref=$referralCode"
    }

    fun getShareMessage(referralCode: String): String {
        return "🚀 Join HashGrid Pro and activate your Web3 cloud mining node! Use my referral link to get a bonus hashrate boost:\n${getReferralUrl(referralCode)}"
    }

    fun shareReferralLink(context: Context, referralCode: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, getShareMessage(referralCode))
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share HashGrid Referral Link"))
        } catch (e: Throwable) {
            Log.e("ReferralShare", "Share intent failed: ${e.message}")
        }
    }

    fun copyReferralLink(context: Context, referralCode: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val fullUrl = getReferralUrl(referralCode)
            cm?.setPrimaryClip(ClipData.newPlainText("HashGrid Referral Link", fullUrl))
            Toast.makeText(context, "Referral link copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "Could not copy: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

object ReferralClipboardHelper {

    private const val TAG = "ReferralClipboard"

    /**
     * Safely reads the system clipboard to auto-detect a referral link or referral code.
     * Guaranteed to never throw SecurityException, NullPointerException, or crash the app
     * even if clipboard access is restricted on certain Android OS versions / manufacturer skins.
     */
    fun safelyDetectReferralCode(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return null

            if (!clipboard.hasPrimaryClip()) {
                return null
            }

            val clip = clipboard.primaryClip ?: return null
            if (clip.itemCount == 0) {
                return null
            }

            val item = clip.getItemAt(0) ?: return null
            val rawText = item.text?.toString()?.trim() ?: return null

            if (rawText.isBlank() || rawText.length > 300) {
                return null
            }

            // Pattern 1: URL with ?ref=HG-XXXX or &ref=HG-XXXX
            val urlRefMatch = Regex("[?&]ref=([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE).find(rawText)
            if (urlRefMatch != null) {
                val code = urlRefMatch.groupValues[1].uppercase()
                Log.d(TAG, "Safely detected referral code from URL: $code")
                return code
            }

            // Pattern 2: Raw referral code format HG-XXXX
            val rawCodeMatch = Regex("^(HG-[A-Z0-9]{4,6})$", RegexOption.IGNORE_CASE).find(rawText)
            if (rawCodeMatch != null) {
                val code = rawCodeMatch.groupValues[1].uppercase()
                Log.d(TAG, "Safely detected raw referral code: $code")
                return code
            }

            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access restricted by OS security policy: ${e.message}")
            null
        } catch (e: NullPointerException) {
            Log.w(TAG, "Clipboard NPE safely guarded: ${e.message}")
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Clipboard safe read exception: ${e.message}")
            null
        }
    }
}
