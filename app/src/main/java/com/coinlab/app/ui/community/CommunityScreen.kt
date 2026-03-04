package com.coinlab.app.ui.community

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.coinlab.app.ui.theme.*
import java.io.File
import java.io.FileOutputStream

// ════════════════════════════════════════════════════════════════════════════
//  v8.2.2 — COMMUNITY SCREEN — Full Reform
//  Features: Firestore posts, comments, like, delete, edit, report,
//  branded share card, image upload, @mention, channel filter, signals, leaderboard
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = CommunityTab.entries
    val snackbarHostState = remember { SnackbarHostState() }

    // v8.8 — Show success message when post is created
    LaunchedEffect(uiState.postSuccess) {
        if (uiState.postSuccess) {
            snackbarHostState.showSnackbar(
                message = "\u2705 Payla\u015f\u0131m\u0131n\u0131z ba\u015far\u0131yla payla\u015f\u0131ld\u0131! T\u00fcm kullan\u0131c\u0131lar g\u00f6rebilir.",
                withDismissAction = true
            )
            viewModel.clearPostSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83E\uDDEA", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Topluluk",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedTab == CommunityTab.FEED) {
                FloatingActionButton(
                    onClick = { viewModel.showCreatePost() },
                    containerColor = CoinLabGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Yeni Gönderi")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Error banner
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFB71C1C).copy(alpha = 0.9f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(uiState.selectedTab),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CoinLabGreen,
                indicator = { tabPositions ->
                    val index = tabs.indexOf(uiState.selectedTab)
                    if (index in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[index]),
                            color = CoinLabGreen
                        )
                    }
                }
            ) {
                tabs.forEach { tab ->
                    val icon = when (tab) {
                        CommunityTab.CHANNELS -> Icons.Default.Groups
                        CommunityTab.FEED -> Icons.Default.Forum
                        CommunityTab.SIGNALS -> Icons.AutoMirrored.Filled.TrendingUp
                        CommunityTab.LEADERBOARD -> Icons.Default.EmojiEvents
                    }
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                tab.title,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(icon, contentDescription = tab.title, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            // Tab content
            when (uiState.selectedTab) {
                CommunityTab.CHANNELS -> ChannelsContent(uiState, viewModel)
                CommunityTab.FEED -> FeedContent(uiState, viewModel)
                CommunityTab.SIGNALS -> SignalsContent(uiState)
                CommunityTab.LEADERBOARD -> LeaderboardContent(uiState)
            }
        }
    }

    // ── Dialogs ──
    if (uiState.showCreatePost) {
        CreatePostSheet(uiState, viewModel)
    }
    if (uiState.editingPostId != null) {
        EditPostDialog(uiState, viewModel)
    }
    if (uiState.showReportDialog != null) {
        ReportPostDialog(viewModel)
    }
    if (uiState.showShareDialog != null) {
        ShareDialog(uiState, viewModel)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CHANNELS TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChannelsContent(uiState: CommunityUiState, viewModel: CommunityViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Kanallar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "\u0130lgi alan\u0131na g\u00f6re kanallara kat\u0131l ve tart\u0131\u015fmalara dahil ol",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(uiState.channels, key = { it.id }) { channel ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectChannel(channel)
                            viewModel.selectTab(CommunityTab.FEED)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CoinLabGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(channel.icon, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            channel.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${channel.memberCount} \u00fcye", style = MaterialTheme.typography.labelSmall, color = CoinLabAqua)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.joinChannel(channel.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (channel.isJoined) MaterialTheme.colorScheme.surfaceVariant else CoinLabGreen
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (channel.isJoined) "Ayr\u0131l" else "Kat\u0131l", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FEED TAB
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedContent(uiState: CommunityUiState, viewModel: CommunityViewModel) {
    val filteredPosts = if (uiState.selectedChannel != null) {
        uiState.feedPosts.filter { it.channelId == uiState.selectedChannel.id }
    } else {
        uiState.feedPosts
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Channel filter chips
        if (uiState.channels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedChannel == null,
                    onClick = { viewModel.selectChannel(null) },
                    label = { Text("T\u00fcm\u00fc", fontSize = 12.sp) }
                )
                uiState.channels.forEach { channel ->
                    FilterChip(
                        selected = uiState.selectedChannel?.id == channel.id,
                        onClick = { viewModel.selectChannel(channel) },
                        label = { Text("${channel.icon} ${channel.name}", fontSize = 12.sp) }
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CoinLabGreen)
            }
        } else if (filteredPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCED", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Hen\u00fcz g\u00f6nderi yok",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "\u0130lk g\u00f6nderiyi sen payla\u015f!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    FeedPostCard(post, uiState, viewModel)
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FEED POST CARD — Like, Comment, Share, Edit, Delete, Report
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeedPostCard(
    post: FeedPost,
    uiState: CommunityUiState,
    viewModel: CommunityViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val isExpanded = uiState.expandedCommentPostId == post.id
    val comments = uiState.comments[post.id] ?: emptyList()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CoinLabGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!post.authorAvatar.isNullOrEmpty() && post.authorAvatar.startsWith("http")) {
                        AsyncImage(
                            model = post.authorAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Letter avatar fallback
                        Text(
                            text = post.author.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoinLabGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.author,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!post.authorBadge.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = CoinLabPurple) {
                                Text(post.authorBadge, fontSize = 9.sp, color = Color.White)
                            }
                        }
                        if (post.isEdited) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "(d\u00fczenlendi)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        getTimeAgo(post.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 3-dot menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Men\u00fc",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (post.isOwnPost) {
                            DropdownMenuItem(
                                text = { Text("\u270F\uFE0F D\u00fczenle") },
                                onClick = {
                                    showMenu = false
                                    viewModel.startEditPost(post.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDDD1\uFE0F Sil", color = CoinLabRed) },
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePost(post.id)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("\uD83D\uDEA9 Raporla") },
                                onClick = {
                                    showMenu = false
                                    viewModel.showReportDialog(post.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("\uD83D\uDCE4 Payla\u015f") },
                            onClick = {
                                showMenu = false
                                viewModel.showShareDialog(post.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Sentiment badge ──
            if (post.sentiment != null) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            "${post.sentiment.emoji} ${post.sentiment.label}",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // ── Content ──
            Text(
                post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // ── Coin tag ──
            if (!post.coinTag.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\$${post.coinTag}",
                    fontWeight = FontWeight.Bold,
                    color = CoinLabGold,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ── Image ──
            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (post.imageUrl.startsWith("data:image/")) {
                    // Base64 encoded image
                    val base64Data = post.imageUrl.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
                    val bitmap = remember(post.imageUrl) {
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Gönderi resmi",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .aspectRatio(16f / 9f),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Gönderi resmi",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .aspectRatio(16f / 9f),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // ── Action Row: Like, Comment, Share ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like
                val likeScale by animateFloatAsState(
                    targetValue = if (post.isLiked) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "like"
                )
                val likeColor by animateColorAsState(
                    targetValue = if (post.isLiked) CoinLabRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "likeColor"
                )
                Row(
                    modifier = Modifier.clickable { viewModel.toggleLike(post.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Be\u011fen",
                        tint = likeColor,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(likeScale)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${post.likes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = likeColor
                    )
                }

                // Comment
                Row(
                    modifier = Modifier.clickable { viewModel.toggleComments(post.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Comment,
                        contentDescription = "Yorum",
                        modifier = Modifier.size(20.dp),
                        tint = if (isExpanded) CoinLabAqua else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${post.comments}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isExpanded) CoinLabAqua else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Share
                Row(
                    modifier = Modifier.clickable { viewModel.showShareDialog(post.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Payla\u015f",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Payla\u015f",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Comments Section (expandable) ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CommentSection(post.id, comments, uiState, viewModel)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  COMMENT SECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CommentSection(
    postId: String,
    comments: List<CommentItem>,
    uiState: CommunityUiState,
    viewModel: CommunityViewModel
) {
    var commentText by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        if (comments.isEmpty()) {
            Text(
                "Hen\u00fcz yorum yok. \u0130lk yorumu sen yap!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            comments.forEach { comment ->
                CommentItemRow(comment, viewModel, postId)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Comment input
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("Yorum yaz...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoinLabGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(postId, commentText)
                            commentText = ""
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commentText.isNotBlank()) {
                        viewModel.addComment(postId, commentText)
                        commentText = ""
                    }
                },
                enabled = commentText.isNotBlank() && !uiState.isCommenting
            ) {
                if (uiState.isCommenting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "G\u00f6nder",
                        tint = if (commentText.isNotBlank()) CoinLabGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: CommentItem,
    viewModel: CommunityViewModel,
    postId: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CoinLabPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (comment.authorAvatar.startsWith("http")) {
                AsyncImage(
                    model = comment.authorAvatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = comment.authorName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoinLabPurple
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    getTimeAgo(comment.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                comment.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (comment.isOwnComment) {
            IconButton(
                onClick = { viewModel.deleteComment(postId, comment.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sil",
                    modifier = Modifier.size(14.dp),
                    tint = CoinLabRed.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CREATE POST BOTTOM SHEET
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostSheet(
    uiState: CommunityUiState,
    viewModel: CommunityViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember { mutableStateOf("") }
    var coinTag by remember { mutableStateOf("") }
    var selectedSentiment by remember { mutableStateOf<Sentiment?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.selectImage(uri) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.hideCreatePost() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Yeni G\u00f6nderi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Content input
            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    val lastWord = it.split(" ").lastOrNull() ?: ""
                    if (lastWord.startsWith("@") && lastWord.length > 1) {
                        viewModel.searchMentions(lastWord.removePrefix("@"))
                    } else {
                        viewModel.clearMentionSuggestions()
                    }
                },
                placeholder = { Text("Ne d\u00fc\u015f\u00fcn\u00fcyorsun?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoinLabGreen)
            )

            // Mention suggestions
            if (uiState.mentionSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        uiState.mentionSuggestions.forEach { (_, name) ->
                            Text(
                                "@$name",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val words = content.split(" ").toMutableList()
                                        if (words.isNotEmpty()) words[words.lastIndex] = "@$name "
                                        content = words.joinToString(" ")
                                        viewModel.clearMentionSuggestions()
                                    }
                                    .padding(8.dp),
                                color = CoinLabGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Image picker row
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Resim Ekle")
                }

                if (uiState.selectedImageUri != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box {
                        AsyncImage(
                            model = uiState.selectedImageUri,
                            contentDescription = "Se\u00e7ilen resim",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { viewModel.selectImage(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kald\u0131r",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Coin tag
            OutlinedTextField(
                value = coinTag,
                onValueChange = { coinTag = it },
                placeholder = { Text("Coin etiketi (\u00f6r: BTC)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoinLabGold)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sentiment chips
            Text(
                "Duyarl\u0131l\u0131k",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Sentiment.entries.forEach { sentiment ->
                    FilterChip(
                        selected = selectedSentiment == sentiment,
                        onClick = {
                            selectedSentiment = if (selectedSentiment == sentiment) null else sentiment
                        },
                        label = {
                            Text(
                                "${sentiment.emoji} ${sentiment.label}",
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        viewModel.createPost(
                            content = content,
                            coinTag = coinTag.ifBlank { null },
                            sentiment = selectedSentiment,
                            imageUri = uiState.selectedImageUri
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = content.isNotBlank() && !uiState.isUploading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen)
            ) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Payla\u015f\u0131l\u0131yor...", color = Color.White)
                } else {
                    Text(
                        "\uD83E\uDDEA Payla\u015f",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  EDIT POST DIALOG
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EditPostDialog(
    uiState: CommunityUiState,
    viewModel: CommunityViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.cancelEditPost() },
        title = {
            Text("G\u00f6nderiyi D\u00fczenle", fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = uiState.editingContent,
                onValueChange = { viewModel.updateEditingContent(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CoinLabGreen)
            )
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveEditPost() },
                colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen)
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelEditPost() }) {
                Text("\u0130ptal")
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  REPORT DIALOG
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReportPostDialog(viewModel: CommunityViewModel) {
    var selectedReason by remember { mutableIntStateOf(-1) }
    val reasons = listOf(
        "Spam",
        "Uygunsuz \u0130\u00e7erik",
        "Yan\u0131lt\u0131c\u0131 Bilgi",
        "Nefret S\u00f6ylemi",
        "Di\u011fer"
    )

    AlertDialog(
        onDismissRequest = { viewModel.hideReportDialog() },
        title = {
            Text("G\u00f6nderiyi Raporla", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Raporlama sebebini se\u00e7in:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                reasons.forEachIndexed { index, reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(
                                    2.dp,
                                    if (selectedReason == index) CoinLabGreen
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                                .background(
                                    if (selectedReason == index) CoinLabGreen
                                    else Color.Transparent,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedReason >= 0) viewModel.reportPost(reasons[selectedReason])
                },
                enabled = selectedReason >= 0,
                colors = ButtonDefaults.buttonColors(containerColor = CoinLabRed)
            ) {
                Text("Raporla")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideReportDialog() }) {
                Text("\u0130ptal")
            }
        }
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  SHARE DIALOG — Branded Image + Text
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ShareDialog(
    uiState: CommunityUiState,
    viewModel: CommunityViewModel
) {
    val context = LocalContext.current
    val post = uiState.feedPosts.find { it.id == uiState.showShareDialog }

    if (post == null) {
        viewModel.hideShareDialog()
        return
    }

    AlertDialog(
        onDismissRequest = { viewModel.hideShareDialog() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83E\uDDEA", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Payla\u015f", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bu g\u00f6nderiyi nas\u0131l payla\u015fmak istiyorsun?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Branded image share
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            shareBrandedImage(context, post)
                            viewModel.hideShareDialog()
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = CoinLabPurple,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "G\u00f6rsel Kart",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "CoinLab temal\u0131 \u00f6zel g\u00f6rsel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Text share
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            shareAsText(context, post)
                            viewModel.hideShareDialog()
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TextSnippet,
                            contentDescription = null,
                            tint = CoinLabAqua,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Metin Payla\u015f\u0131m\u0131",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Zengin metin olarak payla\u015f",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.hideShareDialog() }) {
                Text("Kapat")
            }
        }
    )
}

private fun shareAsText(context: Context, post: FeedPost) {
    val coinInfo = if (!post.coinTag.isNullOrEmpty()) "\n\n\uD83D\uDCB0 \$${post.coinTag}" else ""
    val sentimentInfo = if (post.sentiment != null) " ${post.sentiment.emoji}" else ""
    val shareText =
        "${post.author}$sentimentInfo: ${post.content}$coinInfo\n\n\u2014 CoinLab ile payla\u015f\u0131ld\u0131 \uD83E\uDDEA\nhttps://coinlabtr.com"
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Payla\u015f"))
}

private fun shareBrandedImage(context: Context, post: FeedPost) {
    try {
        val bitmap = createBrandedBitmap(post)
        val file = File(context.cacheDir, "coinlab_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "CoinLab ile payla\u015f\u0131ld\u0131 \uD83E\uDDEA\nhttps://coinlabtr.com"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "G\u00f6rsel Payla\u015f"))
    } catch (_: Exception) {
        // Fallback to text share
        shareAsText(context, post)
    }
}

/**
 * Creates a branded CoinLab share card as a Bitmap.
 * Purple → Blue → Teal gradient background with post content.
 * 1080x1350 (Instagram story ratio).
 */
private fun createBrandedBitmap(post: FeedPost): Bitmap {
    val w = 1080
    val h = 1350
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Gradient background
    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                0xFF2A1A5E.toInt(),
                0xFF6C5CE7.toInt(),
                0xFF4A6CF7.toInt(),
                0xFF00BFA5.toInt()
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    // Card background overlay
    val cardPaint = Paint().apply {
        color = 0x33FFFFFF
        isAntiAlias = true
    }
    canvas.drawRoundRect(RectF(60f, 200f, w - 60f, h - 200f), 40f, 40f, cardPaint)

    // CoinLab logo
    val logoPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 72f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("\uD83E\uDDEA CoinLab", w / 2f, 140f, logoPaint)

    // Author
    val authorPaint = Paint().apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(post.author, w / 2f, 340f, authorPaint)

    // Sentiment
    val metaPaint = Paint().apply {
        color = 0x99FFFFFF.toInt()
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val sentimentText = post.sentiment?.let { "${it.emoji} ${it.label}" } ?: ""
    canvas.drawText(sentimentText, w / 2f, 400f, metaPaint)

    // Content with word wrap
    val contentPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 48f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val maxWidth = w - 160f
    val words = post.content.split(" ")
    var line = ""
    var y = 520f
    for (word in words) {
        val test = if (line.isEmpty()) word else "$line $word"
        if (contentPaint.measureText(test) > maxWidth) {
            canvas.drawText(line, w / 2f, y, contentPaint)
            line = word
            y += 60f
            if (y > h - 360f) {
                canvas.drawText("...", w / 2f, y, contentPaint)
                break
            }
        } else {
            line = test
        }
    }
    if (line.isNotEmpty() && y <= h - 360f) {
        canvas.drawText(line, w / 2f, y, contentPaint)
    }

    // Coin tag
    if (!post.coinTag.isNullOrEmpty()) {
        val tagPaint = Paint().apply {
            color = 0xFFF7931A.toInt()
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("\$${post.coinTag}", w / 2f, y + 80f, tagPaint)
    }

    // Footer line
    val linePaint = Paint().apply {
        color = 0xFF00BFA5.toInt()
        strokeWidth = 6f
    }
    canvas.drawLine(60f, h - 140f, w - 60f, h - 140f, linePaint)

    // Footer text
    val footerPaint = Paint().apply {
        color = 0x99FFFFFF.toInt()
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
        "CoinLab ile payla\u015f\u0131ld\u0131 \uD83E\uDDEA | coinlabtr.com",
        w / 2f,
        h - 80f,
        footerPaint
    )

    return bitmap
}

// ════════════════════════════════════════════════════════════════════════════
//  SIGNALS TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SignalsContent(uiState: CommunityUiState) {
    if (uiState.isSignalsLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = CoinLabGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Sinyaller hesaplan\u0131yor...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (uiState.signals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "\u015eu an aktif sinyal bulunamad\u0131",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "\uD83D\uDCCA Canl\u0131 Trade Sinyalleri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Binance API RSI analizi bazl\u0131 otomatik sinyaller",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(uiState.signals, key = { it.id }) { signal ->
                SignalCard(signal)
            }
        }
    }
}

@Composable
private fun SignalCard(signal: TradeSignal) {
    val dirColor = if (signal.direction == SignalDirection.LONG) CoinLabAqua else CoinLabRed

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(dirColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${signal.direction.emoji} ${signal.direction.label}",
                        fontWeight = FontWeight.Bold,
                        color = dirColor,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    signal.coin,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                // Confidence stars
                Row {
                    repeat(5) { i ->
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (i < signal.confidence) CoinLabGold
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // RSI info
            if (signal.rsiValue != null) {
                val rsiColor = when {
                    signal.rsiValue < 30 -> CoinLabAqua
                    signal.rsiValue > 70 -> CoinLabRed
                    else -> CoinLabGold
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RSI:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "%.1f".format(signal.rsiValue),
                        fontWeight = FontWeight.Bold,
                        color = rsiColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "(${signal.signalSource})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Price levels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceLevel("Giri\u015f", signal.entryPrice, MaterialTheme.colorScheme.onSurface)
                PriceLevel("Hedef", signal.targetPrice, CoinLabAqua)
                PriceLevel("Stop", signal.stopLoss, CoinLabRed)
            }
        }
    }
}

@Composable
private fun PriceLevel(label: String, price: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$%.2f".format(price),
            fontWeight = FontWeight.Bold,
            color = color,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  LEADERBOARD TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeaderboardContent(uiState: CommunityUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "\uD83C\uDFC6 Lider Tablosu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Top 3 podium
        if (uiState.leaderboard.size >= 3) {
            item { TopThreePodium(uiState.leaderboard.take(3)) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Remaining entries
        val remaining = if (uiState.leaderboard.size > 3) uiState.leaderboard.drop(3)
        else uiState.leaderboard
        items(remaining, key = { it.rank }) { entry ->
            LeaderboardItem(entry)
        }
    }
}

@Composable
private fun TopThreePodium(top3: List<LeaderboardEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        if (top3.size >= 2) PodiumItem(top3[1], height = 100, medal = "\uD83E\uDD48")
        if (top3.isNotEmpty()) PodiumItem(top3[0], height = 130, medal = "\uD83E\uDD47")
        if (top3.size >= 3) PodiumItem(top3[2], height = 80, medal = "\uD83E\uDD49")
    }
}

@Composable
private fun PodiumItem(entry: LeaderboardEntry, height: Int, medal: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(medal, fontSize = 28.sp)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(CoinLabPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(entry.avatar ?: "\uD83D\uDC64", fontSize = 22.sp)
        }
        Text(
            entry.username,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1
        )
        Text(
            "+$%.0f".format(entry.totalPnl),
            color = CoinLabAqua,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(height.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            CoinLabGreen.copy(alpha = 0.3f),
                            CoinLabGreen.copy(alpha = 0.05f)
                        )
                    ),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
        )
    }
}

@Composable
private fun LeaderboardItem(entry: LeaderboardEntry) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#${entry.rank}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CoinLabPurple.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.avatar ?: "\uD83D\uDC64", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.username,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!entry.badge.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(containerColor = CoinLabPurple) {
                            Text(entry.badge, fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
                Text(
                    "WR: %.1f%% | ${entry.totalTrades} trade".format(entry.winRate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "+$%.0f".format(entry.totalPnl),
                    fontWeight = FontWeight.Bold,
                    color = CoinLabAqua,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${entry.followers} takip\u00e7i",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  UTILITY
// ════════════════════════════════════════════════════════════════════════════

private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "az \u00f6nce"
        minutes < 60 -> "${minutes}dk \u00f6nce"
        hours < 24 -> "${hours}sa \u00f6nce"
        days < 7 -> "${days}g \u00f6nce"
        days < 30 -> "${days / 7}h \u00f6nce"
        else -> "${days / 30}ay \u00f6nce"
    }
}
