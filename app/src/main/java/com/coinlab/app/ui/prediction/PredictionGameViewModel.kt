package com.coinlab.app.ui.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.data.remote.firebase.PredictionGameRepository
import com.coinlab.app.data.remote.firebase.model.PredictionRound
import com.coinlab.app.data.remote.firebase.model.PredictionScore
import com.coinlab.app.data.remote.firebase.model.UserPrediction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PredictionGameUiState(
    val activeRound: PredictionRound? = null,
    val predictions: List<UserPrediction> = emptyList(),
    val leaderboard: List<PredictionScore> = emptyList(),
    val userPrediction: String? = null,
    val timeRemaining: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: Int = 0
)

@HiltViewModel
class PredictionGameViewModel @Inject constructor(
    private val repository: PredictionGameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PredictionGameUiState())
    val uiState: StateFlow<PredictionGameUiState> = _uiState.asStateFlow()

    init {
        loadActiveRound()
        loadLeaderboard()
    }

    private fun loadActiveRound() {
        viewModelScope.launch {
            repository.getActiveRound().collect { round ->
                _uiState.update { it.copy(activeRound = round, isLoading = false) }
                round?.let {
                    loadPredictions(it.id)
                    startCountdown(it.endTime)
                }
            }
        }
    }

    private fun loadPredictions(roundId: String) {
        viewModelScope.launch {
            repository.getRoundPredictions(roundId).collect { preds ->
                val userId = repository.getCurrentUserId()
                val userPred = preds.find { it.userId == userId }?.prediction
                _uiState.update {
                    it.copy(predictions = preds, userPrediction = userPred)
                }
            }
        }
    }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            repository.getLeaderboard().collect { scores ->
                _uiState.update { it.copy(leaderboard = scores) }
            }
        }
    }

    private fun startCountdown(endTime: Long) {
        viewModelScope.launch {
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    _uiState.update { it.copy(timeRemaining = 0) }
                    break
                }
                _uiState.update { it.copy(timeRemaining = remaining) }
                delay(1000)
            }
        }
    }

    fun makePrediction(direction: String) {
        val roundId = _uiState.value.activeRound?.id ?: return
        viewModelScope.launch {
            try {
                repository.makePrediction(roundId, direction)
                _uiState.update { it.copy(userPrediction = direction) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun createNewRound() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                repository.createRound(
                    coinId = "bitcoin",
                    coinName = "Bitcoin",
                    coinSymbol = "BTC",
                    currentPrice = 0.0,
                    durationMinutes = 5
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }
}
