package com.coinlab.app.di

import com.coinlab.app.BuildConfig
import com.coinlab.app.data.remote.api.BinanceApi
import com.coinlab.app.data.remote.api.BinanceFuturesApi
import com.coinlab.app.data.remote.api.BitgetApi
import com.coinlab.app.data.remote.api.BybitApi
import com.coinlab.app.data.remote.api.CoinGeckoApi
import com.coinlab.app.data.remote.api.CryptoCompareApi
import com.coinlab.app.data.remote.api.FearGreedApi
import com.coinlab.app.data.remote.api.GateioApi
import com.coinlab.app.data.remote.api.GitHubApi
import com.coinlab.app.data.remote.api.OkxApi
import com.coinlab.app.data.remote.interceptor.BinanceFallbackInterceptor
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
    fun provideConnectionPool(): ConnectionPool = ConnectionPool(15, 120, TimeUnit.SECONDS) // v7.7: more connections, longer keep-alive

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
                // CoinGecko public API works without key (10-30 req/min)
                // Do NOT send expired/invalid demo keys — they cause HTTP 401
                chain.proceed(requestBuilder.build())
            }
            .connectionPool(connectionPool)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("binance_http")
    fun provideBinanceOkHttpClient(connectionPool: ConnectionPool): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BinanceFallbackInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectionPool(connectionPool)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
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

    // ─── v12.0 — Liquidation Map Exchange APIs ───────────────────────

    @Provides
    @Singleton
    @Named("binance_futures")
    fun provideBinanceFuturesRetrofit(@Named("binance_http") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://fapi.binance.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("bybit")
    fun provideBybitRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.bybit.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("okx")
    fun provideOkxRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.okx.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("bitget")
    fun provideBitgetRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.bitget.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("gateio")
    fun provideGateioRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.gateio.ws/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBinanceFuturesApi(@Named("binance_futures") retrofit: Retrofit): BinanceFuturesApi {
        return retrofit.create(BinanceFuturesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBybitApi(@Named("bybit") retrofit: Retrofit): BybitApi {
        return retrofit.create(BybitApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOkxApi(@Named("okx") retrofit: Retrofit): OkxApi {
        return retrofit.create(OkxApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBitgetApi(@Named("bitget") retrofit: Retrofit): BitgetApi {
        return retrofit.create(BitgetApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGateioApi(@Named("gateio") retrofit: Retrofit): GateioApi {
        return retrofit.create(GateioApi::class.java)
    }
}
