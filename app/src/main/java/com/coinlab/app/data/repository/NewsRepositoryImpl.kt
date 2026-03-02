package com.coinlab.app.data.repository

import com.coinlab.app.data.remote.api.CryptoCompareApi
import com.coinlab.app.data.remote.dto.toDomain
import com.coinlab.app.domain.model.NewsArticle
import com.coinlab.app.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class NewsRepositoryImpl @Inject constructor(
    private val api: CryptoCompareApi
) : NewsRepository {

    override fun getLatestNews(categories: String?): Flow<Result<List<NewsArticle>>> = flow {
        try {
            val response = api.getLatestNews(categories = categories)
            val articles = response.data?.map { it.toDomain() } ?: emptyList()
            emit(Result.success(articles))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getNewsByCategory(category: String): Flow<Result<List<NewsArticle>>> = flow {
        try {
            val response = api.getNewsByCategory(categories = category)
            val articles = response.data?.map { it.toDomain() } ?: emptyList()
            emit(Result.success(articles))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
