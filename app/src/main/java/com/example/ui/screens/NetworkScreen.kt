package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionType
import com.example.data.model.UserMiningState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun NetworkScreen(
    userState: UserMiningState,
    onSimulateDownlinePurchase: () -> Unit,
    onSimulateNewReferral: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val inviteLink = "https://hashgrid.pro/join?ref=${userState.referralCode}"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        // Hero Referral Code & Invite Card
        item {
            GlowingBorderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("referral_hero_card"),
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
                            text = "AFFILIATE & NETWORK PROGRAM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "7% DIRECT COMMISSION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextGold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Earn 7% USDT on every hardware node deployed by your direct referrals + permanent hashrate power boosts.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.5.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Referral Code Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("YOUR REFERRAL CODE", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                            Text(
                                userState.referralCode,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Invite Link", inviteLink))
                                    Toast.makeText(context, "Referral link copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CardSurfaceElevated)
                                    .testTag("copy_ref_link")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join HashGrid Pro cloud mining with my code ${userState.referralCode}! Get free GRID mining and deploy institutional nodes: $inviteLink"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share HashGrid Referral Link"))
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary)
                                    .testTag("share_ref_link")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = ObsidianBg, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Team Metrics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Team members
                GlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = GoldPrimary.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text("Total Team", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${userState.referralCount}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        )
                        Text("+0.25 GH/s each", style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontSize = 9.5.sp))
                    }
                }

                // Active miners
                GlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = EmeraldAccent.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text("Active Miners", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${userState.activeReferredMiners}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldGlow)
                        )
                        Text("+0.50 GH/s active", style = MaterialTheme.typography.labelSmall.copy(color = TextGold, fontSize = 9.5.sp))
                    }
                }

                // Hashrate Boost
                GlassCard(
                    modifier = Modifier.weight(1f),
                    borderColor = CyanAccent.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text("Hash Bonus", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "+${String.format("%.2f", userState.referralBoostHashrateGh)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = CyanAccent)
                        )
                        Text("GH/s added", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp))
                    }
                }
            }
        }

        // Commission Rules Info Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "COMMISSION & BOOST RULES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                    )
                    Text(
                        text = "1. Direct Downline Purchases: 7% of any hardware rig price is instantly credited in USDT to your Miner Balance.\n2. Registration Boost: Earn +0.25 GH/s permanent free hashrate for every user who registers with your code.\n3. Active Mining Boost: Earn additional +0.50 GH/s when your referral runs free mining.\n4. Anti-Abuse: Self-referrals and circular chains are strictly disallowed.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                    )
                }
            }
        }

        // Interactive Live Affiliate Simulation Sandbox
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = GoldPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = "Test", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "AFFILIATE SIMULATION SANDBOX",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Test and experience the real-time affiliate payout engine right now:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSimulateDownlinePurchase,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_downline_buy_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("Simulate $100 Rig Buy (7% = $7 USDT)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                        }

                        Button(
                            onClick = onSimulateNewReferral,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sim_new_ref_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("Simulate +1 New Referral", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold))
                        }
                    }
                }
            }
        }

        // Commission Stream
        item {
            Text(
                text = "COMMISSION STREAM",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
        }

        val commissions = userState.transactions.filter { it.type == TransactionType.REFERRAL_COMMISSION }
        if (commissions.isEmpty()) {
            item {
                Text("No commissions yet. Share your invite link to start earning 7% USDT.", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
            }
        } else {
            items(commissions) { comm ->
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldAccent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            }
                            Column {
                                Text(comm.description, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("7% Instant Credit", style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontSize = 9.sp))
                            }
                        }
                        Text(
                            "+$${String.format("%.2f", comm.amount)} USDT",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = GoldLight)
                        )
                    }
                }
            }
        }
    }
}
