package com.example.data.model

data class RigCatalogItem(
    val id: String,
    val name: String,
    val priceUsdt: Double,
    val hashrateGh: Double,
    val durationDays: Int = 200,
    val badge: String? = null,
    val description: String,
    val monthlyYieldPercent: Double = 15.0, // ~15% net yield per month
    val dailyYieldUsdt: Double = (priceUsdt * 0.15) / 30.0,
    val totalEstYieldUsdt: Double = (priceUsdt * 0.15 / 30.0) * durationDays
)

enum class RigStatus {
    ACTIVE,
    COMPLETED
}

data class UserRig(
    val id: String,
    val catalogId: String,
    val name: String,
    val priceUsdt: Double,
    val hashrateGh: Double,
    val purchaseTimestamp: Long,
    val durationDays: Int,
    val status: RigStatus = RigStatus.ACTIVE,
    val totalReceivedUsdt: Double = 0.0,
    val thisMonthEarnedUsdt: Double = 0.0,
    val lastYieldCalculatedTimestamp: Long = purchaseTimestamp
) {
    val expiryTimestamp: Long
        get() = purchaseTimestamp + (durationDays.toLong() * 24 * 60 * 60 * 1000)

    val dailyYieldUsdt: Double
        get() = (priceUsdt * 0.15) / 30.0

    fun daysRemaining(currentTimestamp: Long = System.currentTimeMillis()): Int {
        if (purchaseTimestamp <= 0L) return durationDays
        val elapsedMillis = maxOf(0L, currentTimestamp - purchaseTimestamp)
        val elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt()
        return maxOf(0, durationDays - elapsedDays)
    }

    fun progressRatio(currentTimestamp: Long = System.currentTimeMillis()): Float {
        val totalMs = durationDays.toLong() * 24L * 60L * 60L * 1000L
        if (totalMs <= 0L) return 1f
        if (purchaseTimestamp <= 0L) return 0f
        val elapsed = maxOf(0L, currentTimestamp - purchaseTimestamp)
        return (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
}

object DefaultRigs {
    val catalog = listOf(
        RigCatalogItem(
            id = "starter_node",
            name = "Starter Node",
            priceUsdt = 10.0,
            hashrateGh = 2.0,
            durationDays = 200,
            badge = "Popular",
            description = "Entry-level ASIC computing slice with cold-storage synced telemetry."
        ),
        RigCatalogItem(
            id = "pro_miner_node",
            name = "Pro Miner Node",
            priceUsdt = 25.0,
            hashrateGh = 6.0,
            durationDays = 200,
            badge = "Best Value",
            description = "High-efficiency dual-core hashing matrix optimized for continuous block solving."
        ),
        RigCatalogItem(
            id = "quantum_rig_node",
            name = "Quantum Rig Node",
            priceUsdt = 100.0,
            hashrateGh = 30.0,
            durationDays = 200,
            badge = "Pro Choice",
            description = "Enterprise-grade quantum array with redundant liquid immersion cooling."
        ),
        RigCatalogItem(
            id = "titan_enterprise_node",
            name = "Titan Enterprise Node",
            priceUsdt = 500.0,
            hashrateGh = 180.0,
            durationDays = 200,
            badge = "High Yield",
            description = "Institutional cluster rack with dedicated fiber interconnection."
        ),
        RigCatalogItem(
            id = "elite_node",
            name = "Elite Node",
            priceUsdt = 1000.0,
            hashrateGh = 400.0,
            durationDays = 200,
            badge = "Max Power",
            description = "Flagship hyper-dense server cluster delivering maximum computational priority."
        )
    )
}
