package com.coinlab.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import com.coinlab.app.ui.theme.SparklineGreen
import com.coinlab.app.ui.theme.SparklineRed

@Composable
fun SparklineChart(
    data: List<Double>,
    modifier: Modifier = Modifier,
    positiveColor: Color = SparklineGreen,
    negativeColor: Color = SparklineRed,
    showGradient: Boolean = true
) {
    if (data.isEmpty()) return

    val isPositive = remember(data) {
        data.last() >= data.first()
    }
    val lineColor = if (isPositive) positiveColor else negativeColor

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(400))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val maxVal = data.max()
        val minVal = data.min()
        val range = if (maxVal == minVal) 1.0 else maxVal - minVal
        val stepX = width / (data.size - 1).coerceAtLeast(1)
        val progress = animProgress.value
        val visibleCount = (data.size * progress).toInt().coerceAtLeast(2)

        val linePath = Path()
        val fillPath = Path()

        for (i in 0 until visibleCount) {
            val x = i * stepX
            val y = height - ((data[i] - minVal) / range * height).toFloat()
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Gradient fill
        if (showGradient) {
            val lastX = (visibleCount - 1) * stepX
            fillPath.lineTo(lastX, height)
            fillPath.lineTo(0f, height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.25f),
                        lineColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        // Anti-aliased line
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
