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
        // Left: Institutional Node ID & Cold Storage Sync Status Badge
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.15f))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Node Shield",
                    tint = GoldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = "INSTITUTIONAL NODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        modifier = Modifier.testTag("node_id_badge")
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.testTag("cold_storage_badge")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isColdStorageSynced) EmeraldAccent else Color.Gray)
                        )
                        Text(
                            text = if (isColdStorageSynced) "Cold-Storage Synced" else "Syncing...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isColdStorageSynced) TextEmerald else TextMuted,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
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
