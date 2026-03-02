package com.coinlab.app.ui.web3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Web3Screen(
    onBackClick: () -> Unit,
    viewModel: Web3ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web3 & DeFi") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gas Tracker Card
            item {
                GasTrackerCard(gasPrice = uiState.gasPrice)
            }

            // Chain Selector
            item {
                ChainSelector(
                    selectedChain = uiState.selectedChain,
                    onChainSelected = viewModel::selectChain
                )
            }

            // Wallet Section
            item {
                WalletSection(
                    isConnected = uiState.isWalletConnected,
                    walletAddress = uiState.walletAddress,
                    onConnect = viewModel::enterWalletAddress,
                    onDisconnect = viewModel::disconnectWallet
                )
            }

            // Token Balances
            if (uiState.isWalletConnected && uiState.tokenBalances.isNotEmpty()) {
                item {
                    Text(
                        text = "Token Bakiyeleri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.tokenBalances) { token ->
                    TokenBalanceItem(token = token)
                }
            }

            // DeFi Positions
            if (uiState.isWalletConnected && uiState.defiPositions.isNotEmpty()) {
                item {
                    Text(
                        text = "DeFi Pozisyonları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.defiPositions) { position ->
                    DeFiPositionItem(position = position)
                }
            }

            // DeFi Protocols Info
            item {
                DeFiProtocolsCard()
            }

            // NFT Gallery Placeholder
            item {
                NFTGalleryCard(nftCount = uiState.nftCount)
            }

            // Web3 Info Card
            item {
                Web3InfoCard()
            }
        }
    }
}

@Composable
private fun GasTrackerCard(gasPrice: GasPrice?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Gas Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "Ethereum",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(16.dp))

            if (gasPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GasPriceItem(
                        label = "🐢 Düşük",
                        value = "${gasPrice.low.toInt()}",
                        unit = gasPrice.unit,
                        color = Color(0xFF4CAF50)
                    )
                    GasPriceItem(
                        label = "🚶 Orta",
                        value = "${gasPrice.average.toInt()}",
                        unit = gasPrice.unit,
                        color = Color(0xFFFFC107)
                    )
                    GasPriceItem(
                        label = "🚀 Yüksek",
                        value = "${gasPrice.high.toInt()}",
                        unit = gasPrice.unit,
                        color = Color(0xFFF44336)
                    )
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun GasPriceItem(
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ChainSelector(
    selectedChain: Chain,
    onChainSelected: (Chain) -> Unit
) {
    Column {
        Text(
            text = "Ağ Seçimi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chain.entries.forEach { chain ->
                FilterChip(
                    selected = chain == selectedChain,
                    onClick = { onChainSelected(chain) },
                    label = { Text(chain.displayName, maxLines = 1) },
                    leadingIcon = {
                        Text(
                            text = when (chain) {
                                Chain.ETHEREUM -> "⟠"
                                Chain.BSC -> "🔶"
                                Chain.POLYGON -> "🟣"
                                Chain.ARBITRUM -> "🔵"
                                Chain.OPTIMISM -> "🔴"
                                Chain.AVALANCHE -> "🔺"
                                Chain.SOLANA -> "◎"
                            },
                            fontSize = 16.sp
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletSection(
    isConnected: Boolean,
    walletAddress: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showAddressInput by remember { mutableStateOf(false) }
    var addressInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Cüzdan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isConnected) {
                    AssistChip(
                        onClick = onDisconnect,
                        label = { Text("Bağlantıyı Kes") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isConnected && walletAddress != null) {
                // Connected state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${walletAddress.take(6)}...${walletAddress.takeLast(4)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                // Not connected
                Text(
                    text = "Cüzdanınızı bağlayarak token bakiyelerinizi, DeFi pozisyonlarınızı ve NFT'lerinizi görüntüleyin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(visible = showAddressInput) {
                    Column {
                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = { Text("Ethereum Adresi") },
                            placeholder = { Text("0x...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (addressInput.startsWith("0x") && addressInput.length == 42) {
                                        onConnect(addressInput)
                                    }
                                }
                            )
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (addressInput.startsWith("0x") && addressInput.length == 42) {
                                    onConnect(addressInput)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = addressInput.startsWith("0x") && addressInput.length == 42
                        ) {
                            Text("Bağlan")
                        }
                    }
                }

                if (!showAddressInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddressInput = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Adres Gir")
                        }
                        Button(
                            onClick = { /* WalletConnect - future */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("WalletConnect")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenBalanceItem(token: TokenBalance) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("tr", "TR")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = token.symbol.take(2),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = token.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${token.balance} ${token.symbol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = currencyFormat.format(token.valueUsd),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeFiPositionItem(position: DeFiPosition) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("tr", "TR")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = position.protocol.take(1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = position.protocol,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = position.type,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyFormat.format(position.valueUsd),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (position.apy != null) {
                        Text(
                            text = "APY: %${String.format("%.1f", position.apy)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${position.tokenPair} • ${position.chain.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeFiProtocolsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Popüler DeFi Protokolleri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            val protocols = listOf(
                Triple("Uniswap", "DEX - Token Takası", "🦄"),
                Triple("Aave", "Lending & Borrowing", "👻"),
                Triple("Lido", "Liquid Staking", "🌊"),
                Triple("Curve", "Stablecoin DEX", "🔄"),
                Triple("MakerDAO", "CDP & DAI Mint", "🏛️"),
                Triple("Compound", "Lending Protocol", "💰")
            )

            protocols.forEach { (name, desc, emoji) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Open protocol */ }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (name != "Compound") {
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                }
            }
        }
    }
}

@Composable
private fun NFTGalleryCard(nftCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Collections,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "NFT Galeri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (nftCount > 0) "$nftCount NFT bulundu" else "Cüzdanınızı bağlayarak NFT koleksiyonunuzu görüntüleyin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (nftCount > 0) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { /* Open gallery */ }) {
                    Text("Galeriyi Aç")
                }
            }
        }
    }
}

@Composable
private fun Web3InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Web3 Hakkında",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Web3, merkeziyetsiz uygulamalar (dApps) ve akıllı sözleşmeler aracılığıyla çalışan yeni nesil internettir. DeFi protokolleri ile aracısız finansal işlemler gerçekleştirebilir, NFT'lerinizi yönetebilir ve DAO'lara katılabilirsiniz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(12.dp))

            val concepts = listOf(
                "🏦 DeFi" to "Merkezi olmayan finans",
                "🖼️ NFT" to "Eşsiz dijital varlıklar",
                "🗳️ DAO" to "Merkezi olmayan otonom org.",
                "⛓️ L2" to "Katman 2 ölçekleme çözümleri"
            )

            concepts.forEach { (title, desc) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
