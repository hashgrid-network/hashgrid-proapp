package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppNavTab
import com.example.ui.theme.*

@Composable
fun BottomNavBar(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit,
    isMiningActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "MinerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Base bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            DarkNavySurface.copy(alpha = 0.96f),
                            ObsidianBg.copy(alpha = 0.99f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            BorderGlass,
                            BorderSubtle,
                            BorderGlass
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                NavTabItem(
                    title = "Home",
                    iconFilled = Icons.Filled.Home,
                    iconOutlined = Icons.Outlined.Home,
                    isSelected = currentTab == AppNavTab.HOME,
                    testTag = "nav_home",
                    onClick = { onTabSelected(AppNavTab.HOME) }
                )

                // Rigs Store
                NavTabItem(
                    title = "Rigs Store",
                    iconFilled = Icons.Filled.Memory,
                    iconOutlined = Icons.Outlined.Memory,
                    isSelected = currentTab == AppNavTab.RIGS_STORE,
                    testTag = "nav_rigs_store",
                    onClick = { onTabSelected(AppNavTab.RIGS_STORE) }
                )

                // Space in the center for elevated miner button
                Spacer(modifier = Modifier.width(64.dp))

                // Network
                NavTabItem(
                    title = "Network",
                    iconFilled = Icons.Filled.Hub,
                    iconOutlined = Icons.Outlined.Hub,
                    isSelected = currentTab == AppNavTab.NETWORK,
                    testTag = "nav_network",
                    onClick = { onTabSelected(AppNavTab.NETWORK) }
                )

                // Profile
                NavTabItem(
                    title = "Profile",
                    iconFilled = Icons.Filled.AccountBalanceWallet,
                    iconOutlined = Icons.Outlined.AccountBalanceWallet,
                    isSelected = currentTab == AppNavTab.PROFILE,
                    testTag = "nav_profile",
                    onClick = { onTabSelected(AppNavTab.PROFILE) }
                )
            }
        }

        // Center Elevated Glowing "24H ACTIVE" Cloud Miner Button
        Box(
            modifier = Modifier
                .offset(y = (-20).dp)
                .scale(if (isMiningActive) pulseScale else 1.0f)
                .size(68.dp)
                .shadow(16.dp, CircleShape, spotColor = if (isMiningActive) EmeraldAccent else GoldPrimary)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isMiningActive) {
                            listOf(EmeraldGlow, EmeraldDark, ObsidianBg)
                        } else {
                            listOf(GoldLight, GoldDark, ObsidianBg)
                        }
                    )
                )
                .border(
                    width = 2.5.dp,
                    brush = Brush.sweepGradient(
                        if (isMiningActive) {
                            listOf(EmeraldAccent, GoldPrimary, EmeraldAccent)
                        } else {
                            listOf(GoldPrimary, EmeraldAccent, GoldPrimary)
                        }
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    try {
                        onTabSelected(AppNavTab.HOME)
                    } catch (e: Throwable) {
                        android.util.Log.e("NAV_SAFE", "Navigation to home handled", e)
                    }
                }
                .testTag("nav_cloud_miner_center"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Cloud Miner",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = if (isMiningActive) "24H ACTIVE" else "MINER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    title: String,
    iconFilled: ImageVector,
    iconOutlined: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) iconFilled else iconOutlined,
            contentDescription = title,
            tint = if (isSelected) GoldPrimary else TextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) TextGold else TextMuted
            )
        )
    }
}
