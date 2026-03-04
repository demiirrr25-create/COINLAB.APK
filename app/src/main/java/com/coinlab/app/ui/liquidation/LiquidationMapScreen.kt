package com.coinlab.app.ui.liquidation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.data.remote.firebase.ExchangeData
import com.coinlab.app.data.remote.firebase.HeatmapBucket
import com.coinlab.app.data.remote.firebase.LiqSide
import com.coinlab.app.data.remote.firebase.LiquidationEvent
import com.coinlab.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════
// Heatmap Color Palette
// ═══════════════════════════════════════════════════════════════════════

private val HeatmapLongLow = Color(0xFF1B3A1B)       // Dark green
private val HeatmapLongMid = Color(0xFF00C853)        // Bright green
private val HeatmapLongHigh = Color(0xFFB2FF59)       // Yellow-green

private val HeatmapShortLow = Color(0xFF3A1B1B)       // Dark red
private val HeatmapShortMid = Color(0xFFFF1744)       // Bright red
private val HeatmapShortHigh = Color(0xFFFF8A80)       // Light red

private val HeatmapNeutral = Color(0xFF1A1A2E)        // Deep dark blue
private val HeatmapGrid = Color(0xFF2A2A3E)            // Grid line color
private val HeatmapText = Color(0xFF8888AA)            // Axis text
private val HeatmapMarkLine = Color(0xFFFFC107)        // Mark price line (gold)

