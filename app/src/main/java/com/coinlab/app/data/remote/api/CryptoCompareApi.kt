package com.coinlab.app.data.remote.api

import com.coinlab.app.data.remote.dto.NewsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CryptoCompareApi {

    @GET("data/v2/news/")
    suspend fun getLatestNews(
        @Query("lang") lang: String = "TR",
        @Query("categories") categories: String? = null,
        @Query("sortOrder") sortOrder: String = "latest",
        @Query("extraParams") extraParams: String = "CoinLab"
    ): NewsResponseDto

    @GET("data/v2/news/")
    suspend fun getNewsByCategory(
        @Query("categories") categories: String,
        @Query("lang") lang: String = "TR",
        @Query("sortOrder") sortOrder: String = "latest",
        @Query("extraParams") extraParams: String = "CoinLab"
    ): NewsResponseDto
}
