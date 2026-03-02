package com.coinlab.app.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight rate limiter for CoinGecko fallback requests only.
 * Uses short non-blocking delay (max 200ms) and a single retry for 429.
 * Binance requests bypass this entirely (separate OkHttpClient).
 */
class RateLimitInterceptor : Interceptor {

    private val lastRequestTime = AtomicLong(0)
    private val minIntervalMs = 200L // 0.2s between requests (~300/min)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // Only rate-limit CoinGecko API calls (Binance uses separate client)
        val host = chain.request().url.host
        if (host.contains("coingecko")) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime.get()
            if (elapsed < minIntervalMs) {
                val sleepMs = (minIntervalMs - elapsed).coerceAtMost(200L)
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestTime.set(System.currentTimeMillis())
        }

        val response = chain.proceed(chain.request())

        // Handle 429 Too Many Requests — single retry with short wait
        if (response.code == 429 && host.contains("coingecko")) {
            response.close()
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 2
            val waitMs = (retryAfter * 1000).coerceAtMost(5_000L) // Max 5s, not 30s
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return chain.proceed(chain.request())
        }

        return response
    }
}
