package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RigCatalogItem
import com.example.ui.MiningViewModel
import com.example.ui.theme.*

/**
 * Composable AlertDialog that displays hardware node details, deployment cost,
 * and projected yields. Includes verification logic to ensure the user has sufficient
 * minerBalanceUsdt in the ViewModel before permitting purchase finalization, and displays
 * an error message if funds are insufficient.
 */
@Composable
fun NodePurchaseDialog(
    nodeName: String,
    cost: Double,
    dailyYield: Double,
    viewModel: MiningViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hashrateGh: Double? = null,
    durationDays: Int? = null,
    onOpenDeposit: (() -> Unit)? = null
) {
    val currentMinerBalance by viewModel.minerBalanceUsdt.collectAsState()
    val hasEnoughFunds = currentMinerBalance >= cost

    NodePurchaseDialogContent(
        nodeName = nodeName,
        cost = cost,
        dailyYield = dailyYield,
        hashrateGh = hashrateGh,
        durationDays = durationDays,
        currentMinerBalance = currentMinerBalance,
        hasEnoughFunds = hasEnoughFunds,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        onOpenDeposit = onOpenDeposit,
        modifier = modifier
    )
}

/**
 * Overloaded variant accepting RigCatalogItem directly with ViewModel.
 */
@Composable
fun NodePurchaseDialog(
    rig: RigCatalogItem,
    viewModel: MiningViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDeposit: (() -> Unit)? = null
) {
    NodePurchaseDialog(
        nodeName = rig.name,
        cost = rig.priceUsdt,
        dailyYield = rig.dailyYieldUsdt,
        hashrateGh = rig.hashrateGh,
        durationDays = rig.durationDays,
        viewModel = viewModel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        onOpenDeposit = onOpenDeposit
    )
}

/**
 * Overloaded variant accepting explicit balance double for standalone testing or preview.
 */
@Composable
fun NodePurchaseDialog(
    nodeName: String,
    cost: Double,
    dailyYield: Double,
    currentMinerBalance: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hashrateGh: Double? = null,
    durationDays: Int? = null,
    onOpenDeposit: (() -> Unit)? = null
) {
    val hasEnoughFunds = currentMinerBalance >= cost

    NodePurchaseDialogContent(
        nodeName = nodeName,
        cost = cost,
        dailyYield = dailyYield,
        hashrateGh = hashrateGh,
        durationDays = durationDays,
        currentMinerBalance = currentMinerBalance,
        hasEnoughFunds = hasEnoughFunds,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        onOpenDeposit = onOpenDeposit,
        modifier = modifier
    )
}

@Composable
private fun NodePurchaseDialogContent(
    nodeName: String,
    cost: Double,
    dailyYield: Double,
    hashrateGh: Double?,
    durationDays: Int?,
    currentMinerBalance: Double,
    hasEnoughFunds: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenDeposit: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val days = durationDays ?: 200
    val monthlyYield = dailyYield * 30.0
    val totalContractYield = dailyYield * days
    val roiPercent = if (cost > 0.0) (totalContractYield / cost) * 100 else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavySurface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .border(
                width = 1.5.dp,
                color = if (hasEnoughFunds) GoldPrimary.copy(alpha = 0.6f) else CrimsonError.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("node_purchase_alert_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (hasEnoughFunds) GoldPrimary.copy(alpha = 0.15f) else CrimsonError.copy(alpha = 0.15f))
                    .border(1.5.dp, if (hasEnoughFunds) GoldPrimary else CrimsonError, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasEnoughFunds) Icons.Default.Bolt else Icons.Default.Warning,
                    contentDescription = if (hasEnoughFunds) "Purchase Ready" else "Insufficient Funds",
                    tint = if (hasEnoughFunds) GoldPrimary else CrimsonError,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Confirm Node Purchase",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (hashrateGh != null) "$nodeName • ${hashrateGh.toInt()} GH/s" else nodeName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    ),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cost & Miner Balance Check Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg.copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Deployment Cost", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(
                                text = "$${String.format("%,.2f", cost)} USDT",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Your Miner Balance", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(
                                text = "$${String.format("%,.2f", currentMinerBalance)} USDT",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasEnoughFunds) TextEmerald else CrimsonError
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        // Sufficient / Insufficient Balance Status Banner
                        if (hasEnoughFunds) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TextEmerald, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Sufficient Funds (Remaining: $${String.format("%,.2f", currentMinerBalance - cost)} USDT)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            val shortage = cost - currentMinerBalance
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CrimsonError.copy(alpha = 0.12f))
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Insufficient minerBalanceUsdt! You are short by $${String.format("%,.2f", shortage)} USDT. Please deposit funds to finalize this purchase.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = CrimsonError, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                                )
                            }
                        }
                    }
                }

                // Projected Yield Specifications Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianBg.copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PROJECTED YIELD SPECIFICATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextGold,
                                letterSpacing = 0.5.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Projected Daily Yield:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(
                                text = "+$${String.format("%.2f", dailyYield)} USDT / day",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextEmerald)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Est. 30-Day Monthly Yield:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(
                                text = "+$${String.format("%.2f", monthlyYield)} USDT (~15%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Contract Yield ($days days):", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(
                                text = "+$${String.format("%.2f", totalContractYield)} USDT (${String.format("%.0f", roiPercent)}% ROI)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasEnoughFunds) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("confirm_buy_rig_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "CONFIRM & DEPLOY NODE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, color = ObsidianBg)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenDeposit?.invoke()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("deposit_funds_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "DEPOSIT USDT TO CONTINUE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, color = ObsidianBg)
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_buy_rig_button")
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary))
            }
        }
    )
}
