package com.coinlab.app.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp Application Interceptor that tries Binance mirror endpoints
 * when the primary api.binance.com is unreachable.
 *
 * Binance provides multiple mirror endpoints (api1-4.binance.com) that
 * serve identical data. This is critical for users in regions where
 * ISPs may block api.binance.com (e.g. Turkey).
 *
 * As an application interceptor, it can retry with modified URLs
 * before OkHttp's network layer.
 */
class BinanceFallbackInterceptor : Interceptor {

    companion object {
        private const val TAG = "BinanceFallback"
        private const val PRIMARY_HOST = "api.binance.com"
        private val MIRROR_HOSTS = listOf(
            "api1.binance.com",
            "api2.binance.com",
            "api3.binance.com",
            "api4.binance.com"
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
            // Non-successful but got a response — close and try mirrors
            response.close()
        } catch (e: IOException) {
            Log.w(TAG, "Primary $PRIMARY_HOST failed: ${e.message}")
            // Fall through to mirrors
        }

        // Try mirrors in order
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

        // All mirrors failed — throw the last exception
        throw lastException ?: IOException("All Binance endpoints unreachable")
    }
}
