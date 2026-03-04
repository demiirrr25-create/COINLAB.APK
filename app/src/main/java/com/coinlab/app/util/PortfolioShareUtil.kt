package com.coinlab.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * v9.5 — Portfolio Share Utility
 *
 * Provides a simple text-based portfolio share via Android share sheet.
 * Image sharing can be added later via Compose snapshot API.
 */
object PortfolioShareUtil {

    /**
     * Share portfolio summary as text.
     */
    fun sharePortfolioText(
        context: Context,
        totalValue: String,
        totalChange: String,
        coins: List<PortfolioShareItem>
    ) {
        val text = buildString {
            appendLine("📊 CoinLab Portföyüm")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("💰 Toplam Değer: $totalValue")
            appendLine("📈 24s Değişim: $totalChange")
            appendLine()
            coins.take(10).forEach { coin ->
                appendLine("${coin.symbol.uppercase()}: ${coin.value} (${coin.change})")
            }
            appendLine()
            appendLine("CoinLab ile takip ediyorum! 🚀")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "CoinLab Portföyüm")
        }
        context.startActivity(Intent.createChooser(intent, "Portföyü Paylaş"))
    }
}

data class PortfolioShareItem(
    val symbol: String,
    val value: String,
    val change: String
)
