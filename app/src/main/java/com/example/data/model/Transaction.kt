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
    WAITING_PAYMENT,
    CONFIRMING,
    PROCESSING,
    PENDING_REVIEW,
    REJECTED,
    FAILED,
    EXPIRED
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
    val network: String? = null, // "BEP20 (BSC)", "TRC20 (TRON)", etc.
    val paymentId: String? = null,
    val payAddress: String? = null,
    val payAmount: Double? = null,
    val payCurrency: String? = null,
    val nowPaymentsStatus: String? = null,
    val targetRigCatalogId: String? = null // if this payment was a direct plan purchase
)
