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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
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
    onOpenSeries: (Series) -> Unit = {},
    onWebPlay: (String) -> Unit = {}
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
    var routeHint by remember { mutableStateOf("") }
    var sitePlan by remember { mutableStateOf<SiteDash.Plan?>(null) }
    var siteFallback by remember { mutableIntStateOf(0) }

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
    var previewArmed by remember { mutableStateOf(false) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var holdingSpeed by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(-1) }
    var startLatency by remember { mutableIntStateOf(-1) }
    var slowLoad by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableLongStateOf(0L) }
    val veil = remember { Animatable(0f) }
    var startAt by remember { mutableLongStateOf(0L) }
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
    var social by remember { mutableStateOf(com.hongguotv.core.SeriesSocial()) }
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
            runCatching { Graph.library.putProgress(series, index, episodeIds.size, position) }
            runCatching { player.release() }
            runCatching { remote?.close() }
        }
    }

    LaunchedEffect(index) {
        // 换集不做生硬闪切：先压一层黑再淡出，画面是渐进的
        veil.snapTo(0.92f)
        veil.animateTo(0f, tween(360))
    }

    LaunchedEffect(index, quality) {
        loading = true
        error = null
        sitePlan = null
        siteFallback = 0
        startAt = SystemClock.uptimeMillis()
        startLatency = -1
        val reusable = pool.remove(index)
            ?: PrewarmPool.take(episodeIds[index], quality)

        var dashError: String? = null

        fun startWith(source: MediaSource) {
            player.setMediaSource(source, true)
            player.prepare()
            player.setPlaybackSpeed(speed)
            // 断点续看：同一集有记录就记下待跳转位置，等播放器 READY 再 seek
            val (savedIndex, savedPosition) = Graph.library.progressOf(series.id)
            pendingSeek = if (savedIndex == index && savedPosition > 3000L) savedPosition else 0L
            player.playWhenReady = true
            // loading 由首帧回调收起，避免"黑屏但没有任何提示"
        }

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
            routeHint = "App 接口"
            val source = ProgressiveMediaSource.Factory { VideoDataSource(video) }
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri("hongguoplus://episode/${episodeIds[index]}")
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .build()
                )
            startWith(source)
        }.onFailure { appError ->
            // App 接口拿不到时才去试站点直连：网页接口对未登录/受保护集数回 403，
            // 反过来先打它反而容易把这次会话带进风控，所以只做兜底。
            if (reusable == null) {
                val plan = withContext(Dispatchers.IO) {
                    runCatching { SiteDash.resolve(series.id, episodeIds[index], quality) }
                        .onFailure { dashError = it.message }
                        .getOrNull()
                }
                if (plan != null) {
                    sitePlan = plan
                    routeHint = "站点直连 ${plan.quality}"
                    startWith(
                        DashMediaSource.Factory(DefaultDataSource.Factory(context))
                            .createMediaSource(
                                MediaItem.Builder()
                                    .setUri(android.net.Uri.fromFile(SiteDash.mpdFile(plan)))
                                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                                    .build()
                            )
                    )
                    return@LaunchedEffect
                }
            }
            loading = false
            error = buildString {
                append("App 接口：").append(appError.message ?: "取流失败，稍后再试")
                dashError?.let { append("\n站点直连：").append(it) }
            }
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
        val detail = withContext(Dispatchers.IO) { runCatching { ContentCache.detail(series.id) } }.getOrNull()
            ?: return@LaunchedEffect
        social = detail.social
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

    // 预取：只准备「上一集 + 下一集」，且串行执行——预取太多会把带宽和取流接口挤爆，反而拖慢当前这集
    LaunchedEffect(index, quality, loading) {
        if (loading) return@LaunchedEffect
        prefetchJob?.cancel()
        prefetchJob = launch {
            delay(3500)
            val targets = listOf(index + 1, index - 1)
                .filter { it in episodeIds.indices && it != index }
            val gate = Semaphore(1)
            targets.forEach { target ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        if (pool.containsKey(target)) return@withPermit
                        val video = runCatching {
                            val info = Streams.resolve(episodeIds[target], quality)
                            RemoteVideo(Graph.http, info).prepare()
                        }.getOrNull()
                        if (video != null) {
                            if (pool.size < 2 && !pool.containsKey(target)) pool[target] = video
                            else runCatching { video.close() }
                        }
                    }
                }
            }
        }
    }

    // 换集就把上一/下一集的预览实例关掉，别让它们继续挂着连接
    LaunchedEffect(index) {
        runCatching { prevPreview?.close() }
        runCatching { nextPreview?.close() }
        prevPreview = null
        nextPreview = null
    }

    // 滑远之后，把池子里超出 ±2 集的实例关掉，避免占着连接
    LaunchedEffect(index) {
        pool.keys.filter { kotlin.math.abs(it - index) > 2 }.forEach { key ->
            runCatching { pool.remove(key)?.close() }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    if (pendingSeek > 0L) {
                        val target = pendingSeek
                        pendingSeek = 0L
                        runCatching { player.seekTo(target) }
                    }
                    if (startAt > 0L && startLatency < 0) {
                        startLatency = (SystemClock.uptimeMillis() - startAt).toInt()
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    // 这一集看完了：按整集时长记进度，别把已看完的集清成第 0 秒
                    val whole = duration.takeIf { it > 0L } ?: runCatching { player.duration }.getOrDefault(0L)
                    runCatching { Graph.library.putProgress(series, index, episodeIds.size, whole) }
                    runCatching {
                        Graph.account.uploadProgress(series.id, episodeIds[index], index, whole, whole, episodeIds.size)
                    }
                    if (index < episodeIds.lastIndex) {
                        index += 1
                    } else {
                        runCatching { player.pause() }
                        ending = true
                    }
                }
            }

            override fun onPlayerError(playbackError: androidx.media3.common.PlaybackException) {
                // 站点直连的 DASH 在部分机型上会抛 Unexpected runtime error：
                // 先自动降级成「视频/音频两路 progressive 拼接」再试一次，成功后不再报错。
                val plan = sitePlan
                if (plan != null && siteFallback == 0) {
                    siteFallback = 1
                    runCatching {
                        val factory = DefaultHttpDataSource.Factory()
                            .setUserAgent(SiteSession.UA)
                            .setDefaultRequestProperties(
                                mapOf("Referer" to "${SiteSession.SITE}/")
                            )
                        val video = ProgressiveMediaSource.Factory(factory)
                            .createMediaSource(
                                MediaItem.Builder()
                                    .setUri(plan.video)
                                    .setMimeType(MimeTypes.VIDEO_MP4)
                                    .build()
                            )
                        val audio = ProgressiveMediaSource.Factory(factory)
                            .createMediaSource(
                                MediaItem.Builder()
                                    .setUri(plan.audio)
                                    .setMimeType(MimeTypes.AUDIO_MP4)
                                    .build()
                            )
                        player.setMediaSource(MergingMediaSource(video, audio), true)
                        player.prepare()
                        player.playWhenReady = true
                        return
                    }.onFailure { siteFallback = 2 }
                }
                runCatching { Streams.evict(episodeIds[index], quality) }
                loading = false
                val root = playbackError.cause
                error = buildString {
                    append("播放失败（").append(playbackError.errorCodeName).append("）：")
                    append(playbackError.message ?: "解码器或片源异常")
                    if (root != null) {
                        append("\n原因：").append(root.javaClass.simpleName)
                        root.message?.take(160)?.let { append("：").append(it) }
                    }
                }
            }

            // 真正画出第一帧才收掉 loading，否则会出现“黑屏但没提示”的错觉
            override fun onRenderedFirstFrame() {
                loading = false
                if (startAt > 0L && startLatency < 0) {
                    startLatency = (SystemClock.uptimeMillis() - startAt).toInt()
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
                if (System.currentTimeMillis() % 30_000L < 500L) {
                    runCatching {
                        Graph.account.uploadProgress(series.id, episodeIds[index], index, position, duration, episodeIds.size)
                    }
                }
            }
            delay(400)
        }
    }

    // 上下滑预览用的相邻集流：单独实例、预读更小，滑到才播放
    LaunchedEffect(index, quality, loading, previewArmed) {
        if (!previewArmed || loading) return@LaunchedEffect
        delay(400)
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

    LaunchedEffect(index, quality, loading) {
        slowLoad = false
        if (loading) {
            delay(6000)
            if (loading) slowLoad = true
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
            .background(if (isLandscape) Color.Black else Color(0xFF141416))
            .pointerInput(index, episodeIds.size, pageHeight) {
                val limit = if (pageHeight > 0) pageHeight.toFloat() else 1f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        previewArmed = true
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
                detectTapGestures(onTap = {
                    // 点一下只呼出/收起控制层，不打断播放（想暂停点中间的圆形按钮）
                    showControls = !showControls
                })
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

        if (veil.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = veil.value))
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
                    if (slowLoad) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "网络较慢，点一下可重试",
                            color = Color(0xFFBBBBBB),
                            fontSize = 11.sp
                        )
                    }
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
                        Pill("网页播放") {
                            onWebPlay("${SiteSession.SITE}/player/${series.id}/${episodeIds[index]}")
                        }
                        Pill("换清晰度") { quality = qualityOptions.first() }
                    }
                }
            }
        }

        if (isLandscape) {
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
                            runCatching { Graph.library.putProgress(series, index, episodeIds.size, position) }
                            runCatching {
                                Graph.account.uploadProgress(series.id, episodeIds[index], index, position, duration, episodeIds.size)
                            }
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
        }

        // 竖屏：抖音式极简界面（顶部集数/倍速/更多，右侧竖排操作，底部剧集信息+进度+选集）
        if (!isLandscape) {
            PortraitControls(
                visible = showControls,
                series = series,
                social = social,
                episodeIndex = index,
                episodeCount = episodeIds.size,
                speed = speed,
                position = position,
                duration = duration,
                isPlaying = isPlaying,
                startLatency = startLatency,
                commentCount = danmaku.size,
                onBack = {
                    runCatching { Graph.library.putProgress(series, index, episodeIds.size, position) }
                    onClose()
                },
                onTogglePlay = {
                    if (isPlaying) player.pause() else player.play()
                    showControls = true
                },
                onSeek = { ratio ->
                    if (duration > 0) {
                        position = (duration * ratio).toLong()
                        player.seekTo(position)
                    }
                },
                onSpeed = {
                    val next = speedOptions[(speedOptions.indexOf(speed) + 1) % speedOptions.size]
                    speed = next
                    player.setPlaybackSpeed(next)
                    Graph.library.defaultSpeed = next
                },
                onFavorite = {
                    runCatching { Graph.library.toggleFavorite(series) }
                },
                onComment = { showDanmakuInput = true; danmakuNote = null },
                onShare = {
                    runCatching {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "https://hongguoduanju.com/detail?series_id=${series.id}")
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
                    }
                },
                onEpisodes = { showEpisodePicker = true },
                onInfo = { showInfo = true },
                onQuality = {
                    quality = qualityOptions[(qualityOptions.indexOf(quality) + 1) % qualityOptions.size]
                    Graph.library.defaultQuality = quality
                },
                onDanmakuSettings = { showDanmakuSettings = true },
                onPip = {
                    val activity = context as? Activity
                    runCatching {
                        if (activity != null) {
                            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
                            activity.enterPictureInPictureMode(params)
                        }
                    }
                },
                onLandscape = {
                    runCatching { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                }
            )
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
                            (if (series.badge.isNotBlank()) " · ${series.badge}" else "") +
                            (if (startLatency > 0) "  ·  起播 $startLatency ms" else ""),
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
            EpisodeSheet(
                series = series,
                episodeCount = episodeIds.size,
                current = index,
                favorite = Graph.library.isFavorite(series.id),
                onPick = { picked ->
                    index = picked
                    showEpisodePicker = false
                },
                onFavorite = { runCatching { Graph.library.toggleFavorite(series) } },
                onInfo = { showEpisodePicker = false; showInfo = true },
                onSeries = { showEpisodePicker = false; ending = true },
                onDismiss = { showEpisodePicker = false }
            )
        }
    }
}


