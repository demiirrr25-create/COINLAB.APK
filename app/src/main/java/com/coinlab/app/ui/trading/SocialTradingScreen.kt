package com.coinlab.app.ui.trading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.data.remote.firebase.model.TradingSignal
import com.coinlab.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialTradingScreen(
    onBack: () -> Unit,
    viewModel: SocialTradingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredSignals = viewModel.getFilteredSignals()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = CoinLabGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sosyal Trading", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleCreateDialog() }) {
                        Icon(Icons.Filled.Add, contentDescription = "Sinyal Ekle", tint = CoinLabGreen)
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
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "Tümü", "BUY" to "AL", "SELL" to "SAT").forEach { (type, label) ->
                    FilterChip(
                        selected = uiState.filterType == type,
                        onClick = { viewModel.setFilter(type) },
                        label = { Text(label, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoinLabGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CoinLabGreen)
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = CoinLabGold.copy(alpha = 0.7f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            uiState.error ?: "Bilinmeyen hata",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.retryLoading() },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tekrar Dene")
                        }
                    }
                }
            } else if (filteredSignals.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = CoinLabGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Henüz sinyal yok",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.toggleCreateDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("İlk Sinyali Paylaş")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredSignals, key = { it.id }) { signal ->
                        SignalCard(
                            signal = signal,
                            currentUserId = viewModel.currentUserId,
                            onLike = { viewModel.toggleLike(signal.id) }
                        )
                    }
                }
            }
        }

        // Create Signal Dialog
        if (uiState.showCreateDialog) {
            CreateSignalDialog(
                onDismiss = { viewModel.toggleCreateDialog() },
                onCreate = { coinName, coinSymbol, type, entry, target, sl, confidence, desc ->
                    viewModel.createSignal(
                        coinId = coinName.lowercase(),
                        coinName = coinName,
                        coinSymbol = coinSymbol,
                        signalType = type,
                        entryPrice = entry,
                        targetPrice = target,
                        stopLoss = sl,
                        confidence = confidence,
                        description = desc
                    )
                }
            )
        }
    }
}

@Composable
private fun SignalCard(
    signal: TradingSignal,
    currentUserId: String,
    onLike: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale("tr")) }
    val isLiked = signal.likes.containsKey(currentUserId)
    val signalColor = if (signal.isBuy) SparklineGreen else CoinLabRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(signalColor.copy(alpha = 0.15f), DarkSurfaceVariant)
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Signal type badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(signalColor.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (signal.isBuy) "AL" else "SAT",
                            color = signalColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${signal.coinName} (${signal.coinSymbol.uppercase()})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${signal.authorName} · ${timeFormat.format(Date(signal.timestamp))}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    // Confidence stars
                    Row {
                        repeat(signal.confidence) {
                            Text("⭐", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Prices
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PriceLabel("Giriş", signal.entryPrice, Color.White)
                    PriceLabel("Hedef", signal.targetPrice, SparklineGreen)
                    PriceLabel("Stop", signal.stopLoss, CoinLabRed)
                }

                // Profit/Loss indicators
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val profitText = String.format("%.1f%%", signal.potentialProfit)
                    Text(
                        text = "📈 Potansiyel: +$profitText",
                        color = SparklineGreen,
                        fontSize = 12.sp
                    )
                    val lossText = String.format("%.1f%%", signal.potentialLoss)
                    Text(
                        text = "📉 Risk: $lossText",
                        color = CoinLabRed,
                        fontSize = 12.sp
                    )
                }

                if (signal.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = signal.description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Like button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Beğen",
                            tint = if (isLiked) CoinLabRed else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "${signal.likeCount}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceLabel(label: String, price: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text(
            text = "$${String.format("%.2f", price)}",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CreateSignalDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Double, Double, Double, Int, String) -> Unit
) {
    var coinName by remember { mutableStateOf("Bitcoin") }
    var coinSymbol by remember { mutableStateOf("BTC") }
    var signalType by remember { mutableStateOf("BUY") }
    var entryPrice by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var confidence by remember { mutableIntStateOf(3) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Sinyal", fontWeight = FontWeight.Bold) },
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = coinName,
                    onValueChange = { coinName = it },
                    label = { Text("Coin Adı") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coinSymbol,
                    onValueChange = { coinSymbol = it },
                    label = { Text("Sembol") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = signalType == "BUY",
                        onClick = { signalType = "BUY" },
                        label = { Text("AL") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SparklineGreen
                        )
                    )
                    FilterChip(
                        selected = signalType == "SELL",
                        onClick = { signalType = "SELL" },
                        label = { Text("SAT") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoinLabRed
                        )
                    )
                }
                OutlinedTextField(
                    value = entryPrice,
                    onValueChange = { entryPrice = it },
                    label = { Text("Giriş Fiyatı ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it },
                    label = { Text("Hedef Fiyat ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stopLoss,
                    onValueChange = { stopLoss = it },
                    label = { Text("Stop Loss ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // Confidence
                Text("Güven: $confidence/5", color = Color.White.copy(alpha = 0.7f))
                Slider(
                    value = confidence.toFloat(),
                    onValueChange = { confidence = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = CoinLabGreen,
                        activeTrackColor = CoinLabGreen
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoinLabGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = CoinLabGreen,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entry = entryPrice.toDoubleOrNull() ?: 0.0
                    val target = targetPrice.toDoubleOrNull() ?: 0.0
                    val sl = stopLoss.toDoubleOrNull() ?: 0.0
                    onCreate(coinName, coinSymbol, signalType, entry, target, sl, confidence, description)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoinLabGreen)
            ) {
                Text("Paylaş", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
