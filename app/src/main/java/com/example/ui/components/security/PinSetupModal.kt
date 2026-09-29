package com.example.ui.components.security

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.security.BiometricHelper
import com.example.ui.theme.CyberGold
import com.example.ui.theme.SurfaceDark

@Composable
fun PinSetupModal(
    onDismiss: () -> Unit,
    onPinSet: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val hasBiometric = remember { BiometricHelper.isBiometricAvailable(context) }

    var step by remember { mutableIntStateOf(1) } // 1 = Enter new PIN, 2 = Confirm PIN
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(hasBiometric) }
    var errorMessage by remember { mutableStateOf("") }

    val currentEntered = if (step == 1) firstPin else confirmPin

    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    }

    fun onDigitPress(digit: String) {
        if (currentEntered.length < 4) {
            vibrate()
            val newPin = currentEntered + digit
            if (step == 1) {
                firstPin = newPin
                if (newPin.length == 4) {
                    step = 2
                    errorMessage = ""
                }
            } else {
                confirmPin = newPin
                if (newPin.length == 4) {
                    if (newPin == firstPin) {
                        onPinSet(newPin, enableBiometric)
                    } else {
                        errorMessage = "PINs do not match. Try again."
                        confirmPin = ""
                        step = 1
                        firstPin = ""
                    }
                }
            }
        }
    }

    fun onDeletePress() {
        if (currentEntered.isNotEmpty()) {
            vibrate()
            if (step == 1) {
                firstPin = firstPin.dropLast(1)
            } else {
                confirmPin = confirmPin.dropLast(1)
            }
            errorMessage = ""
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CyberGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Pin,
                        contentDescription = "PIN",
                        tint = CyberGold,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (step == 1) "Create 4-Digit PIN" else "Confirm 4-Digit PIN",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (step == 1) "Set a quick access PIN for your mining terminal" else "Re-enter the same 4 digits to confirm",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < currentEntered.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) CyberGold else Color.Transparent)
                                .border(2.dp, if (isFilled) CyberGold else Color.White.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Biometric toggle
                if (hasBiometric) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F1522))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric",
                                tint = CyberGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable Fingerprint Unlock",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Switch(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = CyberGold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Compact Keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            if (key.isEmpty()) {
                                Spacer(modifier = Modifier.size(56.dp))
                            } else if (key == "DEL") {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .clickable { onDeletePress() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "Delete",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF131A29))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                        .clickable { onDigitPress(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Skip for Now",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
