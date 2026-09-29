package com.example.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LuckyWheelConfig
import com.example.data.model.SpinHistoryRecord
import com.example.data.model.SpinRewardType
import com.example.data.model.SpinSector
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun LuckyWheelDialog(
    isSpinReady: Boolean,
    cooldownSeconds: Long,
    spinHistory: List<SpinHistoryRecord>,
    onSpinWin: (SpinSector) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSpinning by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var wonSector by remember { mutableStateOf<SpinSector?>(null) }
    var showWinModal by remember { mutableStateOf(false) }

    val rotationAnim = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = { if (!isSpinning) onDismiss() },
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
                shape = RoundedCornerShape(24.dp),
                borderColor = GoldPrimary,
                borderWidth = 1.5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Lucky Wheel",
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "DAILY LUCKY WHEEL",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Text(
                                    text = "1 Free Spin Every 24 Hours",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextGold
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (!isSpinning) onDismiss() },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DarkNavySurface)
                                .testTag("close_lucky_wheel")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Wheel container with Pointer
                    Box(
                        modifier = Modifier
                            .size(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Canvas Wheel
                        Canvas(
                            modifier = Modifier
                                .size(250.dp)
                                .rotate(rotationAnim.value)
                        ) {
                            val sectors = LuckyWheelConfig.sectors
                            val sweepAngle = 360f / sectors.size
                            val radius = size.minDimension / 2f
                            val center = Offset(size.width / 2f, size.height / 2f)

                            sectors.forEachIndexed { index, sector ->
                                val startAngle = index * sweepAngle
                                val sectorColor = Color(sector.hexColor)

                                drawArc(
                                    color = sectorColor.copy(alpha = 0.85f),
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    size = Size(radius * 2f, radius * 2f),
                                    topLeft = Offset(center.x - radius, center.y - radius)
                                )

                                // Draw sector borders
                                val rad = Math.toRadians((startAngle).toDouble())
                                val lineEnd = Offset(
                                    x = center.x + (radius * cos(rad)).toFloat(),
                                    y = center.y + (radius * sin(rad)).toFloat()
                                )
                                drawLine(
                                    color = Color(0xFF0F172A),
                                    start = center,
                                    end = lineEnd,
                                    strokeWidth = 2f
                                )

                                // Text inside sector
                                val textAngle = startAngle + sweepAngle / 2f
                                val textRad = Math.toRadians(textAngle.toDouble())
                                val textRadius = radius * 0.65f
                                val textX = center.x + (textRadius * cos(textRad)).toFloat()
                                val textY = center.y + (textRadius * sin(textRad)).toFloat()

                                drawIntoCanvas { canvas ->
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = 28f
                                        isFakeBoldText = true
                                        textAlign = Paint.Align.CENTER
                                        isAntiAlias = true
                                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                                    }
                                    canvas.nativeCanvas.save()
                                    canvas.nativeCanvas.rotate(textAngle + 90f, textX, textY)
                                    canvas.nativeCanvas.drawText(sector.title, textX, textY, paint)
                                    canvas.nativeCanvas.restore()
                                }
                            }
                        }

                        // Outer golden metallic rim
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .clip(CircleShape)
                                .border(4.dp, GoldPrimary, CircleShape)
                        )

                        // Top Pointer Needle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-6).dp)
                                .size(24.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val path = Path().apply {
                                    moveTo(size.width / 2f, size.height)
                                    lineTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    close()
                                }
                                drawPath(path, color = GoldLight)
                            }
                        }

                        // Center Spin Core Button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GoldLight, GoldDark, ObsidianBg)
                                    )
                                )
                                .border(2.dp, Color.White, CircleShape)
                                .clickable(enabled = isSpinReady && !isSpinning) {
                                    if (isSpinReady && !isSpinning) {
                                        isSpinning = true
                                        coroutineScope.launch {
                                            // Pick weighted winning sector
                                            val sectors = LuckyWheelConfig.sectors
                                            val totalWeight = sectors.sumOf { it.weight }
                                            var randomWeight = Random.nextInt(totalWeight)
                                            var chosenSector = sectors.first()
                                            for (s in sectors) {
                                                if (randomWeight < s.weight) {
                                                    chosenSector = s
                                                    break
                                                }
                                                randomWeight -= s.weight
                                            }
                                            wonSector = chosenSector

                                            // Target rotation: top needle points at 270 deg
                                            val sweepAngle = 360f / sectors.size
                                            val sectorCenterAngle = (chosenSector.id * sweepAngle) + (sweepAngle / 2f)
                                            // To place sector at 270 (top), we need (270 - sectorCenterAngle) mod 360
                                            val landingOffset = (270f - sectorCenterAngle + 360f) % 360f
                                            val totalRotation = (360f * 6) + landingOffset

                                            rotationAnim.snapTo(0f)
                                            rotationAnim.animateTo(
                                                targetValue = totalRotation,
                                                animationSpec = tween(
                                                    durationMillis = 4200,
                                                    easing = CubicBezierEasing(0.12f, 0.8f, 0.32f, 1.0f)
                                                )
                                            )
                                            isSpinning = false
                                            showWinModal = true
                                            onSpinWin(chosenSector)
                                        }
                                    }
                                }
                                .testTag("spin_center_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSpinning) "..." else "SPIN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ObsidianBg
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Status / Timer Bar
                    if (isSpinReady) {
                        Button(
                            onClick = {
                                if (!isSpinning) {
                                    isSpinning = true
                                    coroutineScope.launch {
                                        val sectors = LuckyWheelConfig.sectors
                                        val totalWeight = sectors.sumOf { it.weight }
                                        var randomWeight = Random.nextInt(totalWeight)
                                        var chosenSector = sectors.first()
                                        for (s in sectors) {
                                            if (randomWeight < s.weight) {
                                                chosenSector = s
                                                break
                                            }
                                            randomWeight -= s.weight
                                        }
                                        wonSector = chosenSector

                                        val sweepAngle = 360f / sectors.size
                                        val sectorCenterAngle = (chosenSector.id * sweepAngle) + (sweepAngle / 2f)
                                        val landingOffset = (270f - sectorCenterAngle + 360f) % 360f
                                        val totalRotation = (360f * 6) + landingOffset

                                        rotationAnim.snapTo(0f)
                                        rotationAnim.animateTo(
                                            targetValue = totalRotation,
                                            animationSpec = tween(
                                                durationMillis = 4200,
                                                easing = CubicBezierEasing(0.12f, 0.8f, 0.32f, 1.0f)
                                            )
                                        )
                                        isSpinning = false
                                        showWinModal = true
                                        onSpinWin(chosenSector)
                                    }
                                }
                            },
                            enabled = !isSpinning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("free_spin_ready_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isSpinning) "SPINNING MATRIX..." else "★ FREE SPIN READY - TAP TO PLAY",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianBg
                                )
                            )
                        }
                    } else {
                        val hours = cooldownSeconds / 3600
                        val mins = (cooldownSeconds % 3600) / 60
                        val secs = cooldownSeconds % 60
                        val timeStr = String.format("%02d:%02d:%02d", hours, mins, secs)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavySurface)
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary)
                                )
                                Text(
                                    text = "Next Free Spin in $timeStr",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Recent History Stream
                    if (spinHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Your Recent Spin Rewards",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkNavySurface)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            spinHistory.take(3).forEach { record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = record.rewardTitle,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                    )
                                    Text(
                                        text = record.rewardSubtitle,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextSecondary,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Winner celebration popup
            if (showWinModal && wonSector != null) {
                val sector = wonSector!!
                Dialog(onDismissRequest = { showWinModal = false }) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(20.dp),
                        borderColor = EmeraldAccent,
                        borderWidth = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = "Win",
                                tint = EmeraldGlow,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "CONGRATULATIONS!",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldGlow
                                )
                            )
                            Text(
                                text = "You Won ${sector.title}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sector.subtitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    showWinModal = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("claim_spin_reward_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "CLAIM TO BALANCE",
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
    }
}
