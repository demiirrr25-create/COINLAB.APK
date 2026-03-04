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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.coinlab.app.R
import com.coinlab.app.ui.components.CandlestickChart
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.alertCreated) {
        if (uiState.alertCreated) {
            snackbarHostState.showSnackbar("Fiyat uyarısı oluşturuldu!")
        }
    }

    if (uiState.showAlertDialog) {
        CreatePriceAlertDialog(
            coinName = uiState.coinDetail?.name ?: "",
            currentPrice = uiState.livePrice ?: uiState.coinDetail?.currentPrice?.get(uiState.currency.lowercase()) ?: 0.0,
            currency = uiState.currency,
            onDismiss = viewModel::hideAlertDialog,
            onConfirm = { price, isAbove -> viewModel.createAlert(price, isAbove) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                                    if (uiState.chartType == ChartType.CANDLE && chart.ohlc.isNotEmpty()) {
                                                        CandlestickChart(
                                                            data = remember(chart) { chart.ohlc },
                                                            modifier = Modifier.fillMaxSize(),
                                                            currency = uiState.currency
                                                        )
                                                    } else {
                                                        InteractiveChart(
                                                            prices = remember(chart) { chart.prices.map { it.second } },
                                                            volumes = remember(chart) { chart.totalVolumes?.map { it.second } ?: emptyList() },
                                                            showVolume = true,
                                                            currency = uiState.currency
                                                        )
                                                    }
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

                                // Chart type + Time range selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Chart type toggle
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilterChip(
                                            selected = uiState.chartType == ChartType.LINE,
                                            onClick = { if (uiState.chartType != ChartType.LINE) viewModel.toggleChartType() },
                                            label = { Text("Çizgi", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        FilterChip(
                                            selected = uiState.chartType == ChartType.CANDLE,
                                            onClick = { if (uiState.chartType != ChartType.CANDLE) viewModel.toggleChartType() },
                                            label = { Text("Mum", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }

                                    // Time range selector
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("1" to "24S", "7" to "7G", "30" to "30G", "365" to "1Y").forEach { (days, label) ->
                                            FilterChip(
                                                selected = uiState.selectedTimeRange == days,
                                                onClick = { viewModel.loadMarketChart(days) },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
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
                                onClick = { viewModel.showAlertDialog() },
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

                        // Proje Hakkında
                        val hasAboutContent = coin.description.isNotBlank() ||
                            coin.categories.isNotEmpty() ||
                            coin.genesisDate != null ||
                            coin.links.homepage.any { it.isNotBlank() } ||
                            coin.links.twitter != null ||
                            coin.links.reddit != null ||
                            coin.links.telegram != null ||
                            coin.links.github.any { it.isNotBlank() }

                        if (hasAboutContent) {
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
                                        text = "Proje Hakkında",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Categories
                                    if (coin.categories.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            coin.categories.take(5).forEach { cat ->
                                                FilterChip(
                                                    selected = false,
                                                    onClick = {},
                                                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }

                                    // Genesis date
                                    coin.genesisDate?.let { date ->
                                        if (date.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            StatRow("Lansman Tarihi", date)
                                        }
                                    }

                                    // Description
                                    if (coin.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        var expanded by remember { mutableStateOf(false) }
                                        val cleanDesc = coin.description.replace(Regex("<[^>]*>"), "")
                                        Text(
                                            text = cleanDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (cleanDesc.length > 200) {
                                            Text(
                                                text = if (expanded) "Daha az göster" else "Daha fazla göster",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CoinLabGold,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { expanded = !expanded }
                                            )
                                        }
                                    }

                                    // Links
                                    val uriHandler = LocalUriHandler.current
                                    val hasLinks = coin.links.homepage.any { it.isNotBlank() } ||
                                        coin.links.twitter != null || coin.links.reddit != null ||
                                        coin.links.telegram != null || coin.links.github.any { it.isNotBlank() }

                                    if (hasLinks) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Text("Bağlantılar", style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        @Composable
                                        fun LinkRow(label: String, url: String) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { try { uriHandler.openUri(url) } catch (_: Exception) {} }
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(label, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = url.removePrefix("https://").removePrefix("http://").take(30),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = CoinLabGold,
                                                    textDecoration = TextDecoration.Underline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        coin.links.homepage.firstOrNull { it.isNotBlank() }?.let { LinkRow("Web Sitesi", it) }
                                        coin.links.twitter?.let { if (it.isNotBlank()) LinkRow("Twitter/X", "https://twitter.com/$it") }
                                        coin.links.reddit?.let { if (it.isNotBlank()) LinkRow("Reddit", it) }
                                        coin.links.telegram?.let { if (it.isNotBlank()) LinkRow("Telegram", "https://t.me/$it") }
                                        coin.links.github.firstOrNull { it.isNotBlank() }?.let { LinkRow("GitHub", it) }
                                        coin.links.blockchain.firstOrNull { it.isNotBlank() }?.let { LinkRow("Blockchain", it) }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePriceAlertDialog(
    coinName: String,
    currentPrice: Double,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, Boolean) -> Unit
) {
    var targetPriceText by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val isAbove = selectedIndex == 0
    val options = listOf("Üstüne Çıkınca", "Altına Düşünce")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fiyat Uyarısı Oluştur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$coinName - Güncel: ${FormatUtils.formatPrice(currentPrice)} ${currency.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = targetPriceText,
                    onValueChange = { targetPriceText = it },
                    label = { Text("Hedef Fiyat (${currency.uppercase()})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val targetPrice = targetPriceText.toDoubleOrNull()
                if (targetPrice != null) {
                    val diff = if (isAbove) targetPrice - currentPrice else currentPrice - targetPrice
                    val pct = if (currentPrice > 0) (diff / currentPrice * 100) else 0.0
                    Text(
                        text = if (diff > 0) "Fark: +%.2f%%".format(pct) else "Fark: %.2f%%".format(pct),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff > 0) CoinLabGreen else CoinLabRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    targetPriceText.toDoubleOrNull()?.let { onConfirm(it, isAbove) }
                },
                enabled = targetPriceText.toDoubleOrNull() != null && targetPriceText.toDoubleOrNull()!! > 0
            ) {
                Text("Uyarı Oluştur")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
