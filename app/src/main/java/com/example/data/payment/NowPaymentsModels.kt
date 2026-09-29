package com.example.data.payment

data class SupportedPayCurrency(
    val code: String,       // e.g. "usdtbsc", "usdttrc20", "btc", "eth", "trx", "sol", "ltc"
    val displayName: String,// e.g. "USDT (BEP20 BSC)", "USDT (TRC20)", "Bitcoin (BTC)", "Ethereum (ETH)"
    val symbol: String,     // "USDT", "BTC", "ETH", "TRX", "SOL"
    val network: String     // "BSC", "TRON", "Bitcoin", "Ethereum", "Solana"
)

object NowPaymentsCurrencies {
    val list = listOf(
        SupportedPayCurrency("usdtbsc", "USDT (BEP20 - Binance Smart Chain)", "USDT", "BEP20 (BSC)"),
        SupportedPayCurrency("usdttrc20", "USDT (TRC20 - TRON Network)", "USDT", "TRC20 (TRON)"),
        SupportedPayCurrency("btc", "Bitcoin (BTC Native)", "BTC", "Bitcoin"),
        SupportedPayCurrency("eth", "Ethereum (ETH ERC20)", "ETH", "Ethereum"),
        SupportedPayCurrency("trx", "TRON (TRX Mainnet)", "TRX", "TRON"),
        SupportedPayCurrency("sol", "Solana (SOL Native)", "SOL", "Solana"),
        SupportedPayCurrency("ltc", "Litecoin (LTC)", "LTC", "Litecoin")
    )
}

data class NowPaymentResponse(
    val paymentId: String,
    val paymentStatus: String, // "waiting", "confirming", "confirmed", "sending", "finished", "failed", "expired"
    val payAddress: String,
    val priceAmount: Double,
    val priceCurrency: String,
    val payAmount: Double,
    val payCurrency: String,
    val orderId: String,
    val orderDescription: String,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val isSuccessOrConfirmed: Boolean
        get() = paymentStatus.equals("confirmed", ignoreCase = true) ||
                paymentStatus.equals("finished", ignoreCase = true)

    val isPendingOrWaiting: Boolean
        get() = paymentStatus.equals("waiting", ignoreCase = true) ||
                paymentStatus.equals("confirming", ignoreCase = true) ||
                paymentStatus.equals("sending", ignoreCase = true)
}
