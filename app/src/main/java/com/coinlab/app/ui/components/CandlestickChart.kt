package com.coinlab.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.coinlab.app.domain.model.OhlcData
import com.coinlab.app.ui.theme.CoinLabGreen
import com.coinlab.app.ui.theme.CoinLabRed

@Composable
fun CandlestickChart(
    data: List<OhlcData>,
    modifier: Modifier = Modifier,
    bullColor: Color = CoinLabGreen,
    bearColor: Color = CoinLabRed,
    overlayLines: List<OverlayLine> = emptyList()
) {
    if (data.isEmpty()) return

    var scale by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val padding = 8f

        val allHighs = data.map { it.high }
        val allLows = data.map { it.low }
        val maxPrice = allHighs.max()
        val minPrice = allLows.min()
        val priceRange = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice

        val candleWidth = ((width - padding * 2) / data.size * scale).coerceIn(2f, 40f)
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

            // Draw wick
            drawLine(
                color = color,
                start = Offset(x + candleWidth / 2, wickTop),
                end = Offset(x + candleWidth / 2, wickBottom),
                strokeWidth = 1.5f
            )

            // Draw body
            val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1f)
            drawRect(
                color = color,
                topLeft = Offset(x, bodyTop),
                size = Size(candleWidth, bodyHeight)
            )
        }

        // Draw overlay lines (SMA, EMA, Bollinger Bands etc.)
        overlayLines.forEach { overlay ->
            drawOverlayLine(
                this, overlay, data.size, minPrice, priceRange,
                width, height, padding, candleWidth, spacing
            )
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
        if (value == null) {
            prevPoint = null
            return@forEachIndexed
        }
        val x = padding + index * (candleWidth + spacing) + candleWidth / 2
        val y = height - ((value - minPrice) / priceRange * (height - padding * 2)).toFloat() - padding
        val currentPoint = Offset(x, y)

        prevPoint?.let { prev ->
            drawScope.drawLine(
                color = overlay.color,
                start = prev,
                end = currentPoint,
                strokeWidth = overlay.strokeWidth
            )
        }
        prevPoint = currentPoint
    }
}
