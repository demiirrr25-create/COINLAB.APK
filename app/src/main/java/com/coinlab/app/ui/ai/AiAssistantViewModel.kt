package com.coinlab.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.ai.GeminiRepository
import com.coinlab.app.domain.model.AiAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatBubble(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val analysis: AiAnalysis? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiAssistantUiState(
    val messages: List<ChatBubble> = listOf(
        ChatBubble(
            text = "Merhaba! 👋 Ben CoinLab AI Asistanı. Kripto paralar hakkında sorular sorabilir veya bir coin analizi isteyebilirsiniz.\n\nÖrnek: \"Bitcoin analiz et\" veya \"Ethereum hakkında ne düşünüyorsun?\"",
            isUser = false
        )
    ),
    val isLoading: Boolean = false,
    val inputText: String = ""
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        val userBubble = ChatBubble(text = text, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userBubble,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            try {
                // Check if user wants coin analysis
                val coinAnalysisRegex = Regex(
                    "(analiz|analyse|analyze|değerlendir|incele).*?(bitcoin|ethereum|bnb|solana|xrp|cardano|dogecoin|polkadot|avalanche|matic|polygon)",
                    RegexOption.IGNORE_CASE
                )
                val coinMention = coinAnalysisRegex.find(text)

                if (coinMention != null) {
                    val coinName = coinMention.groupValues[2].replaceFirstChar { it.uppercase() }
                    val analysis = geminiRepository.analyzeCoin(
                        coinName = coinName,
                        coinSymbol = coinName.take(3).uppercase(),
                        currentPrice = 0.0,
                        priceChange24h = 0.0
                    )
                    val responseBubble = ChatBubble(
                        text = formatAnalysis(analysis),
                        isUser = false,
                        analysis = analysis
                    )
                    _uiState.update {
                        it.copy(messages = it.messages + responseBubble, isLoading = false)
                    }
                } else {
                    val response = geminiRepository.chat(text)
                    val responseBubble = ChatBubble(text = response, isUser = false)
                    _uiState.update {
                        it.copy(messages = it.messages + responseBubble, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                val errorBubble = ChatBubble(
                    text = "Üzgünüm, bir hata oluştu: ${e.message}",
                    isUser = false
                )
                _uiState.update {
                    it.copy(messages = it.messages + errorBubble, isLoading = false)
                }
            }
        }
    }

    private fun formatAnalysis(analysis: AiAnalysis): String {
        return buildString {
            appendLine("📊 **AI Analiz Sonucu**")
            appendLine()
            appendLine("${analysis.sentimentEmoji} Duygu: ${analysis.sentiment}")
            appendLine("⚠️ Risk: ${analysis.riskLevel} (${analysis.riskScore}/10)")
            appendLine()
            appendLine(analysis.summary)
            appendLine()
            if (analysis.recommendation.isNotBlank()) {
                appendLine("💡 ${analysis.recommendation}")
                appendLine()
            }
            if (analysis.keyPoints.isNotEmpty()) {
                appendLine("📌 Önemli Noktalar:")
                analysis.keyPoints.forEach { point ->
                    appendLine("  • $point")
                }
            }
            appendLine()
            appendLine("⚠️ Bu finansal tavsiye değildir.")
        }
    }
}
