package com.example.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.remote.DiagnosticsInterceptor
import com.example.data.remote.NetworkDiagnosticsLogger
import com.example.model.TabloChannel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class MultiviewPlayerManager(
    private val context: Context,
    private val onPlayerError: (Int, String) -> Unit
) {
    private val maxStreams = 4
    private val players = mutableMapOf<Int, ExoPlayer>()
    private val currentUrls = mutableMapOf<Int, String>()
    private val retryCounts = mutableMapOf<Int, Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var focusedTileIndex = 0

    // Separate OkHttpClient for ExoPlayer HLS network requests (Port 80)
    // Free of Tablo HMAC / Lighthouse auth headers; includes DiagnosticsInterceptor for logging
    private val exoOkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val query = url.query
            val builder = request.newBuilder()

            // Strictly set User-Agent and strip any auth / signing headers
            builder.header("User-Agent", "Tablo-FAST/1.7.0")
            builder.removeHeader("Authorization")
            builder.removeHeader("Date")
            builder.removeHeader("Lighthouse")

            if (query != null) {
                if (query.endsWith("=") && query.count { it == '=' } == 1) {
                    val bareKey = query.removeSuffix("=")
                    val newUrl = url.newBuilder()
                        .query(null)
                        .addQueryParameter(bareKey, null) // Passing null value prevents '=' addition
                        .build()
                    builder.url(newUrl)
                } else if (!query.contains("=")) {
                    val newUrl = url.newBuilder()
                        .query(null)
                        .addQueryParameter(query, null)
                        .build()
                    builder.url(newUrl)
                }
            }
            chain.proceed(builder.build())
        }
        .addInterceptor(DiagnosticsInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun getPlayer(tileIndex: Int): ExoPlayer {
        return getOrCreatePlayer(tileIndex)
    }

    private fun getOrCreatePlayer(tileIndex: Int): ExoPlayer {
        val existing = players[tileIndex]
        if (existing != null) return existing

        // Optimized load control for low latency live streams on Fire TV
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,   // minBufferMs
                5000,   // maxBufferMs
                1000,   // bufferForPlaybackMs
                1500    // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(exoOkHttpClient)
            .setUserAgent("Tablo-FAST/1.7.0")

        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                val isFocused = (tileIndex == focusedTileIndex)
                volume = if (isFocused) 1.0f else 0.0f
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            retryCounts[tileIndex] = 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val formattedErr = formatPlaybackError(error)
                        val currentRetry = retryCounts.getOrDefault(tileIndex, 0)
                        Log.w(
                            "MultiviewPlayer",
                            "Playback error on tile $tileIndex (attempt $currentRetry):\n$formattedErr"
                        )
                        val activeUrl = currentUrls[tileIndex]
                        if (activeUrl == null) {
                            onPlayerError(tileIndex, formattedErr)
                            return
                        }
                        if (currentRetry < 2) {
                            retryCounts[tileIndex] = currentRetry + 1
                            handler.postDelayed({
                                val p = players[tileIndex]
                                if (p != null) {
                                    try {
                                        p.stop()
                                        p.setMediaItem(MediaItem.fromUri(activeUrl))
                                        p.prepare()
                                        p.play()
                                    } catch (e: Exception) {
                                        Log.e("MultiviewPlayer", "Error retrying tile $tileIndex: ${e.message}")
                                    }
                                }
                            }, 1500L)
                        } else {
                            Log.e("MultiviewPlayer", "Tile $tileIndex exceeded max retries:\n$formattedErr")
                            onPlayerError(tileIndex, formattedErr)
                        }
                    }
                })
            }
        players[tileIndex] = player
        return player
    }

    private fun formatPlaybackError(error: PlaybackException): String {
        val errorCodeName = error.errorCodeName
        var causeClass = ""
        var causeMsg = ""
        var httpCode: Int? = null
        var httpUrl: String? = null

        var current: Throwable? = error
        while (current != null) {
            val className = current.javaClass.simpleName
            val msg = current.message
            if (current !is PlaybackException && causeClass.isEmpty()) {
                causeClass = className.ifBlank { current.javaClass.name }
                causeMsg = msg ?: ""
            }
            if (current is HttpDataSource.HttpDataSourceException) {
                httpUrl = current.dataSpec?.uri?.toString()
                if (current is HttpDataSource.InvalidResponseCodeException) {
                    httpCode = current.responseCode
                }
            }
            current = current.cause
        }

        val sb = StringBuilder()
        sb.append("[").append(errorCodeName).append("]")
        if (causeClass.isNotEmpty()) {
            sb.append(" ").append(causeClass)
            if (causeMsg.isNotEmpty()) {
                sb.append(": ").append(causeMsg)
            }
        } else if (!error.message.isNullOrBlank()) {
            sb.append(" ").append(error.message)
        }

        if (httpCode != null || !httpUrl.isNullOrBlank()) {
            val redactedUrl = NetworkDiagnosticsLogger.redactSensitiveData(httpUrl ?: "")
            if (httpCode != null) {
                sb.append("\nHTTP ").append(httpCode).append(" @ ").append(redactedUrl)
            } else if (redactedUrl.isNotEmpty()) {
                sb.append("\nURL: ").append(redactedUrl)
            }
        }
        return sb.toString()
    }

    fun playChannel(tileIndex: Int, channel: TabloChannel, streamUrl: String) {
        val player = getOrCreatePlayer(tileIndex)
        val targetUrl = if (streamUrl.isNotEmpty()) streamUrl else channel.streamUrl
        if (targetUrl.isEmpty()) return

        if (currentUrls[tileIndex] == targetUrl && player.playbackState != Player.STATE_IDLE) {
            // Already playing this stream
            return
        }

        currentUrls[tileIndex] = targetUrl
        retryCounts[tileIndex] = 0
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(targetUrl))
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.05f)
                        .setMinPlaybackSpeed(0.95f)
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            // Only active audio tile outputs sound; all others remain at full broadcast video brightness
            val isFocused = (tileIndex == focusedTileIndex)
            player.volume = if (isFocused) 1.0f else 0.0f
        } catch (e: Exception) {
            Log.e("MultiviewPlayer", "Failed to start playback on tile $tileIndex: ${e.message}")
        }
    }

    /**
     * Set which tile currently has audio focus.
     * All video streams remain visually active; only the audio is adjusted.
     */
    fun setAudioTile(tileIndex: Int) {
        focusedTileIndex = tileIndex
        for (i in 0 until maxStreams) {
            val player = players[i] ?: continue
            val isFocused = (i == tileIndex)
            val targetVolume = if (isFocused) 1.0f else 0.0f
            if (player.volume != targetVolume) {
                player.volume = targetVolume
            }
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !isFocused)
                .build()
        }
    }

    fun swapStreams(fromTile: Int, toTile: Int) {
        val urlFrom = currentUrls[fromTile]
        val urlTo = currentUrls[toTile]
        if (urlFrom != null && urlTo != null) {
            currentUrls[fromTile] = urlTo
            currentUrls[toTile] = urlFrom

            val playerFrom = getOrCreatePlayer(fromTile)
            val playerTo = getOrCreatePlayer(toTile)

            playerFrom.setMediaItem(MediaItem.fromUri(urlTo))
            playerFrom.prepare()
            playerTo.setMediaItem(MediaItem.fromUri(urlFrom))
            playerTo.prepare()

            setAudioTile(focusedTileIndex)
        }
    }

    fun pauseAll() {
        players.values.forEach { it.pause() }
    }

    fun resumeAll() {
        players.values.forEach { it.play() }
    }

    fun stopTile(tileIndex: Int) {
        try {
            val player = players[tileIndex]
            if (player != null) {
                player.stop()
                player.clearMediaItems()
            }
            currentUrls.remove(tileIndex)
            retryCounts.remove(tileIndex)
        } catch (e: Exception) {
            Log.e("MultiviewPlayer", "Error stopping tile $tileIndex: ${e.message}")
        }
    }

    fun togglePlayPause(tileIndex: Int): Boolean {
        val player = players[tileIndex] ?: return false
        return if (player.isPlaying) {
            player.pause()
            false
        } else {
            player.play()
            true
        }
    }

    fun isPlaying(tileIndex: Int): Boolean {
        return players[tileIndex]?.isPlaying == true
    }

    fun play(tileIndex: Int) {
        players[tileIndex]?.play()
    }

    fun pause(tileIndex: Int) {
        players[tileIndex]?.pause()
    }

    fun goToLive(tileIndex: Int) {
        val player = players[tileIndex] ?: return
        try {
            player.seekToDefaultPosition()
            player.play()
        } catch (e: Exception) {
            Log.e("MultiviewPlayer", "Error seeking to live on tile $tileIndex: ${e.message}")
        }
    }

    fun getCurrentUrl(tileIndex: Int): String? = currentUrls[tileIndex]

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
        currentUrls.clear()
    }
}
