package com.borja.langtooldroid

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/borborborja/langtooldroid/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}

object GitHubClient {
    private const val BASE_URL = "https://api.github.com/"

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: GitHubApi by lazy {
        retrofit.create(GitHubApi::class.java)
    }
}
