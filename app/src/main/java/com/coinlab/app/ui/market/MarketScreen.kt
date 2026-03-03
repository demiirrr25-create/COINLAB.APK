package com.coinlab.app.ui.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.coinlab.app.ui.theme.CoinLabGreen
import com.coinlab.app.ui.theme.CoinLabRed
import com.coinlab.app.ui.theme.CoinLabAqua
import com.coinlab.app.ui.theme.CoinLabGold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.data.remote.CoinCategoryMapper.CoinCategory
import com.coinlab.app.ui.components.CoinListItem
import com.coinlab.app.ui.components.ShimmerList
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onCoinClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isSearchVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showBtcDominanceSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Lazy WebSocket: track visible items and subscribe only to those symbols
    @OptIn(FlowPreview::class)
    LaunchedEffect(listState, uiState.coins) {
        if (uiState.coins.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val firstIndex = (visibleItems.firstOrNull()?.index ?: 1) - 1 // -1 for header item
            val lastIndex = (visibleItems.lastOrNull()?.index ?: 1) - 1
            // Add buffer of 10 items above and below for smoother experience
            val bufferStart = (firstIndex - 10).coerceAtLeast(0)
            val bufferEnd = (lastIndex + 10).coerceAtMost(uiState.coins.size - 1)
            (bufferStart..bufferEnd).mapNotNull { i ->
                uiState.coins.getOrNull(i)?.symbol?.lowercase()
            }
        }
            .debounce(300)
            .distinctUntilChanged()
            .collect { visibleSymbols ->
                viewModel.updateVisibleSymbols(visibleSymbols)
            }
    }

    // Disconnect WebSocket on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectWebSocket()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "CoinLab",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Search Bar
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_coins)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        // Tab Chips — main navigation tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                MarketTab.ALL to R.string.tab_all,
                MarketTab.WATCHLIST to R.string.tab_watchlist,
                MarketTab.TRENDING to R.string.tab_trending,
                MarketTab.TOP_GAINERS to R.string.tab_gainers,
                MarketTab.TOP_LOSERS to R.string.tab_losers
            )
            items(tabs) { (tab, labelRes) ->
                FilterChip(
                    selected = uiState.selectedTab == tab && uiState.selectedCategory == CoinCategory.ALL,
                    onClick = { viewModel.onTabSelected(tab) },
                    label = { Text(stringResource(labelRes)) }
                )
            }
        }

        // Category Chips — shown when ALL tab is selected
        AnimatedVisibility(
            visible = uiState.selectedTab == MarketTab.ALL || uiState.selectedCategory != CoinCategory.ALL,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val categories = CoinCategory.entries
                items(categories) { category ->
                    val count = if (category == CoinCategory.ALL) {
                        uiState.totalCoinCount
                    } else {
                        uiState.categoryCounts[category] ?: 0
                    }
                    val label = getCategoryLabel(category)
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.onCategorySelected(category) },
                        label = {
                            Text(
                                text = if (count > 0 && category != CoinCategory.ALL) {
                                    "${category.emoji} $label ($count)"
                                } else {
                                    "${category.emoji} $label"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Content
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    ShimmerList()
                }

                uiState.error != null && uiState.coins.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.error_loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.loadCoins() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                else -> {
                    val displayCoins = when (uiState.selectedTab) {
                        MarketTab.WATCHLIST -> uiState.watchlistCoins
                        MarketTab.TRENDING -> uiState.trendingCoins
                        else -> uiState.filteredCoins
                    }

                    if (displayCoins.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (uiState.selectedTab) {
                                    MarketTab.WATCHLIST -> stringResource(R.string.empty_watchlist)
                                    else -> stringResource(R.string.no_coins_found)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Modernized Market Summary Header
                            if (uiState.totalMarketCap > 0) {
                                item {
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            // Total Market Cap
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { onCoinClick("bitcoin") }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(CoinLabAqua.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                        contentDescription = null,
                                                        tint = CoinLabAqua,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = formatLargeNumber(uiState.totalMarketCap),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(R.string.market_cap),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            // BTC Dominance → opens bottom sheet
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { showBtcDominanceSheet = true }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(CoinLabGold.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "\u20BF",
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CoinLabGold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "%.1f%%".format(uiState.btcDominance),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = stringResource(R.string.btc_dominance),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }

                                            // 24h Change
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                val changeColor = if (uiState.marketCapChange24h >= 0) CoinLabGreen else CoinLabRed
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(changeColor.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Timeline,
                                                        contentDescription = null,
                                                        tint = changeColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "%+.2f%%".format(uiState.marketCapChange24h),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = changeColor,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "24h",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Market header
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#  ${stringResource(R.string.coin)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${stringResource(R.string.price)} / 24h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(
                                items = displayCoins,
                                key = { it.id }
                            ) { coin ->
                                CoinListItem(
                                    coin = coin,
                                    currency = uiState.currency,
                                    onClick = { onCoinClick(coin.id) }
                                )
                            }

                            // Footer with count info
                            item {
                                if (uiState.totalCoinCount > 0) {
                                    val categoryLabel = if (uiState.selectedCategory != CoinCategory.ALL) {
                                        " (${getCategoryLabel(uiState.selectedCategory)})"
                                    } else ""
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${displayCoins.size} / ${uiState.totalCoinCount} coin$categoryLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // BTC Dominance Bottom Sheet
    if (showBtcDominanceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBtcDominanceSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.btc_dominance_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.btc_dominance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Large dominance display
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(CoinLabGold.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "%.1f%%".format(uiState.btcDominance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = CoinLabGold
                        )
                        Text(
                            text = "BTC",
                            style = MaterialTheme.typography.labelMedium,
                            color = CoinLabGold.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dominance breakdown
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Piyasa Dağılımı",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // BTC bar
                        DominanceBar(
                            label = "Bitcoin (BTC)",
                            percentage = uiState.btcDominance,
                            color = CoinLabGold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Remaining (Others)
                        val othersPercent = (100.0 - uiState.btcDominance).coerceAtLeast(0.0)
                        DominanceBar(
                            label = "Altcoinler",
                            percentage = othersPercent,
                            color = CoinLabAqua
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Market cap info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatLargeNumber(uiState.totalMarketCap),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toplam Piyasa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatLargeNumber(uiState.totalMarketCap * uiState.btcDominance / 100.0),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoinLabGold
                        )
                        Text(
                            text = "BTC Piyasa Değeri",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatLargeNumber(value: Double): String {
    return when {
        value >= 1_000_000_000_000 -> "$${String.format("%.2f", value / 1_000_000_000_000)}T"
        value >= 1_000_000_000 -> "$${String.format("%.2f", value / 1_000_000_000)}B"
        value >= 1_000_000 -> "$${String.format("%.1f", value / 1_000_000)}M"
        else -> "$${String.format("%.0f", value)}"
    }
}

@Composable
private fun DominanceBar(
    label: String,
    percentage: Double,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%.1f%%".format(percentage),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percentage / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Get localized category label.
 * Uses the Turkish names from CoinCategory enum.
 * TODO: Use stringResource when adding to strings.xml
 */
@Composable
private fun getCategoryLabel(category: CoinCategory): String {
    return when (category) {
        CoinCategory.ALL -> stringResource(R.string.tab_all)
        CoinCategory.LAYER_1 -> stringResource(R.string.cat_layer1)
        CoinCategory.LAYER_2 -> stringResource(R.string.cat_layer2)
        CoinCategory.DEFI -> stringResource(R.string.cat_defi)
        CoinCategory.MEME -> stringResource(R.string.cat_meme)
        CoinCategory.NFT_GAMING -> stringResource(R.string.cat_nft_gaming)
        CoinCategory.AI -> stringResource(R.string.cat_ai)
        CoinCategory.INFRASTRUCTURE -> stringResource(R.string.cat_infra)
        CoinCategory.EXCHANGE -> stringResource(R.string.cat_exchange)
        CoinCategory.PRIVACY -> stringResource(R.string.cat_privacy)
        CoinCategory.STABLECOIN_DEFI -> stringResource(R.string.cat_stable_defi)
    }
}