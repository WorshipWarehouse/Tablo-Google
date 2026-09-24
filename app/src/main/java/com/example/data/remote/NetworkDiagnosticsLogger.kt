package com.example.data.remote

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.Response

data class NetworkLogEntry(
    val id: Long,
    val timestamp: String,
    val method: String,
    val url: String,
    val status: String,
    val tokenPrefix: String,
    val requestHeaderNames: String,
    val responseHeaders: String,
    val responseExcerpt: String
)

object NetworkDiagnosticsLogger {
    private var nextId = 1L
    val logs = mutableStateListOf<NetworkLogEntry>()
    private const val MAX_LOGS = 100

    @Synchronized
    fun log(
        method: String,
        url: String,
        status: String,
        tokenPrefix: String = "N/A",
        requestHeaderNames: String = "",
        responseHeaders: String = "",
        responseBody: String = ""
    ) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date())
        val redactedUrl = redactSensitiveData(url)
        val redactedBody = redactSensitiveData(responseBody).take(300)

        val entry = NetworkLogEntry(
            id = nextId++,
            timestamp = timeStr,
            method = method.uppercase(Locale.US),
            url = redactedUrl,
            status = status,
            tokenPrefix = tokenPrefix,
            requestHeaderNames = requestHeaderNames,
            responseHeaders = responseHeaders,
            responseExcerpt = redactedBody
        )

        logs.add(0, entry)
        if (logs.size > MAX_LOGS) {
            logs.removeAt(logs.lastIndex)
        }
    }

    fun clear() {
        logs.clear()
    }

    fun redactSensitiveData(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var s = input

        // Redact authorization headers / Bearer / Lighthouse / tablo tokens
        s = s.replace(Regex("""(?i)(Bearer\s+)[A-Za-z0-9._~+/-]+=*"""), "$1[REDACTED]")
        s = s.replace(Regex("""(?i)(Lighthouse:\s*)[^\s,;\r\n]+"""), "$1[REDACTED]")
        s = s.replace(Regex("""(?i)(tablo:[^:\s,;\r\n]+:)[a-fA-F0-9]+"""), "$1[REDACTED]")

        // Redact URL path tokens
        s = s.replace(Regex("""/account/[A-Za-z0-9_-]{10,}/"""), "/account/[REDACTED]/")
        s = s.replace(Regex("""/player/sessions/[A-Za-z0-9_-]{5,}/"""), "/player/sessions/[REDACTED]/")

        // Redact JSON sensitive fields: password, access_token, token, email, secret
        s = s.replace(
            Regex("""(?i)"(password|pass|access_token|token|secret|lighthouseToken|device_token)":\s*"[^"]+""""),
            "\"$1\": \"[REDACTED]\""
        )

        return s
    }
}

/**
 * OkHttp Interceptor that automatically records network calls into NetworkDiagnosticsLogger.
 */
class DiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val urlStr = request.url.toString()
        val requestHeaderNames = request.headers.names().joinToString(", ")

        // Extract session token prefix from URL query or path
        var tokenPrefix = "N/A"
        val queryStr = request.url.query
        if (!queryStr.isNullOrEmpty()) {
            val tokenMatch = Regex("""(?i)(?:token=|^|\?|&)([a-zA-Z0-9]{4,})""").find(queryStr)
            if (tokenMatch != null) {
                tokenPrefix = tokenMatch.groupValues[1].take(4)
            }
        }
        if (tokenPrefix == "N/A") {
            val path = request.url.encodedPath
            val sessionMatch = Regex("""/player/sessions/([a-zA-Z0-9]{4,})""").find(path)
            if (sessionMatch != null) {
                tokenPrefix = sessionMatch.groupValues[1].take(4)
            }
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            NetworkDiagnosticsLogger.log(
                method = method,
                url = urlStr,
                status = "Failed: ${e.message ?: "Network error"}",
                tokenPrefix = tokenPrefix,
                requestHeaderNames = requestHeaderNames,
                responseHeaders = "",
                responseBody = ""
            )
            throw e
        }

        val statusCode = response.code
        val statusMessage = response.message.ifBlank { if (statusCode in 200..299) "OK" else "Error" }
        val statusStr = "$statusCode $statusMessage"

        val respHeaderStr = if (statusCode !in 200..299) {
            response.headers.joinToString("\n") { "${it.first}: ${it.second}" }
        } else ""

        var bodyExcerpt = ""
        try {
            val responseBody = response.body
            if (responseBody != null) {
                val source = responseBody.source()
                source.request(Long.MAX_VALUE)
                val buffer = source.buffer
                bodyExcerpt = buffer.clone().readString(Charsets.UTF_8)
            }
        } catch (_: Exception) {}

        NetworkDiagnosticsLogger.log(
            method = method,
            url = urlStr,
            status = statusStr,
            tokenPrefix = tokenPrefix,
            requestHeaderNames = requestHeaderNames,
            responseHeaders = respHeaderStr,
            responseBody = bodyExcerpt
        )

        return response
    }
}
