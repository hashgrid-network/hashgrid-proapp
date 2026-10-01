package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.ui.MiningViewModel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.components.NodePurchaseDialog
import com.example.ui.theme.*

@Composable
fun RigsStoreScreen(
    userState: UserMiningState,
    onBuyRig: (RigCatalogItem) -> Unit,
    onPayWithNowPayments: (RigCatalogItem, payCurrency: String) -> Unit,
    onOpenDeposit: () -> Unit,
    viewModel: MiningViewModel? = null,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var hardwareSubFilter by remember { mutableIntStateOf(0) }
    var rigToBuy by remember { mutableStateOf<RigCatalogItem?>(null) }
    var selectedCryptoPayment by remember { mutableStateOf("usdtbsc") }

    val accountState by (viewModel?.accountState ?: remember { MutableStateFlow(null) }).collectAsState()

    val allDeployedNodes: List<HardwareNode> = remember(accountState, userState) {
        if (accountState?.deployedRigs?.isNotEmpty() == true) {
            accountState!!.deployedRigs.map { it.toHardwareNode() }
        } else {
            userState.userRigs.map { rig ->
                HardwareNode(
                    id = rig.id,
                    name = rig.name,
                    hashrateGh = rig.hashrateGh,
                    costUsdt = rig.priceUsdt,
                    dailyYieldUsdt = (rig.priceUsdt * 0.15) / 30.0,
                    deployedTimestamp = rig.purchaseTimestamp,
                    totalDays = rig.durationDays
                )
            }
        }
    }

    val cryptoCurrencies = listOf(
        "usdtbsc" to "USDT (BSC)",
        "usdttrc20" to "USDT (TRON)",
        "btc" to "BTC",
        "eth" to "ETH",
        "sol" to "SOL",
        "trx" to "TRX"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Section Switcher Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkNavySurface)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val activeNodesCount = allDeployedNodes.count { it.calculateRemainingDays() > 0 }
                listOf("Node Catalog", "My Hardware ($activeNodesCount)").forEachIndexed { idx, title ->
                    val isSel = selectedSection == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GoldPrimary else Color.Transparent)
                            .clickable { selectedSection = idx }
                            .padding(vertical = 10.dp)
                            .testTag("rig_tab_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) ObsidianBg else TextSecondary
                            )
                        )
                    }
                }
            }
        }

        // Daily Limit & Non-Refundable Notice
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = GoldPrimary.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Daily Limit", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "DAILY PURCHASE LIMIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                            )
                        }
                        Text(
                            text = "$${String.format("%.0f", userState.dailySpentUsdt)} / $5,000 USDT",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val limitProgress = (userState.dailySpentUsdt / 5000.0).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(
                        progress = { limitProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = GoldPrimary,
                        trackColor = DarkNavySurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GoldLight, modifier = Modifier.size(14.dp))
                        Text(
                            text = "All node purchases are non-refundable. Hardware runs for 195–210 days with ~15% monthly USDT yield.",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                        )
                    }
                }
            }
        }

        if (selectedSection == 0) {
            // CATALOG LIST
            item {
                Text(
                    text = "AVAILABLE MINING NODES",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
            }

            items(DefaultRigs.catalog) { rig ->
                GlowingBorderCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rig_card_${rig.id}"),
                    glowColor = when (rig.priceUsdt.toInt()) {
                        10 -> Color(0xFF38BDF8)
                        25 -> EmeraldAccent
                        100 -> GoldPrimary
                        500 -> PurpleAccent
                        else -> GoldLight
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Title row + Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = rig.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                )
                                Text(
                                    text = "${rig.hashrateGh} GH/s Compute Power",
                                    style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGlow, fontWeight = FontWeight.Bold)
                                )
                            }

                            if (rig.badge != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GoldPrimary.copy(alpha = 0.2f))
                                        .border(1.dp, GoldPrimary, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = rig.badge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold, fontSize = 9.5.sp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = rig.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Specs Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkNavySurface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ownership", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                                Text("${rig.durationDays} Days", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Monthly Yield", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                                Text("~15% USDT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Daily Yield", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                                Text("+$${String.format("%.2f", rig.dailyYieldUsdt)}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Buy button & Price
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Deployment Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp))
                                Text(
                                    "$${rig.priceUsdt.toInt()}.00 USDT",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                                )
                            }

                            Button(
                                onClick = { rigToBuy = rig },
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("deploy_btn_${rig.id}"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DEPLOY NODE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                            }
                        }
                    }
                }
            }
        } else {
            // MY DEPLOYED HARDWARE LIST
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Active Nodes", "Completed Matrix").forEachIndexed { idx, title ->
                        val isSel = hardwareSubFilter == idx
                        FilterChip(
                            selected = isSel,
                            onClick = { hardwareSubFilter = idx },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = ObsidianBg
                            )
                        )
                    }
                }
            }

            val now = System.currentTimeMillis()
            val filteredNodes = allDeployedNodes.filter { node ->
                val remaining = node.calculateRemainingDays(now)
                if (hardwareSubFilter == 0) remaining > 0 else remaining == 0
            }

            if (filteredNodes.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (hardwareSubFilter == 0) "No Active Hardware Nodes" else "No Completed Nodes",
                                style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Deploy hardware from the catalog to activate monthly USDT yield.",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredNodes) { rig ->
                    val remainingDays = rig.calculateRemainingDays(now)
                    val progress = rig.calculateProgressRatio(now)
                    val isExpired = remainingDays == 0

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (!isExpired) EmeraldAccent else Color.Gray.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(rig.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                    Text(
                                        "${rig.hashrateGh} GH/s • Remaining: $remainingDays / ${rig.totalDays} Days",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (!isExpired) EmeraldGlow else CrimsonError,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Text(
                                    "$${rig.costUsdt.toInt()} USDT",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (!isExpired) EmeraldAccent else CrimsonError,
                                trackColor = DarkNavySurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (isExpired) "0 Days Remaining (Expired)" else "$remainingDays Days Remaining (${rig.totalDays}d Contract)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isExpired) CrimsonError else TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    "Monthly: +$${String.format("%.2f", rig.costUsdt * 0.15)} USDT",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom AlertDialog for Hardware Node Purchase Confirmation
    if (rigToBuy != null) {
        val rig = rigToBuy!!
        if (viewModel != null) {
            NodePurchaseDialog(
                rig = rig,
                viewModel = viewModel,
                onConfirm = {
                    onBuyRig(rig)
                    rigToBuy = null
                },
                onDismiss = { rigToBuy = null },
                onOpenDeposit = onOpenDeposit
            )
        } else {
            NodePurchaseDialog(
                nodeName = rig.name,
                cost = rig.priceUsdt,
                dailyYield = rig.dailyYieldUsdt,
                hashrateGh = rig.hashrateGh,
                durationDays = rig.durationDays,
                currentMinerBalance = userState.minerBalanceUsdt,
                onConfirm = {
                    onBuyRig(rig)
                    rigToBuy = null
                },
                onDismiss = { rigToBuy = null },
                onOpenDeposit = onOpenDeposit
            )
        }
    }
}
