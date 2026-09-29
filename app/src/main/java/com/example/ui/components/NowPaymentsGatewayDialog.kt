package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.payment.NowPaymentResponse
import com.example.ui.theme.*

@Composable
fun NowPaymentsGatewayDialog(
    payment: NowPaymentResponse,
    isCheckingStatus: Boolean,
    onCheckStatus: (paymentId: String) -> Unit,
    onSimulateConfirm: (paymentId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "PulseGateway")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseG"
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
            GlowingBorderCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(4.dp),
                glowColor = GoldPrimary
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "NOWPAYMENTS GATEWAY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextGold
                                    )
                                )
                                Text(
                                    text = "ID: ${payment.paymentId}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkNavySurface)
                                .testTag("close_nowpayments_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, if (payment.isSuccessOrConfirmed) EmeraldAccent else GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (payment.isSuccessOrConfirmed) EmeraldAccent else GoldPrimary)
                                        .scale(pulseScale)
                                )
                                Text(
                                    text = "STATUS: ${payment.paymentStatus.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (payment.isSuccessOrConfirmed) TextEmerald else TextGold
                                    )
                                )
                            }

                            Text(
                                text = "Auto-Listening IPN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pay Amount Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardSurfaceElevated)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SEND EXACT AMOUNT",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format("%.4f", payment.payAmount)} ${payment.payCurrency.uppercase()}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "≈ $${String.format("%.2f", payment.priceAmount)} USD Equivalent",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextEmerald)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stylized QR Code
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(118.dp)) {
                            val cellSize = size.width / 9f
                            for (i in 0..8) {
                                for (j in 0..8) {
                                    if ((i in 0..2 && j in 0..2) || (i in 6..8 && j in 0..2) || (i in 0..2 && j in 6..8) || (i * 3 + j * 7) % 5 == 0) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(i * cellSize, j * cellSize),
                                            size = Size(cellSize * 0.9f, cellSize * 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtle Gateway Clarification Notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.5.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Automated Gateway: A unique one-time deposit address is generated for this transaction. Funds will automatically credit to your mining balance upon network confirmation.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Deposit Address Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "DEPOSIT ADDRESS (${payment.payCurrency.uppercase()})",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (payment.payAddress.length > 22) {
                                    "${payment.payAddress.take(12)}...${payment.payAddress.takeLast(8)}"
                                } else {
                                    payment.payAddress
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Deposit Address", payment.payAddress))
                                    Toast.makeText(context, "Address copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_nowpayments_address")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (Check Status & Simulate Instant Confirmation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCheckStatus(payment.paymentId) },
                            enabled = !isCheckingStatus,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("check_payment_status_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isCheckingStatus) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ObsidianBg, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CHECK STATUS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                            }
                        }

                        Button(
                            onClick = { onSimulateConfirm(payment.paymentId) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("simulate_payment_confirm_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("INSTANT VERIFY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextEmerald))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Payment is automatically verified via NOWPayments IPN and credited to your Firestore account upon blockchain confirmation.",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentSuccessDialog(
    payment: NowPaymentResponse?,
    onDismiss: () -> Unit
) {
    if (payment == null) return

    Dialog(onDismissRequest = onDismiss) {
        GlowingBorderCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            glowColor = EmeraldAccent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(EmeraldDark.copy(alpha = 0.5f))
                        .border(2.dp, EmeraldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = EmeraldGlow,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PAYMENT CONFIRMED!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldGlow
                    )
                )

                Text(
                    text = "NOWPayments Gateway Verified",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextGold,
                        fontWeight = FontWeight.Bold
                    )
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
                            Text("Payment ID:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text(payment.paymentId, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount Paid:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text("${String.format("%.4f", payment.payAmount)} ${payment.payCurrency.uppercase()}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = GoldLight))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("USD Value:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text("$${String.format("%.2f", payment.priceAmount)} USD", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = TextEmerald))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Firestore Status:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                            Text("COMPLETED (Synced)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = EmeraldGlow))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Your mining plan/wallet balance has been activated and updated across the HashGrid network.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center, fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dismiss_success_payment_dialog"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "VIEW UPDATED DASHBOARD",
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