// ═══════════════════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidationMapScreen(
    onBackClick: () -> Unit,
    viewModel: LiquidationMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.liquidation_map),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Yenile",
                            tint = CoinLabGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.aggregatedData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CoinLabGreen)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Borsa verileri yükleniyor...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Coin selector
                item { CoinSelector(uiState.selectedCoin, viewModel.availableCoins, viewModel::selectCoin) }

                // Time filter chips
                item { TimeFilterRow(uiState.timeFilter, viewModel.timeFilters, viewModel::setTimeFilter) }

                // Main Heatmap
                item {
                    HeatmapCard(
                        buckets = uiState.aggregatedData?.heatmapBuckets ?: emptyList(),
                        markPrice = uiState.aggregatedData?.markPrice ?: 0.0,
                        selectedIndex = uiState.selectedBucketIndex,
                        threshold = uiState.threshold,
                        onBucketSelected = viewModel::selectBucket,
                        baseCoin = uiState.selectedCoin
                    )
                }

                // Color scale legend
                item { HeatmapLegend() }

                // Selected bucket info
                if (uiState.selectedBucketIndex >= 0) {
                    val buckets = uiState.aggregatedData?.heatmapBuckets ?: emptyList()
                    if (uiState.selectedBucketIndex < buckets.size) {
                        item {
                            BucketDetailCard(buckets[uiState.selectedBucketIndex], uiState.selectedCoin)
                        }
                    }
                }

                // Stats overview cards
                item {
                    StatsOverviewRow(uiState)
                }

                // Long/Short ratio bar
                item {
                    LongShortCard(
                        longRatio = uiState.aggregatedData?.longRatio ?: 0.5,
                        shortRatio = uiState.aggregatedData?.shortRatio ?: 0.5
                    )
                }

                // Exchange breakdown
                item {
                    ExchangeBreakdownCard(
                        exchanges = uiState.aggregatedData?.exchangeBreakdowns ?: emptyList()
                    )
                }

                // Recent liquidations
                val recentLiqs = uiState.aggregatedData?.recentLiquidations?.take(20) ?: emptyList()
                if (recentLiqs.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.liquidation_recent),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(recentLiqs) { liq ->
                        LiquidationEventRow(liq, uiState.selectedCoin)
                    }
                }

                // Error display
                uiState.error?.let { error ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2A1500)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = CoinLabGold)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    error,
                                    color = CoinLabGold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Coin Selector
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CoinSelector(
    selectedCoin: String,
    coins: List<String>,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(coins) { coin ->
            val isSelected = coin == selectedCoin
            val bgColor by animateColorAsState(
                if (isSelected) CoinLabGreen else Color(0xFF1A1400),
                animationSpec = tween(200),
                label = "coinBg"
            )
            val textColor = if (isSelected) Color.Black else CoinLabGold

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bgColor,
                modifier = Modifier.clickable { onSelect(coin) }
            ) {
                Text(
                    coin,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Time Filter
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun TimeFilterRow(
    selected: String,
    filters: List<String>,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(filter, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoinLabGreen,
                    selectedLabelColor = Color.Black,
                    containerColor = Color(0xFF1A1400),
                    labelColor = CoinLabGold
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Heatmap Canvas Card
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun HeatmapCard(
    buckets: List<HeatmapBucket>,
    markPrice: Double,
    selectedIndex: Int,
    threshold: Float,
    onBucketSelected: (Int) -> Unit,
    baseCoin: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF0D0A00)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$baseCoin ${stringResource(R.string.liquidation_subtitle)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                if (markPrice > 0) {
                    Text(
                        "$${formatNumber(markPrice)}",
                        color = CoinLabGold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Canvas heatmap
            val density = LocalDensity.current
            val thresholdValue = threshold

            if (buckets.isNotEmpty()) {
                val maxLiqValue = remember(buckets) {
                    buckets.maxOfOrNull { it.totalLiquidationUsd } ?: 1.0
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeatmapNeutral)
                        .pointerInput(buckets) {
                            detectTapGestures { offset ->
                                val bucketHeight = size.height.toFloat() / buckets.size
                                val index = ((size.height - offset.y) / bucketHeight).toInt()
                                    .coerceIn(0, buckets.size - 1)
                                onBucketSelected(index)
                            }
                        }
                ) {
                    drawHeatmap(buckets, markPrice, maxLiqValue, selectedIndex, thresholdValue)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeatmapNeutral),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Heatmap verisi bulunamadı",
                        color = HeatmapText,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeatmap(
    buckets: List<HeatmapBucket>,
    markPrice: Double,
    maxLiqValue: Double,
    selectedIndex: Int,
    threshold: Float
) {
    val leftPadding = 70f
    val rightPadding = 20f
    val topPadding = 10f
    val bottomPadding = 10f

    val chartWidth = size.width - leftPadding - rightPadding
    val chartHeight = size.height - topPadding - bottomPadding
    val bucketHeight = chartHeight / buckets.size
    val halfWidth = chartWidth / 2f

    val thresholdMultiplier = if (maxLiqValue > 0) maxLiqValue * (1f - threshold) else 1.0

    // Draw grid lines
    for (i in buckets.indices step 5) {
        val y = topPadding + chartHeight - (i * bucketHeight) - bucketHeight / 2
        drawLine(
            color = HeatmapGrid,
            start = Offset(leftPadding, y),
            end = Offset(size.width - rightPadding, y),
            strokeWidth = 0.5f
        )
    }

    // Center line
    drawLine(
        color = HeatmapGrid.copy(alpha = 0.5f),
        start = Offset(leftPadding + halfWidth, topPadding),
        end = Offset(leftPadding + halfWidth, topPadding + chartHeight),
        strokeWidth = 1f
    )

    // Draw buckets
    buckets.forEachIndexed { index, bucket ->
        val y = topPadding + chartHeight - ((index + 1) * bucketHeight)

        // Long liquidation bar (right side, green)
        val longNorm = if (thresholdMultiplier > 0)
            (bucket.longLiquidationUsd / thresholdMultiplier).coerceIn(0.0, 1.0).toFloat()
        else 0f

        if (longNorm > 0.01f) {
            val color = lerpColor(HeatmapLongLow, HeatmapLongMid, HeatmapLongHigh, longNorm)
            drawRect(
                color = color,
                topLeft = Offset(leftPadding + halfWidth, y),
                size = Size(halfWidth * longNorm, bucketHeight - 1f)
            )
        }

        // Short liquidation bar (left side, red)
        val shortNorm = if (thresholdMultiplier > 0)
            (bucket.shortLiquidationUsd / thresholdMultiplier).coerceIn(0.0, 1.0).toFloat()
        else 0f

        if (shortNorm > 0.01f) {
            val color = lerpColor(HeatmapShortLow, HeatmapShortMid, HeatmapShortHigh, shortNorm)
            val barWidth = halfWidth * shortNorm
            drawRect(
                color = color,
                topLeft = Offset(leftPadding + halfWidth - barWidth, y),
                size = Size(barWidth, bucketHeight - 1f)
            )
        }

        // Selected highlight
        if (index == selectedIndex) {
            drawRect(
                color = CoinLabGold.copy(alpha = 0.3f),
                topLeft = Offset(leftPadding, y),
                size = Size(chartWidth, bucketHeight),
                style = Stroke(width = 2f)
            )
        }

        // Price labels (every 5th bucket)
        if (index % 5 == 0) {
            drawContext.canvas.nativeCanvas.drawText(
                "$${formatPriceLabel(bucket.priceLevel)}",
                8f,
                y + bucketHeight / 2 + 4f,
                android.graphics.Paint().apply {
                    color = 0xFF8888AA.toInt()
                    textSize = 22f
                    isAntiAlias = true
                }
            )
        }
    }

    // Mark price horizontal line
    if (markPrice > 0 && buckets.isNotEmpty()) {
        val minP = buckets.minOf { it.priceLow }
        val maxP = buckets.maxOf { it.priceHigh }
        if (markPrice in minP..maxP) {
            val fraction = ((markPrice - minP) / (maxP - minP)).toFloat()
            val markY = topPadding + chartHeight - (fraction * chartHeight)

            // Dashed line effect
            val dashLength = 8f
            val gapLength = 4f
            var x = leftPadding
            while (x < size.width - rightPadding) {
                drawLine(
                    color = HeatmapMarkLine,
                    start = Offset(x, markY),
                    end = Offset((x + dashLength).coerceAtMost(size.width - rightPadding), markY),
                    strokeWidth = 2f
                )
                x += dashLength + gapLength
            }

            // Mark price label
            drawContext.canvas.nativeCanvas.drawText(
                "► $${formatPriceLabel(markPrice)}",
                leftPadding + chartWidth - 120f,
                markY - 6f,
                android.graphics.Paint().apply {
                    color = 0xFFFFC107.toInt()
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            )
        }
    }

    // Axis labels
    drawContext.canvas.nativeCanvas.apply {
        val shortLabel = "SHORT"
        val longLabel = "LONG"
        val labelPaint = android.graphics.Paint().apply {
            color = 0xFF8888AA.toInt()
            textSize = 20f
            isAntiAlias = true
        }
        drawText(shortLabel, leftPadding + halfWidth * 0.3f, size.height - 2f, labelPaint)
        drawText(longLabel, leftPadding + halfWidth + halfWidth * 0.3f, size.height - 2f, labelPaint)
    }
}

private fun lerpColor(low: Color, mid: Color, high: Color, t: Float): Color {
    return if (t < 0.5f) {
        val f = t * 2f
        Color(
            red = low.red + (mid.red - low.red) * f,
            green = low.green + (mid.green - low.green) * f,
            blue = low.blue + (mid.blue - low.blue) * f,
            alpha = 0.4f + t * 0.6f
        )
    } else {
        val f = (t - 0.5f) * 2f
        Color(
            red = mid.red + (high.red - mid.red) * f,
            green = mid.green + (high.green - mid.green) * f,
            blue = mid.blue + (high.blue - mid.blue) * f,
            alpha = 0.7f + t * 0.3f
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Heatmap Legend
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Short side
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(HeatmapShortLow, HeatmapShortMid, HeatmapShortHigh)
                        )
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text("Short Liq.", color = HeatmapShortMid, fontSize = 11.sp)
        }

        Text(
            "← Yoğunluk →",
            color = HeatmapText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        // Long side
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Long Liq.", color = HeatmapLongMid, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(HeatmapLongLow, HeatmapLongMid, HeatmapLongHigh)
                        )
                    )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Bucket Detail Card
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun BucketDetailCard(bucket: HeatmapBucket, baseCoin: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1A1400)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Fiyat Seviyesi",
                    color = HeatmapText,
                    fontSize = 12.sp
                )
                Text(
                    "$${formatNumber(bucket.priceLevel)}",
                    color = CoinLabGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Long Likidasyon", color = HeatmapLongMid, fontSize = 11.sp)
                    Text(
                        "$${formatCompact(bucket.longLiquidationUsd)}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Short Likidasyon", color = HeatmapShortMid, fontSize = 11.sp)
                    Text(
                        "$${formatCompact(bucket.shortLiquidationUsd)}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
            if (bucket.isEstimated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ ${stringResource(R.string.liquidation_estimated)}",
                    color = CoinLabGold.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Stats Overview Row
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun StatsOverviewRow(uiState: LiquidationUiState) {
    val data = uiState.aggregatedData ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Open Interest
        StatMiniCard(
            title = stringResource(R.string.liquidation_open_interest),
            value = "$${formatCompact(data.totalOpenInterestUsd)}",
            icon = Icons.Filled.AccountBalance,
            color = CoinLabGold,
            modifier = Modifier.weight(1f)
        )

        // Funding Rate
        val fundingColor = if (data.aggregatedFundingRate >= 0) SparklineGreen else CoinLabRed
        StatMiniCard(
            title = stringResource(R.string.liquidation_funding_rate),
            value = "${if (data.aggregatedFundingRate >= 0) "+" else ""}${
                String.format("%.4f", data.aggregatedFundingRate * 100)
            }%",
            icon = Icons.Filled.Percent,
            color = fundingColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF0D0A00)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(title, color = HeatmapText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Long / Short Ratio Card
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LongShortCard(longRatio: Double, shortRatio: Double) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF0D0A00)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.liquidation_long_short),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))

            // Ratio bar
            val longPct = (longRatio * 100).roundToInt()
            val shortPct = (shortRatio * 100).roundToInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        tint = SparklineGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Long", color = SparklineGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Text("${longPct}%", color = SparklineGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${shortPct}%", color = CoinLabRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("Short", color = CoinLabRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        null,
                        tint = CoinLabRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(longRatio.toFloat().coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(SparklineGreen.copy(alpha = 0.6f), SparklineGreen)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .weight(shortRatio.toFloat().coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CoinLabRed, CoinLabRed.copy(alpha = 0.6f))
                                )
                            )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Exchange Breakdown Card
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ExchangeBreakdownCard(exchanges: List<ExchangeData>) {
    if (exchanges.isEmpty()) return

    val totalOI = exchanges.sumOf { it.openInterestUsd }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF0D0A00)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.liquidation_exchange_breakdown),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))

            exchanges.forEach { ex ->
                val share = if (totalOI > 0) (ex.openInterestUsd / totalOI).toFloat() else 0f
                val exchangeColor = when (ex.exchange) {
                    "Binance" -> Color(0xFFF3BA2F)
                    "Bybit" -> Color(0xFFF7A600)
                    "OKX" -> Color(0xFF00C8FF)
                    "Bitget" -> Color(0xFF00E5A0)
                    "Gate.io" -> Color(0xFF2EBD85)
                    else -> CoinLabGold
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(exchangeColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ex.exchange,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.width(60.dp)
                        )
                        // OI bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1400))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(exchangeColor)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$${formatCompact(ex.openInterestUsd)}",
                        color = HeatmapText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(60.dp)
                    )
                }
            }

            // Funding rates
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = HeatmapGrid, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                exchanges.forEach { ex ->
                    val fundColor = if (ex.fundingRate >= 0) SparklineGreen else CoinLabRed
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ex.exchange.take(3),
                            color = HeatmapText,
                            fontSize = 10.sp
                        )
                        Text(
                            "${String.format("%.3f", ex.fundingRate * 100)}%",
                            color = fundColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Recent Liquidation Event Row
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LiquidationEventRow(event: LiquidationEvent, baseCoin: String) {
    val isLong = event.side == LiqSide.LONG
    val sideColor = if (isLong) SparklineGreen else CoinLabRed
    val sideText = if (isLong) "LONG" else "SHORT"
    val sideIcon = if (isLong) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0D0A00)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    sideIcon,
                    contentDescription = null,
                    tint = sideColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row {
                        Text(
                            sideText,
                            color = sideColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            event.exchange,
                            color = HeatmapText,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "$${formatNumber(event.price)}",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${formatCompact(event.usdValue)}",
                    color = sideColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    "${String.format("%.4f", event.quantity)} $baseCoin",
                    color = HeatmapText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════════

private fun formatNumber(value: Double): String {
    if (value == 0.0) return "0"
    return if (value >= 1) {
        NumberFormat.getNumberInstance(Locale.US).apply {
            maximumFractionDigits = 2
        }.format(value)
    } else {
        String.format("%.6f", value)
    }
}

private fun formatCompact(value: Double): String {
    return when {
        value >= 1_000_000_000 -> String.format("%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format("%.2fM", value / 1_000_000)
        value >= 1_000 -> String.format("%.1fK", value / 1_000)
        value >= 1 -> String.format("%.0f", value)
        else -> String.format("%.2f", value)
    }
}

private fun formatPriceLabel(price: Double): String {
    return when {
        price >= 10000 -> String.format("%.0f", price)
        price >= 100 -> String.format("%.1f", price)
        price >= 1 -> String.format("%.2f", price)
        else -> String.format("%.4f", price)
    }
}
