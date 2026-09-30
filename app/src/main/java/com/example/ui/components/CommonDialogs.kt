package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DefaultRigs
import com.example.data.model.TaskPlatform
import com.example.ui.theme.*

@Composable
fun DepositDialog(
    onDismiss: () -> Unit,
    onInitiateNowPayments: (amountUsd: Double, payCurrency: String) -> Unit,
    onConfirmDeposit: (amount: Double, network: String) -> Unit
) {
    val context = LocalContext.current
    var selectedCryptoCode by remember { mutableStateOf("usdtbsc") }
    var depositAmountText by remember { mutableStateOf("50") }

    val cryptoCurrencies = listOf(
        Triple("usdtbsc", "USDT (BSC BEP20)", "BEP20"),
        Triple("usdttrc20", "USDT (TRON TRC20)", "TRC20"),
        Triple("btc", "Bitcoin (BTC)", "BTC"),
        Triple("eth", "Ethereum (ETH)", "ERC20"),
        Triple("trx", "TRON (TRX)", "TRX"),
        Triple("sol", "Solana (SOL)", "SOL")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                borderColor = GoldPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DEPOSIT CRYPTO",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextGold
                                )
                            )
                            Text(
                                text = "Powered by NOWPayments Gateway",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldGlow,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkNavySurface)
                                .testTag("close_deposit_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Deposit Amount Input
                    OutlinedTextField(
                        value = depositAmountText,
                        onValueChange = { depositAmountText = it },
                        label = { Text("Deposit Amount (USD / USDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deposit_amount_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Select Payment Cryptocurrency",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Crypto selector chips
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cryptoCurrencies.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { (code, label, net) ->
                                    val isSel = selectedCryptoCode == code
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) CardSurfaceElevated else DarkNavySurface)
                                            .border(1.dp, if (isSel) GoldPrimary else Color(0xFF334155), RoundedCornerShape(8.dp))
                                            .clickable { selectedCryptoCode = code }
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                            .testTag("crypto_select_$code"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) GoldLight else TextSecondary,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Button: Generate NOWPayments Invoice
                    Button(
                        onClick = {
                            val amt = depositAmountText.toDoubleOrNull() ?: 50.0
                            onInitiateNowPayments(amt, selectedCryptoCode)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("nowpayments_generate_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PAY VIA NOWPAYMENTS GATEWAY",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianBg
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Notice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = "Info", tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Text(
                            text = "NOWPayments automatically monitors the blockchain and notifies HashGrid IPN for instant Firestore balance credit.",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Test Buttons for Instant Credit
                    Text(
                        text = "Instant Development / Test Credit",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10.0, 25.0, 50.0, 100.0).forEach { amt ->
                            OutlinedButton(
                                onClick = { onConfirmDeposit(amt, selectedCryptoCode.uppercase()) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_deposit_${amt.toInt()}"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("+$$amt", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WithdrawalDialog(
    currentBalanceUsdt: Double,
    onDismiss: () -> Unit,
    onConfirmWithdraw: (amount: Double, address: String, network: String) -> Unit
) {
    var selectedNetwork by remember { mutableStateOf("BEP20 (BSC)") }
    var addressInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("15.0") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                borderColor = EmeraldAccent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WITHDRAW USDT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextEmerald
                            )
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkNavySurface)
                                .testTag("close_withdrawal_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Withdrawable balance card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("AVAILABLE BALANCE: WITHDRAWABLE FROM RIGS & REFERRALS", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 7.5.sp))
                                Text(
                                    "$${String.format("%.2f", currentBalanceUsdt)} USDT",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = GoldLight)
                                )
                                Text("✓ Withdrawable (Rigs & Referrals)", style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald, fontSize = 8.5.sp, fontWeight = FontWeight.Bold))
                            }
                            Button(
                                onClick = { amountInput = String.format("%.2f", currentBalanceUsdt) },
                                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("MAX", style = MaterialTheme.typography.labelSmall.copy(color = GoldPrimary, fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Network selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("BEP20 (BSC)", "TRC20 (TRON)").forEach { net ->
                            val isSel = selectedNetwork == net
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) EmeraldAccent else Color.Transparent)
                                    .clickable { selectedNetwork = net }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = net,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) ObsidianBg else TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount input
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it; errorMessage = null },
                        label = { Text("Amount (Minimum $10 USDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_amount_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldAccent,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Address input
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it; errorMessage = null },
                        label = { Text("Recipient $selectedNetwork Address") },
                        placeholder = { Text("Paste your personal wallet address") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_address_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldAccent,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.labelSmall.copy(color = CrimsonError, fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 24H review window notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = "Security", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "24-HOUR SECURITY REVIEW WINDOW",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextGold)
                                )
                                Text(
                                    text = "All outgoing withdrawals undergo automated cold-storage signature verification. Status progresses: Pending → Processing → Completed.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 9.5.sp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (amt < 10.0 || amt > currentBalanceUsdt) {
                                errorMessage = "Insufficient withdrawable USDT balance."
                                onConfirmWithdraw(amt, addressInput, selectedNetwork)
                            } else if (addressInput.length < 10) {
                                errorMessage = "Please enter a valid wallet address."
                            } else {
                                onConfirmWithdraw(amt, addressInput, selectedNetwork)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_withdrawal_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "CONFIRM WITHDRAWAL",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianBg
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfitCalculatorDialog(
    onDismiss: () -> Unit,
    gridPriceUsd: Double = 0.01,
    onDeployNode: ((hashrateGh: Double) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkNavySurface)
                            .testTag("close_calc_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                HashrateProfitCalculator(
                    initialHashrateGh = 30.0,
                    gridMarketPriceUsd = gridPriceUsd,
                    onDeployNodeClicked = { gh ->
                        onDismiss()
                        onDeployNode?.invoke(gh)
                    }
                )
            }
        }
    }
}

@Composable
fun MicroTaskSubmissionDialog(
    onDismiss: () -> Unit,
    onSubmit: (platform: TaskPlatform, initialViews: Int, finalViews: Int, notes: String) -> Unit
) {
    var selectedPlatform by remember { mutableStateOf(TaskPlatform.WHATSAPP_STATUS) }
    var initialViews by remember { mutableStateOf("25") }
    var finalViews by remember { mutableStateOf("110") }
    var notes by remember { mutableStateOf("Submitted 2 screenshots at start and +12h mark") }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            borderColor = GoldPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SUBMIT DAILY MICRO-TASK",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextGold)
                )
                Text(
                    text = "Bounty: $1.00 to $50.00 USDT",
                    style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGlow)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Platform selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TaskPlatform.WHATSAPP_STATUS to "WhatsApp",
                        TaskPlatform.INSTAGRAM_STORY to "Instagram",
                        TaskPlatform.TELEGRAM_STORY to "Telegram"
                    ).forEach { (plat, label) ->
                        val isSel = plat == selectedPlatform
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) GoldPrimary else DarkNavySurface)
                                .clickable { selectedPlatform = plat }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isSel) ObsidianBg else TextSecondary))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = initialViews,
                    onValueChange = { initialViews = it },
                    label = { Text("Initial View Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = finalViews,
                    onValueChange = { finalViews = it },
                    label = { Text("View Count after 12 Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Screenshot Proof Links / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmit(
                            selectedPlatform,
                            initialViews.toIntOrNull() ?: 0,
                            finalViews.toIntOrNull() ?: 0,
                            notes
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SUBMIT FOR ADMIN AUDIT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                }
            }
        }
    }
}

