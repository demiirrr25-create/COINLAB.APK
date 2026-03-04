package com.coinlab.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.ui.components.CoinListItem
import com.coinlab.app.ui.theme.*
import com.coinlab.app.data.remote.api.FearGreedDataItem
import com.coinlab.app.data.remote.dto.TrendingCoinDto
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCoinClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onWeb3Click: () -> Unit = {},
    onCommunityClick: () -> Unit = {},
    onAirdropClick: () -> Unit = {},
    onComparisonClick: () -> Unit = {},
    onStakingClick: () -> Unit = {},
    onAllMarketClick: () -> Unit = {},
    onPredictionClick: () -> Unit = {},
    onTradingClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val displayName = uiState.displayName

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { WelcomeHeader(onSearchClick = onSearchClick, displayName = displayName) }

            item {
                MarketSummaryCard(
                    fearGreedValue = uiState.fearGreedValue,
                    fearGreedLabel = uiState.fearGreedLabel,
                    fearGreedHistory = uiState.fearGreedHistory,
                    totalMarketCap = uiState.totalMarketCap,
                    totalVolume24h = uiState.totalVolume24h,
                    btcDominance = uiState.btcDominance,
                    ethDominance = uiState.ethDominance,
                    marketCapChangePercent24h = uiState.marketCapChangePercent24h,
                    activeCryptos = uiState.activeCryptos,
                    currency = uiState.currency
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.top_coins),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.isWebSocketConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CoinLabGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.live),
                                style = MaterialTheme.typography.labelSmall,
                                color = CoinLabGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(onClick = onAllMarketClick) {
                        Text(
                            text = stringResource(R.string.see_all),
                            color = CoinLabGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = CoinLabGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CoinLabGreen)
                    }
                }
            } else if (uiState.error != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.error_loading),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.loadData() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            } else {
                items(uiState.top5Coins, key = { it.id }) { coin ->
                    CoinListItem(
                        coin = coin,
                        currency = uiState.currency,
                        onClick = { onCoinClick(coin.id) }
                    )
                }
            }

            // ── TOP MOVERS SECTION ──────────────────────
            if (uiState.topGainers.isNotEmpty() || uiState.topLosers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\uD83D\uDD25 Top Movers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.topGainers + uiState.topLosers, key = { "mover_${it.id}" }) { coin ->
                            TopMoverChip(
                                name = coin.name,
                                symbol = coin.symbol.uppercase(),
                                change = coin.priceChangePercentage24h,
                                imageUrl = coin.image,
                                onClick = { onCoinClick(coin.id) }
                            )
                        }
                    }
                }
            }

            // ── TRENDING SECTION ─────────────────────────
            if (uiState.trendingCoins.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\uD83D\uDCC8 Trending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.trendingCoins, key = { "trend_${it.id}" }) { coin ->
                            TrendingCoinChip(coin = coin, onClick = { onCoinClick(coin.id) })
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.quick_access),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                QuickAccessGrid(
                    onWeb3Click = onWeb3Click,
                    onCommunityClick = onCommunityClick,
                    onAirdropClick = onAirdropClick,
                    onComparisonClick = onComparisonClick,
                    onStakingClick = onStakingClick,
                    onPredictionClick = onPredictionClick,
                    onTradingClick = onTradingClick
                )
            }
        }
    }
}

@Composable
private fun WelcomeHeader(onSearchClick: () -> Unit, displayName: String = "") {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..17 -> "İyi günler"
        in 18..22 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
    val greetingText = if (displayName.isNotBlank()) "$greeting, $displayName" else "$greeting!"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HomeGradientStart,
                        CoinLabGreen.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animated Flask icon
                AnimatedFlaskIcon(modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CoinLabGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = LocalDate.now().format(
                            DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("tr"))
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CoinLabGreen.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = CoinLabGreen
                )
            }
        }
    }
}

/**
 * Animated Erlenmeyer flask icon with rising bubbles (Canvas-based).
 */
@Composable
private fun AnimatedFlaskIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "flask")

    // Bubble animation phases (0f → 1f loop)
    val bubble1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "b1"
    )
    val bubble2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = LinearEasing), RepeatMode.Restart),
        label = "b2"
    )
    val bubble3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "b3"
    )
    val bubble4 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2700, easing = LinearEasing), RepeatMode.Restart),
        label = "b4"
    )
    val bubble5 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "b5"
    )

