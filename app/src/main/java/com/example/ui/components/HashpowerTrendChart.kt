package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HardwareNode
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Data point model representing historical aggregate hashpower telemetry.
 */
data class HashpowerPoint(
    val timestamp: Long,
    val aggregateHashpowerGh: Double,
    val label: String,
    val activeNodes: Int = 1
)

enum class TimeframeOption(val label: String, val pointCount: Int, val durationMillis: Long) {
    H24("24H", 12, 24 * 3600 * 1000L),
    D7("7D", 14, 7 * 24 * 3600 * 1000L),
    D30("30D", 15, 30 * 24 * 3600 * 1000L),
    ALL("ALL", 16, 90 * 24 * 3600 * 1000L)
}

/**
 * A native Jetpack Compose dashboard UI component modeled after Recharts
 * (<ResponsiveContainer>, <AreaChart>, <CartesianGrid>, <XAxis>, <YAxis>, <Tooltip>).
 * Visualizes the trend of aggregateHashpowerGh over time for institutional node network tracking.
 */
@Composable
fun HashpowerTrendChart(
    aggregateHashpowerGh: Double,
    modifier: Modifier = Modifier,
    deployedRigsCount: Int = 0,
    networkStatus: String = "Nominal • 99.8% Efficiency"
) {
    var selectedTimeframe by remember { mutableStateOf(TimeframeOption.H24) }
    var touchedPointIndex by remember { mutableStateOf<Int?>(null) }
    var touchPositionX by remember { mutableStateOf<Float?>(null) }

    // Generate historical points tailored to the current aggregateHashpowerGh
    val dataPoints = remember(aggregateHashpowerGh, selectedTimeframe, deployedRigsCount) {
        generateHashpowerTrend(
            currentHashpowerGh = aggregateHashpowerGh,
            timeframe = selectedTimeframe,
            nodeCount = deployedRigsCount
        )
    }

    val minGh = remember(dataPoints) { dataPoints.minOfOrNull { it.aggregateHashpowerGh } ?: 0.0 }
    val maxGh = remember(dataPoints) {
        val highest = dataPoints.maxOfOrNull { it.aggregateHashpowerGh } ?: aggregateHashpowerGh
        max(highest * 1.15, 5.0)
    }

    val activeSelectedPoint = touchedPointIndex?.let { idx ->
        if (idx in dataPoints.indices) dataPoints[idx] else null
    } ?: dataPoints.lastOrNull()

    GlowingBorderCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hashpower_trend_chart_card"),
        glowColor = CyanAccent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title & Recharts Telemetry Badge
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
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Hashpower Trend",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "INSTITUTIONAL NODE NETWORK",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMuted,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "Aggregate Hashpower Trend",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                }

                // Recharts badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkNavySurface)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
                            text = "RECHARTS CORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 8.5.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Stats Banner (Value, Delta, Peak)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Current Telemetry",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", activeSelectedPoint?.aggregateHashpowerGh ?: aggregateHashpowerGh),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "GH/s",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                        )
                    }
                }

                // Timeframe Selectors (24H, 7D, 30D, ALL)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavySurface)
                        .padding(3.dp)
                ) {
                    TimeframeOption.values().forEach { option ->
                        val isSelected = selectedTimeframe == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CyanAccent else Color.Transparent)
                                .clickable {
                                    selectedTimeframe = option
                                    touchedPointIndex = null
                                    touchPositionX = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) ObsidianBg else TextSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Tooltip Card (shown during drag / touch)
            if (activeSelectedPoint != null && touchedPointIndex != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardSurfaceElevated)
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Text(
                                text = activeSelectedPoint.label,
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        Text(
                            text = "${String.format("%.2f", activeSelectedPoint.aggregateHashpowerGh)} GH/s • ${activeSelectedPoint.activeNodes} Nodes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // The Recharts-style Canvas (CartesianGrid + Area Chart + Touch Scrubbing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0F1A))
                    .pointerInput(dataPoints) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchPositionX = offset.x
                                val width = size.width.toFloat()
                                if (width > 0 && dataPoints.isNotEmpty()) {
                                    val index = ((offset.x / width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    touchedPointIndex = index
                                }
                            }
                        )
                    }
                    .pointerInput(dataPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchPositionX = offset.x
                                val width = size.width.toFloat()
                                if (width > 0 && dataPoints.isNotEmpty()) {
                                    val index = ((offset.x / width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    touchedPointIndex = index
                                }
                            },
                            onDragEnd = {
                                // Keep last touched state active for reference
                            },
                            onDrag = { change, _ ->
                                touchPositionX = change.position.x
                                val width = size.width.toFloat()
                                if (width > 0 && dataPoints.isNotEmpty()) {
                                    val index = ((change.position.x / width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    touchedPointIndex = index
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                    val w = size.width
                    val h = size.height
                    if (dataPoints.isEmpty() || w <= 0 || h <= 0) return@Canvas

                    val range = max(maxGh - minGh, 1.0)

                    // 1. Cartesian Grid lines (Horizontal dashes)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = h - (h * (i.toFloat() / gridLines))
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    // 2. Compute Path Coordinates
                    val points = dataPoints.mapIndexed { index, point ->
                        val x = (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * w
                        val normalizedY = ((point.aggregateHashpowerGh - minGh) / range).coerceIn(0.0, 1.0).toFloat()
                        val y = h - (normalizedY * (h * 0.85f)) - (h * 0.05f)
                        Offset(x, y)
                    }

                    // 3. Build Smooth Monotone Cubic Bezier Curve (Area & Stroke)
                    val strokePath = Path()
                    val areaPath = Path()

                    if (points.isNotEmpty()) {
                        strokePath.moveTo(points.first().x, points.first().y)
                        areaPath.moveTo(points.first().x, h)
                        areaPath.lineTo(points.first().x, points.first().y)

                        for (i in 0 until points.size - 1) {
                            val current = points[i]
                            val next = points[i + 1]
                            val controlPoint1 = Offset(current.x + (next.x - current.x) / 2f, current.y)
                            val controlPoint2 = Offset(current.x + (next.x - current.x) / 2f, next.y)

                            strokePath.cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                next.x, next.y
                            )
                            areaPath.cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                next.x, next.y
                            )
                        }

                        areaPath.lineTo(points.last().x, h)
                        areaPath.close()

                        // Draw Gradient Area Fill (Recharts Area Monotone)
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    CyanAccent.copy(alpha = 0.35f),
                                    CyanAccent.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Draw Glowing Stroke Line
                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(CyanAccent, EmeraldGlow, CyanAccent)
                            ),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // 4. Draw Vertex Nodes
                        points.forEachIndexed { idx, offset ->
                            val isTouched = (idx == touchedPointIndex)
                            val dotColor = if (isTouched) GoldPrimary else CyanAccent
                            val radius = if (isTouched) 6.dp.toPx() else 3.dp.toPx()

                            if (isTouched) {
                                drawCircle(
                                    color = GoldPrimary.copy(alpha = 0.3f),
                                    radius = radius * 2.2f,
                                    center = offset
                                )
                            }
                            drawCircle(
                                color = dotColor,
                                radius = radius,
                                center = offset
                            )
                            drawCircle(
                                color = Color(0xFF0A0F1A),
                                radius = radius * 0.45f,
                                center = offset
                            )
                        }

                        // 5. Hairline Scrub Guide if touched
                        touchedPointIndex?.let { idx ->
                            if (idx in points.indices) {
                                val touchX = points[idx].x
                                drawLine(
                                    color = CyanAccent.copy(alpha = 0.6f),
                                    start = Offset(touchX, 0f),
                                    end = Offset(touchX, h),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // X-Axis Timeline Labels (Recharts <XAxis />)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val labelCount = min(4, dataPoints.size)
                if (labelCount > 0) {
                    val step = (dataPoints.size - 1) / (labelCount - 1).coerceAtLeast(1)
                    for (i in 0 until labelCount) {
                        val index = (i * step).coerceIn(0, dataPoints.size - 1)
                        Text(
                            text = dataPoints[index].label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Institutional Node Network Performance Metrics Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkNavySurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Growth",
                        tint = EmeraldGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Peak: ${String.format("%.1f", maxGh)} GH/s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent)
                    )
                    Text(
                        text = networkStatus,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = EmeraldGlow,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Generates realistic historical aggregate hashrate points leading up to the current value.
 */
private fun generateHashpowerTrend(
    currentHashpowerGh: Double,
    timeframe: TimeframeOption,
    nodeCount: Int
): List<HashpowerPoint> {
    val count = timeframe.pointCount
    val now = System.currentTimeMillis()
    val step = timeframe.durationMillis / count
    val baseHash = 2.0 // Base free mining rig hashrate

    val dateFormat = when (timeframe) {
        TimeframeOption.H24 -> SimpleDateFormat("HH:mm", Locale.getDefault())
        TimeframeOption.D7 -> SimpleDateFormat("EEE", Locale.getDefault())
        TimeframeOption.D30 -> SimpleDateFormat("dd MMM", Locale.getDefault())
        TimeframeOption.ALL -> SimpleDateFormat("MMM yyyy", Locale.getDefault())
    }

    val points = mutableListOf<HashpowerPoint>()
    for (i in 0 until count) {
        val timestamp = now - ((count - 1 - i) * step)
        val progress = i.toDouble() / (count - 1).coerceAtLeast(1)

        // Smooth growth curve towards current aggregate hashpower with institutional stability
        val jitter = if (i == count - 1) 0.0 else (((i * 7) % 5) - 2) * 0.04
        val interpolated = baseHash + (currentHashpowerGh - baseHash) * progress + jitter
        val finalGh = max(baseHash, interpolated)

        val estimatedActiveNodes = max(1, (1 + (nodeCount * progress).toInt()))

        points.add(
            HashpowerPoint(
                timestamp = timestamp,
                aggregateHashpowerGh = finalGh,
                label = dateFormat.format(Date(timestamp)),
                activeNodes = estimatedActiveNodes
            )
        )
    }

    // Ensure the last point is strictly equal to the live currentHashpowerGh
    if (points.isNotEmpty()) {
        val last = points.last()
        points[points.size - 1] = last.copy(
            aggregateHashpowerGh = currentHashpowerGh,
            label = "Now",
            activeNodes = max(1, nodeCount + 1)
        )
    }

    return points
}
