package com.coinlab.app.ui.prediction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionGameScreen(
    onBack: () -> Unit,
    viewModel: PredictionGameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Tahmin Yap", "Liderlik Tablosu")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Casino,
                            contentDescription = null,
                            tint = CoinLabGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fiyat Tahmin Oyunu", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tabs
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = DarkSurface,
                contentColor = CoinLabGreen,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        color = CoinLabGreen
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { viewModel.setTab(index) },
                        text = {
                            Text(
                                title,
                                color = if (uiState.selectedTab == index) CoinLabGreen else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    )
                }
            }

            when (uiState.selectedTab) {
                0 -> PredictionTab(uiState, viewModel)
                1 -> LeaderboardTab(uiState)
            }
        }
    }
}

@Composable
private fun PredictionTab(
    uiState: PredictionGameUiState,
    viewModel: PredictionGameViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val round = uiState.activeRound
            if (round != null) {
                // Active Round Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(CardGradient1Start.copy(alpha = 0.3f), CardGradient1End.copy(alpha = 0.15f)))
                            )
                            .padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${round.coinName} (${round.coinSymbol})",
                                color = CoinLabGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Başlangıç: $${String.format("%.2f", round.startPrice)}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            // Countdown
                            val minutes = (uiState.timeRemaining / 60000).toInt()
                            val seconds = ((uiState.timeRemaining % 60000) / 1000).toInt()
                            Text(
                                text = if (uiState.timeRemaining > 0) {
                                    String.format("⏱ %02d:%02d", minutes, seconds)
                                } else "⏱ Süre doldu!",
                                color = if (uiState.timeRemaining > 0) CoinLabGold else CoinLabRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )

                            Spacer(Modifier.height(16.dp))

                            // Predictions count
                            val upCount = uiState.predictions.count { it.prediction == "UP" }
                            val downCount = uiState.predictions.count { it.prediction == "DOWN" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⬆️ Yükselir", color = SparklineGreen, fontWeight = FontWeight.Bold)
                                    Text("$upCount kişi", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⬇️ Düşer", color = CoinLabRed, fontWeight = FontWeight.Bold)
                                    Text("$downCount kişi", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            // Prediction buttons
                            if (uiState.userPrediction == null && uiState.timeRemaining > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.makePrediction("UP") },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SparklineGreen
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Filled.TrendingUp, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Yükselir", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.makePrediction("DOWN") },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CoinLabRed
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Filled.TrendingDown, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Düşer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            } else if (uiState.userPrediction != null) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.userPrediction == "UP")
                                            SparklineGreen.copy(alpha = 0.2f) else CoinLabRed.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Tahmininiz: ${if (uiState.userPrediction == "UP") "⬆️ Yükselir" else "⬇️ Düşer"}",
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // No active round
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Casino,
                            contentDescription = null,
                            tint = CoinLabGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Aktif oyun yok",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Yeni bir tahmin turu başlatın!",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.createNewRound() },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Yeni Tur Başlat", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Recent predictions
        if (uiState.predictions.isNotEmpty()) {
            item {
                Text(
                    "Son Tahminler",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            items(uiState.predictions.takeLast(10).reversed()) { pred ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (pred.prediction == "UP") "⬆️" else "⬇️",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = pred.userName,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        pred.isCorrect?.let { correct ->
                            Text(
                                text = if (correct) "✅ Doğru" else "❌ Yanlış",
                                color = if (correct) SparklineGreen else CoinLabRed,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTab(uiState: PredictionGameUiState) {
    if (uiState.leaderboard.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = CoinLabGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Henüz skor yok",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(uiState.leaderboard) { index, score ->
                val medal = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "#${index + 1}"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (index < 3) CoinLabGreen.copy(alpha = 0.05f + (0.05f * (3 - index)))
                        else DarkSurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = medal,
                            fontSize = if (index < 3) 28.sp else 16.sp,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = score.userName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${score.correctCount}/${score.totalCount} doğru · %${String.format("%.0f", score.accuracy)} isabetlilik",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${score.totalScore}",
                                color = CoinLabGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            if (score.streak > 0) {
                                Text(
                                    text = "🔥 ${score.streak} seri",
                                    color = CoinLabNeon,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
