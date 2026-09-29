package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserMiningState
import com.example.ui.AppNavTab
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun CloudMinerScreen(
    userState: UserMiningState,
    onStartMining: () -> Unit,
    onOpenLuckyWheel: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToRigsStore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CoreRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (userState.isFreeMiningActive) 5000 else 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CoreAngle"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (userState.isFreeMiningActive) 8000 else 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterAngle"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAnim"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Core Online Interactive Circular Reactor
        item {
            GlowingBorderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mining_reactor_card"),
                glowColor = if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUANTUM FREE CORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (userState.isFreeMiningActive) EmeraldDark else DarkNavySurface)
                                .border(1.dp, if (userState.isFreeMiningActive) EmeraldAccent else CrimsonError, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (userState.isFreeMiningActive) "ONLINE (24H)" else "SESSION EXPIRED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (userState.isFreeMiningActive) TextEmerald else CrimsonError,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Circular Reactor Core (Big interactive button)
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .scale(if (userState.isFreeMiningActive) pulseScale else 1.0f)
                            .clip(CircleShape)
                            .clickable {
                                if (!userState.isFreeMiningActive) {
                                    onStartMining()
                                }
                            }
                            .testTag("core_online_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer animated rotating ring 1
                        Canvas(modifier = Modifier.size(230.dp).rotate(rotationAngle)) {
                            val stroke = 4.dp.toPx()
                            val color1 = if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary
                            val color2 = if (userState.isFreeMiningActive) CyanAccent else GoldLight
                            drawArc(
                                brush = Brush.sweepGradient(listOf(color1, Color.Transparent, color2, Color.Transparent, color1)),
                                startAngle = 0f,
                                sweepAngle = 280f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }

                        // Inner counter rotating ring 2
                        Canvas(modifier = Modifier.size(190.dp).rotate(counterRotation)) {
                            val stroke = 3.dp.toPx()
                            val color = if (userState.isFreeMiningActive) EmeraldGlow else GoldDark
                            drawArc(
                                color = color.copy(alpha = 0.6f),
                                startAngle = 45f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }

                        // Center solid core button
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .shadow(16.dp, CircleShape, spotColor = if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = if (userState.isFreeMiningActive) {
                                            listOf(EmeraldDark, CardSurfaceElevated, ObsidianBg)
                                        } else {
                                            listOf(GoldDark.copy(alpha = 0.8f), CardSurfaceElevated, ObsidianBg)
                                        }
                                    )
                                )
                                .border(
                                    2.dp,
                                    if (userState.isFreeMiningActive) EmeraldAccent else GoldPrimary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Core",
                                    tint = if (userState.isFreeMiningActive) EmeraldGlow else GoldLight,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (userState.isFreeMiningActive) "CORE ONLINE" else "START MINING",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                if (userState.isFreeMiningActive) {
                                    val rem = userState.freeSessionRemainingSeconds()
                                    val hh = rem / 3600
                                    val mm = (rem % 3600) / 60
                                    val ss = rem % 60
                                    Text(
                                        text = String.format("%02d:%02d:%02d", hh, mm, ss),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldGlow,
                                            fontSize = 12.sp
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "24-Hour Cycle",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextGold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live GRID Balance Counter Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TOTAL MINED GRID TOKENS",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CyanAccent.copy(alpha = 0.15f))
                                        .border(0.5.dp, CyanAccent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "PRE-LAUNCH",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent,
                                            fontSize = 7.5.sp
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format("%,.4f", userState.gridBalance)} GRID",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldLight
                                )
                            )
                            Text(
                                text = "≈ $${String.format("%.2f", userState.gridBalance * 0.05)} USD (@ $0.05 Pre-Launch Rate)",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontSize = 9.5.sp)
                            )
                        }
                    }
                }
            }
        }

        // Free Hashrate Hard Cap Progress (10.0 GH/s Max)
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
                            text = "FREE HASHRATE CAPACITY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                        )
                        Text(
                            text = "${String.format("%.2f", userState.aggregateFreeHashrateGh)} / 10.00 GH/s Max Cap",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val capRatio = (userState.aggregateFreeHashrateGh / 10.0).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(
                        progress = { capRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldAccent,
                        trackColor = DarkNavySurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "To prevent abuse, free hashrate is hard capped at 10.0 GH/s. Boost power via referrals (+0.25 to +0.50 GH/s) and daily lucky spins.",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.5.sp)
                    )
                }
            }
        }

        // Hashrate Breakdown Matrix
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "POWER ALLOCATION MATRIX",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base Node Allocation:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text("${String.format("%.2f", userState.baseFreeHashrateGh)} GH/s", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Referral Hashrate Boost:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text("+${String.format("%.2f", userState.referralBoostHashrateGh)} GH/s", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                    }

                    if (userState.isBoostActive()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lucky Wheel 24H Boost:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text("+${String.format("%.2f", userState.temporaryBoostHashrateGh)} GH/s", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deployed Paid Hardware Nodes:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        Text("+${String.format("%.2f", userState.activePaidRigsHashrateGh)} GH/s", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = CyanAccent))
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Aggregate Hashpower:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        Text(
                            "${String.format("%.2f", userState.totalAggregateHashrateGh)} GH/s",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                        )
                    }
                }
            }
        }

        // Embedded Interactive Hashrate Profit Calculator with Slider UI
        item {
            com.example.ui.components.HashrateProfitCalculator(
                initialHashrateGh = userState.totalAggregateHashrateGh.coerceAtLeast(6.0),
                gridMarketPriceUsd = 0.145,
                onDeployNodeClicked = { onNavigateToRigsStore() }
            )
        }

        // Referral Boost Shortcut
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToNetwork() },
                borderColor = GoldPrimary.copy(alpha = 0.4f)
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
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Boost Hashrate with Referrals", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Text("+0.25 GH/s registration • +0.50 GH/s active miner", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp))
                        }
                    }
                    Button(
                        onClick = onNavigateToNetwork,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("INVITE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                    }
                }
            }
        }
    }
}
