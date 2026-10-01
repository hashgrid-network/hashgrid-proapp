package com.example.data.model

data class CryptoTickerPrice(
    val symbol: String, // "BTC/USDT", "ETH/USDT"
    val price: Double,
    val change24h: Double,
    val high24h: Double,
    val low24h: Double
)

data class UserMiningState(
    val uid: String = "",
    val email: String = "",
    val nodeId: String = "",
    val isColdStorageSynced: Boolean = true,
    
    // Balances (Strict Zero Defaults)
    val minerBalanceUsdt: Double = 0.0, // Withdrawable USDT
    val gridBalance: Double = 0.0,      // GRID coin balance
    
    // Free Mining Core
    val baseFreeHashrateGh: Double = 2.0, // Free Base Node power (2.0 GH/s)
    val referralCount: Int = 0,           // 0 referrals initially
    val activeReferredMiners: Int = 0,     // 0 active mining referrals
    val temporaryBoostHashrateGh: Double = 0.0, // From lucky wheel
    val temporaryBoostExpiry: Long = 0L,
    val freeMiningSessionStart: Long = 0L,
    val freeMiningSessionEnd: Long = 0L,
    val isFreeMiningActive: Boolean = false,
    
    // Limits and Referrals
    val referralCode: String = "",
    val referredBy: String? = null,
    val teamCount: Long = 0L,
    val teamEarningsUsdt: Double = 0.0,
    val totalHashrateBoostGh: Double = 0.0,
    val dailySpentUsdt: Double = 0.0,
    val dailySpentResetDate: Long = System.currentTimeMillis(),
    val lastDailySpinTimestamp: Long = 0L,
    
    // Lists (Empty lists initially)
    val userRigs: List<UserRig> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val spinHistory: List<SpinHistoryRecord> = emptyList(),
    val microTasks: List<MicroTaskSubmission> = emptyList(),
    val videoPromotions: List<VideoPromotionSubmission> = emptyList(),

    // Web3 Security & Persistence
    val secretKey: String = "",
    val isKeyBackedUp: Boolean = false,
    val isPinConfigured: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isAppLocked: Boolean = false,
    val lastYieldTickTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val isAdmin: Boolean = false,
    val role: String = "user",
    val isAuthenticated: Boolean = true
) {
    val activeKey: String
        get() = secretKey

    // Referral boost calculation: +0.25 per registration, +0.50 per active miner
    val referralBoostHashrateGh: Double
        get() = if (totalHashrateBoostGh > 0.0) totalHashrateBoostGh else ((referralCount * 0.25) + (activeReferredMiners * 0.50))

    // Free hashrate capped strictly at 10.0 GH/s
    val aggregateFreeHashrateGh: Double
        get() {
            val raw = baseFreeHashrateGh + referralBoostHashrateGh + if (isBoostActive()) temporaryBoostHashrateGh else 0.0
            return raw.coerceAtMost(10.0)
        }

    // Active paid rigs hashrate
    val activePaidRigsHashrateGh: Double
        get() = userRigs.filter { it.status == RigStatus.ACTIVE }.sumOf { it.hashrateGh }

    // Total aggregate network hashrate
    val totalAggregateHashrateGh: Double
        get() = (if (isFreeMiningActive) aggregateFreeHashrateGh else 0.0) + activePaidRigsHashrateGh

    fun isBoostActive(currentTime: Long = System.currentTimeMillis()): Boolean {
        return temporaryBoostExpiry > currentTime && temporaryBoostHashrateGh > 0.0
    }

    fun isSpinReady(currentTime: Long = System.currentTimeMillis()): Boolean {
        return (currentTime - lastDailySpinTimestamp) >= 24 * 60 * 60 * 1000
    }

    fun spinCooldownRemainingSeconds(currentTime: Long = System.currentTimeMillis()): Long {
        val diff = (lastDailySpinTimestamp + 24 * 60 * 60 * 1000) - currentTime
        return if (diff > 0) diff / 1000 else 0
    }

    fun freeSessionRemainingSeconds(currentTime: Long = System.currentTimeMillis()): Long {
        if (!isFreeMiningActive) return 0
        val diff = freeMiningSessionEnd - currentTime
        return if (diff > 0) diff / 1000 else 0
    }
}
