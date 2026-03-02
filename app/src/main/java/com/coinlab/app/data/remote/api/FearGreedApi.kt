package com.coinlab.app.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

interface FearGreedApi {

    @GET("fng/")
    suspend fun getFearGreedIndex(
        @Query("limit") limit: Int = 1,
        @Query("format") format: String = "json"
    ): FearGreedResponse

    @GET("fng/")
    suspend fun getFearGreedHistory(
        @Query("limit") limit: Int = 30,
        @Query("format") format: String = "json"
    ): FearGreedResponse
}

data class FearGreedResponse(
    val name: String?,
    val data: List<FearGreedDataItem>?,
    val metadata: FearGreedMetadata?
)

data class FearGreedDataItem(
    val value: String?,
    val value_classification: String?,
    val timestamp: String?,
    val time_until_update: String?
)

data class FearGreedMetadata(
    val error: String?
)
