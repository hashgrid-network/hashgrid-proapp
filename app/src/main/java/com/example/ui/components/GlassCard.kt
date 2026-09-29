package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = BorderGlass,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = CardSurface,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.95f),
                        DarkNavySurface.copy(alpha = 0.90f)
                    )
                )
            )
            .border(
                BorderStroke(
                    borderWidth,
                    Brush.linearGradient(
                        colors = listOf(
                            borderColor,
                            borderColor.copy(alpha = 0.15f)
                        )
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun GlowingBorderCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    glowColor: Color = GoldPrimary,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardSurfaceElevated.copy(alpha = 0.95f),
                        DarkNavySurface.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.sweepGradient(
                        listOf(
                            glowColor.copy(alpha = 0.8f),
                            Color.Transparent,
                            glowColor.copy(alpha = 0.4f),
                            Color.Transparent,
                            glowColor.copy(alpha = 0.8f)
                        )
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}
