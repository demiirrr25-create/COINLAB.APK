package com.coinlab.app.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp Application Interceptor that tries Binance mirror endpoints
 * when the primary api.binance.com is unreachable.
 *
 * Binance provides mirror endpoints (api1-4.binance.com) with identical data.
 * Critical for users in regions where ISPs block api.binance.com (e.g. Turkey).
 *
 * Only tries PRIMARY + 2 mirrors to keep worst-case latency under 30s
 * (3 attempts × ~10s timeout each).
 */
class BinanceFallbackInterceptor : Interceptor {

    companion object {
        private const val TAG = "BinanceFallback"
        private const val PRIMARY_HOST = "api.binance.com"
        // Only 2 mirrors — if ISP blocks Binance, all mirrors likely blocked too
        // Keeping it short to fail fast and fall through to CoinGecko
        private val MIRROR_HOSTS = listOf(
            "api1.binance.com",
            "api2.binance.com"
        )
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val host = originalRequest.url.host

        // Only intercept Binance API calls
        if (host != PRIMARY_HOST) {
            return chain.proceed(originalRequest)
        }

        // Try primary first
        try {
            val response = chain.proceed(originalRequest)
            if (response.isSuccessful) return response
            response.close()
        } catch (e: IOException) {
            Log.w(TAG, "Primary $PRIMARY_HOST failed: ${e.message}")
        }

        // Try mirrors (max 2)
        var lastException: IOException? = null
        for (mirrorHost in MIRROR_HOSTS) {
            try {
                val mirrorUrl = originalRequest.url.newBuilder()
                    .host(mirrorHost)
                    .build()
                val mirrorRequest = originalRequest.newBuilder()
                    .url(mirrorUrl)
                    .build()
                val response = chain.proceed(mirrorRequest)
                if (response.isSuccessful) {
                    Log.i(TAG, "Mirror $mirrorHost succeeded")
                    return response
                }
                response.close()
            } catch (e: IOException) {
                Log.w(TAG, "Mirror $mirrorHost failed: ${e.message}")
                lastException = e
            }
        }

        throw lastException ?: IOException("All Binance endpoints unreachable")
    }
}