// ── 竖屏：抖音式播放界面 ─────────────────────────────────────────
@Composable
private fun PortraitControls(
    visible: Boolean,
    series: Series,
    social: com.hongguotv.core.SeriesSocial = com.hongguotv.core.SeriesSocial(),
    episodeIndex: Int,
    episodeCount: Int,
    speed: Float,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    startLatency: Int,
    commentCount: Int,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: () -> Unit,
    onFavorite: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onEpisodes: () -> Unit,
    onInfo: () -> Unit,
    onQuality: () -> Unit,
    onDanmakuSettings: () -> Unit,
    onPip: () -> Unit,
    onLandscape: () -> Unit
) {
    var showMore by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(Graph.library.isFavorite(series.id)) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(series.id) {
        favorite = Graph.library.isFavorite(series.id)
        val prefs = context.getSharedPreferences("hongguoplus_likes", android.content.Context.MODE_PRIVATE)
        liked = prefs.getBoolean("liked_${series.id}", false)
        likeCount = prefs.getInt("likes_${series.id}", 0)
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(280))) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                    )
            )

            // 暂停时：整屏正中的播放按钮
            if (!isPlaying) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x73000000))
                            .clickable { onTogglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(28.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.26f, size.height * 0.1f)
                                lineTo(size.width * 0.88f, size.height * 0.5f)
                                lineTo(size.width * 0.26f, size.height * 0.9f)
                                close()
                            }
                            drawPath(path, Color.White)
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
            ) {
                // 顶部：返回 · 集数 · 倍速 · 更多
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "‹",
                        color = Color.White,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "第 ${episodeIndex + 1} 集",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x4D000000))
                            .clickable { onSpeed() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⟳", color = Color(0xFFE6E6E6), fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("${speed}x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "⋮",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier.clickable { showMore = true }
                    )
                }

                Spacer(Modifier.weight(1f))

                // 右侧竖排：收藏 / 评论 / 点赞 / 分享
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.padding(end = 12.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        PortraitAction(
                            glyph = RailGlyph.STAR,
                            label = social.favoriteText.ifBlank { "收藏" },
                            active = favorite,
                            activeTint = PlusColors.Gold
                        ) {
                            favorite = runCatching { Graph.library.toggleFavorite(series) }.getOrDefault(favorite)
                        }
                        PortraitAction(
                            glyph = RailGlyph.COMMENT,
                            label = social.commentText.ifBlank { if (commentCount > 0) "$commentCount" else "评论" }
                        ) {
                            onComment()
                        }
                        PortraitAction(
                            glyph = RailGlyph.HEART,
                            label = social.likeText.ifBlank { if (likeCount > 0) likeCount.toString() else "点赞" },
                            active = liked,
                            activeTint = Color(0xFFFF4D5F)
                        ) {
                            liked = !liked
                            likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                            val prefs = context.getSharedPreferences("hongguoplus_likes", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("liked_${series.id}", liked).putInt("likes_${series.id}", likeCount).apply()
                        }
                        PortraitAction(glyph = RailGlyph.SHARE, label = social.shareText.ifBlank { "分享" }) { onShare() }
                    }
                }

                // 底部：剧集信息 + 进度 + 选集条
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "系列剧 · 全 $episodeCount 集",
                            color = Color(0xFFD6D6D6),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x4D000000))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                        if (startLatency >= 0) {
                            Spacer(Modifier.width(8.dp))
                            Text("起播 ${startLatency}ms", color = Color(0x99FFFFFF), fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        series.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onInfo() }
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ${episodeIndex + 1} 集",
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "展开",
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onInfo() }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 细进度条（点哪跳哪）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) onSeek((offset.x / size.width).coerceIn(0f, 1f))
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x4DFFFFFF))
                        )
                        val ratio = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        Box(
                            Modifier
                                .fillMaxWidth(ratio)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(PlusColors.Accent)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(position), color = Color(0xB3FFFFFF), fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(duration), color = Color(0xB3FFFFFF), fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x59000000))
                                .clickable { onEpisodes() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("选集 · 全 $episodeCount 集", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text("⌃", color = Color(0xFFB3B3B3), fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x40000000))
                                .clickable { onLandscape() },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(20.dp)) {
                                val stroke = size.width * 0.1f
                                fun arrow(x0: Float, y0: Float, x1: Float, y1: Float) {
                                    drawLine(
                                        Color.White,
                                        Offset(size.width * x0, size.height * y0),
                                        Offset(size.width * x1, size.height * y1),
                                        strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                                // 左上 → 右下
                                arrow(0.06f, 0.06f, 0.42f, 0.06f)
                                arrow(0.06f, 0.06f, 0.06f, 0.42f)
                                // 右下 → 左上
                                arrow(0.94f, 0.94f, 0.58f, 0.94f)
                                arrow(0.94f, 0.94f, 0.94f, 0.58f)
                            }
                        }
                    }
                }
            }

            // 控制层可见时：画面正中的播放/暂停按钮
            if (visible) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0x8C000000))
                            .clickable { onTogglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.size(28.dp)) {
                            if (isPlaying) {
                                val bar = size.width * 0.16f
                                drawRoundRect(
                                    Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.26f, size.height * 0.1f),
                                    size = Size(bar, size.height * 0.8f),
                                    cornerRadius = CornerRadius(bar * 0.3f)
                                )
                                drawRoundRect(
                                    Color.White,
                                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.1f),
                                    size = Size(bar, size.height * 0.8f),
                                    cornerRadius = CornerRadius(bar * 0.3f)
                                )
                            } else {
                                val path = Path().apply {
                                    moveTo(size.width * 0.28f, size.height * 0.12f)
                                    lineTo(size.width * 0.86f, size.height * 0.5f)
                                    lineTo(size.width * 0.28f, size.height * 0.88f)
                                    close()
                                }
                                drawPath(path, Color.White)
                            }
                        }
                    }
                }
            }

            // 更多菜单
            if (showMore) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable { showMore = false }
                )
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 92.dp, end = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xF21F1F21))
                        .clickable { }
                        .padding(vertical = 4.dp)
                ) {
                    listOf(
                        "清晰度：当前档位" to onQuality,
                        "弹幕设置" to onDanmakuSettings,
                        "简介" to onInfo,
                        "横屏观看" to onLandscape,
                        "画中画" to onPip
                    ).forEach { (label, action) ->
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    showMore = false
                                    action()
                                }
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 右侧栏图标类型。 */
private enum class RailGlyph { STAR, COMMENT, HEART, SHARE }

@Composable
private fun PortraitAction(
    glyph: RailGlyph,
    label: String,
    active: Boolean = false,
    activeTint: Color = Color.White,
    onClick: () -> Unit
) {
    val tint = if (active) activeTint else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(22.dp)) { RailIcon.draw(this, glyph, tint) }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color(0xE6FFFFFF), fontSize = 10.sp)
    }
}

