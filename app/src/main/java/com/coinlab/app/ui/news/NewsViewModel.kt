package com.coinlab.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coinlab.app.domain.model.NewsArticle
import com.coinlab.app.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val articles: List<NewsArticle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedCategory: String? = null
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    init {
        loadNews()
    }

    fun loadNews(category: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedCategory = category) }
            val flow = if (category != null) {
                newsRepository.getNewsByCategory(category)
            } else {
                newsRepository.getLatestNews()
            }
            flow.collectLatest { result ->
                result.fold(
                    onSuccess = { articles ->
                        _uiState.update {
                            it.copy(articles = articles, isLoading = false, error = null)
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isLoading = false, error = error.localizedMessage)
                        }
                    }
                )
            }
        }
    }

    fun refresh() {
        loadNews(_uiState.value.selectedCategory)
    }
}
