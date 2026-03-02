package com.coinlab.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.coinlab.app.R
import com.coinlab.app.ui.components.FormatUtils
import com.coinlab.app.ui.components.InteractiveChart
import com.coinlab.app.ui.components.PriceChangeIndicator
import com.coinlab.app.ui.theme.CoinLabGold
import com.coinlab.app.ui.theme.CoinLabGreen
import com.coinlab.app.ui.theme.CoinLabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    coinId: String,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onTechnicalAnalysis: () -> Unit = {},
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currency = uiState.currency.lowercase()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                uiState.coinDetail?.let { coin ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = coin.image,
                            contentDescription = coin.name,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${coin.name} (${coin.symbol})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = viewModel::toggleWatchlist) {
                    Icon(
                        imageVector = if (uiState.isInWatchlist) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Watchlist",
                        tint = if (uiState.isInWatchlist) CoinLabGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                uiState.coinDetail?.let { coin ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Price Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Show live price if available, otherwise static price
                            val displayPrice = uiState.livePrice ?: (coin.currentPrice[currency] ?: 0.0)
                            Text(
                                text = FormatUtils.formatPrice(displayPrice, uiState.currency),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PriceChangeIndicator(
                                    changePercentage = uiState.livePriceChange ?: coin.priceChangePercentage24h,
                                    showBackground = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "24h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.livePrice != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "● Canlı",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CoinLabGreen
                                    )
                                }
                            }
                        }

                        // Chart Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                when {
                                    uiState.isChartLoading -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    uiState.chartError != null -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = uiState.chartError ?: "Grafik yüklenemedi",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.error,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                FilledTonalButton(onClick = viewModel::retryChart) {
                                                    Text("Tekrar Dene")
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        uiState.marketChart?.let { chart ->
                                            if (chart.prices.isEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(220.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = "Grafik verisi boş",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        FilledTonalButton(onClick = viewModel::retryChart) {
                                                            Text("Tekrar Dene")
                                                        }
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(220.dp)
                                                ) {
                                                    InteractiveChart(
                                                        prices = remember(chart) { chart.prices.map { it.second } },
                                                        volumes = remember(chart) { chart.totalVolumes?.map { it.second } ?: emptyList() },
                                                        showVolume = true,
                                                        currency = uiState.currency
                                                    )
                                                }
                                            }
                                        } ?: Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Grafik verisi bulunamadı",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Time range selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf("1" to "24S", "7" to "7G", "30" to "30G", "90" to "90G", "365" to "1Y").forEach { (days, label) ->
                                        FilterChip(
                                            selected = uiState.selectedTimeRange == days,
                                            onClick = { viewModel.loadMarketChart(days) },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onAddTransaction,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.add_to_portfolio))
                            }
                            FilledTonalButton(
                                onClick = { /* TODO: Price alert dialog */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.set_alert))
                            }
                        }

                        // Technical Analysis Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onTechnicalAnalysis,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Teknik Analiz")
                            }
                        }

                        // Market Stats
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.market_stats),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                StatRow(stringResource(R.string.market_cap), FormatUtils.formatMarketCap(coin.marketCap[currency] ?: 0L, uiState.currency))
                                StatRow(stringResource(R.string.volume_24h), FormatUtils.formatVolume(coin.totalVolume[currency] ?: 0.0, uiState.currency))
                                StatRow(stringResource(R.string.rank), "#${coin.marketCapRank}")
                                StatRow(stringResource(R.string.high_24h), FormatUtils.formatPrice(coin.high24h[currency] ?: 0.0, uiState.currency))
                                StatRow(stringResource(R.string.low_24h), FormatUtils.formatPrice(coin.low24h[currency] ?: 0.0, uiState.currency))

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                StatRow(stringResource(R.string.circulating_supply), FormatUtils.formatSupply(coin.circulatingSupply) + " ${coin.symbol}")
                                coin.totalSupply?.let {
                                    StatRow(stringResource(R.string.total_supply), FormatUtils.formatSupply(it) + " ${coin.symbol}")
                                }
                                coin.maxSupply?.let {
                                    StatRow(stringResource(R.string.max_supply), FormatUtils.formatSupply(it) + " ${coin.symbol}")
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                StatRow(stringResource(R.string.ath), FormatUtils.formatPrice(coin.ath[currency] ?: 0.0, uiState.currency))
                                StatRow(stringResource(R.string.ath_change), FormatUtils.formatPercentage(coin.athChangePercentage[currency] ?: 0.0))
                                StatRow(stringResource(R.string.atl), FormatUtils.formatPrice(coin.atl[currency] ?: 0.0, uiState.currency))
                            }
                        }

                        // Price Changes
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.price_changes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                PriceChangeRow("24 ${stringResource(R.string.hours)}", coin.priceChangePercentage24h)
                                PriceChangeRow("7 ${stringResource(R.string.days)}", coin.priceChangePercentage7d)
                                PriceChangeRow("30 ${stringResource(R.string.days)}", coin.priceChangePercentage30d)
                                PriceChangeRow("1 ${stringResource(R.string.year)}", coin.priceChangePercentage1y)
                            }
                        }

                        // Description
                        if (coin.description.isNotBlank()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.about),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = coin.description.replace(Regex("<[^>]*>"), ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PriceChangeRow(label: String, percentage: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PriceChangeIndicator(
            changePercentage = percentage,
            showBackground = true
        )
    }
}