val purple = Color(0xFFF7931A)       // Bitcoin Gold (was purple)
        val teal = Color(0xFFFFB347)         // Warm Amber (was teal)
        val deepPurple = Color(0xFF2A1500)   // Dark burnt (was deep purple)
        val midPurple = Color(0xFFFF9800)    // Orange glow (was mid purple)
        val blue = Color(0xFFE8A317)         // Deep Gold (was blue)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Flask dimensions (relative)
        val neckLeft = w * 0.38f
        val neckRight = w * 0.62f
        val neckTop = h * 0.1f
        val neckBottom = h * 0.38f
        val baseLeft = w * 0.15f
        val baseRight = w * 0.85f
        val baseBottom = h * 0.88f
        val liquidTop = h * 0.52f

        // Flask body path
        val flaskPath = Path().apply {
            moveTo(neckLeft, neckTop)
            lineTo(neckLeft, neckBottom)
            lineTo(baseLeft, baseBottom - h * 0.04f)
            quadraticTo(baseLeft - w * 0.02f, baseBottom, baseLeft + w * 0.06f, baseBottom)
            lineTo(baseRight - w * 0.06f, baseBottom)
            quadraticTo(baseRight + w * 0.02f, baseBottom, baseRight, baseBottom - h * 0.04f)
            lineTo(neckRight, neckBottom)
            lineTo(neckRight, neckTop)
        }

        // Glass fill (frosted)
        drawPath(flaskPath, color = deepPurple.copy(alpha = 0.25f))

        // Liquid fill
        val liquidPath = Path().apply {
            val lLeft = neckLeft + (baseLeft - neckLeft) * ((liquidTop - neckBottom) / (baseBottom - neckBottom))
            val lRight = neckRight + (baseRight - neckRight) * ((liquidTop - neckBottom) / (baseBottom - neckBottom))
            moveTo(lLeft, liquidTop)
            // Wavy top
            cubicTo(lLeft + w * 0.1f, liquidTop - h * 0.02f, lRight - w * 0.1f, liquidTop + h * 0.02f, lRight, liquidTop)
            lineTo(baseRight, baseBottom - h * 0.04f)
            quadraticTo(baseRight + w * 0.02f, baseBottom, baseRight - w * 0.06f, baseBottom)
            lineTo(baseLeft + w * 0.06f, baseBottom)
            quadraticTo(baseLeft - w * 0.02f, baseBottom, baseLeft, baseBottom - h * 0.04f)
            close()
        }
        drawPath(liquidPath, color = deepPurple.copy(alpha = 0.85f))

        // Teal glow at bottom
        val tealPath = Path().apply {
            val tealTop = h * 0.75f
            val tLeft = baseLeft + (neckLeft - baseLeft) * 0.15f
            val tRight = baseRight - (baseRight - neckRight) * 0.15f
            moveTo(tLeft, tealTop)
            cubicTo(tLeft + w * 0.15f, tealTop - h * 0.02f, tRight - w * 0.15f, tealTop + h * 0.02f, tRight, tealTop)
            lineTo(baseRight, baseBottom - h * 0.04f)
            quadraticTo(baseRight + w * 0.02f, baseBottom, baseRight - w * 0.06f, baseBottom)
            lineTo(baseLeft + w * 0.06f, baseBottom)
            quadraticTo(baseLeft - w * 0.02f, baseBottom, baseLeft, baseBottom - h * 0.04f)
            close()
        }
        drawPath(tealPath, color = teal.copy(alpha = 0.4f))

        // Flask outline
        drawPath(flaskPath, color = purple, style = Stroke(width = w * 0.04f))

        // Neck rim
        drawLine(
            color = blue,
            start = Offset(neckLeft - w * 0.03f, neckTop),
            end = Offset(neckRight + w * 0.03f, neckTop),
            strokeWidth = w * 0.05f
        )

        // Glass reflection (left highlight)
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(neckLeft + w * 0.02f, neckTop + h * 0.04f),
            end = Offset(neckLeft + w * 0.02f, neckBottom - h * 0.02f),
            strokeWidth = w * 0.02f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(neckLeft + (baseLeft - neckLeft) * 0.3f, neckBottom + h * 0.04f),
            end = Offset(baseLeft + w * 0.08f, baseBottom - h * 0.12f),
            strokeWidth = w * 0.02f
        )

        // --- Rising bubbles ---
        fun drawBubble(phase: Float, startX: Float, radius: Float, color: Color) {
            val travel = liquidTop - h * 0.02f  // from liquid bottom to above liquid top
            val yStart = baseBottom - h * 0.12f
            val y = yStart - (yStart - travel) * phase
            val x = startX + kotlin.math.sin(phase * 6.28f) * w * 0.04f
            val alpha = if (phase < 0.1f) phase * 10f else if (phase > 0.85f) (1f - phase) * 6.67f else 1f
            drawCircle(color = color.copy(alpha = 0.6f * alpha), radius = radius, center = Offset(x, y))
            // Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.35f * alpha),
                radius = radius * 0.35f,
                center = Offset(x - radius * 0.25f, y - radius * 0.3f)
            )
        }

        drawBubble(bubble1, w * 0.42f, w * 0.045f, purple)
        drawBubble(bubble2, w * 0.55f, w * 0.035f, teal)
        drawBubble(bubble3, w * 0.48f, w * 0.03f, midPurple)
        drawBubble(bubble4, w * 0.60f, w * 0.025f, blue)
        drawBubble(bubble5, w * 0.38f, w * 0.028f, teal)
    }
}