/** 右侧栏图标：全部用 Canvas 画，避免 emoji 风格不一致。 */

private object RailIcon {
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, glyph: RailGlyph, tint: Color) {
        val w = scope.size.width
        val h = scope.size.height
        when (glyph) {
            RailGlyph.STAR -> {
                val path = Path()
                val cx = w / 2f
                val cy = h / 2f
                val outer = w * 0.5f
                val inner = outer * 0.42f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val angle = Math.toRadians(-90.0 + i * 36.0)
                    val x = cx + (r * Math.cos(angle)).toFloat()
                    val y = cy + (r * Math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                scope.drawPath(path, tint)
            }
            RailGlyph.COMMENT -> {
                // 圆角气泡 + 小尾巴
                val radius = w * 0.22f
                val bubble = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = 0f, right = w, bottom = h * 0.78f,
                            cornerRadius = CornerRadius(radius)
                        )
                    )
                }
                scope.drawPath(bubble, tint)
                val tail = Path().apply {
                    moveTo(w * 0.26f, h * 0.72f)
                    lineTo(w * 0.24f, h)
                    lineTo(w * 0.52f, h * 0.74f)
                    close()
                }
                scope.drawPath(tail, tint)
                // 三个点用背景色抠出来
                val dot = w * 0.085f
                for (i in 0..2) {
                    scope.drawCircle(
                        Color(0x33000000).copy(alpha = 0.75f),
                        radius = dot,
                        center = androidx.compose.ui.geometry.Offset(w * (0.24f + i * 0.26f), h * 0.38f)
                    )
                }
            }
            RailGlyph.HEART -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.92f)
                    cubicTo(w * 0.12f, h * 0.62f, w * 0.02f, h * 0.38f, w * 0.18f, h * 0.2f)
                    cubicTo(w * 0.32f, h * 0.06f, w * 0.46f, h * 0.12f, w * 0.5f, h * 0.26f)
                    cubicTo(w * 0.54f, h * 0.12f, w * 0.68f, h * 0.06f, w * 0.82f, h * 0.2f)
                    cubicTo(w * 0.98f, h * 0.38f, w * 0.88f, h * 0.62f, w * 0.5f, h * 0.92f)
                    close()
                }
                scope.drawPath(path, tint)
            }
            RailGlyph.SHARE -> {
                // 斜向箭头
                val stroke = w * 0.11f
                scope.drawLine(tint, androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.9f),
                    androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.18f), stroke)
                val head = Path().apply {
                    moveTo(w * 0.4f, h * 0.14f)
                    lineTo(w * 0.9f, h * 0.1f)
                    lineTo(w * 0.86f, h * 0.6f)
                    close()
                }
                scope.drawPath(head, tint)
            }
        }
    }
}

