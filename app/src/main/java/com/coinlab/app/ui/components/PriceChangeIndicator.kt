package com.coinlab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.coinlab.app.ui.theme.SparklineGreen
import com.coinlab.app.ui.theme.CoinLabRed
import java.util.Locale

@Composable
fun PriceChangeIndicator(
    changePercentage: Double,
    modifier: Modifier = Modifier,
    showBackground: Boolean = false
) {
    val isPositive = changePercentage >= 0
    val color = if (isPositive) SparklineGreen else CoinLabRed
    val icon = if (isPositive) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown
    val text = String.format(Locale.US, "%.2f%%", kotlin.math.abs(changePercentage))

    val bgModifier = if (showBackground) {
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        modifier
    }

    Row(
        modifier = bgModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
