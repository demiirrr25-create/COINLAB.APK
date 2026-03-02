package com.coinlab.app.domain.model

data class FearGreedIndex(
    val value: Int,
    val valueClassification: String,
    val timestamp: Long,
    val timeUntilUpdate: Long? = null
) {
    val emoji: String get() = when {
        value <= 25 -> "😱"
        value <= 45 -> "😨"
        value <= 55 -> "😐"
        value <= 75 -> "😊"
        else -> "🤑"
    }

    val label: String get() = when {
        value <= 25 -> "Aşırı Korku"
        value <= 45 -> "Korku"
        value <= 55 -> "Nötr"
        value <= 75 -> "Açgözlülük"
        else -> "Aşırı Açgözlülük"
    }
}
