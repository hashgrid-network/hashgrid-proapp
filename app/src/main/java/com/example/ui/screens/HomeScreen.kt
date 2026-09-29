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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RigStatus
import com.example.data.model.TransactionType
import com.example.data.model.UserMiningState
import com.example.ui.AppNavTab
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    userState: UserMiningState,
    onNavigateToTab: (AppNavTab) -> Unit,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenLuckyWheel: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenHowItWorks: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Hero Dual Balance Card
        item {
            GlowingBorderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_balance_card"),
                glowColor = GoldPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INSTITUTIONAL PORTFOLIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        // Lucky wheel badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (userState.isSpinReady()) GoldPrimary else DarkNavySurface)
                                .clickable { onOpenLuckyWheel() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("home_lucky_wheel_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stars,
                                    contentDescription = "Wheel",
                                    tint = if (userState.isSpinReady()) ObsidianBg else GoldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (userState.isSpinReady()) "SPIN READY" else "DAILY SPIN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (userState.isSpinReady()) ObsidianBg else TextGold,
                                        fontSize = 9.5.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Two Column Balances
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Miner Balance USDT
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Miner Balance (USDT)",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${String.format("%.2f", userState.minerBalanceUsdt)}",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldLight
                                )
                            )
                            Text(
                                text = "≈ Withdrawable Funds",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextEmerald,
                                    fontSize = 9.5.sp
                                )
                            )
                        }

                        // Vertical separator
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(Color(0xFF334155))
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // GRID Coin Balance
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GRID Coin Balance",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format("%,.3f", userState.gridBalance),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "GRID Token (Free Core)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextGold,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (Deposit, Withdraw, Mine)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenDeposit,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("home_deposit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deposit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                        }

                        Button(
                            onClick = onOpenWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("home_withdraw_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Withdraw", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextEmerald))
                        }
                    }
                }
            }
        }

        // Live Hashrate Overview & Free Mining Quick Status
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTab(AppNavTab.CLOUD_MINER) }
                    .testTag("home_cloud_miner_card"),
                borderColor = if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (userState.isFreeMiningActive) EmeraldDark else DarkNavySurface)
                                .border(1.5.dp, if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Mining",
                                tint = if (userState.isFreeMiningActive) EmeraldGlow else GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "FREE MINING CORE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (userState.isFreeMiningActive) EmeraldAccent else CrimsonError)
                                )
                            }
                            Text(
                                text = if (userState.isFreeMiningActive) {
                                    val secs = userState.freeSessionRemainingSeconds()
                                    val h = secs / 3600
                                    val m = (secs % 3600) / 60
                                    val s = secs % 60
                                    "ACTIVE • ${String.format("%02d:%02d:%02d", h, m, s)}"
                                } else {
                                    "OFFLINE • TAP TO START"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (userState.isFreeMiningActive) EmeraldGlow else TextGold
                                )
                            )
                            Text(
                                text = "Aggregate Power: ${String.format("%.2f", userState.totalAggregateHashrateGh)} GH/s",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                            )
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Road to Milestone Progress
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                        Text(
                            text = "ROAD-TO-MILESTONE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextGold,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Next: $50.00 Milestone",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val balance = userState.minerBalanceUsdt
                    val milestones = listOf(3.0, 10.0, 50.0)
                    val target = 50.0
                    val progress = (balance / target).coerceIn(0.0, 1.0).toFloat()

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = GoldPrimary,
                        trackColor = DarkNavySurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        milestones.forEach { m ->
                            val reached = balance >= m
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (reached) EmeraldAccent else Color.Gray)
                                )
                                Text(
                                    text = "$${m.toInt()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (reached) TextEmerald else TextMuted,
                                        fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Grid Tools (Profit Calculator & How It Works)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Profit Calculator Button Card
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenCalculator() }
                        .testTag("home_profit_calc_card"),
                    borderColor = GoldPrimary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calc", tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Yield Calculator", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text("Simulate 15% net yield", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp))
                    }
                }

                // How it works Card
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenHowItWorks() }
                        .testTag("home_how_it_works_card"),
                    borderColor = CyanAccent.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Guide", tint = CyanAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("How It Works", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text("4-step mining protocol", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp))
                    }
                }
            }
        }

        // Active Hardware Rigs Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEPLOYED HARDWARE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                TextButton(
                    onClick = { onNavigateToTab(AppNavTab.RIGS_STORE) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Store", color = GoldPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val activeRigs = userState.userRigs.filter { it.status == RigStatus.ACTIVE }
        if (activeRigs.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTab(AppNavTab.RIGS_STORE) }
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No Paid Rigs Deployed", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold))
                        Text("Deploy a node starting at $10 for ~15% monthly USDT yield", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 10.sp))
                    }
                }
            }
        } else {
            items(activeRigs.take(3)) { rig ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = GoldPrimary.copy(alpha = 0.3f)
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
                            Column {
                                Text(rig.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                                Text("${rig.hashrateGh} GH/s • 195-210 Days Matrix", style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGlow))
                            }
                            Text(
                                "+$${String.format("%.2f", rig.totalReceivedUsdt)} USDT",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = GoldLight)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { rig.progressRatio() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = EmeraldAccent,
                            trackColor = DarkNavySurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${rig.daysRemaining()} Days Remaining", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp))
                            Text("~15% Monthly Yield", style = MaterialTheme.typography.labelSmall.copy(color = TextGold, fontSize = 9.5.sp))
                        }
                    }
                }
            }
        }

        // Recent Activity Stream
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RECENT ACTIVITY STREAM",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        }

        if (userState.transactions.isEmpty()) {
            item {
                Text("No transactions recorded yet.", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
            }
        } else {
            items(userState.transactions.take(5)) { tx ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val icon = when (tx.type) {
                                TransactionType.DEPOSIT -> Icons.Default.ArrowDownward
                                TransactionType.WITHDRAWAL -> Icons.Default.ArrowUpward
                                TransactionType.REFERRAL_COMMISSION -> Icons.Default.Hub
                                TransactionType.MINING_PAYOUT_USDT -> Icons.Default.Bolt
                                TransactionType.LUCKY_SPIN_REWARD -> Icons.Default.Stars
                                else -> Icons.Default.Receipt
                            }
                            val tint = when (tx.type) {
                                TransactionType.DEPOSIT, TransactionType.REFERRAL_COMMISSION, TransactionType.MINING_PAYOUT_USDT -> EmeraldAccent
                                TransactionType.WITHDRAWAL -> GoldPrimary
                                else -> CyanAccent
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            }

                            Column(modifier = Modifier.widthIn(max = 200.dp)) {
                                Text(
                                    text = tx.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary),
                                    maxLines = 1
                                )
                                Text(
                                    text = tx.status.name.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                                )
                            }
                        }

                        Text(
                            text = "${if (tx.type == TransactionType.WITHDRAWAL || tx.type == TransactionType.RIG_PURCHASE) "-" else "+"}${String.format("%.2f", tx.amount)} ${tx.currency}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (tx.type == TransactionType.WITHDRAWAL) CrimsonError else GoldLight
                            )
                        )
                    }
                }
            }
        }
    }
}
