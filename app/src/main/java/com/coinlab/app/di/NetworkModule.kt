package com.coinlab.app.di

import com.coinlab.app.BuildConfig
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.api.CryptoCompareApi
import com.coinlab.app.data.remote.api.FearGreedApi
import com.coinlab.app.data.remote.api.GitHubApi
import com.coinlab.app.data.remote.interceptor.RateLimitInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRateLimitInterceptor(): RateLimitInterceptor = RateLimitInterceptor()

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool = ConnectionPool(5, 30, TimeUnit.SECONDS)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        rateLimitInterceptor: RateLimitInterceptor,
        connectionPool: ConnectionPool
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader("Accept", "application/json")
                // Only add CoinGecko API key for CoinGecko requests
                val host = originalRequest.url.host
                if (host.contains("coingecko")) {
                    requestBuilder.addHeader("x-cg-demo-api-key", "CG-FHpn2gST1GXaShKC3Utfn7NU")
                }
                chain.proceed(requestBuilder.build())
            }
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("binance_http")
    fun provideBinanceOkHttpClient(connectionPool: ConnectionPool): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("binance_ws")
    fun provideBinanceWebSocketClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for WebSocket
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS) // Keep-alive pings
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("coingecko")
    fun provideCoinGeckoRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.COINGECKO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("binance")
    fun provideBinanceRetrofit(@Named("binance_http") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("cryptocompare")
    fun provideCryptoCompareRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.CRYPTOCOMPARE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("feargreed")
    fun provideFearGreedRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.alternative.me/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(@Named("coingecko") retrofit: Retrofit): CoinGeckoApi {
        return retrofit.create(CoinGeckoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBinanceApi(@Named("binance") retrofit: Retrofit): BinanceApi {
        return retrofit.create(BinanceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCryptoCompareApi(@Named("cryptocompare") retrofit: Retrofit): CryptoCompareApi {
        return retrofit.create(CryptoCompareApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFearGreedApi(@Named("feargreed") retrofit: Retrofit): FearGreedApi {
        return retrofit.create(FearGreedApi::class.java)
    }

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(@Named("binance_http") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(@Named("github") retrofit: Retrofit): GitHubApi {
        return retrofit.create(GitHubApi::class.java)
    }
}
