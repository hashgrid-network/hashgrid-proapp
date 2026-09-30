package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.SecretKeyUtils
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowingBorderCard
import com.example.ui.theme.*

@Composable
fun WelcomeAuthScreen(
    isLoading: Boolean,
    onCreateAccount: () -> Unit,
    onRestoreAccount: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var isLoginMode by remember { mutableStateOf(false) }
    var inputKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isLoginMode) {
        isLoginMode = false
        errorMessage = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("welcome_auth_screen")
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GoldPrimary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // High-End Sacred Triangle Branding Header
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_sacred_triangle),
                contentDescription = "The Quantum Apex Matrix Logo",
                tint = Color.Unspecified, // Keeps gold/cyan/emerald custom colors
                modifier = Modifier
                    .size(96.dp)
                    .testTag("welcome_sacred_triangle_logo")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "HASHGRID PRO",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextGold,
                    letterSpacing = 2.sp
                )
            )

            Text(
                text = "INSTITUTIONAL CLOUD MINING & WEB3 PROTOCOL",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 9.sp
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedContent(
                targetState = isLoginMode,
                label = "AuthModeTransition"
            ) { inLogin ->
                if (!inLogin) {
                    // ==========================================
                    // WELCOME MODE: CREATE OR RESTORE
                    // ==========================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Protocol Highlights Card
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = GoldPrimary.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "15% Monthly Node Yield (USDT)",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Non-Custodial Web3 KeyStore Cryptography",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Instant 24/7 Cloud Node Synchronization",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 1: Create New Account
                        Button(
                            onClick = onCreateAccount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_create_new_account"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "✨ CREATE NEW ACCOUNT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ObsidianBg,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        // Button 2: Restore / Login with Secret Key
                        Button(
                            onClick = {
                                isLoginMode = true
                                errorMessage = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_restore_login_account"),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkNavySurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = TextGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔑 RESTORE / LOGIN WITH KEY",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextGold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Non-Custodial Protocol: You own your secret keys and mining nodes. No centralized passwords required.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // ==========================================
                    // LOGIN / RESTORE MODE
                    // ==========================================
                    GlowingBorderCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = CyanAccent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    IconButton(
                                        onClick = {
                                            isLoginMode = false
                                            errorMessage = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextGold)
                                    }
                                    Text(
                                        text = "RESTORE ACCOUNT",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CyanAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "WEB3 AUTH",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = CyanAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "Enter your 16-character Web3 Secret Key or Master Admin Key to restore node balances and active rigs:",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )

                            // Secret Key Input with Auto-Formatting and Paste
                            OutlinedTextField(
                                value = inputKey,
                                onValueChange = { raw ->
                                    val cleaned = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }
                                    inputKey = cleaned
                                    errorMessage = null
                                },
                                label = { Text("Secret Key (HG-XXXX-XXXX-XXXX-XXXX)", fontSize = 11.sp) },
                                placeholder = { Text("HG-7K9P-M2X4-W8Q1-J5R3", color = TextMuted) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = clipboard?.primaryClip
                                                if (clip != null && clip.itemCount > 0) {
                                                    val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: ""
                                                    if (text.isNotBlank()) {
                                                        inputKey = SecretKeyUtils.normalizeSecretKey(text)
                                                        errorMessage = null
                                                    }
                                                }
                                            } catch (e: Throwable) {
                                                errorMessage = "Clipboard read error: ${e.message}"
                                            }
                                        },
                                        modifier = Modifier.testTag("auth_paste_key_btn")
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = CyanAccent)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_secret_key_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall.copy(color = CrimsonError, fontWeight = FontWeight.Bold)
                                )
                            }

                            // Submit Button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    val clean = SecretKeyUtils.normalizeSecretKey(inputKey)
                                    val isMaster = SecretKeyUtils.isMasterAdminKey(clean)
                                    if (!isMaster && !SecretKeyUtils.isValidSecretKey(clean)) {
                                        errorMessage = "Invalid Key Format. Must be: HG-XXXX-XXXX-XXXX-XXXX"
                                        return@Button
                                    }
                                    onRestoreAccount(clean)
                                },
                                enabled = !isLoading && inputKey.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_submit_restore_account"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ObsidianBg, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CONNECTING TO NODE...", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ObsidianBg))
                                } else {
                                    Icon(Icons.Default.Login, contentDescription = null, tint = ObsidianBg, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LOGIN / RESTORE ACCOUNT", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, color = ObsidianBg))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
