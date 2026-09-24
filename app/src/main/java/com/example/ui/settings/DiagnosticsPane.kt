package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.NetworkDiagnosticsLogger
import com.example.data.remote.NetworkLogEntry
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.BugReport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.model.TabloChannel
import com.example.model.TabloDevice
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class StreamTestStep(
    val name: String,
    val status: String, // "PENDING", "RUNNING", "PASS", "FAIL"
    val details: String = ""
)

fun resolveHlsUri(basePlaylistUrl: String, relativeUri: String): String {
    if (relativeUri.startsWith("http://") || relativeUri.startsWith("https://")) {
        return relativeUri
    }
    val lastSlash = basePlaylistUrl.lastIndexOf('/')
    if (lastSlash == -1) return relativeUri
    val baseDir = basePlaylistUrl.substring(0, lastSlash + 1)
    return if (relativeUri.startsWith("/")) {
        val domainEnd = basePlaylistUrl.indexOf('/', basePlaylistUrl.indexOf("://") + 3)
        val domain = if (domainEnd == -1) basePlaylistUrl else basePlaylistUrl.substring(0, domainEnd)
        "$domain$relativeUri"
    } else {
        "$baseDir$relativeUri"
    }
}

@Composable
fun DiagnosticsPane(
    focusRequester: FocusRequester,
    tabloDevice: TabloDevice?,
    allChannels: List<TabloChannel>,
    onRequestSidebar: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val logs = NetworkDiagnosticsLogger.logs

    val coroutineScope = rememberCoroutineScope()
    var showStreamTestArea by remember { mutableStateOf(false) }
    var selectedChannelIndex by remember { mutableStateOf(0) }
    val testChannel = if (allChannels.isNotEmpty()) {
        allChannels[selectedChannelIndex.coerceIn(0, allChannels.size - 1)]
    } else null

    var testSteps by remember { mutableStateOf<List<StreamTestStep>?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs.size, searchQuery) {
        if (searchQuery.isBlank()) {
            logs.toList()
        } else {
            val q = searchQuery.lowercase()
            logs.filter { it.url.lowercase().contains(q) || it.method.lowercase().contains(q) || it.status.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("diagnostics_pane")
    ) {
        // Top Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "API & Network Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live trace of all Tablo Cloud & Device API requests (sensitive tokens auto-redacted)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showStreamTestArea = !showStreamTestArea },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showStreamTestArea) TabloTeal else Color(0xFF1E293B)
                    ),
                    border = BorderStroke(1.dp, if (showStreamTestArea) Color.Transparent else TvBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Stream Test",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showStreamTestArea) "Hide Stream Test" else "Stream Test", color = Color.White, fontSize = 13.sp)
                }

                Button(
                    onClick = { NetworkDiagnosticsLogger.clear() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF332222)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("clear_diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Trace", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        }

        // Expanded Stream Test Panel
        if (showStreamTestArea) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = TvSurfaceElevated,
                border = BorderStroke(1.dp, TvBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HLS Stream Step-by-Step Self-Test (No ExoPlayer)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (selectedChannelIndex > 0) {
                                        selectedChannelIndex--
                                    } else if (allChannels.isNotEmpty()) {
                                        selectedChannelIndex = allChannels.size - 1
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                enabled = allChannels.isNotEmpty() && !isTesting,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("◀", color = Color.White, fontSize = 12.sp)
                            }

                            Text(
                                text = testChannel?.let { "${it.displayChannel} ${it.network}" } ?: "No Channels",
                                color = TabloTeal,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )

                            Button(
                                onClick = {
                                    if (allChannels.isNotEmpty()) {
                                        selectedChannelIndex = (selectedChannelIndex + 1) % allChannels.size
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                enabled = allChannels.isNotEmpty() && !isTesting,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("▶", color = Color.White, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = {
                                    val device = tabloDevice
                                    val channel = testChannel
                                    if (device != null && channel != null && !isTesting) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            isTesting = true
                                            var step1 = StreamTestStep("1. POST /watch (Signed)", "RUNNING")
                                            var step2 = StreamTestStep("2. GET playlist_url", "PENDING")
                                            var step3 = StreamTestStep("3. GET variant/segment", "PENDING")
                                            var step4 = StreamTestStep("4. DELETE session", "PENDING")
                                            testSteps = listOf(step1, step2, step3, step4)

                                            var playlistUrl: String? = null
                                            var sessionToken: String? = null

                                            // Step 1: POST /watch (signed)
                                            try {
                                                val channelIdentifier = channel.identifier
                                                    ?: channel.channelPath.substringAfter("/guide/channels/")
                                                    ?: channel.channelId

                                                val path = "/guide/channels/$channelIdentifier/watch"
                                                val watchUrl = "${device.localBaseUrl}$path"
                                                val isGen4 = device.isGen4 || channel.identifier != null
                                                val bodyStr = if (isGen4) com.example.data.remote.TabloGen4Auth.makeWatchBody(device.clientId) else ""
                                                val (authHeader, dateHeader) = if (isGen4) com.example.data.remote.TabloGen4Auth.makeDeviceAuth("POST", path, bodyStr) else Pair("", "")

                                                val client = okhttp3.OkHttpClient.Builder()
                                                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                    .build()

                                                val reqBuilder = okhttp3.Request.Builder()
                                                    .url(watchUrl)
                                                    .post(bodyStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                                                    .header("User-Agent", com.example.data.remote.TabloGen4Auth.USER_AGENT_WATCH)

                                                if (isGen4) {
                                                    reqBuilder.header("Authorization", authHeader)
                                                    reqBuilder.header("Date", dateHeader)
                                                }

                                                client.newCall(reqBuilder.build()).execute().use { response ->
                                                    val code = response.code
                                                    val body = response.body?.string() ?: ""

                                                    var redactedBody = body
                                                    redactedBody = redactedBody.replace(Regex(""""(?:token|sessionToken)":\s*"[^"]+""""), "\"token\": \"[REDACTED]\"")
                                                    redactedBody = redactedBody.replace(Regex(""""(?:lighthouseToken|device_token)":\s*"[^"]+""""), "\"lighthouseToken\": \"[REDACTED]\"")

                                                    val tokenMatch = Regex(""""token":\s*"([^"]+)"""").find(body)
                                                    sessionToken = tokenMatch?.groupValues?.get(1)

                                                    val playlistMatch = Regex(""""playlist_url":\s*"([^"]+)"""").find(body)
                                                    playlistUrl = playlistMatch?.groupValues?.get(1)
                                                    if (playlistUrl != null && !playlistUrl!!.startsWith("http")) {
                                                        playlistUrl = "${device.localBaseUrl}$playlistUrl"
                                                    }

                                                    val isOk = code in 200..299 && !playlistUrl.isNullOrBlank()
                                                    step1 = step1.copy(
                                                        status = if (isOk) "PASS" else "FAIL",
                                                        details = "HTTP Status: $code\n\nResponse Body:\n$redactedBody"
                                                    )
                                                    testSteps = listOf(step1, step2, step3, step4)
                                                }
                                            } catch (e: Exception) {
                                                step1 = step1.copy(
                                                    status = "FAIL",
                                                    details = "Error: ${e.message}"
                                                )
                                                testSteps = listOf(step1, step2, step3, step4)
                                            }

                                            // Step 2: GET playlist_url (plain, with User-Agent)
                                            if (step1.status == "PASS" && playlistUrl != null) {
                                                step2 = step2.copy(status = "RUNNING")
                                                testSteps = listOf(step1, step2, step3, step4)
                                                try {
                                                    val client = okhttp3.OkHttpClient.Builder()
                                                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                        .build()

                                                    val request = okhttp3.Request.Builder()
                                                        .url(playlistUrl!!)
                                                        .header("User-Agent", "Tablo-FAST/1.7.0")
                                                        .build()

                                                    client.newCall(request).execute().use { response ->
                                                        val code = response.code
                                                        val headersStr = response.headers.names().joinToString("\n") { name ->
                                                            "$name: ${response.header(name)}"
                                                        }
                                                        val body = response.body?.string() ?: ""
                                                        val bodyExcerpt = body.take(500)

                                                        val isOk = code in 200..299
                                                        step2 = step2.copy(
                                                            status = if (isOk) "PASS" else "FAIL",
                                                            details = "HTTP Status: $code\n\nResponse Headers:\n$headersStr\n\nBody Excerpt (First 500 chars):\n$bodyExcerpt"
                                                        )
                                                        testSteps = listOf(step1, step2, step3, step4)

                                                        // Step 3: GET variant or segment
                                                        if (isOk) {
                                                            step3 = step3.copy(status = "RUNNING")
                                                            testSteps = listOf(step1, step2, step3, step4)
                                                            try {
                                                                var targetUri = ""
                                                                val lines = body.lineSequence().map { it.trim() }.toList()
                                                                for (line in lines) {
                                                                    if (line.isNotEmpty() && !line.startsWith("#")) {
                                                                        targetUri = line
                                                                        break
                                                                    }
                                                                }

                                                                if (targetUri.isEmpty()) {
                                                                    step3 = step3.copy(
                                                                        status = "FAIL",
                                                                        details = "No segment or variant URI found in m3u8 playlist."
                                                                    )
                                                                    testSteps = listOf(step1, step2, step3, step4)
                                                                } else {
                                                                    val resolvedUrl = resolveHlsUri(playlistUrl!!, targetUri)
                                                                    val redactedResolvedUrl = com.example.data.remote.NetworkDiagnosticsLogger.redactSensitiveData(resolvedUrl)

                                                                    val subRequest = okhttp3.Request.Builder()
                                                                        .url(resolvedUrl)
                                                                        .header("User-Agent", "Tablo-FAST/1.7.0")
                                                                        .build()

                                                                    client.newCall(subRequest).execute().use { subResp ->
                                                                        val subCode = subResp.code
                                                                        val bytes = subResp.body?.bytes()?.size ?: 0
                                                                        val subIsOk = subCode in 200..299

                                                                        step3 = step3.copy(
                                                                            status = if (subIsOk) "PASS" else "FAIL",
                                                                            details = "Resolved URL: $redactedResolvedUrl\nHTTP Status: $subCode\nBytes received: $bytes"
                                                                        )
                                                                        testSteps = listOf(step1, step2, step3, step4)
                                                                    }
                                                                }
                                                            } catch (subE: Exception) {
                                                                step3 = step3.copy(
                                                                    status = "FAIL",
                                                                    details = "Error: ${subE.message}"
                                                                )
                                                                testSteps = listOf(step1, step2, step3, step4)
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    step2 = step2.copy(
                                                        status = "FAIL",
                                                        details = "Error: ${e.message}"
                                                    )
                                                    testSteps = listOf(step1, step2, step3, step4)
                                                }
                                            }

                                            // Step 4: DELETE session
                                            if (!sessionToken.isNullOrBlank()) {
                                                step4 = step4.copy(status = "RUNNING")
                                                testSteps = listOf(step1, step2, step3, step4)
                                                try {
                                                    val path = "/player/sessions/$sessionToken"
                                                    val deleteUrl = "${device.localBaseUrl}$path"
                                                    val (authHeader, dateHeader) = com.example.data.remote.TabloGen4Auth.makeDeviceAuth("DELETE", path, "")

                                                    val client = okhttp3.OkHttpClient.Builder()
                                                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                                        .build()

                                                    val request = okhttp3.Request.Builder()
                                                        .url(deleteUrl)
                                                        .delete()
                                                        .header("User-Agent", com.example.data.remote.TabloGen4Auth.USER_AGENT_WATCH)
                                                        .header("Authorization", authHeader)
                                                        .header("Date", dateHeader)
                                                        .build()

                                                    client.newCall(request).execute().use { response ->
                                                        val code = response.code
                                                        val isOk = code in 200..299
                                                        step4 = step4.copy(
                                                            status = if (isOk) "PASS" else "FAIL",
                                                            details = "HTTP Status: $code"
                                                        )
                                                        testSteps = listOf(step1, step2, step3, step4)
                                                    }
                                                } catch (e: Exception) {
                                                    step4 = step4.copy(
                                                        status = "FAIL",
                                                        details = "Error: ${e.message}"
                                                    )
                                                    testSteps = listOf(step1, step2, step3, step4)
                                                }
                                            } else {
                                                step4 = step4.copy(
                                                    status = "FAIL",
                                                    details = "No session token from Step 1, DELETE skipped."
                                                )
                                                testSteps = listOf(step1, step2, step3, step4)
                                            }

                                            isTesting = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TabloTeal),
                                enabled = testChannel != null && !isTesting,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isTesting) "Running..." else "Run stream test", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    testSteps?.let { steps ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            steps.forEach { step ->
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        when (step.status) {
                                            "PASS" -> Color(0xFF10B981)
                                            "FAIL" -> Color(0xFFEF4444)
                                            "RUNNING" -> TabloTeal
                                            else -> Color(0xFF475569)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = step.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = step.status,
                                                color = when (step.status) {
                                                    "PASS" -> Color(0xFF10B981)
                                                    "FAIL" -> Color(0xFFEF4444)
                                                    "RUNNING" -> TabloTeal
                                                    else -> Color(0xFF94A3B8)
                                                },
                                                fontWeight = FontWeight.Black,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF020617), RoundedCornerShape(4.dp))
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = step.details,
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 10,
                                                modifier = Modifier.verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Filter Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(8.dp),
            color = TvSurface,
            border = BorderStroke(1.dp, TvBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(TabloTeal),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Filter requests by URL, method, status...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logs.isEmpty()) "No network calls captured yet." else "No matching requests found.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLogs, key = { it.id }) { logEntry ->
                    NetworkLogItem(logEntry)
                }
            }
        }
    }
}

@Composable
fun NetworkLogItem(entry: NetworkLogEntry) {
    val methodColor = when (entry.method) {
        "GET" -> Color(0xFF4CAF50)
        "POST" -> Color(0xFF2196F3)
        "DELETE" -> Color(0xFFFF5252)
        "PUT" -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }

    val isSuccess = entry.status.startsWith("2")
    val statusColor = if (isSuccess) Color(0xFF81C784) else Color(0xFFFF8A80)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = TvSurface,
        border = BorderStroke(1.dp, if (isSuccess) TvBorder else Color(0x66FF5252))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Method Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = methodColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, methodColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = entry.method,
                            color = methodColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = entry.status,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Token Prefix Badge
                    if (entry.tokenPrefix != "N/A") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x3300D2B4),
                            border = BorderStroke(1.dp, TabloTeal.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Token: ${entry.tokenPrefix}",
                                color = TabloTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Timestamp
                Text(
                    text = entry.timestamp,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // URL
            Text(
                text = entry.url,
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            if (entry.requestHeaderNames.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Request Headers Sent: ${entry.requestHeaderNames}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (entry.responseHeaders.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Response Headers:\n${entry.responseHeaders}",
                    color = Color(0xFFFFA726),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (entry.responseExcerpt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF11161D), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = entry.responseExcerpt,
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 6
                    )
                }
            }
        }
    }
}
