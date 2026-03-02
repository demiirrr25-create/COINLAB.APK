package com.coinlab.app.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path

data class GitHubUser(
    val login: String = "",
    val id: Long = 0,
    val avatar_url: String = "",
    val name: String? = null,
    val bio: String? = null,
    val public_repos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
)

interface GitHubApi {

    @GET("users/{username}")
    suspend fun getUser(
        @Path("username") username: String
    ): GitHubUser
}
