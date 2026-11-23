// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement as LayoutArrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.content.res.Configuration
import android.app.PictureInPictureParams
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.hongguotv.core.Danmaku
import com.hongguotv.core.DanmakuRepository
import com.hongguotv.core.OfficialCommentRepository
import com.hongguotv.core.Series
import com.hongguotv.core.seasonNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val qualityOptions = listOf(1080, 720, 480)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    series: Series,
    episodeIds: List<String>,
    startIndex: Int,
    onClose: () -> Unit,
    onOpenSeries: (Series) -> Unit = {}
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
        ExoPlayer.Builder(context)
            // 起播门槛压到 300ms、追帧 500ms：换集/开播几乎是点了就出画面
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 12_000,
                        /* maxBufferMs = */ 45_000,
                        /* bufferForPlaybackMs = */ 300,
                        /* bufferForPlaybackAfterRebufferMs = */ 500
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build().apply { playWhenReady = true }
    }
    var remote by remember { mutableStateOf<RemoteVideo?>(null) }
    // 上下各 5 集预取池：滑到哪集直接从池里取，不再等取流
    val pool = remember { mutableStateMapOf<Int, RemoteVideo>() }
    var prefetchJob by remember { mutableStateOf<Job?>(null) }
    var swipeHint by remember { mutableStateOf<String?>(null) }
    var danmakuStyle by remember { mutableStateOf(DanmakuStyle.load(context)) }
    // 上下滑预览：上一集 / 下一集的独立预览流（静音，滑动时才播）
    var prevPreview by remember { mutableStateOf<RemoteVideo?>(null) }
    var nextPreview by remember { mutableStateOf<RemoteVideo?>(null) }
    var pageHeight by remember { mutableIntStateOf(0) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var holdingSpeed by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val uiScope = rememberCoroutineScope()
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var danmakuOn by remember { mutableStateOf(true) }
    var danmaku by remember { mutableStateOf<List<Danmaku>>(emptyList()) }
    var showDanmakuInput by remember { mutableStateOf(false) }
    var danmakuText by remember { mutableStateOf("") }
    var danmakuNote by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var seasonList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var relatedList by remember { mutableStateOf<List<Series>>(emptyList()) }

    val activity = context as? Activity
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    // 横屏时把系统状态栏收起来（图标不压在画面上），时间/电量由播放器自己在底部那条里画
    DisposableEffect(isLandscape) {
        val act = context as? Activity
        val controller = act?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val bars = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        if (controller != null) {
            if (isLandscape) controller.hide(bars) else controller.show(bars)
        }
        onDispose { if (controller != null) runCatching { controller.show(bars) } }
    }

    // 时钟（横屏右上角 + 底部状态条共用）
    LaunchedEffect(Unit) {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        while (true) {
            clock = fmt.format(java.util.Date())
            delay(20_000)
        }
    }

    // 电量
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(Unit) {
        runCatching {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            runCatching {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            runCatching { prefetchJob?.cancel() }
            runCatching { prevPreview?.close() }
            runCatching { nextPreview?.close() }
            pool.values.forEach { runCatching { it.close() } }
            pool.clear()
            runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
            runCatching { player.release() }
            runCatching { remote?.close() }
        }
    }

    LaunchedEffect(index, quality) {
        loading = true
        error = null
        val reusable = pool.remove(index)
        val result = if (reusable != null) {
            Result.success(reusable)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val info = Streams.resolve(episodeIds[index], quality)
                    RemoteVideo(Graph.http, info).prepare()
                }
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
            // 断点续看：同一集有记录就从上次位置接着播
            val (savedIndex, savedPosition) = Graph.library.progressOf(series.id)
            if (savedIndex == index && savedPosition > 3000L) {
                runCatching { player.seekTo(savedPosition) }
            }
            player.playWhenReady = true
            runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
            loading = false
        }.onFailure {
            loading = false
            error = it.message ?: "取流失败，稍后再试"
        }
    }

    // 官方弹幕：按 30 秒窗口分段拉取，随播放进度滚动加载
    LaunchedEffect(index, danmakuOn) {
        danmaku = emptyList()
        if (!danmakuOn) return@LaunchedEffect
        val repository = DanmakuRepository(Graph.http)
        var lastWindowStart = -1L
        while (true) {
            val total = duration.coerceAtLeast(1L)
            val start = ((position / 20_000L) * 20_000L)
            if (start != lastWindowStart) {
                val fetched = withContext(Dispatchers.IO) {
                    runCatching {
                        repository.window(series.id, episodeIds[index], total, start).comments
                    }.getOrDefault(emptyList())
                }
                if (fetched.isNotEmpty()) {
                    danmaku = (danmaku + fetched).distinctBy { it.id }.sortedBy { it.offsetMs }
                }
                lastWindowStart = start
            }
            delay(8_000)
        }
    }

    // 同 IP / 上一季 / 下一季 / 同作者推荐（结局时展示）
    LaunchedEffect(series.id) {
        val detail = withContext(Dispatchers.IO) { runCatching { Graph.repo.detail(series.id) } }.getOrNull()
            ?: return@LaunchedEffect
        val current = seasonNumber(series.title)
        seasonList = detail.sameName
            .filter { it.id != series.id }
            .filter { seasonNumber(it.title) != Int.MAX_VALUE }
            .sortedBy { seasonNumber(it.title) }
            .take(12)
        relatedList = detail.related.filter { it.id != series.id }.take(12)
        if (seasonList.isEmpty() && relatedList.isEmpty()) {
            seasonList = detail.related
        }
        current // 保留 current 参与计算，避免误用
    }

    // 预取：当前集就绪后，后台把「上 5 集 + 下 5 集」依次准备好（2 路并发），滑动换集秒开
    LaunchedEffect(index, quality, loading) {
        if (loading) return@LaunchedEffect
        prefetchJob?.cancel()
        prefetchJob = launch {
            delay(900)
            val targets = (1..5).flatMap { listOf(index + it, index - it) }
                .filter { it in episodeIds.indices && it != index }
            val gate = Semaphore(2)
            targets.forEach { target ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        if (pool.containsKey(target)) return@withPermit
                        val video = runCatching {
                            val info = Streams.resolve(episodeIds[target], quality)
                            RemoteVideo(Graph.http, info).prepare()
                        }.getOrNull()
                        if (video != null) {
                            if (pool.size < 12 && !pool.containsKey(target)) pool[target] = video
                            else runCatching { video.close() }
                        }
                    }
                }
            }
        }
    }

    // 滑远之后，把池子里超出 ±6 集的实例关掉，避免占着连接
    LaunchedEffect(index) {
        pool.keys.filter { kotlin.math.abs(it - index) > 6 }.forEach { key ->
            runCatching { pool.remove(key)?.close() }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) loading = false
                if (playbackState == Player.STATE_ENDED) {
                    runCatching { Graph.library.putHistory(series, index, episodeIds.size) }
                    if (index < episodeIds.lastIndex) {
                        index += 1
                    } else {
                        runCatching { player.pause() }
                        ending = true
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width.toFloat() / videoSize.height.toFloat()
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
        var lastSaved = 0L
        while (true) {
            position = runCatching { player.currentPosition }.getOrDefault(0L)
            duration = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
            // 每 4 秒落一次盘，杀进程/断电也不会丢太多进度
            if (System.currentTimeMillis() - lastSaved > 4000L && position > 0L) {
                lastSaved = System.currentTimeMillis()
                runCatching {
                    Graph.library.putProgress(series, index, episodeIds.size, position)
                }
            }
            delay(400)
        }
    }

    // 上下滑预览用的相邻集流：单独实例、预读更小，滑到才播放
    LaunchedEffect(index, quality, loading) {
        if (loading) return@LaunchedEffect
        delay(600)
        launch(Dispatchers.IO) {
            if (index > 0) {
                val v = runCatching {
                    val info = Streams.resolve(episodeIds[index - 1], quality)
                    RemoteVideo(Graph.http, info, aheadBytes = 1L * 1024 * 1024).prepare()
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (prevPreview != v) { runCatching { prevPreview?.close() }; prevPreview = v }
                }
            } else {
                withContext(Dispatchers.Main) { runCatching { prevPreview?.close() }; prevPreview = null }
            }
            if (index < episodeIds.lastIndex) {
                val v = runCatching {
                    val info = Streams.resolve(episodeIds[index + 1], quality)
                    RemoteVideo(Graph.http, info, aheadBytes = 1L * 1024 * 1024).prepare()
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (nextPreview != v) { runCatching { nextPreview?.close() }; nextPreview = v }
                }
            } else {
                withContext(Dispatchers.Main) { runCatching { nextPreview?.close() }; nextPreview = null }
            }
        }
    }

    // 换集后的轻提示
    LaunchedEffect(index) {
        swipeHint = "第 ${index + 1} 集"
        delay(1100)
        swipeHint = null
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
            .pointerInput(index, episodeIds.size, pageHeight) {
                val limit = if (pageHeight > 0) pageHeight.toFloat() else 1f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        uiScope.launch {
                            drag.snapTo((drag.value + amount).coerceIn(-limit * 1.05f, limit * 1.05f))
                        }
                    },
                    onDragEnd = {
                        val d = drag.value
                        val dir = when {
                            d <= -limit * 0.2f && index < episodeIds.lastIndex -> 1
                            d >= limit * 0.2f && index > 0 -> -1
                            else -> 0
                        }
                        uiScope.launch {
                            if (dir != 0) {
                                drag.animateTo(dir * limit, tween(150))
                                index += dir
                                drag.snapTo(0f)
                            } else {
                                drag.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = {
                        uiScope.launch { drag.animateTo(0f, spring()) }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showControls = !showControls })
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { if (it.height > 0) pageHeight = it.height }
        ) {
            // 上一集预览（往下拉时露出）
            PreviewPage(
                video = prevPreview,
                playing = drag.value > 8f,
                aspectRatio = videoAspect,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (-pageHeight + drag.value).toInt()) }
            )
            // 下一集预览（往上推时露出）
            PreviewPage(
                video = nextPreview,
                playing = drag.value < -8f,
                aspectRatio = videoAspect,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (pageHeight + drag.value).toInt()) }
            )
            // 当前集
            // 用 TextureView 承载主画面：SurfaceView 在滑动位移/裁剪时会出现黑块
            AndroidView(
                factory = { ctx ->
                    AspectRatioFrameLayout(ctx).apply {
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val view = android.view.TextureView(ctx)
                        addView(
                            view,
                            android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        player.setVideoTextureView(view)
                    }
                },
                update = { root -> root.setAspectRatio(videoAspect) },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, drag.value.toInt()) }
            )
        }

        // 长按左/右区域 2x 快进
        if (!showEpisodePicker && !ending && !showInfo && !showDanmakuSettings && !showDanmakuInput) {
            SpeedHoldZone(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth(0.26f)
                    .align(Alignment.CenterStart),
                onStart = {
                    if (speed < 2.0f) {
                        holdingSpeed = true
                        player.setPlaybackSpeed(2.0f)
                    }
                },
                onEnd = {
                    if (holdingSpeed) {
                        holdingSpeed = false
                        player.setPlaybackSpeed(speed)
                    }
                }
            )
            SpeedHoldZone(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth(0.26f)
                    .align(Alignment.CenterEnd),
                onStart = {
                    if (speed < 2.0f) {
                        holdingSpeed = true
                        player.setPlaybackSpeed(2.0f)
                    }
                },
                onEnd = {
                    if (holdingSpeed) {
                        holdingSpeed = false
                        player.setPlaybackSpeed(speed)
                    }
                }
            )
            if (holdingSpeed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "2.0x 快进中",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 70.dp)
                            .background(Color(0x8C000000), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (danmakuOn && !showEpisodePicker && danmaku.isNotEmpty()) {
            DanmakuOverlay(
                items = danmaku,
                positionMs = position,
                playing = isPlaying && !loading,
                style = danmakuStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp, start = 8.dp, end = 8.dp)
            )
        }

        // 横屏：系统状态条搬到屏幕底部（时间 / 电量），顶部只留软件自己的信息
        if (isLandscape) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.BottomEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(clock, color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    if (charging) {
                        Text("⚡", color = Color(0xCCFFD24D), fontSize = 11.sp)
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        if (batteryPct in 0..100) "${batteryPct}%" else "--",
                        color = Color(0xCCFFFFFF),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 换集后的小提示（上滑/下滑的结果反馈）
        AnimatedVisibility(
            visible = swipeHint != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(260)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    swipeHint.orEmpty(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0x8C000000), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 控制条显示时的滑动提示
        if (showControls && !loading && !ending) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(end = 10.dp, top = 70.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (index < episodeIds.lastIndex) {
                    Text("↑ 下一集", color = Color(0xB3FFFFFF), fontSize = 10.sp)
                    Spacer(Modifier.height(4.dp))
                }
                if (index > 0) {
                    Text("↓ 上一集", color = Color(0xB3FFFFFF), fontSize = 10.sp)
                }
            }
        }

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

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(280))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = if (isLandscape) 30.dp else 16.dp, top = 12.dp),
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
                    // 横屏：时间放在右上角
                    if (isLandscape) {
                        Text(
                            clock,
                            color = Color(0xE6FFFFFF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
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
                    PlayPauseButton(playing = isPlaying) {
                        if (isPlaying) player.pause() else player.play()
                        showControls = true
                    }
                    Spacer(Modifier.width(8.dp))
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
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(bottom = 62.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(12.dp))
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
                    Pill(if (danmakuOn) "弹幕开" else "弹幕关") {
                        danmakuOn = !danmakuOn
                    }
                    Pill("弹幕设置") { showDanmakuSettings = true }
                    Pill("发弹幕") { showDanmakuInput = true; danmakuNote = null }
                    Pill("简介") { showInfo = true }
                    Pill(if (isLandscape) "竖屏" else "横屏") {
                        runCatching {
                            activity?.requestedOrientation = if (isLandscape)
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }
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
                    Spacer(Modifier.width(12.dp))
                }
            }
        }

        if (showDanmakuInput) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = danmakuText,
                            onValueChange = { if (it.length <= 40) danmakuText = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Pill("发送", true) {
                            val text = danmakuText.trim()
                            if (text.isBlank()) { danmakuNote = "先写点什么"; return@Pill }
                            if (!Graph.account.isLoggedIn()) {
                                danmakuNote = "发弹幕需要登录红果账号（我的 › 设置与自检 › 登录）"
                                return@Pill
                            }
                            danmakuNote = "正在发送…"
                            val seriesId = series.id
                            val episodeId = episodeIds[index]
                            val atPosition = position
                            danmakuText = ""
                            kotlinx.coroutines.MainScope().launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        OfficialCommentRepository(Graph.http)
                                            .publish(Graph.account.credentials(), seriesId, episodeId, text)
                                    }
                                }
                                result.onSuccess {
                                    danmaku = (danmaku + Danmaku(it, atPosition, text, 0)).sortedBy { it.offsetMs }
                                    danmakuNote = "已发送"
                                    showDanmakuInput = false
                                }.onFailure { danmakuNote = it.message ?: "发送失败" }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Pill("关闭") { showDanmakuInput = false }
                    }
                    danmakuNote?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = PlusColors.Gold, fontSize = 11.sp)
                    }
                }
            }
        }

        if (showDanmakuSettings) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .background(Color(0xF216161B), RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Text("弹幕设置", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    Text("显示行数", color = PlusColors.SubText, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 4, 5, 6).forEach { n ->
                            Pill("$n 行", danmakuStyle.rows == n) {
                                danmakuStyle = danmakuStyle.copy(rows = n).also {
                                    DanmakuStyle.save(context, it)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("透明度", color = PlusColors.SubText, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${(danmakuStyle.alpha * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                    }
                    Slider(
                        value = danmakuStyle.alpha,
                        onValueChange = {
                            danmakuStyle = danmakuStyle.copy(alpha = it)
                        },
                        onValueChangeFinished = { DanmakuStyle.save(context, danmakuStyle) },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = PlusColors.Accent,
                            activeTrackColor = PlusColors.Accent
                        )
                    )

                    Spacer(Modifier.height(6.dp))
                    Text("稀疏度", color = PlusColors.SubText, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "稀疏" to 0.5f,
                            "标准" to 1f,
                            "密集" to 1.5f
                        ).forEach { (label, value) ->
                            Pill(label, kotlin.math.abs(danmakuStyle.density - value) < 0.1f) {
                                danmakuStyle = danmakuStyle.copy(density = value).also {
                                    DanmakuStyle.save(context, it)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("恢复默认") {
                            danmakuStyle = DanmakuStyle().also { DanmakuStyle.save(context, it) }
                        }
                        Pill("完成", true) { showDanmakuSettings = false }
                    }
                }
            }
        }

        if (showInfo) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(series.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "第 ${index + 1}/${episodeIds.size} 集" +
                            (if (series.badge.isNotBlank()) " · ${series.badge}" else ""),
                        color = Color(0xFFAAAAAA), fontSize = 11.sp
                    )
                    if (series.tags.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(series.tags, color = PlusColors.Gold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("简介", color = PlusColors.SubText, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        series.description.ifBlank { "暂无简介" },
                        color = Color(0xFFDDDDDD), fontSize = 12.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Pill("收起") { showInfo = false }
                }
            }
        }

        if (ending) {
            Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(16.dp)
                ) {
                    Text(
                        "《${series.title}》看完了",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (seasonList.isNotEmpty() || relatedList.isNotEmpty())
                            "同 IP / 同一系列的其他季，接着看不用找"
                        else "正在找同 IP 的其他季…",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(14.dp))

                    fun seasonLabel(item: Series): String {
                        val current = seasonNumber(series.title)
                        val other = seasonNumber(item.title)
                        return when {
                            other != Int.MAX_VALUE && current != Int.MAX_VALUE && other > current -> "下一季"
                            other != Int.MAX_VALUE && current != Int.MAX_VALUE && other < current -> "上一季"
                            other != Int.MAX_VALUE -> "第 ${other} 季"
                            else -> item.badge.ifBlank { "同 IP" }
                        }
                    }

                    if (seasonList.isNotEmpty()) {
                        Text("同一系列", color = PlusColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(seasonList, key = { it.id }) { item ->
                                SeriesCard(item, seasonLabel(item)) { ending = false; onOpenSeries(item) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (seasonList.isEmpty() && relatedList.isNotEmpty()) {
                        Text("同 IP 推荐", color = PlusColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(relatedList, key = { it.id }) { item ->
                                SeriesCard(item, item.badge.ifBlank { "同 IP" }) { ending = false; onOpenSeries(item) }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("再看一遍") {
                            ending = false
                            index = 0
                        }
                        Pill("收起") { ending = false }
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

@Composable
private fun SeriesCard(item: Series, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable { onClick() }
    ) {
        Box(
            Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PlusColors.SurfaceHigh)
        ) {
            if (item.cover.isNotBlank()) {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                label,
                color = Color.White,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x40FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            if (playing) {
                val barWidth = w * 0.3f
                val radius = CornerRadius(barWidth / 2)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(barWidth, h * 0.76f),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.58f, h * 0.12f),
                    size = Size(barWidth, h * 0.76f),
                    cornerRadius = radius
                )
            } else {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.1f)
                    lineTo(w * 0.92f, h * 0.5f)
                    lineTo(w * 0.2f, h * 0.9f)
                    close()
                }
                drawPath(path = path, color = Color.White)
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

/** 相邻集预览页：自带 ExoPlayer（静音），用 TextureView 才能参与 Compose 的位移/裁剪合成。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviewPage(
    video: RemoteVideo?,
    playing: Boolean,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val texture = remember { mutableStateOf<android.view.TextureView?>(null) }

    LaunchedEffect(video) {
        if (video == null) return@LaunchedEffect
        val p = ExoPlayer.Builder(context).build().apply { volume = 0f }
        runCatching {
            val source = ProgressiveMediaSource.Factory { VideoDataSource(video) }
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri("preview://${video.info.url.hashCode()}")
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                )
            p.setMediaSource(source)
            p.prepare()
            p.playWhenReady = playing
        }
        previewPlayer = p
        texture.value?.let { p.setVideoTextureView(it) }
    }
    LaunchedEffect(playing) { previewPlayer?.playWhenReady = playing }
    DisposableEffect(Unit) { onDispose { runCatching { previewPlayer?.release() } } }

    AndroidView(
        factory = { ctx ->
            AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                val view = android.view.TextureView(ctx)
                addView(
                    view,
                    android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                texture.value = view
                previewPlayer?.setVideoTextureView(view)
            }
        },
        update = { root -> root.setAspectRatio(aspectRatio) },
        modifier = modifier
    )
}

/** 长按 0.45 秒触发的加速区：抬手即恢复。 */
@Composable
private fun SpeedHoldZone(
    modifier: Modifier,
    onStart: () -> Unit,
    onEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var held = true
                var armed = false
                val timer = scope.launch {
                    delay(450)
                    if (held) {
                        armed = true
                        onStart()
                    }
                }
                while (held) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) held = false
                }
                timer.cancel()
                if (armed) onEnd()
            }
        }
    )
}
