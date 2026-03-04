package com.coinlab.app.domain.model

/**
 * v9.5 — AI Analysis Result from Gemini
 *
 * Represents the structured output from the AI coin analysis assistant.
 */
data class AiAnalysis(
    val summary: String = "",
    val sentiment: String = "Nötr",
    val recommendation: String = "",
    val riskScore: Int = 5,
    val keyPoints: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val sentimentEmoji: String
        get() = when (sentiment.lowercase()) {
            "pozitif", "bullish", "yükseliş" -> "🟢"
            "negatif", "bearish", "düşüş" -> "🔴"
            else -> "🟡"
        }

    val riskLevel: String
        get() = when {
            riskScore <= 3 -> "Düşük Risk"
            riskScore <= 6 -> "Orta Risk"
            else -> "Yüksek Risk"
        }
}
