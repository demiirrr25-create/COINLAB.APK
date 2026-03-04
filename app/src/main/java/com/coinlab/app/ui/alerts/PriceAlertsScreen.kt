package com.coinlab.app.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.coinlab.app.data.local.entity.PriceAlertEntity
import com.coinlab.app.ui.components.FormatUtils
import com.coinlab.app.ui.theme.SparklineGreen
import com.coinlab.app.ui.theme.CoinLabRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertsScreen(
    onBack: () -> Unit,
    viewModel: PriceAlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.price_alerts_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
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

            // Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedTab == AlertTab.ALL,
                    onClick = { viewModel.onTabSelected(AlertTab.ALL) },
                    label = { Text(stringResource(R.string.tab_all)) }
                )
                FilterChip(
                    selected = uiState.selectedTab == AlertTab.ACTIVE,
                    onClick = { viewModel.onTabSelected(AlertTab.ACTIVE) },
                    label = { Text(stringResource(R.string.alert_active)) }
                )
                FilterChip(
                    selected = uiState.selectedTab == AlertTab.TRIGGERED,
                    onClick = { viewModel.onTabSelected(AlertTab.TRIGGERED) },
                    label = { Text(stringResource(R.string.alert_triggered)) }
                )
            }

            // Alert count summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AlertStatItem(
                        label = stringResource(R.string.tab_all),
                        count = uiState.alerts.size
                    )
                    AlertStatItem(
                        label = stringResource(R.string.alert_active),
                        count = uiState.activeAlerts.size
                    )
                    AlertStatItem(
                        label = stringResource(R.string.alert_triggered),
                        count = uiState.triggeredAlerts.size
                    )
                }
            }

            val displayAlerts = when (uiState.selectedTab) {
                AlertTab.ALL -> uiState.alerts
                AlertTab.ACTIVE -> uiState.activeAlerts
                AlertTab.TRIGGERED -> uiState.triggeredAlerts
            }

            if (displayAlerts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_alerts),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.no_alerts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayAlerts, key = { it.id }) { alert ->
                        val dismissState = rememberSwipeToDismissBoxState()

                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            LaunchedEffect(alert.id) {
                                viewModel.deleteAlert(alert.id)
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CoinLabRed.copy(alpha = 0.2f))
                                        .padding(end = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = CoinLabRed
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            AlertItem(
                                alert = alert,
                                currency = uiState.currency,
                                onToggle = { viewModel.toggleAlertActive(alert) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.showCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Yeni Uyarı")
        }

        if (uiState.showCreateDialog) {
            CreateAlertFromListDialog(
                onDismiss = { viewModel.hideCreateDialog() },
                onConfirm = { coinId, coinSymbol, coinName, coinImage, targetPrice, isAbove ->
                    viewModel.createAlert(
                        coinId = coinId,
                        coinSymbol = coinSymbol,
                        coinName = coinName,
                        coinImage = coinImage,
                        targetPrice = targetPrice,
                        isAbove = isAbove
                    )
                }
            )
        }
    }
}

@Composable
private fun AlertStatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlertItem(
    alert: PriceAlertEntity,
    currency: String,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isTriggered)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = alert.coinImage,
                contentDescription = alert.coinName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alert.coinName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alert.coinSymbol.uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (alert.isAbove) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (alert.isAbove) SparklineGreen else CoinLabRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (alert.isAbove) stringResource(R.string.alert_above) else stringResource(R.string.alert_below),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alert.isAbove) SparklineGreen else CoinLabRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = FormatUtils.formatPrice(alert.targetPrice, currency),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (alert.isTriggered) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✅ ${stringResource(R.string.alert_triggered)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SparklineGreen
                    )
                }
            }

            if (!alert.isTriggered) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (alert.isActive) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = "Toggle",
                        tint = if (alert.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertFromListDialog(
    onDismiss: () -> Unit,
    onConfirm: (coinId: String, coinSymbol: String, coinName: String, coinImage: String, targetPrice: Double, isAbove: Boolean) -> Unit
) {
    var coinIdText by remember { mutableStateOf("") }
    var coinNameText by remember { mutableStateOf("") }
    var coinSymbolText by remember { mutableStateOf("") }
    var targetPriceText by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val options = listOf("Üstüne Çıkınca", "Altına Düşünce")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Fiyat Uyarısı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Coin bilgilerini girin ve hedef fiyat belirleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = coinIdText,
                    onValueChange = { coinIdText = it.lowercase().trim() },
                    label = { Text("Coin ID (ör: bitcoin)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = coinNameText,
                    onValueChange = { coinNameText = it },
                    label = { Text("Coin Adı (ör: Bitcoin)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = coinSymbolText,
                    onValueChange = { coinSymbolText = it.uppercase().trim() },
                    label = { Text("Sembol (ör: BTC)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    label = { Text("Hedef Fiyat (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val isValid = coinIdText.isNotBlank() && coinNameText.isNotBlank() &&
                coinSymbolText.isNotBlank() && targetPriceText.toDoubleOrNull() != null &&
                targetPriceText.toDoubleOrNull()!! > 0
            Button(
                onClick = {
                    onConfirm(
                        coinIdText, coinSymbolText, coinNameText, "",
                        targetPriceText.toDouble(), selectedIndex == 0
                    )
                },
                enabled = isValid
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