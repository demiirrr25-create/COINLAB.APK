package com.coinlab.app.ui.community

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("tr", "TR")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.community),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == CommunityTab.FEED) {
                FloatingActionButton(
                    onClick = viewModel::showCreatePost,
                    containerColor = CoinLabGreen,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.new_post))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = CommunityTab.entries.indexOf(uiState.selectedTab),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = CoinLabGreen,
                edgePadding = 8.dp
            ) {
                CommunityTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                tab.title,
                                color = if (uiState.selectedTab == tab) CoinLabGreen
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        icon = {
                            Icon(
                                when (tab) {
                                    CommunityTab.CHANNELS -> Icons.Default.Groups
                                    CommunityTab.FEED -> Icons.Default.Forum
                                    CommunityTab.SIGNALS -> Icons.AutoMirrored.Filled.TrendingUp
                                    CommunityTab.LEADERBOARD -> Icons.Default.EmojiEvents
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (uiState.selectedTab == tab) CoinLabGreen
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            when (uiState.selectedTab) {
                CommunityTab.CHANNELS -> ChannelsContent(
                    channels = uiState.channels,
                    onJoin = viewModel::joinChannel,
                    onSelect = { channel ->
                        viewModel.selectChannel(channel)
                        viewModel.selectTab(CommunityTab.FEED)
                    }
                )
                CommunityTab.FEED -> FeedContent(
                    posts = if (uiState.selectedChannel != null) {
                        uiState.feedPosts.filter { it.channelId == uiState.selectedChannel?.id }
                    } else uiState.feedPosts,
                    onLike = viewModel::toggleLike,
                    selectedChannel = uiState.selectedChannel,
                    onClearChannel = { viewModel.selectChannel(null) }
                )
                CommunityTab.SIGNALS -> SignalsContent(
                    signals = uiState.signals,
                    currencyFormat = currencyFormat,
                    isLoading = uiState.isSignalsLoading
                )
                CommunityTab.LEADERBOARD -> LeaderboardContent(
                    entries = uiState.leaderboard,
                    currencyFormat = currencyFormat
                )
            }
        }

        // Create Post Dialog
        if (uiState.showCreatePost) {
            CreatePostDialog(
                onDismiss = viewModel::hideCreatePost,
                onPost = { content, coin, sentiment ->
                    viewModel.createPost(content, coin, sentiment)
                }
            )
        }
    }
}

@Composable
private fun ChannelsContent(
    channels: List<CommunityChannel>,
    onJoin: (String) -> Unit,
    onSelect: (CommunityChannel) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Topluluk Kanalları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(channels, key = { it.id }) { channel ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(channel) },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CoinLabGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.icon,
                            fontSize = 22.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = channel.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${channel.memberCount} üye",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (channel.isJoined) {
                        OutlinedButton(
                            onClick = { onJoin(channel.id) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Ayrıl", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            onClick = { onJoin(channel.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Katıl", style = MaterialTheme.typography.labelSmall, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedContent(
    posts: List<FeedPost>,
    onLike: (String) -> Unit,
    selectedChannel: CommunityChannel? = null,
    onClearChannel: () -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedChannel != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedChannel.icon} ${selectedChannel.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearChannel) {
                        Text("Tümü", color = CoinLabGreen)
                    }
                }
            }
        }
        items(posts, key = { it.id }) { post ->
            FeedPostCard(post = post, onLike = { onLike(post.id) })
        }
        if (posts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz gönderi yok",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPostCard(
    post: FeedPost,
    onLike: () -> Unit
) {
    val context = LocalContext.current
    val likeColor by animateColorAsState(
        targetValue = if (post.isLiked) CoinLabRed else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "likeColor"
    )
    val timeAgo = remember(post.timestamp) { getTimeAgo(post.timestamp) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CoinLabGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.author.take(2).uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoinLabGreen
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.author,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (post.authorBadge != null) {
                                Spacer(Modifier.width(6.dp))
                                Badge(
                                    containerColor = when (post.authorBadge) {
                                        "Pro" -> CoinLabGreen
                                        "Whale" -> CoinLabAqua
                                        else -> CoinLabPurple
                                    }
                                ) {
                                    Text(
                                        text = post.authorBadge,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        fontSize = 10.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (post.sentiment != null) {
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                "${post.sentiment.emoji} ${post.sentiment.label}",
                                fontSize = 12.sp
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (post.sentiment) {
                                Sentiment.BULLISH -> CoinLabGreen.copy(alpha = 0.1f)
                                Sentiment.BEARISH -> CoinLabRed.copy(alpha = 0.1f)
                                Sentiment.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Content
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )

            // Coin tag
            if (post.coinTag != null) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { },
                    label = { Text("$$${post.coinTag}") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = CoinLabGreen.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.like),
                            modifier = Modifier.size(18.dp),
                            tint = likeColor
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${post.likes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = likeColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${post.comments}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val coinInfo = if (post.coinTag != null) " #${post.coinTag}" else ""
                        val shareText = "${post.author}: ${post.content}$coinInfo\n\n— CoinLab ile paylaşıldı 🧪"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Paylaş"))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.share),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalsContent(
    signals: List<TradeSignal>,
    currencyFormat: NumberFormat,
    isLoading: Boolean = false
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = CoinLabGreen.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = CoinLabGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sinyaller RSI analizi ile API'den üretilir. Yatırım tavsiyesi değildir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CoinLabGreen, modifier = Modifier.size(24.dp))
                }
            }
        }

        items(signals, key = { it.id }) { signal ->
            SignalCard(signal = signal, currencyFormat = currencyFormat)
        }
    }
}

@Composable
private fun SignalCard(
    signal: TradeSignal,
    currencyFormat: NumberFormat
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = signal.direction.emoji,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${signal.coin} ${signal.direction.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "by ${signal.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (signal.signalSource.isNotEmpty()) {
                            Text(
                                text = signal.signalSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = CoinLabAqua,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (signal.rsiValue != null) {
                        Badge(
                            containerColor = when {
                                signal.rsiValue < 30 -> CoinLabGreen.copy(alpha = 0.2f)
                                signal.rsiValue > 70 -> CoinLabRed.copy(alpha = 0.2f)
                                else -> CoinLabAqua.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = "RSI ${String.format("%.1f", signal.rsiValue)}",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    signal.rsiValue < 30 -> CoinLabGreen
                                    signal.rsiValue > 70 -> CoinLabRed
                                    else -> CoinLabAqua
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    if (signal.leverage != null) {
                        Badge(
                            containerColor = CoinLabGreen
                        ) {
                            Text(
                                text = "${signal.leverage}x",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                        }
                    }
                    if (signal.pnlPercent != null) {
                        Text(
                            text = "${if (signal.pnlPercent >= 0) "+" else ""}${String.format("%.1f", signal.pnlPercent)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (signal.pnlPercent >= 0) CoinLabGreen else CoinLabRed
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Price levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PriceLevel("Giriş", currencyFormat.format(signal.entryPrice), CoinLabAqua)
                PriceLevel("Hedef", currencyFormat.format(signal.targetPrice), CoinLabGreen)
                PriceLevel("Stop", currencyFormat.format(signal.stopLoss), CoinLabRed)
            }

            Spacer(Modifier.height(12.dp))

            // Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Güven: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    repeat(5) { index ->
                        Icon(
                            if (index < signal.confidence) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (index < signal.confidence) CoinLabGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val timeAgo = remember(signal.timestamp) { getTimeAgo(signal.timestamp) }
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceLevel(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LeaderboardContent(
    entries: List<LeaderboardEntry>,
    currencyFormat: NumberFormat
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top 3 Podium
        item {
            if (entries.size >= 3) {
                TopThreePodium(entries.take(3), currencyFormat)
            }
        }

        // Rest of leaderboard
        itemsIndexed(entries.drop(3)) { _, entry ->
            LeaderboardItem(entry = entry, currencyFormat = currencyFormat)
        }
    }
}

@Composable
private fun TopThreePodium(
    top3: List<LeaderboardEntry>,
    currencyFormat: NumberFormat
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CoinLabGreen.copy(alpha = 0.05f),
                            CoinLabAqua.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd place
                if (top3.size > 1) {
                    PodiumItem(entry = top3[1], medal = "🥈", height = 100, currencyFormat = currencyFormat)
                }
                // 1st place
                PodiumItem(entry = top3[0], medal = "🥇", height = 130, currencyFormat = currencyFormat)
                // 3rd place
                if (top3.size > 2) {
                    PodiumItem(entry = top3[2], medal = "🥉", height = 80, currencyFormat = currencyFormat)
                }
            }
        }
    }
}

@Composable
private fun PodiumItem(
    entry: LeaderboardEntry,
    medal: String,
    height: Int,
    currencyFormat: NumberFormat
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(text = medal, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CoinLabGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.username.take(2).uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.username,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "%${String.format("%.0f", entry.winRate)} Win",
            style = MaterialTheme.typography.bodySmall,
            color = CoinLabGreen,
            fontSize = 11.sp
        )
        Text(
            text = "+${currencyFormat.format(entry.totalPnl)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = CoinLabGreen,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LeaderboardItem(
    entry: LeaderboardEntry,
    currencyFormat: NumberFormat
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                text = "#${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )

            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CoinLabAqua.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.username.take(2).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = CoinLabAqua
                )
            }

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.badge != null) {
                        Spacer(Modifier.width(4.dp))
                        Badge(
                            containerColor = when (entry.badge) {
                                "Pro" -> CoinLabGreen
                                "Whale" -> CoinLabAqua
                                else -> CoinLabPurple
                            }
                        ) {
                            Text(entry.badge, fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
                Text(
                    text = "${entry.totalTrades} işlem • ${entry.followers} takipçi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // PnL
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${currencyFormat.format(entry.totalPnl)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = CoinLabGreen
                )
                Text(
                    text = "%${String.format("%.1f", entry.winRate)} Win",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPost: (String, String?, Sentiment?) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var coinTag by remember { mutableStateOf("") }
    var selectedSentiment by remember { mutableStateOf<Sentiment?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.new_post),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Ne düşünüyorsun?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        cursorColor = CoinLabGreen
                    )
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = coinTag,
                    onValueChange = { coinTag = it.uppercase() },
                    label = { Text("Coin Etiketi (opsiyonel)") },
                    placeholder = { Text("BTC, ETH...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        cursorColor = CoinLabGreen
                    )
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Görüş:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sentiment.entries.forEach { sentiment ->
                        FilterChip(
                            selected = selectedSentiment == sentiment,
                            onClick = {
                                selectedSentiment = if (selectedSentiment == sentiment) null else sentiment
                            },
                            label = { Text("${sentiment.emoji} ${sentiment.label}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoinLabGreen.copy(alpha = 0.2f),
                                selectedLabelColor = CoinLabGreen
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPost(
                        content,
                        coinTag.ifBlank { null },
                        selectedSentiment
                    )
                },
                enabled = content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoinLabGreen,
                    contentColor = Color.Black
                )
            ) {
                Text("Paylaş", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "${minutes}dk önce"
        hours < 24 -> "${hours}sa önce"
        days < 7 -> "${days}g önce"
        else -> "${days / 7}h önce"
    }
}
