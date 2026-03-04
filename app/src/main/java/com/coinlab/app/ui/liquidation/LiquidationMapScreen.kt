package com.coinlab.app.ui.liquidation

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.coinlab.app.R
import com.coinlab.app.data.remote.firebase.ExchangeData
import com.coinlab.app.data.remote.firebase.LiqSide
import com.coinlab.app.data.remote.firebase.LiquidationEvent
import com.coinlab.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════
// v12.3 — CoinGlass-Style Open Interest Liquidation Map
// Search bar, threshold slider, model dropdown, zoom, gradient legend
// ═══════════════════════════════════════════════════════════════════════

private val HeatmapText = Color(0xFF8888AA)
private val HeatmapGrid = Color(0xFF2A2A3E)

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
                        if (uiState.wsConnected) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SparklineGreen)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (uiState.markPrice > 0) {
                        Text(
                            "$${formatNumber(uiState.markPrice)}",
                            color = CoinLabGold,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    // Fullscreen / Zoom toggle
                    IconButton(onClick = { viewModel.toggleFullscreen() }) {
                        Icon(
                            if (uiState.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = stringResource(R.string.liquidation_zoom),
                            tint = CoinLabGold
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Yenile",
                            tint = CoinLabGreen
                        )
                    }
                    // Hidden debug toggle — triple-tap to activate
                    var debugTapCount by remember { mutableIntStateOf(0) }
                    var lastDebugTap by remember { mutableLongStateOf(0L) }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - lastDebugTap > 600) debugTapCount = 0
                                debugTapCount++
                                lastDebugTap = now
                                if (debugTapCount >= 3) {
                                    viewModel.toggleDebugMode()
                                    debugTapCount = 0
                                }
                            }
                    )
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
                // Symbol Search Bar
                item {
                    SymbolSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::setSearchQuery
                    )
                }

                // Coin selector (filtered by search)
                item { CoinSelector(uiState.selectedCoin, viewModel.filteredCoins, viewModel::selectCoin) }

                // Time filter chips
                item { TimeFilterRow(uiState.timeFilter, viewModel.timeFilters, viewModel::setTimeFilter) }

                // Model dropdown + threshold slider row
                item {
                    HeatmapControlsRow(
                        selectedModel = uiState.selectedModel,
                        availableModels = viewModel.availableModels,
                        onModelSelect = viewModel::setModel,
                        threshold = uiState.threshold,
                        onThresholdChange = viewModel::setThreshold
                    )
                }

                // Professional WebView Chart
                item {
                    val chartHeight = if (uiState.isFullscreen) 640.dp else 480.dp
                    LiquidationChartWebView(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Gradient scale legend
                item { GradientScaleLegend() }

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
// WebView Chart — TradingView Lightweight Charts + Heatmap Overlay
// ═══════════════════════════════════════════════════════════════════════

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiquidationChartWebView(
    viewModel: LiquidationMapViewModel,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle for WebView pause/resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_DESTROY -> {
                    webView?.destroy()
                    webView = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect chart commands and forward to WebView
    LaunchedEffect(webView) {
        val wv = webView ?: return@LaunchedEffect
        viewModel.chartCommands.collect { cmd ->
            val js = when (cmd) {
                is ChartCommand.SetCandleData -> "setCandleData('${cmd.json.escapeForJs()}')"
                is ChartCommand.UpdateCandle -> "updateCandle('${cmd.json.escapeForJs()}')"
                is ChartCommand.SetHeatmap -> "setHeatmapData('${cmd.json.escapeForJs()}')"
                is ChartCommand.SetMarkPrice -> "setMarkPrice(${cmd.price})"
                is ChartCommand.SetPrecision -> "setPricePrecision(${cmd.precision}, ${cmd.minMove})"
                is ChartCommand.SetThreshold -> "setThreshold(${cmd.value})"
                is ChartCommand.SetModel -> "setModel('${cmd.model}')"
                is ChartCommand.SetLongShortTotals -> "setLongShortTotals(${cmd.totalLongUsd}, ${cmd.totalShortUsd})"
                is ChartCommand.SetLiquidationSpikes -> "setLiquidationSpikes('${cmd.json.escapeForJs()}')"
                is ChartCommand.AddLiquidationSpike -> "addLiquidationSpike('${cmd.json.escapeForJs()}')"
                is ChartCommand.SetDebugMode -> "setDebugMode(${cmd.enabled})"
            }
            wv.post { wv.evaluateJavascript(js, null) }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = false
                }
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        viewModel.onChartReady()
                    }
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onTimeframeChanged(tf: String) {
                        viewModel.setTimeFilter(tf)
                    }
                }, "AndroidBridge")

                loadUrl("file:///android_asset/liquidation_chart.html")
                webView = this
            }
        },
        modifier = modifier
    )
}

