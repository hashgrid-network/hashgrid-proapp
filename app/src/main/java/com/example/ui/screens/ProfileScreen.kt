package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PromoStatus
import com.example.data.model.UserMiningState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    userState: UserMiningState,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onOpenLuckyWheel: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenMicroTask: () -> Unit,
    onOpenVideoPromo: () -> Unit,
    onOpenHowItWorks: () -> Unit,
    onOpenTaskPolicy: () -> Unit,
    onApprovePendingTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Institutional Node Credentials Card
        item {
            GlowingBorderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("node_credentials_card"),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(DarkNavySurface)
                                    .border(1.5.dp, GoldPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(26.dp))
                            }
                            Column {
                                Text(
                                    text = userState.nodeId,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                )
                                Text(
                                    text = userState.email,
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldDark.copy(alpha = 0.5f))
                                .border(1.dp, EmeraldAccent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TIER-1 VERIFIED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextEmerald, fontSize = 8.5.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavySurface)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Security Protocol", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                            Text("Cold-Storage", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Withdrawal Review", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                            Text("24H Audit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Affiliate Tier", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                            Text("7% Direct", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                        }
                    }
                }
            }
        }

        // Wallet & Balances Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = EmeraldAccent.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "WALLET BALANCES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Miner Balance", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                            Text(
                                "$${String.format("%.2f", userState.minerBalanceUsdt)} USDT",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("GRID Coin Balance", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                            Text(
                                "${String.format("%,.3f", userState.gridBalance)} GRID",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenDeposit,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("profile_deposit_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deposit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                        }

                        Button(
                            onClick = onOpenWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("profile_withdraw_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Withdraw", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextEmerald))
                        }
                    }
                }
            }
        }

        // Daily Lucky Wheel Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLuckyWheel() }
                    .testTag("profile_lucky_wheel_card"),
                borderColor = GoldPrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text("Daily Lucky Wheel", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Text(
                                if (userState.isSpinReady()) "★ Free Spin Ready Now" else "Cooldown in progress (24h interval)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (userState.isSpinReady()) EmeraldGlow else TextSecondary,
                                    fontWeight = if (userState.isSpinReady()) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }

                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Promotion & Tasks Hub
        item {
            Text(
                text = "PROMOTIONAL BOUNTIES & TASKS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
        }

        // Micro-Tasks Button
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMicroTask() }
                    .testTag("profile_micro_task_card"),
                borderColor = GoldPrimary.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(EmeraldDark.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Daily Micro-Tasks (Stories)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Text("WhatsApp / Insta / Telegram • $1 to $50 USDT", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                        }
                    }
                    Button(
                        onClick = onOpenMicroTask,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("SUBMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                    }
                }
            }
        }

        // Creator Video Promotion Button
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenVideoPromo() }
                    .testTag("profile_video_promo_card"),
                borderColor = PurpleAccent.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PurpleAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Creator Video Promotion", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Text("YouTube & Reels • $100 to $2000 USDT Pool", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp))
                        }
                    }
                    Button(
                        onClick = onOpenVideoPromo,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("SUBMIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }

        // Policy & Guide Links
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileOptionCard(
                    title = "Task Policy",
                    subtitle = "Organic view rules",
                    icon = Icons.Default.Gavel,
                    tint = CyanAccent,
                    onClick = onOpenTaskPolicy,
                    modifier = Modifier.weight(1f)
                )
                ProfileOptionCard(
                    title = "How It Works",
                    subtitle = "4-step protocol",
                    icon = Icons.Default.MenuBook,
                    tint = GoldPrimary,
                    onClick = onOpenHowItWorks,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Admin & Simulation Controls
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Color(0xFF334155)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Text("ADMIN & SIMULATION CONTROLS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextMuted))
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val pendingCount = userState.microTasks.count { it.status == PromoStatus.PENDING_REVIEW }
                    Button(
                        onClick = onApprovePendingTasks,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (pendingCount > 0) "Simulate Admin Approval for $pendingCount Pending Bounty ($${String.format("%.2f", userState.microTasks.sumOf { it.rewardUsdt })} USDT)" else "Approve Pending Bounties (0 Pending)",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextGold, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable { onClick() },
        borderColor = tint.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp))
        }
    }
}
