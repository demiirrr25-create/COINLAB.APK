package com.coinlab.app.data.remote.dto

import com.coinlab.app.domain.model.NewsArticle
import com.google.gson.annotations.SerializedName

data class NewsResponseDto(
    @SerializedName("Data") val data: List<NewsArticleDto>?
)

data class NewsArticleDto(
    val id: String,
    val title: String,
    val body: String,
    @SerializedName("imageurl") val imageUrl: String?,
    val url: String,
    @SerializedName("source_info") val sourceInfo: SourceInfoDto?,
    val categories: String?,
    @SerializedName("published_on") val publishedOn: Long?,
    val tags: String?
)

data class SourceInfoDto(
    val name: String?
)

fun NewsArticleDto.toDomain(): NewsArticle {
    return NewsArticle(
        id = id,
        title = title,
        body = body,
        imageUrl = imageUrl,
        url = url,
        source = sourceInfo?.name ?: "Unknown",
        categories = categories ?: "",
        publishedAt = publishedOn ?: 0L,
        tags = tags?.split("|")?.map { it.trim() } ?: emptyList()
    )
}