@Composable
fun VideoPromotionDialog(
    onDismiss: () -> Unit,
    onSubmit: (platform: TaskPlatform, url: String, channel: String) -> Unit
) {
    var platform by remember { mutableStateOf(TaskPlatform.YOUTUBE_VIDEO) }
    var videoUrl by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            borderColor = GoldPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CREATOR VIDEO PROMOTION",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextGold)
                )
                Text(
                    text = "Reward Pool: $100 to $2,000 USDT every 15 days",
                    style = MaterialTheme.typography.labelSmall.copy(color = EmeraldGlow)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        TaskPlatform.YOUTUBE_VIDEO to "YouTube Video",
                        TaskPlatform.INSTAGRAM_REEL to "Instagram Reel"
                    ).forEach { (plat, label) ->
                        val isSel = plat == platform
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) GoldPrimary else DarkNavySurface)
                                .clickable { platform = plat }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isSel) ObsidianBg else TextSecondary))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("Channel / Handle Name") },
                    placeholder = { Text("@cryptominer_pro") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Video URL Link") },
                    placeholder = { Text("https://youtube.com/watch?v=...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Notice: Only genuine organic views are counted. Fake or bot traffic results in immediate permanent ban.",
                    style = MaterialTheme.typography.labelSmall.copy(color = CrimsonError, fontSize = 10.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (videoUrl.isNotBlank()) {
                            onSubmit(platform, videoUrl, channel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SUBMIT VIDEO", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                }
            }
        }
    }
}

@Composable
fun HowItWorksDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            borderColor = GoldPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HOW HASHGRID WORKS", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextGold))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val steps = listOf(
                    Triple("1. Free Mining (GRID Coins)", "Tap Core Online every 24 hours to earn free GRID coins. Invite friends to boost your free hashrate up to 10 GH/s.", Icons.Default.Bolt),
                    Triple("2. Deploy Paid Cloud Rigs", "Deploy ASIC hardware nodes from $10 to $1000. Each rig operates for 195-210 days generating ~15% net yield monthly directly into your withdrawable Miner Balance.", Icons.Default.Memory),
                    Triple("3. 7% Instant Commission", "Share your unique invite link. Whenever a direct downline member deploys any rig, 7% USDT commission is instantly credited to your wallet.", Icons.Default.Hub),
                    Triple("4. Secure Audited Payouts", "Withdraw your USDT balance anytime (Min $10) via BEP20 or TRC20 with 24-hour cold-storage automated security audits.", Icons.Default.AccountBalanceWallet)
                )

                steps.forEach { (title, desc, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = title, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                            Text(desc, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("GOT IT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                }
            }
        }
    }
}

@Composable
fun TaskPolicyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            borderColor = CyanAccent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TASK COMPLETION POLICY", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = CyanAccent))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "1. Story & Status Micro-Tasks:\n• Submissions must show the initial posting time and view count.\n• A follow-up screenshot after 12 hours is mandatory.\n• Limit: 1 submission per platform per 24 hours.\n\n2. Creator Video Promotions:\n• Video reviews are audited bi-weekly (every 15 days).\n• Rewards range between $100 and $2000 USDT based on organic view density and authentic audience engagement.\n• Any synthetic traffic or fake views will trigger permanent account freezing.\n\n3. Payout Processing:\n• Approved task bounties are directly credited to your withdrawable Miner Balance.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("UNDERSTOOD", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                }
            }
        }
    }
}

@Composable
fun GridPreLaunchLockedDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            borderColor = CyanAccent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 GRID Token Pre-Launch Notice",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = CyanAccent)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Free mined GRID tokens are currently locked during the official testnet / pre-launch phase. Unlocking will occur at the Token Generation Event (TGE) upon DEX/CEX mainnet listing.\n\nOnly USDT profits earned through Active Hardware Nodes and 7% Downline Referral Commissions are instantly withdrawable.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("grid_locked_dialog_ok_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                }
            }
        }
    }
}

