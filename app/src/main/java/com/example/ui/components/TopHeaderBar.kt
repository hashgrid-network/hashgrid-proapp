package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

@Composable
fun TopHeaderBar(
    nodeId: String,
    isColdStorageSynced: Boolean,
    tickers: List<CryptoTickerPrice>,
    modifier: Modifier = Modifier
) {
    var currentTickerIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(tickers) {
        while (true) {
            delay(3500)
            if (tickers.isNotEmpty()) {
                currentTickerIndex = (currentTickerIndex + 1) % tickers.size
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBg.copy(alpha = 0.95f))
            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        // Ticker Bar (Institutional Live Feeds)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkNavySurface)
                .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(EmeraldAccent)
                )
                Text(
                    text = "LIVE MARKET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                )
            }

            if (tickers.isNotEmpty()) {
                val currentTicker = tickers[currentTickerIndex.coerceIn(0, tickers.size - 1)]
                AnimatedContent(
                    targetState = currentTicker,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TickerAnimation"
                ) { ticker ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = ticker.symbol,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (ticker.symbol.startsWith("GRID")) {
                                "$${String.format("%.4f", ticker.price)}"
                            } else {
                                "$${String.format("%,.2f", ticker.price)}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GoldLight
                            )
                        )
                        val isPositive = ticker.change24h >= 0
                        Text(
                            text = "${if (isPositive) "+" else ""}${String.format("%.2f", ticker.change24h)}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPositive) EmeraldAccent else CrimsonError,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Node ID & Cold Storage Sync Status
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
                        .size(28.dp)
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
                    Text(
                        text = nodeId,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        modifier = Modifier.testTag("node_id_badge")
                    )
                }
            }

            // Cold storage badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isColdStorageSynced) EmeraldDark.copy(alpha = 0.4f) else DarkNavySurface)
                    .border(
                        1.dp,
                        if (isColdStorageSynced) EmeraldAccent.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("cold_storage_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isColdStorageSynced) EmeraldAccent else Color.Gray)
                    )
                    Text(
                        text = if (isColdStorageSynced) "Cold-Storage Synced" else "Syncing...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isColdStorageSynced) TextEmerald else TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