// ── 选集：底部圆角弹层 ───────────────────────────────────────────
@Composable
private fun EpisodeSheet(
    series: Series,
    episodeCount: Int,
    current: Int,
    favorite: Boolean,
    onPick: (Int) -> Unit,
    onFavorite: () -> Unit,
    onInfo: () -> Unit,
    onSeries: () -> Unit,
    onDismiss: () -> Unit
) {
    val pageSize = 30
    var page by remember(current) { mutableIntStateOf(current / pageSize) }
    var fav by remember(favorite) { mutableStateOf(favorite) }
    val pages = ((episodeCount + pageSize - 1) / pageSize).coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable { onDismiss() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF1E1E20))
                .clickable { }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66FFFFFF))
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlusColors.SurfaceHigh)
                ) {
                    if (series.cover.isNotBlank()) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = series.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        series.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "第 ${current / pageSize + 1} 段 · 全 $episodeCount 集",
                        color = Color(0xFF8C8C8C),
                        fontSize = 12.sp
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                Text(
                    "简介",
                    color = Color(0xFF8C8C8C),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onInfo() }
                )
                Text(
                    "选集",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "系列剧",
                    color = Color(0xFF8C8C8C),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onSeries() }
                )
            }
            // 分段切换
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(pages) { p ->
                    val start = p * pageSize + 1
                    val end = minOf((p + 1) * pageSize, episodeCount)
                    Text(
                        "$start-$end",
                        color = if (p == page) Color.White else Color(0xFF7A7A7A),
                        fontSize = 13.sp,
                        fontWeight = if (p == page) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clickable { page = p }
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val from = page * pageSize
                val to = minOf(from + pageSize, episodeCount)
                items(to - from) { offset ->
                    val i = from + offset
                    Box(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (i == current) Color(0x33FF5A36) else Color(0xFF2A2A2D))
                            .clickable { onPick(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            color = if (i == current) PlusColors.Accent else Color(0xFFDDDDDD),
                            fontSize = 13.sp,
                            fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (i == current) {
                            Text(
                                "▮▮",
                                color = PlusColors.Accent,
                                fontSize = 6.sp,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(PlusColors.Accent)
                    .clickable {
                        fav = runCatching { Graph.library.toggleFavorite(series) }.getOrDefault(fav)
                        onFavorite()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (fav) "★ 已收藏" else "☆ 收藏",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
