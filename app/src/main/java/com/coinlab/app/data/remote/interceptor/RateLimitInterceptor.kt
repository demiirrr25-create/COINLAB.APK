package com.coinlab.app.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class RateLimitInterceptor : Interceptor {

    private val lastRequestTime = AtomicLong(0)
    private val minIntervalMs = 200L // 0.2s between requests (~300/min)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // Only rate-limit CoinGecko API calls
        val host = chain.request().url.host
        if (host.contains("coingecko")) {
            synchronized(this) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestTime.get()
                if (elapsed < minIntervalMs) {
                    try {
                        Thread.sleep(minIntervalMs - elapsed)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                lastRequestTime.set(System.currentTimeMillis())
            }
        }

        val response = chain.proceed(chain.request())

        // Handle 429 Too Many Requests with exponential backoff
        if (response.code == 429) {
            response.close()
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 5
            val waitMs = (retryAfter * 1000).coerceAtMost(30_000)
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
