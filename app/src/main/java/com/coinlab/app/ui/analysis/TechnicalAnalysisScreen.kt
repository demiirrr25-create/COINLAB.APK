package com.coinlab.app.ui.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.ui.components.CandlestickChart
import com.coinlab.app.ui.components.OverlayLine
import com.coinlab.app.ui.components.SparklineChart
import com.coinlab.app.ui.theme.CoinLabBlue
import com.coinlab.app.ui.theme.CoinLabGold
import com.coinlab.app.ui.theme.CoinLabGreen
import com.coinlab.app.ui.theme.CoinLabPurple
import com.coinlab.app.ui.theme.CoinLabRed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TechnicalAnalysisScreen(
    coinId: String,
    onBack: () -> Unit,
    viewModel: TechnicalAnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${uiState.coinDetail?.name ?: coinId} ${stringResource(R.string.technical_analysis)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (uiState.isLoading && uiState.ohlcData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Signal Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.signalType) {
                            SignalType.BUY -> CoinLabGreen.copy(alpha = 0.1f)
                            SignalType.SELL -> CoinLabRed.copy(alpha = 0.1f)
                            SignalType.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (uiState.signalType) {
                                SignalType.BUY -> Icons.Filled.TrendingUp
                                SignalType.SELL -> Icons.Filled.TrendingDown
                                SignalType.NEUTRAL -> Icons.Filled.TrendingFlat
                            },
                            contentDescription = null,
                            tint = when (uiState.signalType) {
                                SignalType.BUY -> CoinLabGreen
                                SignalType.SELL -> CoinLabRed
                                SignalType.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.signal_summary),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.signalSummary.ifEmpty { "Analiz yükleniyor..." },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (uiState.signalType) {
                                    SignalType.BUY -> CoinLabGreen
                                    SignalType.SELL -> CoinLabRed
                                    SignalType.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                // Candlestick Chart
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.candlestick_chart),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (uiState.ohlcData.isNotEmpty()) {
                            val overlayLines = buildList {
                                if (uiState.selectedIndicators.contains(IndicatorType.SMA_20) && uiState.sma20.isNotEmpty()) {
                                    add(OverlayLine(uiState.sma20, CoinLabBlue, 1.5f, "SMA 20"))
                                }
                                if (uiState.selectedIndicators.contains(IndicatorType.EMA_50) && uiState.ema50.isNotEmpty()) {
                                    add(OverlayLine(uiState.ema50, CoinLabGold, 1.5f, "EMA 50"))
                                }
                                if (uiState.selectedIndicators.contains(IndicatorType.BOLLINGER)) {
                                    if (uiState.bollingerUpper.isNotEmpty()) add(OverlayLine(uiState.bollingerUpper, CoinLabPurple.copy(alpha = 0.6f), 1f, "BB Upper"))
                                    if (uiState.bollingerLower.isNotEmpty()) add(OverlayLine(uiState.bollingerLower, CoinLabPurple.copy(alpha = 0.6f), 1f, "BB Lower"))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            ) {
                                CandlestickChart(
                                    data = uiState.ohlcData,
                                    overlayLines = overlayLines
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_chart_data),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Time range selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("1" to "1G", "7" to "7G", "14" to "14G", "30" to "1A", "365" to "1Y").forEach { (days, label) ->
                                FilterChip(
                                    selected = uiState.selectedTimeRange == days,
                                    onClick = { viewModel.loadOhlcData(days) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Indicator Toggles
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.indicators),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IndicatorType.entries.forEach { indicator ->
                                FilterChip(
                                    selected = uiState.selectedIndicators.contains(indicator),
                                    onClick = { viewModel.toggleIndicator(indicator) },
                                    label = { Text(indicator.displayName()) }
                                )
                            }
                        }
                    }
                }

                // RSI Card
                if (uiState.selectedIndicators.contains(IndicatorType.RSI) && uiState.rsiValues.isNotEmpty()) {
                    val latestRsi = uiState.rsiValues.lastOrNull { it != null }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RSI (14)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                latestRsi?.let {
                                    val rsiColor = when {
                                        it < 30 -> CoinLabGreen
                                        it > 70 -> CoinLabRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    Text(
                                        text = String.format("%.1f", it),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = rsiColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            latestRsi?.let {
                                LinearProgressIndicator(
                                    progress = { (it / 100).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = when {
                                        it < 30 -> CoinLabGreen
                                        it > 70 -> CoinLabRed
                                        else -> CoinLabBlue
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Aşırı Satım (<30)", style = MaterialTheme.typography.labelSmall, color = CoinLabGreen)
                                    Text("Aşırı Alım (>70)", style = MaterialTheme.typography.labelSmall, color = CoinLabRed)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            val rsiChartData = uiState.rsiValues.filterNotNull()
                            if (rsiChartData.size > 2) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                ) {
                                    SparklineChart(
                                        data = rsiChartData,
                                        positiveColor = CoinLabBlue,
                                        negativeColor = CoinLabBlue
                                    )
                                }
                            }
                        }
                    }
                }

                // MACD Card
                if (uiState.selectedIndicators.contains(IndicatorType.MACD)) {
                    val latestMacd = uiState.macdLine.lastOrNull { it != null }
                    val latestSignalLine = uiState.macdSignal.lastOrNull { it != null }
                    val latestHist = uiState.macdHistogram.lastOrNull { it != null }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "MACD (12, 26, 9)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                MacdValueItem("MACD", latestMacd, CoinLabBlue)
                                MacdValueItem("Signal", latestSignalLine, CoinLabGold)
                                MacdValueItem("Histogram", latestHist,
                                    if ((latestHist ?: 0.0) >= 0) CoinLabGreen else CoinLabRed
                                )
                            }
                        }
                    }
                }

                // Fear & Greed Index
                uiState.fearGreedIndex?.let { fgi ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.fear_greed_index),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = fgi.emoji,
                                style = MaterialTheme.typography.displayLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${fgi.value}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = fearGreedColor(fgi.value)
                            )
                            Text(
                                text = fgi.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = fearGreedColor(fgi.value)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { fgi.value / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = fearGreedColor(fgi.value),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("😱 Korku", style = MaterialTheme.typography.labelSmall)
                                Text("Açgözlülük 🤑", style = MaterialTheme.typography.labelSmall)
                            }

                            // Fear & Greed History
                            if (uiState.fearGreedHistory.size > 2) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Son 30 Gün",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                ) {
                                    SparklineChart(
                                        data = uiState.fearGreedHistory.reversed().map { it.value.toDouble() },
                                        positiveColor = CoinLabGold,
                                        negativeColor = CoinLabGold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MacdValueItem(label: String, value: Double?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.let { String.format("%.4f", it) } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun IndicatorType.displayName(): String = when (this) {
    IndicatorType.SMA_20 -> "SMA 20"
    IndicatorType.EMA_50 -> "EMA 50"
    IndicatorType.RSI -> "RSI"
    IndicatorType.MACD -> "MACD"
    IndicatorType.BOLLINGER -> "Bollinger"
    IndicatorType.STOCH_RSI -> "Stoch RSI"
}

@Composable
private fun fearGreedColor(value: Int): Color = when {
    value <= 25 -> CoinLabRed
    value <= 45 -> Color(0xFFFF9800)
    value <= 55 -> CoinLabGold
    value <= 75 -> Color(0xFF8BC34A)
    else -> CoinLabGreen
}
