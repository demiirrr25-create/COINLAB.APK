package com.coinlab.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinlab.app.domain.model.OhlcData
import com.coinlab.app.ui.theme.SparklineGreen
import com.coinlab.app.ui.theme.CoinLabRed
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CandlestickChart(
    data: List<OhlcData>,
    modifier: Modifier = Modifier,
    bullColor: Color = SparklineGreen,
    bearColor: Color = CoinLabRed,
    currency: String = "USD"
) {
    if (data.size < 2) {
        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Yetersiz mum verisi", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(600))
    }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 8
    }}
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }
    val tooltipBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Column(modifier = modifier) {
        // Tooltip when candle selected
        if (selectedIndex in data.indices) {
            val candle = data[selectedIndex]
            val color = if (candle.close >= candle.open) bullColor else bearColor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tooltipBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("O: ${numberFormat.format(candle.open)}", fontSize = 10.sp, color = color)
                        Text("C: ${numberFormat.format(candle.close)}", fontSize = 10.sp, color = color)
                    }
                    Column {
                        Text("H: ${numberFormat.format(candle.high)}", fontSize = 10.sp, color = bullColor)
                        Text("L: ${numberFormat.format(candle.low)}", fontSize = 10.sp, color = bearColor)
                    }
                    Text(dateFormat.format(Date(candle.time)), fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selectedIndex >= 0) 190.dp else 220.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val cw = size.width.toFloat() / data.size
                        val idx = (offset.x / cw).toInt().coerceIn(0, data.size - 1)
                        selectedIndex = if (selectedIndex == idx) -1 else idx
                    }
                }
        ) {
            val progress = animationProgress.value
            val visibleCount = (data.size * progress).toInt().coerceAtLeast(1)
            val visible = data.takeLast(visibleCount)
            if (visible.isEmpty()) return@Canvas

            val allHigh = visible.maxOf { it.high }
            val allLow = visible.minOf { it.low }
            val range = (allHigh - allLow).coerceAtLeast(0.0001)

            val chartHeight = size.height * 0.82f
            val chartTop = size.height * 0.02f
            val candleTotalWidth = size.width / visible.size
            val candleBodyWidth = (candleTotalWidth * 0.6f).coerceIn(2f, 30f)
            val wickWidth = (candleTotalWidth * 0.12f).coerceIn(1f, 3f)

            // Grid
            for (i in 0..3) {
                val y = chartTop + chartHeight * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }

            // Volume bottom area
            val volTop = chartTop + chartHeight
            val volHeight = size.height - volTop
            val maxVol = visible.maxOfOrNull { it.volume }?.coerceAtLeast(1.0) ?: 1.0

            visible.forEachIndexed { index, candle ->
                val x = index * candleTotalWidth + candleTotalWidth / 2f
                val isBull = candle.close >= candle.open
                val clr = if (isBull) bullColor else bearColor

                // Wick
                val highY = chartTop + ((allHigh - candle.high) / range * chartHeight).toFloat()
                val lowY = chartTop + ((allHigh - candle.low) / range * chartHeight).toFloat()
                drawLine(clr, Offset(x, highY), Offset(x, lowY), strokeWidth = wickWidth)

                // Body
                val openY = chartTop + ((allHigh - candle.open) / range * chartHeight).toFloat()
                val closeY = chartTop + ((allHigh - candle.close) / range * chartHeight).toFloat()
                val bodyTop2 = minOf(openY, closeY)
                val bodyH = (maxOf(openY, closeY) - bodyTop2).coerceAtLeast(1f)
                drawRect(clr, Offset(x - candleBodyWidth / 2f, bodyTop2), Size(candleBodyWidth, bodyH))

                // Volume
                if (candle.volume > 0) {
                    val vH = ((candle.volume / maxVol) * volHeight * 0.8).toFloat()
                    drawRect(clr.copy(alpha = 0.25f), Offset(x - candleBodyWidth / 2f, size.height - vH), Size(candleBodyWidth, vH))
                }

                // Selection crosshair
                val globalIdx = index + (data.size - visibleCount)
                if (globalIdx == selectedIndex) {
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                }
            }
        }
    }
}

// Overlay-compatible overload for TechnicalAnalysisScreen
@Composable
fun CandlestickChart(
    data: List<OhlcData>,
    modifier: Modifier = Modifier,
    bullColor: Color = SparklineGreen,
    bearColor: Color = CoinLabRed,
    overlayLines: List<OverlayLine> = emptyList()
) {
    if (data.isEmpty()) return

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val padding = 8f

        val maxPrice = data.maxOf { it.high }
        val minPrice = data.minOf { it.low }
        val priceRange = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice

        val candleWidth = ((width - padding * 2) / data.size).coerceIn(2f, 40f)
        val spacing = candleWidth * 0.2f

        data.forEachIndexed { index, ohlc ->
            val x = padding + index * (candleWidth + spacing)
            if (x > width) return@forEachIndexed

            val isBull = ohlc.close >= ohlc.open
            val color = if (isBull) bullColor else bearColor

            val bodyTop = height - ((maxOf(ohlc.open, ohlc.close) - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding
            val bodyBottom = height - ((minOf(ohlc.open, ohlc.close) - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding
            val wickTop = height - ((ohlc.high - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding
            val wickBottom = height - ((ohlc.low - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding

            drawLine(color, Offset(x + candleWidth / 2, wickTop), Offset(x + candleWidth / 2, wickBottom), strokeWidth = 1.5f)

            val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1f)
            drawRect(color, Offset(x, bodyTop), Size(candleWidth, bodyHeight))
        }

        overlayLines.forEach { overlay ->
            drawOverlayLine(this, overlay, data.size, minPrice, priceRange, width, height, padding, candleWidth, spacing)
        }
    }
}

data class OverlayLine(
    val values: List<Double?>,
    val color: Color,
    val strokeWidth: Float = 2f,
    val label: String = ""
)

private fun drawOverlayLine(
    drawScope: DrawScope,
    overlay: OverlayLine,
    dataSize: Int,
    minPrice: Double,
    priceRange: Double,
    width: Float,
    height: Float,
    padding: Float,
    candleWidth: Float,
    spacing: Float
) {
    val values = overlay.values.take(dataSize)
    var prevPoint: Offset? = null

    values.forEachIndexed { index, value ->
        if (value == null) { prevPoint = null; return@forEachIndexed }
        val x = padding + index * (candleWidth + spacing) + candleWidth / 2
        val y = height - ((value - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding
        val currentPoint = Offset(x, y)
        prevPoint?.let { prev ->
            drawScope.drawLine(overlay.color, prev, currentPoint, overlay.strokeWidth)
        }
        prevPoint = currentPoint
    }
}
