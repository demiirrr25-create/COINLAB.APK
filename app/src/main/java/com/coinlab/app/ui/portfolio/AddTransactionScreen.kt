package com.coinlab.app.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coinlab.app.R
import com.coinlab.app.domain.model.PortfolioEntry
import com.coinlab.app.domain.model.TransactionType
import com.coinlab.app.ui.theme.SparklineGreen
import com.coinlab.app.ui.theme.CoinLabRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    coinId: String,
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val coinDetail by viewModel.coinDetail.collectAsState()

    var transactionType by remember { mutableStateOf(TransactionType.BUY) }
    var amount by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // Auto-fill price when coin detail loads
    coinDetail?.let { detail ->
        if (price.isEmpty()) {
            price = (detail.currentPrice["usd"] ?: detail.currentPrice["try"] ?: 0.0).toString()
        }
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.add_transaction),
                    style = MaterialTheme.typography.titleLarge,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Transaction Type
            Text(
                text = stringResource(R.string.transaction_type),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = transactionType == TransactionType.BUY,
                    onClick = { transactionType = TransactionType.BUY },
                    label = { Text(stringResource(R.string.buy)) }
                )
                FilterChip(
                    selected = transactionType == TransactionType.SELL,
                    onClick = { transactionType = TransactionType.SELL },
                    label = { Text(stringResource(R.string.sell)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.amount)) },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Price per unit
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(stringResource(R.string.price_per_unit)) },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = { Text("$") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Total
            val total = (amount.toDoubleOrNull() ?: 0.0) * (price.toDoubleOrNull() ?: 0.0)
            Text(
                text = "${stringResource(R.string.total)}: $${String.format("%.2f", total)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: return@Button
                    val priceVal = price.toDoubleOrNull() ?: return@Button

                    scope.launch {
                        viewModel.addTransaction(
                            PortfolioEntry(
                                coinId = coinId,
                                coinSymbol = coinDetail?.symbol ?: coinId.uppercase(),
                                coinName = coinDetail?.name ?: coinId,
                                coinImage = coinDetail?.image ?: "",
                                amount = amountVal,
                                buyPrice = priceVal,
                                currency = "USD",
                                note = note,
                                type = transactionType
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = amount.toDoubleOrNull() != null && price.toDoubleOrNull() != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == TransactionType.BUY) SparklineGreen else CoinLabRed
                )
            ) {
                Text(
                    text = if (transactionType == TransactionType.BUY)
                        stringResource(R.string.buy_coin) else stringResource(R.string.sell_coin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
