package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryFilterTab(val label: String) {
    ALL("All Activity"),
    DEPLOYMENTS("Node Deployments"),
    REWARDS("Mining Rewards")
}

data class UnifiedDashboardTx(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountText: String,
    val currency: String,
    val isIncome: Boolean,
    val timestamp: Long,
    val isDeployment: Boolean,
    val statusBadge: String,
    val statusColor: Color,
    val icon: ImageVector
)

@Composable
fun TransactionHistoryView(
    accountState: UserCloudAccount?,
    userState: UserMiningState,
    modifier: Modifier = Modifier,
    onNavigateToStore: (() -> Unit)? = null
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilterTab.ALL) }
    var isExpanded by remember { mutableStateOf(false) }

    // Aggregate transactions pulling strictly from user account data
    val allTransactions = remember(accountState, userState) {
        buildChronologicalTransactions(accountState, userState)
    }

    val filteredTransactions = remember(allTransactions, selectedFilter) {
        when (selectedFilter) {
            HistoryFilterTab.ALL -> allTransactions
            HistoryFilterTab.DEPLOYMENTS -> allTransactions.filter { it.isDeployment }
            HistoryFilterTab.REWARDS -> allTransactions.filter { !it.isDeployment }
        }
    }

    val deploymentCount = remember(allTransactions) { allTransactions.count { it.isDeployment } }
    val rewardCount = remember(allTransactions) { allTransactions.count { !it.isDeployment } }

    GlowingBorderCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_transaction_history_card"),
        glowColor = GoldPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Transactions",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "LEDGER & SETTLEMENTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "Transaction History",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                }

                // Total count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkNavySurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${allTransactions.size} Records",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Tabs (All, Node Deployments, Mining Rewards)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavySurface)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryFilterTab.values().forEach { tab ->
                    val isSelected = selectedFilter == tab
                    val badgeCount = when (tab) {
                        HistoryFilterTab.ALL -> allTransactions.size
                        HistoryFilterTab.DEPLOYMENTS -> deploymentCount
                        HistoryFilterTab.REWARDS -> rewardCount
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) GoldPrimary else Color.Transparent)
                            .clickable { selectedFilter = tab }
                            .padding(vertical = 8.dp)
                            .testTag("history_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = when (tab) {
                                    HistoryFilterTab.ALL -> "All"
                                    HistoryFilterTab.DEPLOYMENTS -> "Nodes"
                                    HistoryFilterTab.REWARDS -> "Rewards"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) ObsidianBg else TextSecondary,
                                    fontSize = 10.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) ObsidianBg.copy(alpha = 0.2f) else DarkNavySurface)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$badgeCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) ObsidianBg else TextMuted,
                                        fontSize = 8.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transactions List
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavySurface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = when (selectedFilter) {
                                HistoryFilterTab.ALL -> "No transaction records found."
                                HistoryFilterTab.DEPLOYMENTS -> "No node deployments recorded yet."
                                HistoryFilterTab.REWARDS -> "No mining rewards recorded yet."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        if (selectedFilter == HistoryFilterTab.DEPLOYMENTS && onNavigateToStore != null) {
                            TextButton(onClick = onNavigateToStore) {
                                Text("Deploy First Hardware Node", color = GoldPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                val displayList = if (isExpanded) filteredTransactions else filteredTransactions.take(5)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayList.forEach { tx ->
                        TransactionRowItem(tx = tx)
                    }
                }

                if (filteredTransactions.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toggle_expand_tx_history")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Show Less" else "View All ${filteredTransactions.size} Transactions",
                                color = CyanAccent,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowItem(tx: UnifiedDashboardTx) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(tx.timestamp) { dateFormat.format(Date(tx.timestamp)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurface)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("tx_item_${tx.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (tx.isDeployment) CyanAccent.copy(alpha = 0.15f) else EmeraldAccent.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (tx.isDeployment) CyanAccent.copy(alpha = 0.4f) else EmeraldAccent.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tx.icon,
                        contentDescription = null,
                        tint = if (tx.isDeployment) CyanAccent else EmeraldGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tx.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tx.statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tx.statusBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = tx.statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 7.5.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = tx.subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = tx.amountText,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (tx.isIncome) EmeraldGlow else TextPrimary
                    )
                )
                Text(
                    text = tx.currency,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/**
 * Builds chronological list pulling from user account data:
 * 1. Node deployments from accountState.deployedRigs / userState.userRigs
 * 2. Mining rewards from userState.transactions
 * 3. Mined token accruals reflected in user balances
 */
private fun buildChronologicalTransactions(
    accountState: UserCloudAccount?,
    userState: UserMiningState
): List<UnifiedDashboardTx> {
    val items = mutableListOf<UnifiedDashboardTx>()
    val seenDeploymentIds = mutableSetOf<String>()

    val now = System.currentTimeMillis()

    // 1. Pull Node Deployments from accountState.deployedRigs
    accountState?.deployedRigs?.forEach { rigMap ->
        val node = rigMap.toHardwareNode()
        seenDeploymentIds.add(node.id)
        val remainingDays = node.calculateRemainingDays(now)
        val isExpired = remainingDays == 0

        items.add(
            UnifiedDashboardTx(
                id = "rig_${node.id}",
                title = "Node Deployed: ${node.name}",
                subtitle = "+${node.hashrateGh} GH/s Power • ${node.totalDays}d Contract",
                amountText = "-$${String.format("%.2f", node.costUsdt)}",
                currency = "USDT",
                isIncome = false,
                timestamp = if (node.deployedTimestamp > 0L) node.deployedTimestamp else now,
                isDeployment = true,
                statusBadge = if (isExpired) "COMPLETED" else "ACTIVE NODE",
                statusColor = if (isExpired) Color.Gray else CyanAccent,
                icon = Icons.Default.Memory
            )
        )
    }

    // Also pull from userState.userRigs if not in accountState.deployedRigs
    userState.userRigs.forEach { rig ->
        if (!seenDeploymentIds.contains(rig.id)) {
            seenDeploymentIds.add(rig.id)
            val remainingDays = rig.daysRemaining(now)
            val isExpired = remainingDays == 0

            items.add(
                UnifiedDashboardTx(
                    id = "rig_${rig.id}",
                    title = "Node Deployed: ${rig.name}",
                    subtitle = "+${rig.hashrateGh} GH/s Power • ${rig.durationDays}d Contract",
                    amountText = "-$${String.format("%.2f", rig.priceUsdt)}",
                    currency = "USDT",
                    isIncome = false,
                    timestamp = if (rig.purchaseTimestamp > 0L) rig.purchaseTimestamp else now,
                    isDeployment = true,
                    statusBadge = if (isExpired) "COMPLETED" else "ACTIVE NODE",
                    statusColor = if (isExpired) Color.Gray else CyanAccent,
                    icon = Icons.Default.Memory
                )
            )
        }
    }

    // 2. Pull from userState.transactions
    userState.transactions.forEach { tx ->
        when (tx.type) {
            TransactionType.RIG_PURCHASE -> {
                if (!seenDeploymentIds.contains(tx.id)) {
                    items.add(
                        UnifiedDashboardTx(
                            id = tx.id,
                            title = "Node Deployment",
                            subtitle = tx.description.ifBlank { "Hardware Node Activated" },
                            amountText = "-$${String.format("%.2f", tx.amount)}",
                            currency = tx.currency,
                            isIncome = false,
                            timestamp = tx.timestamp,
                            isDeployment = true,
                            statusBadge = "ACTIVE",
                            statusColor = CyanAccent,
                            icon = Icons.Default.Memory
                        )
                    )
                }
            }
            TransactionType.MINING_PAYOUT_USDT -> {
                items.add(
                    UnifiedDashboardTx(
                        id = tx.id,
                        title = "Mining Yield Payout",
                        subtitle = tx.description.ifBlank { "Hardware Node Yield Accrual" },
                        amountText = "+$${String.format("%.4f", tx.amount)}",
                        currency = tx.currency,
                        isIncome = true,
                        timestamp = tx.timestamp,
                        isDeployment = false,
                        statusBadge = "CREDITED",
                        statusColor = EmeraldAccent,
                        icon = Icons.Default.Bolt
                    )
                )
            }
            TransactionType.MINING_PAYOUT_GRID -> {
                items.add(
                    UnifiedDashboardTx(
                        id = tx.id,
                        title = "GRID Block Reward",
                        subtitle = tx.description.ifBlank { "Consensus Mining Reward" },
                        amountText = "+${String.format("%.4f", tx.amount)}",
                        currency = "GRID",
                        isIncome = true,
                        timestamp = tx.timestamp,
                        isDeployment = false,
                        statusBadge = "CREDITED",
                        statusColor = TextGold,
                        icon = Icons.Default.Token
                    )
                )
            }
            TransactionType.LUCKY_SPIN_REWARD -> {
                items.add(
                    UnifiedDashboardTx(
                        id = tx.id,
                        title = "Lucky Wheel Yield",
                        subtitle = tx.description.ifBlank { "Daily Wheel Reward" },
                        amountText = "+${String.format("%.4f", tx.amount)}",
                        currency = tx.currency,
                        isIncome = true,
                        timestamp = tx.timestamp,
                        isDeployment = false,
                        statusBadge = "BONUS",
                        statusColor = GoldLight,
                        icon = Icons.Default.Casino
                    )
                )
            }
            TransactionType.REFERRAL_COMMISSION -> {
                items.add(
                    UnifiedDashboardTx(
                        id = tx.id,
                        title = "Affiliate Yield Share",
                        subtitle = tx.description.ifBlank { "Network Node Commission" },
                        amountText = "+$${String.format("%.2f", tx.amount)}",
                        currency = tx.currency,
                        isIncome = true,
                        timestamp = tx.timestamp,
                        isDeployment = false,
                        statusBadge = "AFFILIATE",
                        statusColor = EmeraldGlow,
                        icon = Icons.Default.GroupAdd
                    )
                )
            }
            else -> {
                // Deposit or withdrawal if desired
            }
        }
    }

    // 3. If user has active balances or hashrate but no logged rewards yet, synthesize ledger records from account state
    val totalMinedUsdt = userState.minerBalanceUsdt
    val totalMinedGrid = userState.gridBalance
    val hasRewardTx = items.any { !it.isDeployment }

    if (!hasRewardTx) {
        if (totalMinedUsdt > 0.0) {
            items.add(
                UnifiedDashboardTx(
                    id = "accrued_usdt_${userState.uid.take(8)}",
                    title = "Accumulated Mining Yield",
                    subtitle = "Automated Hardware & Node Yield Settlement",
                    amountText = "+$${String.format("%.4f", totalMinedUsdt)}",
                    currency = "USDT",
                    isIncome = true,
                    timestamp = if (userState.lastSyncTimestamp > 0L) userState.lastSyncTimestamp else now - 3600000L,
                    isDeployment = false,
                    statusBadge = "CONFIRMED",
                    statusColor = EmeraldGlow,
                    icon = Icons.Default.Paid
                )
            )
        }
        if (totalMinedGrid > 0.0) {
            items.add(
                UnifiedDashboardTx(
                    id = "accrued_grid_${userState.uid.take(8)}",
                    title = "GRID Genesis Mining Reward",
                    subtitle = "Protocol Free Mining Core Allocation",
                    amountText = "+${String.format("%.4f", totalMinedGrid)}",
                    currency = "GRID",
                    isIncome = true,
                    timestamp = if (userState.freeMiningSessionStart > 0L) userState.freeMiningSessionStart else now - 7200000L,
                    isDeployment = false,
                    statusBadge = "LOCKED",
                    statusColor = CyanAccent,
                    icon = Icons.Default.Token
                )
            )
        }
    }

    // Return strict chronological order (newest first)
    return items.sortedByDescending { it.timestamp }
}
