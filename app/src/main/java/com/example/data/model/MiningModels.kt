package com.example.data.model

import java.util.UUID

data class HardwareNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hashrateGh: Double = 0.0,
    val costUsdt: Double = 0.0,
    val dailyYieldUsdt: Double = 0.0,
    val deployedTimestamp: Long = 0L,
    val totalDays: Int = 200
) {
    val remainingDays: Int
        get() = calculateRemainingDays()

    val progressRatio: Float
        get() = calculateProgressRatio()

    fun calculateRemainingDays(now: Long = System.currentTimeMillis()): Int {
        if (deployedTimestamp <= 0L) return totalDays
        val elapsedMillis = maxOf(0L, now - deployedTimestamp)
        val elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt()
        return maxOf(0, totalDays - elapsedDays)
    }

    fun calculateProgressRatio(now: Long = System.currentTimeMillis()): Float {
        if (totalDays <= 0) return 1f
        if (deployedTimestamp <= 0L) return 0f
        val totalMs = totalDays.toLong() * 24L * 60L * 60L * 1000L
        val elapsedMs = maxOf(0L, now - deployedTimestamp)
        return (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
}

fun Map<String, Any>.calculateRemainingDays(now: Long = System.currentTimeMillis()): Int {
    val deployedTimestamp = (this["deployedTimestamp"] as? Number)?.toLong()
        ?: (this["purchaseTimestamp"] as? Number)?.toLong() ?: 0L
    val totalDays = (this["totalDays"] as? Number)?.toInt()
        ?: (this["durationDays"] as? Number)?.toInt() ?: 200
    if (deployedTimestamp <= 0L) return totalDays
    val elapsedMillis = maxOf(0L, now - deployedTimestamp)
    val elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt()
    return maxOf(0, totalDays - elapsedDays)
}

fun Map<String, Any>.calculateProgressRatio(now: Long = System.currentTimeMillis()): Float {
    val deployedTimestamp = (this["deployedTimestamp"] as? Number)?.toLong()
        ?: (this["purchaseTimestamp"] as? Number)?.toLong() ?: 0L
    val totalDays = (this["totalDays"] as? Number)?.toInt()
        ?: (this["durationDays"] as? Number)?.toInt() ?: 200
    if (totalDays <= 0) return 1f
    if (deployedTimestamp <= 0L) return 0f
    val totalMs = totalDays.toLong() * 24L * 60L * 60L * 1000L
    val elapsedMs = maxOf(0L, now - deployedTimestamp)
    return (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
}

data class UserCloudAccount(
    val secretKey: String = "",
    val isAdmin: Boolean = false,
    val minerBalanceUsdt: Double = 0.0,
    val gridBalance: Double = 0.0,
    val isFreeMiningActive: Boolean = false,
    val freeMiningStartTime: Long = 0L,
    val freeMiningEndTime: Long = 0L,
    val aggregateHashpowerGh: Double = 2.0,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val hardwareNodes: List<Map<String, Any>> = emptyList()
) {
    // Backwards compatibility getter
    val deployedRigs: List<Map<String, Any>>
        get() = hardwareNodes
}

fun HardwareNode.toMap(): Map<String, Any> = mapOf(
    "id" to id,
    "nodeId" to id,
    "name" to name,
    "hashrateGh" to hashrateGh,
    "costUsdt" to costUsdt,
    "priceUsdt" to costUsdt,
    "dailyYieldUsdt" to dailyYieldUsdt,
    "deployedTimestamp" to deployedTimestamp,
    "purchaseTimestamp" to deployedTimestamp,
    "totalDays" to totalDays,
    "durationDays" to totalDays
)

fun Map<String, Any>.toHardwareNode(): HardwareNode = HardwareNode(
    id = (this["id"] as? String) ?: (this["nodeId"] as? String) ?: UUID.randomUUID().toString(),
    name = (this["name"] as? String) ?: "Mining Node",
    hashrateGh = (this["hashrateGh"] as? Number)?.toDouble() ?: 0.0,
    costUsdt = (this["costUsdt"] as? Number)?.toDouble() ?: (this["priceUsdt"] as? Number)?.toDouble() ?: 0.0,
    dailyYieldUsdt = (this["dailyYieldUsdt"] as? Number)?.toDouble() ?: 0.0,
    deployedTimestamp = (this["deployedTimestamp"] as? Number)?.toLong() ?: (this["purchaseTimestamp"] as? Number)?.toLong() ?: 0L,
    totalDays = (this["totalDays"] as? Number)?.toInt() ?: (this["durationDays"] as? Number)?.toInt() ?: 200
)

fun UserCloudAccount.toMap(): Map<String, Any> = mapOf(
    "secretKey" to secretKey,
    "nodeId" to (secretKey.take(12)),
    "isAdmin" to isAdmin,
    "minerBalanceUsdt" to minerBalanceUsdt,
    "gridBalance" to gridBalance,
    "isFreeMiningActive" to isFreeMiningActive,
    "freeMiningStartTime" to freeMiningStartTime,
    "freeMiningEndTime" to freeMiningEndTime,
    "aggregateHashpowerGh" to aggregateHashpowerGh,
    "lastSyncTimestamp" to lastSyncTimestamp,
    "hardwareNodes" to hardwareNodes
)

fun Map<String, Any>.toUserCloudAccount(key: String): UserCloudAccount = UserCloudAccount(
    secretKey = key,
    isAdmin = (this["isAdmin"] as? Boolean) ?: (key.startsWith("HG-ADM9")),
    minerBalanceUsdt = (this["minerBalanceUsdt"] as? Number)?.toDouble() ?: 0.0,
    gridBalance = (this["gridBalance"] as? Number)?.toDouble() ?: 0.0,
    isFreeMiningActive = (this["isFreeMiningActive"] as? Boolean) ?: false,
    freeMiningStartTime = (this["freeMiningStartTime"] as? Number)?.toLong() ?: 0L,
    freeMiningEndTime = (this["freeMiningEndTime"] as? Number)?.toLong()
        ?: (this["freeMiningSessionEnd"] as? Number)?.toLong() ?: 0L,
    aggregateHashpowerGh = (this["aggregateHashpowerGh"] as? Number)?.toDouble() ?: 2.0,
    lastSyncTimestamp = (this["lastSyncTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
    hardwareNodes = (this["hardwareNodes"] as? List<Map<String, Any>>)
        ?: (this["deployedRigs"] as? List<Map<String, Any>>)
        ?: emptyList()
)

fun UserCloudAccount.toUserMiningState(): UserMiningState {
    val rigs = hardwareNodes.map { rigMap ->
        val node = rigMap.toHardwareNode()
        UserRig(
            id = node.id,
            catalogId = node.name.lowercase().replace(" ", "_"),
            name = node.name,
            hashrateGh = node.hashrateGh,
            priceUsdt = node.costUsdt,
            purchaseTimestamp = node.deployedTimestamp,
            durationDays = node.totalDays,
            status = RigStatus.ACTIVE
        )
    }
    return UserMiningState(
        uid = secretKey,
        secretKey = secretKey,
        isAdmin = isAdmin,
        role = if (isAdmin) "superadmin" else "user",
        minerBalanceUsdt = minerBalanceUsdt,
        gridBalance = gridBalance,
        isFreeMiningActive = isFreeMiningActive,
        freeMiningSessionStart = freeMiningStartTime,
        freeMiningSessionEnd = freeMiningEndTime,
        baseFreeHashrateGh = aggregateHashpowerGh,
        lastSyncTimestamp = lastSyncTimestamp,
        userRigs = rigs,
        isAuthenticated = secretKey.isNotBlank()
    )
}

fun UserMiningState.toUserCloudAccount(): UserCloudAccount {
    return UserCloudAccount(
        secretKey = secretKey,
        isAdmin = isAdmin,
        minerBalanceUsdt = minerBalanceUsdt,
        gridBalance = gridBalance,
        isFreeMiningActive = isFreeMiningActive,
        freeMiningStartTime = freeMiningSessionStart,
        freeMiningEndTime = freeMiningSessionEnd,
        aggregateHashpowerGh = aggregateFreeHashrateGh,
        lastSyncTimestamp = lastSyncTimestamp,
        hardwareNodes = userRigs.map { rig ->
            HardwareNode(
                id = rig.id,
                name = rig.name,
                hashrateGh = rig.hashrateGh,
                costUsdt = rig.priceUsdt,
                dailyYieldUsdt = rig.dailyYieldUsdt,
                deployedTimestamp = rig.purchaseTimestamp,
                totalDays = rig.durationDays
            ).toMap()
        }
    )
}

