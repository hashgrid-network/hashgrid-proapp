package com.example.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun HashrateProfitCalculator(
    initialHashrateGh: Double = 30.0,
    gridMarketPriceUsd: Double = 0.05,
    onDeployNodeClicked: ((hashrateGh: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var hashrateGh by remember { mutableFloatStateOf(initialHashrateGh.toFloat().coerceIn(1f, 500f)) }
    var selectedTabPeriod by remember { mutableIntStateOf(2) } // 0 = Daily, 1 = Weekly, 2 = Monthly, 3 = 200-Day Cycle

    // Presets
    val presets = listOf(
        "1 GH/s" to 1f,
        "6 GH/s" to 6f,
        "30 GH/s" to 30f,
        "100 GH/s" to 100f,
        "400 GH/s" to 400f
    )

    // Yield Calculations
    // 1 GH/s corresponds to approx $3.33 of hardware value at standard enterprise density (e.g. $10 for 2 GH/s, $100 for 30 GH/s)
    val estimatedHardwareCostUsd = (hashrateGh * 3.333).toDouble()
    // Monthly yield is 15% net yield in USDT
    val monthlyUsdtYield = estimatedHardwareCostUsd * 0.15
    val dailyUsdtYield = monthlyUsdtYield / 30.0
    val weeklyUsdtYield = dailyUsdtYield * 7.0
    val cycle200DaysUsdtYield = dailyUsdtYield * 200.0

    // Mined GRID coins formula: 1 GH/s produces approx 30.24 GRID / day in the quantum core
    val dailyGridCoins = (hashrateGh * 30.24).toDouble()
    val weeklyGridCoins = dailyGridCoins * 7.0
    val monthlyGridCoins = dailyGridCoins * 30.0
    val cycle200DaysGridCoins = dailyGridCoins * 200.0

    // Selected period outputs
    val (periodTitle, periodUsdt, periodGrid, periodDays) = when (selectedTabPeriod) {
        0 -> Quadruple("24-Hour Daily Return", dailyUsdtYield, dailyGridCoins, 1)
        1 -> Quadruple("7-Day Weekly Return", weeklyUsdtYield, weeklyGridCoins, 7)
        2 -> Quadruple("30-Day Monthly Return", monthlyUsdtYield, monthlyGridCoins, 30)
        else -> Quadruple("200-Day Full Cycle Return", cycle200DaysUsdtYield, cycle200DaysGridCoins, 200)
    }

    val gridValueInUsd = periodGrid * gridMarketPriceUsd
    val totalEstimatedValueUsd = periodUsdt + gridValueInUsd

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hashrate_profit_calculator"),
        borderColor = GoldPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Title & Live Market Rate Tag
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.2f))
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "HASHRATE YIELD CALCULATOR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextGold
                            )
                        )
                        Text(
                            text = "Simulate 15% monthly USDT yield + GRID token power",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkNavySurface)
                        .border(1.dp, CyanAccent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PRE-LAUNCH: $0.05 / GRID",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 9.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hashrate Gauge Display & Stepper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkNavySurface)
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SELECTED COMPUTING HASHRATE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 9.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format("%.1f", hashrateGh),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GH/s",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // Stepper Buttons (+ / -)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                hashrateGh = (hashrateGh - 5f).coerceAtLeast(1f)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardSurfaceElevated)
                                .border(1.dp, Color(0xFF334155), CircleShape)
                                .testTag("calc_minus_hashrate")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                hashrateGh = (hashrateGh + 5f).coerceAtMost(500f)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                                .testTag("calc_plus_hashrate")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = ObsidianBg, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Clean Interactive Slider
            Slider(
                value = hashrateGh,
                onValueChange = { hashrateGh = it },
                valueRange = 1f..500f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hashrate_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = GoldPrimary,
                    activeTrackColor = GoldPrimary,
                    inactiveTrackColor = Color(0xFF1E293B)
                )
            )

            // Preset Quick-Picks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (label, value) ->
                    val isSel = (hashrateGh - value).let { it >= -0.5f && it <= 0.5f }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) GoldPrimary else CardSurfaceElevated)
                            .border(1.dp, if (isSel) GoldPrimary else Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable { hashrateGh = value }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) ObsidianBg else TextSecondary,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Period Switcher (Daily, Weekly, Monthly, 200-Day Cycle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavySurface)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Daily", "Weekly", "Monthly", "200-Days").forEachIndexed { idx, title ->
                    val isSel = selectedTabPeriod == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) EmeraldAccent else Color.Transparent)
                            .clickable { selectedTabPeriod = idx }
                            .padding(vertical = 6.dp)
                            .testTag("calc_period_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) ObsidianBg else TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Estimated Return Highlight Card
            GlowingBorderCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = EmeraldAccent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = periodTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextEmerald,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "USDT Miner Yield",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Text(
                                text = "+$${String.format("%.2f", periodUsdt)} USDT",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldLight
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(Color(0xFF334155))
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Mined GRID Coins",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                            Text(
                                text = "+${String.format("%,.1f", periodGrid)} GRID",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "≈ +$${String.format("%.2f", gridValueInUsd)} USD",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextGold, fontSize = 9.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Combined Estimated Value:",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "≈ $${String.format("%.2f", totalEstimatedValueUsd)} USD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldGlow
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ROI & Node Deployment Breakdown Specs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkNavySurface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Est. Node Cost", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                    Text("$${String.format("%.0f", estimatedHardwareCostUsd)} USD", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net Monthly Yield", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                    Text("~15.00%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("200-Day Return", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp))
                    Text("+100.0% Net", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                }
            }

            if (onDeployNodeClicked != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { onDeployNodeClicked(hashrateGh.toDouble()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("calc_deploy_node_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DEPLOY THIS HASHRATE (${String.format("%.0f", hashrateGh)} GH/s)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ObsidianBg
                        )
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
