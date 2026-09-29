package com.example.data.model

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    RIG_PURCHASE,
    MINING_PAYOUT_USDT,
    MINING_PAYOUT_GRID,
    REFERRAL_COMMISSION,
    LUCKY_SPIN_REWARD,
    TASK_PROMOTION_REWARD
}

enum class TransactionStatus {
    COMPLETED,
    PENDING_REVIEW,
    PROCESSING,
    REJECTED
}

data class TransactionItem(
    val id: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String, // "USDT" or "GRID"
    val timestamp: Long,
    val status: TransactionStatus = TransactionStatus.COMPLETED,
    val description: String,
    val txHash: String? = null,
    val address: String? = null,
    val network: String? = null // "BEP20 (BSC)" or "TRC20 (TRON)"
)
