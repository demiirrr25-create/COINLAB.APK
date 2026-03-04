package com.coinlab.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.firebase.ChatRepository
import com.coinlab.app.data.remote.firebase.model.ChatMessage
import com.coinlab.app.data.remote.firebase.model.ChatRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val chatRooms: List<ChatRoom> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val currentChatId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val messageInput: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val currentUserId: String get() = chatRepository.getCurrentUserId()
    val currentUserName: String get() = chatRepository.getCurrentUserName()

    init {
        loadChats()
    }

    private fun loadChats() {
        viewModelScope.launch {
            chatRepository.getChats().collect { rooms ->
                _uiState.update { it.copy(chatRooms = rooms, isLoading = false) }
            }
        }
    }

    fun loadMessages(chatId: String) {
        _uiState.update { it.copy(currentChatId = chatId, isLoading = true) }
        viewModelScope.launch {
            chatRepository.getMessages(chatId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs, isLoading = false) }
            }
        }
    }

    fun updateMessageInput(text: String) {
        _uiState.update { it.copy(messageInput = text) }
    }

    fun sendMessage() {
        val chatId = _uiState.value.currentChatId ?: return
        val text = _uiState.value.messageInput.trim()
        if (text.isBlank()) return

        _uiState.update { it.copy(messageInput = "") }

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(chatId, text)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun startNewChat(otherUserId: String, otherUserName: String) {
        viewModelScope.launch {
            try {
                val chatId = chatRepository.getOrCreateChat(
                    otherUserId = otherUserId,
                    otherUserName = otherUserName,
                    currentUserName = currentUserName
                )
                _uiState.update { it.copy(currentChatId = chatId) }
                loadMessages(chatId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
