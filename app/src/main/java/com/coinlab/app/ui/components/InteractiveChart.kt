package com.coinlab.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinlab.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun InteractiveChart(
    prices: List<Double>,
    volumes: List<Double> = emptyList(),
    modifier: Modifier = Modifier,
    lineColor: Color = ChartLine,
    gradientStartColor: Color = ChartGradientStart,
    gradientEndColor: Color = ChartGradientEnd,
    showVolume: Boolean = false,
    currency: String = "USD",
    timeLabels: List<String> = emptyList()
) {
    if (prices.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Yetersiz grafik verisi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(prices) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showTooltip by remember { mutableStateOf(false) }

    val isPositive = remember(prices) { prices.last() >= prices.first() }
    val actualLineColor = if (isPositive) lineColor else SparklineRed

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(prices) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchPosition = offset
                            showTooltip = true
                            val idx = ((offset.x / size.width) * (prices.size - 1))
                                .toInt()
                                .coerceIn(0, prices.lastIndex)
                            selectedIndex = idx
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchPosition = change.position
                            val idx = ((change.position.x / size.width) * (prices.size - 1))
                                .toInt()
                                .coerceIn(0, prices.lastIndex)
                            selectedIndex = idx
                        },
                        onDragEnd = {
                            showTooltip = false
                            selectedIndex = -1
                        },
                        onDragCancel = {
                            showTooltip = false
                            selectedIndex = -1
                        }
                    )
                }
                .pointerInput(prices) {
                    detectTapGestures { offset ->
                        val idx = ((offset.x / size.width) * (prices.size - 1))
                            .toInt()
                            .coerceIn(0, prices.lastIndex)
                        selectedIndex = idx
                        touchPosition = offset
                        showTooltip = true
                    }
                }
        ) {
            val chartHeight = if (showVolume && volumes.isNotEmpty()) {
                size.height * 0.75f
            } else {
                size.height
            }
            val chartWidth = size.width
            val padding = 4f

            val maxPrice = prices.max()
            val minPrice = prices.min()
            val priceRange = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice

            val progress = animationProgress.value
            val visibleCount = (prices.size * progress).toInt().coerceAtLeast(2)

            // Build price line path
            val linePath = Path()
            val fillPath = Path()

            for (i in 0 until visibleCount) {
                val x = padding + (i.toFloat() / (prices.size - 1)) * (chartWidth - padding * 2)
                val y = padding + ((maxPrice - prices[i]) / priceRange).toFloat() * (chartHeight - padding * 2)

                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            // Fill gradient
            val lastX = padding + ((visibleCount - 1).toFloat() / (prices.size - 1)) * (chartWidth - padding * 2)
            fillPath.lineTo(lastX, chartHeight)
            fillPath.lineTo(padding, chartHeight)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        actualLineColor.copy(alpha = 0.3f),
                        actualLineColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = chartHeight
                )
            )

            // Draw line
            drawPath(
                path = linePath,
                color = actualLineColor,
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Volume bars
            if (showVolume && volumes.isNotEmpty()) {
                val volumeTop = chartHeight + 8f
                val volumeHeight = size.height - volumeTop
                val maxVolume = volumes.max()
                if (maxVolume > 0) {
                    val barWidth = (chartWidth - padding * 2) / volumes.size
                    volumes.forEachIndexed { i, vol ->
                        val barH = (vol / maxVolume * volumeHeight).toFloat()
                        val barX = padding + i * barWidth
                        val barColor = if (i > 0 && prices.getOrNull(i) ?: 0.0 >= (prices.getOrNull(i - 1) ?: 0.0)) {
                            actualLineColor.copy(alpha = 0.4f)
                        } else {
                            SparklineRed.copy(alpha = 0.4f)
                        }
                        drawRect(
                            color = barColor,
                            topLeft = Offset(barX, volumeTop + volumeHeight - barH),
                            size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barH)
                        )
                    }
                }
            }

            // Crosshair
            if (showTooltip && selectedIndex in prices.indices) {
                val crossX = padding + (selectedIndex.toFloat() / (prices.size - 1)) * (chartWidth - padding * 2)
                val crossY = padding + ((maxPrice - prices[selectedIndex]) / priceRange).toFloat() * (chartHeight - padding * 2)

                // Vertical line
                drawLine(
                    color = actualLineColor.copy(alpha = 0.5f),
                    start = Offset(crossX, 0f),
                    end = Offset(crossX, chartHeight),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                )

                // Horizontal line
                drawLine(
                    color = actualLineColor.copy(alpha = 0.3f),
                    start = Offset(0f, crossY),
                    end = Offset(chartWidth, crossY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                )

                // Point circle
                drawCircle(
                    color = actualLineColor,
                    radius = 6f,
                    center = Offset(crossX, crossY)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 3f,
                    center = Offset(crossX, crossY)
                )
            }
        }

        // Tooltip
        if (showTooltip && selectedIndex in prices.indices) {
            val priceValue = prices[selectedIndex]
            val formattedPrice = formatChartPrice(priceValue, currency)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedPrice,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = actualLineColor
                    )
                    if (timeLabels.isNotEmpty() && selectedIndex < timeLabels.size) {
                        Text(
                            text = timeLabels[selectedIndex],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatChartPrice(price: Double, currency: String): String {
    val fmt = NumberFormat.getCurrencyInstance(
        Locale.US
    ).apply {
        maximumFractionDigits = when {
            price < 1 -> 6
            price < 100 -> 4
            else -> 2
        }
    }
    return fmt.format(price)
}
