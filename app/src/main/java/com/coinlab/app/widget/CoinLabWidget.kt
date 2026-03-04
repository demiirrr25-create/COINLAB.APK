package com.coinlab.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.coinlab.app.MainActivity
import com.coinlab.app.R
import com.coinlab.app.data.local.CoinLabDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoinLabWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val topCoins = withContext(Dispatchers.IO) {
            try {
                val db = CoinLabDatabase.getInstance(context)
                db.coinDao().getTopCoinsSync(5)
            } catch (e: Exception) {
                emptyList()
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    coins = topCoins.map { coin ->
                        WidgetCoinData(
                            symbol = coin.symbol.uppercase(),
                            name = coin.name,
                            price = formatWidgetPrice(coin.currentPrice),
                            changePercent = coin.priceChangePercentage24h,
                            isPositive = (coin.priceChangePercentage24h ?: 0.0) >= 0
                        )
                    }
                )
            }
        }
    }

    private fun formatWidgetPrice(price: Double?): String {
        if (price == null) return "-"
        return if (price >= 1) {
            String.format("₺%.2f", price)
        } else {
            String.format("₺%.6f", price)
        }
    }
}

data class WidgetCoinData(
    val symbol: String,
    val name: String,
    val price: String,
    val changePercent: Double?,
    val isPositive: Boolean
)

@Composable
private fun WidgetContent(coins: List<WidgetCoinData>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CoinLab",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorProvider(Color(0xFFF7931A)) // Bitcoin Gold
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "CANLI",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(Color(0xFFFFC107)) // Amber Gold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (coins.isEmpty()) {
            Text(
                text = "Veri yükleniyor...",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onBackground
                )
            )
        } else {
            coins.forEach { coin ->
                WidgetCoinRow(coin)
                Spacer(modifier = GlanceModifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun WidgetCoinRow(coin: WidgetCoinData) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Symbol
        Text(
            text = coin.symbol,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = GlanceTheme.colors.onBackground
            ),
            modifier = GlanceModifier.width(52.dp)
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Price
        Text(
            text = coin.price,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = GlanceTheme.colors.onBackground
            )
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Change percent
        val changeText = coin.changePercent?.let {
            String.format("%s%.1f%%", if (it >= 0) "+" else "", it)
        } ?: "-"

        val changeColor = if (coin.isPositive) {
            ColorProvider(Color(0xFF4CAF50))
        } else {
            ColorProvider(Color(0xFFF44336))
        }

        Text(
            text = changeText,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = changeColor
            ),
            modifier = GlanceModifier.width(56.dp)
        )
    }
}
