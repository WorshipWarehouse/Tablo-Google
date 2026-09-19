package com.example.data.remote

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/**
 * OkHttp Interceptor that signs local Tablo device requests using HMAC-MD5 per the Tablo Gen 4 spec.
 * Injects Authorization, Date (RFC 1123), Lighthouse, and User-Agent headers.
 */
class TabloGen4SigningInterceptor(
    private var lighthouseTokenProvider: () -> String? = { null }
) : Interceptor {

    fun setLighthouseTokenProvider(provider: () -> String?) {
        this.lighthouseTokenProvider = provider
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url

        // Do not touch HLS streaming segment / playlist requests on port 80 (they use URL token only)
        if (url.port == 80) {
            return chain.proceed(originalRequest)
        }

        // If Authorization header is already provided as Bearer token (Cloud API), do not override
        val existingAuth = originalRequest.header("Authorization")
        if (!existingAuth.isNullOrEmpty() && existingAuth.startsWith("Bearer ")) {
            return chain.proceed(originalRequest)
        }

        val builder = originalRequest.newBuilder()

        // Read body string for HMAC calculation
        val bodyString = extractBodyString(originalRequest)

        val method = originalRequest.method.uppercase()
        val path = url.encodedPath

        // Generate Auth header and RFC 1123 Date header via TabloGen4Auth
        val (authHeader, dateHeader) = TabloGen4Auth.makeDeviceAuth(method, path, bodyString)

        builder.header("Authorization", authHeader)
        builder.header("Date", dateHeader)

        if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            builder.header("User-Agent", TabloGen4Auth.USER_AGENT_WATCH)
        }

        val currentLighthouse = originalRequest.header("Lighthouse") ?: lighthouseTokenProvider()
        if (!currentLighthouse.isNullOrEmpty() && originalRequest.header("Lighthouse").isNullOrEmpty()) {
            builder.header("Lighthouse", currentLighthouse)
        }

        return chain.proceed(builder.build())
    }

    private fun extractBodyString(request: Request): String {
        val body = request.body ?: return ""
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } catch (_: Exception) {
            ""
        }
    }
}
