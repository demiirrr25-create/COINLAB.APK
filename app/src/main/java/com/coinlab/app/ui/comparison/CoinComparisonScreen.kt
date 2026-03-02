package com.coinlab.app.ui.comparison

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.coinlab.app.ui.components.PriceChangeIndicator
import com.coinlab.app.ui.components.SparklineChart
import com.coinlab.app.ui.theme.CoinLabBlue
import com.coinlab.app.ui.theme.CoinLabGold
import com.coinlab.app.ui.theme.CoinLabGreen
import com.coinlab.app.ui.theme.CoinLabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinComparisonScreen(
    onBack: () -> Unit,
    viewModel: CoinComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.compare_coins),
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

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Coin selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoinSelector(
                        detail = uiState.coin1Detail,
                        onClick = { viewModel.showPicker(1) },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    CoinSelector(
                        detail = uiState.coin2Detail,
                        onClick = { viewModel.showPicker(2) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Chart comparison
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
                            text = stringResource(R.string.price_chart_comparison),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.coin1Chart?.let { chart1 ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            ) {
                                SparklineChart(
                                    data = chart1.prices.map { it.second },
                                    positiveColor = CoinLabBlue,
                                    negativeColor = CoinLabBlue
                                )
                            }
                            Text(
                                text = uiState.coin1Detail?.name ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoinLabBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        uiState.coin2Chart?.let { chart2 ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                            ) {
                                SparklineChart(
                                    data = chart2.prices.map { it.second },
                                    positiveColor = CoinLabGold,
                                    negativeColor = CoinLabGold
                                )
                            }
                            Text(
                                text = uiState.coin2Detail?.name ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoinLabGold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("1" to "1G", "7" to "7G", "30" to "1A", "90" to "3A", "365" to "1Y").forEach { (days, label) ->
                                FilterChip(
                                    selected = uiState.selectedDays == days,
                                    onClick = { viewModel.setDays(days) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Comparison Table
                val coin1 = uiState.coin1Detail
                val coin2 = uiState.coin2Detail
                val currency = uiState.currency.lowercase()

                if (coin1 != null && coin2 != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.comparison_table),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            ComparisonRow(
                                label = stringResource(R.string.price),
                                value1 = FormatUtils.formatPrice(coin1.currentPrice[currency] ?: 0.0, uiState.currency),
                                value2 = FormatUtils.formatPrice(coin2.currentPrice[currency] ?: 0.0, uiState.currency)
                            )
                            ComparisonRow(
                                label = stringResource(R.string.market_cap),
                                value1 = FormatUtils.formatMarketCap(coin1.marketCap[currency] ?: 0L, uiState.currency),
                                value2 = FormatUtils.formatMarketCap(coin2.marketCap[currency] ?: 0L, uiState.currency)
                            )
                            ComparisonRow(
                                label = "24h %",
                                value1 = FormatUtils.formatPercentage(coin1.priceChangePercentage24h),
                                value2 = FormatUtils.formatPercentage(coin2.priceChangePercentage24h),
                                value1Color = if (coin1.priceChangePercentage24h >= 0) CoinLabGreen else CoinLabRed,
                                value2Color = if (coin2.priceChangePercentage24h >= 0) CoinLabGreen else CoinLabRed
                            )
                            ComparisonRow(
                                label = "7d %",
                                value1 = FormatUtils.formatPercentage(coin1.priceChangePercentage7d),
                                value2 = FormatUtils.formatPercentage(coin2.priceChangePercentage7d),
                                value1Color = if (coin1.priceChangePercentage7d >= 0) CoinLabGreen else CoinLabRed,
                                value2Color = if (coin2.priceChangePercentage7d >= 0) CoinLabGreen else CoinLabRed
                            )
                            ComparisonRow(
                                label = "30d %",
                                value1 = FormatUtils.formatPercentage(coin1.priceChangePercentage30d),
                                value2 = FormatUtils.formatPercentage(coin2.priceChangePercentage30d),
                                value1Color = if (coin1.priceChangePercentage30d >= 0) CoinLabGreen else CoinLabRed,
                                value2Color = if (coin2.priceChangePercentage30d >= 0) CoinLabGreen else CoinLabRed
                            )
                            ComparisonRow(
                                label = stringResource(R.string.rank),
                                value1 = "#${coin1.marketCapRank}",
                                value2 = "#${coin2.marketCapRank}"
                            )
                            ComparisonRow(
                                label = stringResource(R.string.volume_24h),
                                value1 = FormatUtils.formatVolume(coin1.totalVolume[currency] ?: 0.0, uiState.currency),
                                value2 = FormatUtils.formatVolume(coin2.totalVolume[currency] ?: 0.0, uiState.currency)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Coin Picker Bottom Sheet
    if (uiState.showCoinPicker > 0) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hidePicker,
            sheetState = sheetState
        ) {
            Text(
                text = stringResource(R.string.select_coin),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(uiState.allCoins, key = { it.id }) { coin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectCoin(uiState.showCoinPicker, coin.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = coin.image,
                            contentDescription = coin.name,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = coin.name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = coin.symbol.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinSelector(
    detail: com.coinlab.app.domain.model.CoinDetail?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            detail?.let {
                AsyncImage(
                    model = it.image,
                    contentDescription = it.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = it.symbol.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: run {
                Text("Seçin", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    value1: String,
    value2: String,
    value1Color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    value2Color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value1,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = value1Color,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value2,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = value2Color,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}
