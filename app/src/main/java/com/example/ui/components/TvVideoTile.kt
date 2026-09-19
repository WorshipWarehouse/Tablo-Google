package com.example.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.R
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.TabloAiring
import com.example.model.TabloChannel
import com.example.ui.theme.ActiveAudioBorderColor
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TvBackground

@OptIn(UnstableApi::class)
@Composable
fun TvVideoTile(
    tileIndex: Int,
    channel: TabloChannel,
    airing: TabloAiring?,
    player: ExoPlayer?,
    isAudioFocused: Boolean,
    onFocused: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    showOverlayInfo: Boolean = true
) {
    val context = LocalContext.current

    // Deterministic subtle gray border when focused; no dark/mute overlay on inactive feeds
    val borderStroke = if (isAudioFocused) {
        BorderStroke(2.dp, ActiveAudioBorderColor)
    } else {
        BorderStroke(1.dp, Color(0x1AFFFFFF))
    }

    val baseModifier = modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(4.dp))
        .border(borderStroke, RoundedCornerShape(4.dp))
        .clickable {
            onFocused()
            onSelect()
        }

    val combinedModifier = if (focusRequester != null) {
        baseModifier.focusRequester(focusRequester)
    } else {
        baseModifier
    }

    Box(
        modifier = combinedModifier.background(TvBackground)
    ) {
        // 1. Live Video Surface using TextureView for stable multiview compositing
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    val playerView = LayoutInflater.from(ctx)
                        .inflate(R.layout.tv_video_player_view, null) as PlayerView
                    playerView.player = player
                    playerView
                },
                update = { playerView ->
                    if (playerView.player != player) {
                        playerView.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Channel & Airing Subtle Lower Third Pill (Appears cleanly, no giant overlays)
        AnimatedVisibility(
            visible = showOverlayInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xCC050810)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Channel Badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isAudioFocused) Color(0xD91E293B) else Color(0x661E293B),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                border = if (isAudioFocused) BorderStroke(1.dp, ActiveAudioBorderColor.copy(alpha = 0.6f)) else BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${channel.displayChannel} ${channel.network}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Program Title
                    val title = airing?.title ?: "${channel.callSign} Live Broadcast"
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
