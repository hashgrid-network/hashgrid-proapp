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
import androidx.compose.ui.draw.scale
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
    onOpenSecretKeyBackup: () -> Unit = {},
    onOpenSecretKeyRestore: () -> Unit = {},
    onOpenPinSetup: () -> Unit = {},
    onToggleBiometric: (Boolean) -> Unit = {},
    onLockAppNow: () -> Unit = {},
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

        // Web3 Security & Smart App Lock Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_web3_card"),
                borderColor = GoldPrimary.copy(alpha = 0.4f)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Text(
                                "WEB3 SECURITY & APP LOCK",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                            )
                        }

                        // Backup Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (userState.isKeyBackedUp) EmeraldDark.copy(alpha = 0.4f) else GoldDark.copy(alpha = 0.4f))
                                .border(1.dp, if (userState.isKeyBackedUp) EmeraldAccent else GoldPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (userState.isKeyBackedUp) "BACKED UP ✓" else "KEY BACKUP REQUIRED ⚠️",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (userState.isKeyBackedUp) TextEmerald else TextGold,
                                    fontSize = 8.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secret Key Display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkNavySurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Account Secret Key", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.sp))
                            val maskedKey = if (userState.secretKey.length > 8) {
                                "${userState.secretKey.take(7)}••••-••••-${userState.secretKey.takeLast(4)}"
                            } else {
                                userState.secretKey
                            }
                            Text(
                                maskedKey,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight,
                                    fontSize = 11.5.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSecretKeyBackup,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                        ) {
                            Text("View / Copy", fontSize = 11.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // PIN & Biometric Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenPinSetup,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                        ) {
                            Icon(Icons.Default.Pin, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (userState.isPinConfigured) "Change PIN" else "Set 4-Digit PIN",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenSecretKeyRestore,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Key", fontSize = 11.sp, color = TextEmerald)
                        }
                    }

                    // Biometric Toggle and Instant Lock Row
                    if (userState.isPinConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkNavySurface.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fingerprint Unlock", fontSize = 11.sp, color = TextPrimary)
                            }

                            Switch(
                                checked = userState.isBiometricEnabled,
                                onCheckedChange = onToggleBiometric,
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = GoldPrimary)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = onLockAppNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Terminal Now (Test Security)", fontSize = 11.sp, color = GoldLight)
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
                    val gridUsdVal = userState.gridBalance * 0.05
                    val totalPort = userState.minerBalanceUsdt + gridUsdVal

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WALLET BALANCES",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanAccent.copy(alpha = 0.15f))
                                .border(0.5.dp, CyanAccent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Pre-Launch: $0.05 / GRID",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent,
                                    fontSize = 8.5.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Est. Portfolio: $${String.format("%,.2f", totalPort)} USD",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 10.sp)
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
                            Text("Withdrawable", style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontSize = 8.5.sp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("GRID Balance", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                            Text(
                                "${String.format("%,.3f", userState.gridBalance)} GRID",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color.White)
                            )
                            Text("≈ $${String.format("%.2f", gridUsdVal)} USD", style = MaterialTheme.typography.labelSmall.copy(color = TextGold, fontSize = 8.5.sp))
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
