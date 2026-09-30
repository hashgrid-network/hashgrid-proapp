package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CryptoTickerPrice
import com.example.ui.theme.*

@Composable
fun TopHeaderBar(
    nodeId: String,
    isColdStorageSynced: Boolean,
    preLaunchPriceUsd: Double = 0.01,
    isCloudSynced: Boolean = false,
    connectionErrorMsg: String? = null,
    tickers: List<CryptoTickerPrice> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: High-End Sacred Geometry Logo & "HashGrid Pro" Title
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_sacred_triangle),
                contentDescription = "The Quantum Apex Matrix Logo",
                tint = Color.Unspecified, // Keeps gold/cyan/emerald custom colors
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_header_sacred_triangle")
            )
            Column {
                Text(
                    text = "HashGrid Pro",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextGold,
                        letterSpacing = 1.sp,
                        fontSize = 15.sp
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        modifier = Modifier.testTag("node_id_badge")
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isColdStorageSynced) EmeraldAccent else Color.Gray)
                            .testTag("cold_storage_badge")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center: Real-Time Heartbeat Status Indicator Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isCloudSynced) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFB71C1C).copy(alpha = 0.2f))
                .border(
                    1.dp,
                    if (isCloudSynced) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color(0xFFF44336).copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("cloud_heartbeat_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isCloudSynced) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Text(
                    text = if (isCloudSynced) "CLOUD LIVE" else (connectionErrorMsg ?: "DISCONNECTED"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isCloudSynced) Color(0xFF81C784) else Color(0xFFE57373),
                        fontSize = 8.5.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Single-line compact pill badge "PRE-LAUNCH: $0.05 / GRID"
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavySurface)
                .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .testTag("pre_launch_badge")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                )
                Text(
                    text = "PRE-LAUNCH: $${String.format("%.2f", preLaunchPriceUsd)} / GRID",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanAccent,
                        fontSize = 9.5.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
