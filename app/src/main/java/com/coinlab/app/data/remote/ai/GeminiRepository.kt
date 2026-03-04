package com.coinlab.app.data.remote.ai

import com.coinlab.app.domain.model.AiAnalysis
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v9.5 — Gemini AI Repository
 *
 * Provides AI-powered coin analysis using Google Gemini Pro.
 * API key is hardcoded for MVP; can be migrated to Remote Config later.
 */
@Singleton
class GeminiRepository @Inject constructor() {

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = "AIzaSyCAKJJMzt-xvMINaT3QiLgF1mr5sv09oDE",
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 40
            topP = 0.95f
            maxOutputTokens = 1024
        }
    )

    /**
     * Analyze a cryptocurrency and return structured analysis.
     */
    suspend fun analyzeCoin(
        coinName: String,
        coinSymbol: String,
        currentPrice: Double,
        priceChange24h: Double,
        marketCap: Double? = null,
        volume24h: Double? = null
    ): AiAnalysis {
        val prompt = buildAnalysisPrompt(coinName, coinSymbol, currentPrice, priceChange24h, marketCap, volume24h)

        return try {
            val response = model.generateContent(prompt)
            val text = response.text ?: "Analiz yapılamadı."
            parseAnalysisResponse(text)
        } catch (e: Exception) {
            AiAnalysis(
                summary = "Analiz sırasında hata oluştu: ${e.message}",
                sentiment = "Belirsiz",
                recommendation = "Tekrar deneyin",
                riskScore = 5,
                keyPoints = listOf("AI servisi şu an yanıt veremiyor")
            )
        }
    }

    /**
     * Free-form chat with the AI about crypto topics.
     */
    suspend fun chat(userMessage: String): String {
        val systemPrompt = """
            Sen CoinLab uygulamasının AI kripto asistanısın. Kullanıcılara kripto paralar,
            blockchain teknolojisi, DeFi, NFT ve Web3 hakkında yardımcı oluyorsun.
            Yanıtlarını Türkçe ver. Kısa ve öz ol. Yatırım tavsiyesi olmadığını belirt.
            Kullanıcı sorusu: $userMessage
        """.trimIndent()

        return try {
            val response = model.generateContent(systemPrompt)
            response.text ?: "Yanıt alınamadı."
        } catch (e: Exception) {
            "Üzgünüm, şu an yanıt veremiyorum. Hata: ${e.message}"
        }
    }

    private fun buildAnalysisPrompt(
        coinName: String,
        coinSymbol: String,
        currentPrice: Double,
        priceChange24h: Double,
        marketCap: Double?,
        volume24h: Double?
    ): String {
        val mcText = marketCap?.let { "Piyasa Değeri: $${String.format("%,.0f", it)}" } ?: ""
        val volText = volume24h?.let { "24s Hacim: $${String.format("%,.0f", it)}" } ?: ""

        return """
            Kripto para analizi yap. Aşağıdaki bilgileri kullanarak TÜRKÇE analiz hazırla.
            
            Coin: $coinName ($coinSymbol)
            Güncel Fiyat: $${String.format("%.2f", currentPrice)}
            24 Saatlik Değişim: ${String.format("%.2f", priceChange24h)}%
            $mcText
            $volText
            
            Yanıtını tam olarak şu formatta ver (başlıkları değiştirme):
            
            ÖZET: (2-3 cümle genel değerlendirme)
            DUYGU: (Pozitif/Negatif/Nötr)
            TAVSİYE: (1 cümle kısa tavsiye)
            RİSK: (1-10 arası sayı)
            NOKTALAR:
            - (önemli nokta 1)
            - (önemli nokta 2)
            - (önemli nokta 3)
            
            NOT: Bu finansal tavsiye değildir, sadece teknik analizdir.
        """.trimIndent()
    }

    private fun parseAnalysisResponse(text: String): AiAnalysis {
        val lines = text.lines()

        var summary = ""
        var sentiment = "Nötr"
        var recommendation = ""
        var riskScore = 5
        val keyPoints = mutableListOf<String>()

        var currentSection = ""

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("ÖZET:") -> {
                    currentSection = "summary"
                    summary = trimmed.removePrefix("ÖZET:").trim()
                }
                trimmed.startsWith("DUYGU:") -> {
                    currentSection = "sentiment"
                    sentiment = trimmed.removePrefix("DUYGU:").trim()
                }
                trimmed.startsWith("TAVSİYE:") -> {
                    currentSection = "recommendation"
                    recommendation = trimmed.removePrefix("TAVSİYE:").trim()
                }
                trimmed.startsWith("RİSK:") -> {
                    currentSection = "risk"
                    riskScore = trimmed.removePrefix("RİSK:").trim()
                        .filter { it.isDigit() }
                        .take(2)
                        .toIntOrNull() ?: 5
                }
                trimmed.startsWith("NOKTALAR:") -> {
                    currentSection = "points"
                }
                trimmed.startsWith("- ") && currentSection == "points" -> {
                    keyPoints.add(trimmed.removePrefix("- ").trim())
                }
                trimmed.startsWith("NOT:") -> { /* skip disclaimer */ }
                currentSection == "summary" && trimmed.isNotEmpty() && !trimmed.startsWith("DUYGU") -> {
                    summary += " $trimmed"
                }
            }
        }

        // Fallback if parsing fails
        if (summary.isBlank()) {
            summary = text.take(300)
        }

        return AiAnalysis(
            summary = summary.trim(),
            sentiment = sentiment,
            recommendation = recommendation,
            riskScore = riskScore.coerceIn(1, 10),
            keyPoints = keyPoints.ifEmpty { listOf("Detaylı analiz yukarıda.") }
        )
    }
}
