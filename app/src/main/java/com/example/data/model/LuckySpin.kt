package com.example.data.model

enum class SpinRewardType {
    GRID_TOKENS,
    HASHRATE_BOOST,
    USDT
}

data class SpinSector(
    val id: Int,
    val title: String,
    val subtitle: String,
    val type: SpinRewardType,
    val value: Double,
    val hexColor: Long,
    val weight: Int // relative probability
)

object LuckyWheelConfig {
    val sectors = listOf(
        SpinSector(0, "15 GRID", "Free Mined Tokens", SpinRewardType.GRID_TOKENS, 15.0, 0xFFF59E0B, weight = 40),
        SpinSector(1, "+0.5 GH/s", "24H Hash Boost", SpinRewardType.HASHRATE_BOOST, 0.5, 0xFF10B981, weight = 30),
        SpinSector(2, "30 GRID", "Bonus Coins", SpinRewardType.GRID_TOKENS, 30.0, 0xFF3B82F6, weight = 25),
        SpinSector(3, "$1.00 USDT", "Miner Balance", SpinRewardType.USDT, 1.0, 0xFF8B5CF6, weight = 12),
        SpinSector(4, "+1.5 GH/s", "24H Super Boost", SpinRewardType.HASHRATE_BOOST, 1.5, 0xFFEC4899, weight = 10),
        SpinSector(5, "50 GRID", "Mega Token Drop", SpinRewardType.GRID_TOKENS, 50.0, 0xFF14B8A6, weight = 15),
        SpinSector(6, "$2.00 USDT", "Instant Payout", SpinRewardType.USDT, 2.0, 0xFFEAB308, weight = 6),
        SpinSector(7, "$5.00 USDT", "JACKPOT WIN", SpinRewardType.USDT, 5.0, 0xFFEF4444, weight = 2)
    )
}

data class SpinHistoryRecord(
    val id: String,
    val rewardTitle: String,
    val rewardSubtitle: String,
    val timestamp: Long,
    val rewardType: SpinRewardType,
    val value: Double
)
