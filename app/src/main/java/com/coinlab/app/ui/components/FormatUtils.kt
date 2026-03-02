package com.coinlab.app.ui.components

import java.text.NumberFormat
import java.util.Locale

object FormatUtils {

    fun formatPrice(price: Double, currency: String = "USD"): String {
        val locale = Locale.US
        val format = NumberFormat.getCurrencyInstance(locale)
        format.maximumFractionDigits = when {
            price < 0.01 -> 8
            price < 1 -> 6
            price < 100 -> 4
            else -> 2
        }
        format.minimumFractionDigits = 2
        return format.format(price)
    }

    fun formatMarketCap(value: Long, currency: String = "USD"): String {
        val symbol = "$"
        return when {
            value >= 1_000_000_000_000 -> "$symbol${String.format(Locale.US, "%.2f", value / 1_000_000_000_000.0)}T"
            value >= 1_000_000_000 -> "$symbol${String.format(Locale.US, "%.2f", value / 1_000_000_000.0)}B"
            value >= 1_000_000 -> "$symbol${String.format(Locale.US, "%.2f", value / 1_000_000.0)}M"
            value >= 1_000 -> "$symbol${String.format(Locale.US, "%.2f", value / 1_000.0)}K"
            else -> "$symbol$value"
        }
    }

    fun formatVolume(value: Double, currency: String = "USD"): String {
        return formatMarketCap(value.toLong(), currency)
    }

    fun formatSupply(value: Double): String {
        return when {
            value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "%.2fK", value / 1_000.0)
            else -> String.format(Locale.US, "%.2f", value)
        }
    }

    fun formatPercentage(value: Double): String {
        val prefix = if (value >= 0) "+" else ""
        return "$prefix${String.format(Locale.US, "%.2f", value)}%"
    }

    fun formatRelativeTime(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - (timestampMs * if (timestampMs < 10_000_000_000L) 1000 else 1)
        return when {
            diff < 60_000 -> "Az önce"
            diff < 3_600_000 -> "${diff / 60_000} dk önce"
            diff < 86_400_000 -> "${diff / 3_600_000} sa önce"
            diff < 604_800_000 -> "${diff / 86_400_000} gün önce"
            else -> "${diff / 604_800_000} hafta önce"
        }
    }
}
