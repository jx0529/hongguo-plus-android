// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.hongguotv.core.RemoteVideo
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val qualityOptions = listOf(1080, 720, 480)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    series: Series,
    episodeIds: List<String>,
    startIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, episodeIds.lastIndex.coerceAtLeast(0))) }
    var quality by remember { mutableIntStateOf(Graph.library.defaultQuality) }
    var speed by remember { mutableFloatStateOf(Graph.library.defaultSpeed) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var showEpisodePicker by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    var remote by remember { mutableStateOf<RemoteVideo?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
            runCatching { player.release() }
            runCatching { remote?.close() }
        }
    }

    LaunchedEffect(index, quality) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val info = Graph.repo.stream(episodeIds[index], quality)
                RemoteVideo(Graph.http, info).prepare()
            }
        }
        result.onSuccess { video ->
            runCatching { remote?.close() }
            remote = video
            val source = ProgressiveMediaSource.Factory { VideoDataSource(video) }
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri("hongguoplus://episode/${episodeIds[index]}")
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                )
            player.setMediaSource(source, true)
            player.prepare()
            player.setPlaybackSpeed(speed)
            player.playWhenReady = true
            runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
            loading = false
        }.onFailure {
            loading = false
            error = it.message ?: "取流失败，稍后再试"
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) loading = false
                if (playbackState == Player.STATE_ENDED) {
                    runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
                    if (index < episodeIds.lastIndex) index += 1 else runCatching { player.pause() }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            position = runCatching { player.currentPosition }.getOrDefault(0L)
            duration = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
            delay(400)
        }
    }

    LaunchedEffect(showControls, isPlaying, loading) {
        if (showControls && isPlaying && !loading) {
            delay(3200)
            showControls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(index, episodeIds.size) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        when {
                            drag <= -110f && index < episodeIds.lastIndex -> index += 1
                            drag >= 110f && index > 0 -> index -= 1
                        }
                        drag = 0f
                    },
                    onVerticalDrag = { _, amount ->
                        drag += amount
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = PlusColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "第 ${index + 1} 集 取流中…",
                        color = Color(0xFFBBBBBB),
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("重试", true) { error = null; index = index }
                        Pill("换清晰度") { quality = qualityOptions.first() }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showControls) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 16.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "‹",
                        color = Color.White,
                        fontSize = 26.sp,
                        modifier = Modifier.clickable {
                            runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
                            onClose()
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            series.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "第 ${index + 1}/${episodeIds.size} 集 · 上滑下一集",
                            color = Color(0xFFBBBBBB),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable {
                                if (isPlaying) player.pause() else player.play()
                                showControls = true
                            }
                            .padding(horizontal = 6.dp)
                    )
                    Text(
                        formatTime(position),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.width(42.dp)
                    )
                    Slider(
                        value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { ratio ->
                            if (duration > 0) {
                                position = (duration * ratio).toLong()
                                player.seekTo(position)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = PlusColors.Accent,
                            activeTrackColor = PlusColors.Accent,
                            inactiveTrackColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatTime(duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.width(46.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 62.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill("${quality}p") {
                        quality = qualityOptions[(qualityOptions.indexOf(quality) + 1) % qualityOptions.size]
                        Graph.library.defaultQuality = quality
                    }
                    Pill("${speed}x") {
                        val next = speedOptions[(speedOptions.indexOf(speed) + 1) % speedOptions.size]
                        speed = next
                        player.setPlaybackSpeed(next)
                        Graph.library.defaultSpeed = next
                    }
                    Pill("选集") { showEpisodePicker = true }
                    Pill("画中画") {
                        val activity = context as? Activity
                        runCatching {
                            if (activity != null) {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(9, 16))
                                    .build()
                                activity.enterPictureInPictureMode(params)
                            }
                        }
                    }
                }
            }
        }

        if (showEpisodePicker) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${series.title} · 共 ${episodeIds.size} 集",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "收起",
                            color = PlusColors.SubText,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { showEpisodePicker = false }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(episodeIds.size) { i ->
                            Box(
                                Modifier
                                    .height(44.dp)
                                    .background(
                                        if (i == index) PlusColors.Accent.copy(alpha = 0.3f) else PlusColors.SurfaceHigh,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        index = i
                                        showEpisodePicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = if (i == index) PlusColors.Accent else Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
