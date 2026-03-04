package com.coinlab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinlab.app.ui.theme.GlassBorder
import com.coinlab.app.ui.theme.GlassSurface
import com.coinlab.app.ui.theme.NeonGold

// ═══════════════════════════════════════════════════════════════════════
//  v8.9.2 — GLASSMORPHISM CARD COMPONENT
//  Semi-transparent surface with gold border glow and neon shadow
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    glowColor: Color = NeonGold,
    glowElevation: Dp = 12.dp,
    borderAlpha: Float = 0.3f,
    surfaceAlpha: Float = 0.08f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = glowElevation,
                shape = shape,
                ambientColor = glowColor.copy(alpha = 0.3f),
                spotColor = glowColor.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = surfaceAlpha),
                        Color.Black.copy(alpha = 0.4f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = borderAlpha),
                        glowColor.copy(alpha = borderAlpha * 0.3f),
                        Color.Transparent
                    )
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

/**
 * A smaller glass card variant for inline elements.
 */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonGold,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.12f),
                        glowColor.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = glowColor.copy(alpha = 0.2f),
                shape = shape
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        content = content
    )
}

/**
 * A gradient header bar with neon glow.
 */
@Composable
fun GlassHeader(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        NeonGold.copy(alpha = 0.3f),
        Color.Transparent
    ),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(colors = gradientColors)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}
