package com.example.ui.components.security

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricHelper
import com.example.ui.theme.CyberGold
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreenOverlay(
    isBiometricEnabled: Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onUnlockWithBiometric: () -> Unit,
    onForgotPinClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? FragmentActivity

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val shakeOffset = remember { Animatable(0f) }

    // Biometric auto-trigger on first display / app launch
    LaunchedEffect(Unit) {
        if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "HashGrid Pro Security",
                subtitle = "Biometric Authentication Required",
                description = "Scan your fingerprint or facial recognition to access the mining dashboard.",
                negativeButtonText = "Use PIN",
                onSuccess = {
                    onUnlockWithBiometric()
                },
                onError = { /* User can enter PIN or tap biometric button */ },
                onFailed = { /* Biometric failed */ }
            )
        }
    }

    fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    fun onDigitPress(digit: String) {
        if (enteredPin.length < 4) {
            vibrate()
            val newPin = enteredPin + digit
            enteredPin = newPin

            if (newPin.length == 4) {
                coroutineScope.launch {
                    delay(80)
                    val success = onUnlockWithPin(newPin)
                    if (!success) {
                        isError = true
                        errorMessage = "Incorrect PIN. Try again."
                        // Shake animation
                        launch {
                            shakeOffset.animateTo(20f, animationSpec = tween(50))
                            shakeOffset.animateTo(-20f, animationSpec = tween(50))
                            shakeOffset.animateTo(10f, animationSpec = tween(50))
                            shakeOffset.animateTo(0f, animationSpec = tween(50))
                        }
                        delay(600)
                        enteredPin = ""
                        isError = false
                    }
                }
            }
        }
    }

    fun onDeletePress() {
        if (enteredPin.isNotEmpty()) {
            vibrate()
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07090E),
                        ObsidianBg,
                        Color(0xFF0A0F1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 28.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CyberGold.copy(alpha = 0.12f))
                        .border(1.5.dp, CyberGold.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = CyberGold,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "HASHGRID PRO",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (BiometricHelper.isBiometricAvailable(context))
                        "Scan Fingerprint / Face or Enter PIN"
                    else
                        "Enter 4-Digit Security PIN",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

                if (BiometricHelper.isBiometricAvailable(context) && activity != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            BiometricHelper.showBiometricPrompt(
                                activity = activity,
                                title = "HashGrid Pro Security",
                                subtitle = "Biometric Authentication Required",
                                description = "Scan your fingerprint or facial recognition to access the mining dashboard.",
                                negativeButtonText = "Use PIN",
                                onSuccess = { onUnlockWithBiometric() },
                                onError = {},
                                onFailed = {}
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGold.copy(alpha = 0.15f),
                            contentColor = CyberGold
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Sensor",
                                tint = CyberGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Scan Fingerprint / Face",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // PIN Dots
                Row(
                    modifier = Modifier.offset(x = shakeOffset.value.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        val dotColor = when {
                            isError -> Color(0xFFEF5350)
                            isFilled -> CyberGold
                            else -> Color.White.copy(alpha = 0.2f)
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) dotColor else Color.Transparent)
                                .border(2.dp, dotColor, CircleShape)
                        )
                    }
                }

                if (isError && errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFEF5350),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            when (key) {
                                "BIO" -> {
                                    if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(SurfaceDark.copy(alpha = 0.6f))
                                                .clickable {
                                                    BiometricHelper.showBiometricPrompt(
                                                        activity = activity,
                                                        title = "HashGrid Pro Security",
                                                        subtitle = "Biometric Authentication Required",
                                                        description = "Scan your fingerprint or facial recognition to access the mining dashboard.",
                                                        negativeButtonText = "Use PIN",
                                                        onSuccess = { onUnlockWithBiometric() },
                                                        onError = {},
                                                        onFailed = {}
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometric",
                                                tint = CyberGold,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(72.dp))
                                    }
                                }
                                "DEL" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceDark.copy(alpha = 0.4f))
                                            .clickable { onDeletePress() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Delete",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceDark.copy(alpha = 0.65f))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                            .clickable { onDigitPress(key) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Forgot PIN / Restore Action
            TextButton(
                onClick = onForgotPinClick,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Forgot PIN? Restore using Secret Key",
                    color = CyberGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
