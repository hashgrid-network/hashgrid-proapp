package com.example.data.model

data class CryptoTickerPrice(
    val symbol: String, // "BTC/USDT", "ETH/USDT"
    val price: Double,
    val change24h: Double,
    val high24h: Double,
    val low24h: Double
)

data class UserMiningState(
    val uid: String = "HG-USER-8921",
    val email: String = "miner8921@hashgrid.pro",
    val nodeId: String = "NODE-US-EAST-#8921",
    val isColdStorageSynced: Boolean = true,
    
    // Balances
    val minerBalanceUsdt: Double = 15.80, // Withdrawable USDT
    val gridBalance: Double = 348.520,   // GRID coin balance
    
    // Free Mining Core
    val baseFreeHashrateGh: Double = 1.0, // Base hashrate
    val referralCount: Int = 3,           // 3 registered referrals
    val activeReferredMiners: Int = 2,     // 2 active mining referrals
    val temporaryBoostHashrateGh: Double = 0.0, // From lucky wheel
    val temporaryBoostExpiry: Long = 0L,
    val freeMiningSessionStart: Long = 0L,
    val freeMiningSessionEnd: Long = 0L,
    val isFreeMiningActive: Boolean = false,
    
    // Limits and Referrals
    val referralCode: String = "HG-8921",
    val dailySpentUsdt: Double = 0.0,
    val dailySpentResetDate: Long = System.currentTimeMillis(),
    val lastDailySpinTimestamp: Long = 0L,
    
    // Lists
    val userRigs: List<UserRig> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val spinHistory: List<SpinHistoryRecord> = emptyList(),
    val microTasks: List<MicroTaskSubmission> = emptyList(),
    val videoPromotions: List<VideoPromotionSubmission> = emptyList()
) {
    // Referral boost calculation: +0.25 per registration, +0.50 per active miner
    val referralBoostHashrateGh: Double
        get() = (referralCount * 0.25) + (activeReferredMiners * 0.50)

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
