package com.coinlab.app.domain.model

data class NewsArticle(
    val id: String,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val url: String,
    val source: String,
    val categories: String,
    val publishedAt: Long,
    val tags: List<String>
)