/** Escape JSON string for safe JS injection */
private fun String.escapeForJs(): String {
    return this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
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
        StatMiniCard(
            title = stringResource(R.string.liquidation_open_interest),
            value = "$${formatCompact(data.totalOpenInterestUsd)}",
            icon = Icons.Filled.AccountBalance,
            color = CoinLabGold,
            modifier = Modifier.weight(1f)
        )

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

            val longPct = (longRatio * 100).roundToInt()
            val shortPct = (shortRatio * 100).roundToInt()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = SparklineGreen, modifier = Modifier.size(16.dp))
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
                    Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = CoinLabRed, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

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
                        Text(ex.exchange.take(3), color = HeatmapText, fontSize = 10.sp)
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
                Icon(sideIcon, contentDescription = null, tint = sideColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Row {
                        Text(sideText, color = sideColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(event.exchange, color = HeatmapText, fontSize = 11.sp)
                    }
                    Text("$${formatNumber(event.price)}", color = Color.White, fontSize = 13.sp)
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
// Symbol Search Bar
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun SymbolSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = {
            Text(
                stringResource(R.string.liquidation_search_hint),
                color = HeatmapText,
                fontSize = 14.sp
            )
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = CoinLabGold, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = HeatmapText, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CoinLabGreen,
            unfocusedBorderColor = Color(0xFF2A2010),
            cursorColor = CoinLabGreen,
            focusedContainerColor = Color(0xFF0D0A00),
            unfocusedContainerColor = Color(0xFF0D0A00),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Heatmap Controls: Model Dropdown + Threshold Slider
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun HeatmapControlsRow(
    selectedModel: String,
    availableModels: List<String>,
    onModelSelect: (String) -> Unit,
    threshold: Float,
    onThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Model selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.liquidation_model),
                color = HeatmapText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            var expanded by remember { mutableStateOf(false) }

            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A1400),
                    modifier = Modifier.clickable { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedModel,
                            color = CoinLabGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = CoinLabGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF1A1400))
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (model) {
                                        "Standard" -> stringResource(R.string.liquidation_model_standard)
                                        "Aggressive" -> stringResource(R.string.liquidation_model_aggressive)
                                        "Conservative" -> stringResource(R.string.liquidation_model_conservative)
                                        else -> model
                                    },
                                    color = if (model == selectedModel) CoinLabGreen else Color.White,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                onModelSelect(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Threshold slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.liquidation_threshold),
                color = HeatmapText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(52.dp)
            )
            Slider(
                value = threshold,
                onValueChange = onThresholdChange,
                modifier = Modifier.weight(1f),
                valueRange = 0f..1f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = CoinLabGreen,
                    activeTrackColor = CoinLabGreen,
                    inactiveTrackColor = Color(0xFF1A1400)
                )
            )
            Text(
                "${(threshold * 100).roundToInt()}%",
                color = CoinLabGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Gradient Scale Legend
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun GradientScaleLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Gradient bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1A237E),  // Deep blue — low
                            Color(0xFF0064C8),  // Blue
                            Color(0xFF00C853),  // Green
                            Color(0xFFFFC107),  // Yellow
                            Color(0xFFFF1744)   // Red — high
                        )
                    )
                )
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Düşük", color = HeatmapText, fontSize = 10.sp)
            Text("Orta", color = HeatmapText, fontSize = 10.sp)
            Text("Yüksek", color = HeatmapText, fontSize = 10.sp)
            Text("Aşırı", color = CoinLabRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