@Composable
private fun MarketSummaryCard(
    fearGreedValue: Int,
    fearGreedLabel: String,
    fearGreedHistory: List<FearGreedDataItem>,
    totalMarketCap: Double,
    totalVolume24h: Double,
    btcDominance: Double,
    ethDominance: Double,
    marketCapChangePercent24h: Double,
    activeCryptos: Int,
    currency: String
) {
    var showFearGreedHistory by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.market_summary),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (marketCapChangePercent24h != 0.0) {
                    val changeColor = if (marketCapChangePercent24h >= 0) SparklineGreen else CoinLabRed
                    Text(
                        text = "%+.2f%% 24h".format(marketCapChangePercent24h),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Top row: Fear&Greed + Market Cap + BTC Dom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Fear & Greed — clickable to show history
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showFearGreedHistory = !showFearGreedHistory }
                ) {
                    val gaugeColor = when {
                        fearGreedValue <= 25 -> CoinLabRed
                        fearGreedValue <= 45 -> Color(0xFFFF9800)
                        fearGreedValue <= 55 -> CoinLabGold
                        fearGreedValue <= 75 -> CoinLabAqua
                        else -> CoinLabGreen
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(gaugeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$fearGreedValue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = gaugeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fearGreedLabel.ifEmpty { "Korku/Açgözlülük" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // Total Market Cap
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CoinLabAqua.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = CoinLabAqua,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatMarketCapDouble(totalMarketCap, currency),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
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

                // BTC Dominance
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(CoinLabGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u20BF",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoinLabGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.1f%%".format(btcDominance),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
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
            }

            // Bottom row: Volume + ETH Dom + Active Cryptos
            if (totalVolume24h > 0 || activeCryptos > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (totalVolume24h > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatMarketCapDouble(totalVolume24h, currency),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "24h Hacim",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }
                    if (ethDominance > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%.1f%%".format(ethDominance),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ETH Hakimiyeti",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }
                    if (activeCryptos > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%,d".format(activeCryptos),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Aktif Kripto",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Fear & Greed History — expandable section
            AnimatedVisibility(
                visible = showFearGreedHistory && fearGreedHistory.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.fear_greed_history),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.last_30_days),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Mini sparkline-style chart of fear & greed values
                    val historyValues = fearGreedHistory.reversed().mapNotNull { it.value?.toDoubleOrNull() }
                    if (historyValues.size >= 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val maxVal = 100f
                                val minVal = 0f
                                val stepX = w / (historyValues.size - 1).coerceAtLeast(1)

                                val path = androidx.compose.ui.graphics.Path()
                                for (i in historyValues.indices) {
                                    val x = i * stepX
                                    val y = h - ((historyValues[i].toFloat() - minVal) / (maxVal - minVal) * h)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }

                                // Gradient fill
                                val fillPath = androidx.compose.ui.graphics.Path().apply {
                                    addPath(path)
                                    lineTo((historyValues.size - 1) * stepX, h)
                                    lineTo(0f, h)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFFF9800).copy(alpha = 0.3f),
                                            Color(0xFFFF9800).copy(alpha = 0.05f)
                                        )
                                    )
                                )
                                drawPath(
                                    path = path,
                                    color = Color(0xFFFF9800),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // History list (last 7 days)
                    fearGreedHistory.take(7).forEach { item ->
                        val value = item.value?.toIntOrNull() ?: 0
                        val classification = item.value_classification ?: ""
                        val timestamp = item.timestamp?.toLongOrNull()
                        val dateStr = if (timestamp != null) {
                            val date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                            date.format(DateTimeFormatter.ofPattern("dd MMM", Locale("tr")))
                        } else ""
                        val itemColor = when {
                            value <= 25 -> CoinLabRed
                            value <= 45 -> Color(0xFFFF9800)
                            value <= 55 -> CoinLabGold
                            value <= 75 -> CoinLabAqua
                            else -> CoinLabGreen
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(itemColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$value",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = itemColor,
                                        fontSize = 8.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = classification,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = itemColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    onWeb3Click: () -> Unit,
    onCommunityClick: () -> Unit,
    onAirdropClick: () -> Unit,
    onComparisonClick: () -> Unit,
    onStakingClick: () -> Unit,
    onPredictionClick: () -> Unit = {},
    onTradingClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = stringResource(R.string.web3_defi),
                subtitle = stringResource(R.string.web3_subtitle),
                icon = Icons.Filled.Token,
                gradientColors = listOf(CardGradient1Start, CardGradient1End),
                onClick = onWeb3Click,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = stringResource(R.string.community),
                subtitle = stringResource(R.string.community_subtitle),
                icon = Icons.Filled.Forum,
                gradientColors = listOf(CardGradient2Start, CardGradient2End),
                onClick = onCommunityClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = "Tahmin Oyunu",
                subtitle = "Fiyat Tahmini",
                icon = Icons.Filled.Casino,
                gradientColors = listOf(Color(0xFFE8A317), Color(0xFFF7931A)),
                onClick = onPredictionClick,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = "Sosyal Trading",
                subtitle = "Sinyaller",
                icon = Icons.Filled.Insights,
                gradientColors = listOf(Color(0xFF2A1500), Color(0xFFFF6B35)),
                onClick = onTradingClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = stringResource(R.string.airdrop_calendar),
                subtitle = stringResource(R.string.airdrop_subtitle),
                icon = Icons.Filled.CardGiftcard,
                gradientColors = listOf(CardGradient3Start, CardGradient3End),
                onClick = onAirdropClick,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = stringResource(R.string.compare_coins),
                subtitle = stringResource(R.string.comparison_subtitle),
                icon = Icons.Filled.CompareArrows,
                gradientColors = listOf(CardGradient4Start, CardGradient4End),
                onClick = onComparisonClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                title = stringResource(R.string.staking),
                subtitle = stringResource(R.string.staking_subtitle),
                icon = Icons.Filled.Layers,
                gradientColors = listOf(CardGradient5Start, CardGradient5End),
                onClick = onStakingClick,
                modifier = Modifier.weight(1f)
            )
            QuickAccessCard(
                title = "Teknik Analiz",
                subtitle = "RSI & G\u00f6stergeler",
                icon = Icons.Filled.QueryStats,
                gradientColors = listOf(Color(0xFF1A237E), Color(0xFF3949AB)),
                onClick = onTradingClick,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            gradientColors.first().copy(alpha = 0.25f),
                            gradientColors.last().copy(alpha = 0.10f)
                        )
                    )
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(gradientColors.map { it.copy(alpha = 0.5f) })
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CoinLabGold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatMarketCap(value: Long, currency: String): String {
    return formatMarketCapDouble(value.toDouble(), currency)
}

private fun formatMarketCapDouble(value: Double, currency: String): String {
    val symbol = when (currency.uppercase()) {
        "TRY" -> "\u20BA"
        "USD" -> "$"
        "EUR" -> "\u20AC"
        "GBP" -> "\u00A3"
        else -> currency
    }
    return when {
        value >= 1_000_000_000_000.0 -> "$symbol%.1fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000.0 -> "$symbol%.1fB".format(value / 1_000_000_000.0)
        value >= 1_000_000.0 -> "$symbol%.1fM".format(value / 1_000_000.0)
        value >= 1000.0 -> "$symbol%.0f".format(value)
        else -> "$symbol%.2f".format(value)
    }
}

@Composable
private fun TopMoverChip(
    name: String,
    symbol: String,
    change: Double,
    imageUrl: String,
    onClick: () -> Unit
) {
    val isPositive = change >= 0
    val changeColor = if (isPositive) SparklineGreen else CoinLabRed
    val bgColor = changeColor.copy(alpha = 0.08f)

    ElevatedCard(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .background(bgColor)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = symbol,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "%+.2f%%".format(change),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = changeColor
            )
        }
    }
}

@Composable
private fun TrendingCoinChip(
    coin: TrendingCoinDto,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = coin.large ?: coin.thumb,
                contentDescription = coin.name,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = coin.symbol.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "#${coin.marketCapRank ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
