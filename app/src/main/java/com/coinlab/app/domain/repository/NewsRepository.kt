package com.coinlab.app.domain.repository

import com.coinlab.app.domain.model.NewsArticle
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun getLatestNews(categories: String? = null): Flow<Result<List<NewsArticle>>>

    fun getNewsByCategory(category: String): Flow<Result<List<NewsArticle>>>
}
