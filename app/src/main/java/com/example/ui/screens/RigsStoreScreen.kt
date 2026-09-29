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
import com.example.data.model.DefaultRigs
import com.example.data.model.RigCatalogItem
import com.example.data.model.RigStatus
import com.example.data.model.UserMiningState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun RigsStoreScreen(
    userState: UserMiningState,
    onBuyRig: (RigCatalogItem) -> Unit,
    onOpenDeposit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Catalog, 1 = My Deployed Hardware
    var hardwareSubFilter by remember { mutableIntStateOf(0) } // 0 = Active, 1 = Completed
    var rigToBuy by remember { mutableStateOf<RigCatalogItem?>(null) }

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
                listOf("Node Catalog", "My Hardware (${userState.userRigs.count { it.status == RigStatus.ACTIVE }})").forEachIndexed { idx, title ->
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

                    // Non refundable notice
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
                                Text("Investment Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp))
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

            val filteredRigs = userState.userRigs.filter {
                if (hardwareSubFilter == 0) it.status == RigStatus.ACTIVE else it.status == RigStatus.COMPLETED
            }

            if (filteredRigs.isEmpty()) {
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
                items(filteredRigs) { rig ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (rig.status == RigStatus.ACTIVE) EmeraldAccent else Color.Gray.copy(alpha = 0.3f)
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
                                    Text("${rig.hashrateGh} GH/s • Status: ${rig.status.name}", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGlow))
                                }
                                Text(
                                    "$${rig.priceUsdt.toInt()} USDT",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { rig.progressRatio() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = EmeraldAccent,
                                trackColor = DarkNavySurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${rig.daysRemaining()} Days Remaining", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                                Text("Received: +$${String.format("%.2f", rig.totalReceivedUsdt)} USDT", style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }

    // Purchase Confirmation Modal
    if (rigToBuy != null) {
        val rig = rigToBuy!!
        val hasEnoughBalance = userState.minerBalanceUsdt >= rig.priceUsdt

        Dialog(onDismissRequest = { rigToBuy = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                borderColor = GoldPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CONFIRM NODE DEPLOYMENT",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextGold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "You are deploying ${rig.name} (${rig.hashrateGh} GH/s) for 195–210 days.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, textAlign = TextAlign.Center)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cost:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Text("$${rig.priceUsdt.toInt()}.00 USDT", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Available Miner Balance:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Text("$${String.format("%.2f", userState.minerBalanceUsdt)} USDT", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (hasEnoughBalance) TextEmerald else CrimsonError))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Approx Monthly Net Yield:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Text("~$${String.format("%.2f", rig.priceUsdt * 0.15)} USDT (15%)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Notice: Purchases are non-refundable. Funds are deployed directly to enterprise hardware clusters.",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp, textAlign = TextAlign.Center)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (hasEnoughBalance) {
                        Button(
                            onClick = {
                                onBuyRig(rig)
                                rigToBuy = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("confirm_buy_rig_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("CONFIRM PURCHASE & DEPLOY", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                        }
                    } else {
                        Button(
                            onClick = {
                                rigToBuy = null
                                onOpenDeposit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("insufficient_funds_deposit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DEPOSIT USDT TO WALLET", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { rigToBuy = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    }
}
