package com.coinlab.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.coinlab.app.ui.theme.PriceDown
import com.coinlab.app.ui.theme.PriceNeutral
import com.coinlab.app.ui.theme.PriceUp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/**
 * High-performance price text with color flash on change.
 * Uses a SINGLE Text composable instead of per-character AnimatedContent.
 * Color flashes green/red on price change, then fades back to neutral.
 */
@Composable
fun AnimatedPriceText(
    price: Double,
    currency: String = "USD",
    style: TextStyle = MaterialTheme.typography.titleSmall,
    fontWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier
) {
    var previousPrice by remember { mutableDoubleStateOf(price) }
    var priceDirection by remember { mutableIntStateOf(0) }

    LaunchedEffect(price) {
        priceDirection = when {
            price > previousPrice -> 1
            price < previousPrice -> -1
            else -> 0
        }
        previousPrice = price
        if (priceDirection != 0) {
            delay(600)
            priceDirection = 0
        }
    }

    val targetColor = when (priceDirection) {
        1 -> PriceUp
        -1 -> PriceDown
        else -> PriceNeutral
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "priceColor"
    )

    val formattedPrice = remember(price, currency) {
        formatPrice(price, currency)
    }

    Text(
        text = formattedPrice,
        style = style,
        fontWeight = fontWeight,
        color = animatedColor,
        modifier = modifier
    )
}

private fun formatPrice(price: Double, currency: String): String {
    val locale = Locale.US
    val currencyFormat = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = when {
            price < 0.01 -> 8
            price < 1 -> 6
            price < 100 -> 4
            else -> 2
        }
        minimumFractionDigits = 2
    }
    val formatted = currencyFormat.format(price)
    // Append USDT label for clarity
    return if (currency.equals("USD", ignoreCase = true) || currency.equals("USDT", ignoreCase = true)) {
        formatted
    } else {
        formatted
    }
}
