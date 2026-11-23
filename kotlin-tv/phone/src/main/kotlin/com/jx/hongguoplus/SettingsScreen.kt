// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.CloudProfile
import com.hongguotv.core.ContentType
import com.hongguotv.core.PlaybackDeviceSession
import com.hongguotv.core.RankBoard
import com.hongguotv.core.RemoteVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一行自检结果。 */
data class CheckRow(val step: String, val ok: Boolean, val detail: String)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefreshMine: () -> Unit = {},
    onSiteLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var loggedIn by remember { mutableStateOf(Graph.account.isLoggedIn()) }
    var profile by remember { mutableStateOf<CloudProfile?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var cloudHistory by remember { mutableStateOf<Int?>(null) }
    var cloudFavorites by remember { mutableStateOf<Int?>(null) }

    var running by remember { mutableStateOf(false) }
    val checks = remember { mutableStateListOf<CheckRow>() }

    var version by remember {
        mutableStateOf(
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        )
    }
    var latest by remember { mutableStateOf<Updater.Release?>(null) }
    var updateChecked by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var cacheInfo by remember { mutableStateOf("") }
    var autoOn by remember { mutableStateOf(AutoUpdate.enabled(context)) }
    var autoText by remember { mutableStateOf(AutoUpdate.message) }
    var readyApk by remember { mutableStateOf(AutoUpdate.pending(context)) }
    var readyVersion by remember { mutableStateOf(AutoUpdate.pendingVersion(context)) }

    LaunchedEffect(autoOn) {
        while (true) {
            autoText = AutoUpdate.message
            readyApk = AutoUpdate.pending(context)
            readyVersion = AutoUpdate.pendingVersion(context)
            kotlinx.coroutines.delay(1500)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val coverBytes = CoverStore.sizeBytes(context)
            cacheInfo = "封面 ${coverBytes / 1024 / 1024}MB · 片源地址 ${Streams.cachedCount()} 条"
        }
    }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            profile = null
            cloudHistory = null
            cloudFavorites = null
            return@LaunchedEffect
        }
        profileError = null
        withContext(Dispatchers.IO) {
            runCatching { Graph.account.profile() }
                .onSuccess { profile = it }
                .onFailure { profileError = it.message ?: "账户信息读取失败" }
            cloudHistory = runCatching { Graph.account.progress().size }.getOrNull()
            cloudFavorites = runCatching { Graph.account.favorites().size }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = PlusColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.size(8.dp))
            Text("设置与自检", color = PlusColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            // ── 账户信息 ─────────────────────────────────────────────
            SectionTitle("账户")
            Card {
                if (!loggedIn) {
                    Text("未登录红果账号", color = PlusColors.Text, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("登录后这里会显示昵称、UID、头像、云端观看记录与收藏数量。", color = PlusColors.SubText, fontSize = 11.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("登录红果账号", PlusColors.Accent, Color.White) { onLogin() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatar = profile?.avatarUrl.orEmpty()
                        if (avatar.isNotBlank()) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                        } else {
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).background(PlusColors.SurfaceHigh),
                                contentAlignment = Alignment.Center
                            ) { Text("红", color = PlusColors.Gold, fontSize = 18.sp) }
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile?.nickname?.ifBlank { Graph.account.nickname }?.ifBlank { "红果用户" } ?: "红果用户",
                                color = PlusColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "UID：${profile?.id?.ifBlank { "—" } ?: "—"}",
                                color = PlusColors.SubText, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(PlusColors.SurfaceHigh)
                                .clickable {
                                    Graph.account.clear()
                                    loggedIn = false
                                    onRefreshMine()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) { Text("退出", color = PlusColors.SubText, fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoLine("登录方式", "手机号验证码（红果 passport）")
                    InfoLine("会话状态", if ((profile?.id ?: "").isNotBlank() || profile != null) "有效" else "待刷新")
                    InfoLine("云端观看记录", cloudHistory?.let { "$it 条" } ?: "读取中…")
                    InfoLine("云端收藏", cloudFavorites?.let { "$it 部" } ?: "读取中…")
                    InfoLine("设备 ID", Graph.account.credentials()?.deviceId?.ifBlank { "—" } ?: "—")
                    profileError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = PlusColors.Accent, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("账号信息只保存在本机，不上传任何第三方。", color = PlusColors.SubText, fontSize = 10.sp)
                }
            }

            // ── 播放自检 ─────────────────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("播放自检")
            Card {
                Text(
                    "逐项检查 首页接口 → 榜单 → 设备注册 → 详情 → 取流 → 分段读取，哪一步空了一眼就能看出来。",
                    color = PlusColors.SubText, fontSize = 11.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        if (running) "自检中…" else "开始自检",
                        if (running) PlusColors.SurfaceHigh else PlusColors.Accent,
                        if (running) PlusColors.SubText else Color.White
                    ) {
                        if (running) return@PillButton
                        running = true
                        checks.clear()
                        scope.launch {
                            val rows = withContext(Dispatchers.IO) { collectChecks() }
                            checks.addAll(rows)
                            running = false
                        }
                    }
                    if (checks.isNotEmpty()) {
                        PillButton("复制报告", PlusColors.SurfaceHigh, PlusColors.Text) {
                            clipboard.setText(AnnotatedString(buildReport(checks, version)))
                        }
                    }
                }
                if (checks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    checks.forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                if (row.ok) "✓" else "✗",
                                color = if (row.ok) PlusColors.Gold else PlusColors.Accent,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(row.step, color = PlusColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(row.detail, color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }

            // ── 存储与缓存 ────────────────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("存储")
            Card {
                InfoLine("已占用", cacheInfo.ifBlank { "统计中…" })
                Text(
                    "收藏、历史和已看过的剧封面会存到本机，弱网下也不用重新加载。",
                    color = PlusColors.SubText, fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(12.dp))
                PillButton("清除缓存", PlusColors.SurfaceHigh, PlusColors.Text) { confirmClear = true }
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠ 清除后会删除本机保存的封面缩略图和片源缓存地址，下次打开需要重新加载；收藏、观看历史和登录状态不受影响。",
                    color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ── 账号与安全 ────────────────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("账号与安全")
            Card {
                InfoLine("登录凭证", if (!loggedIn) "未登录" else if (Graph.account.credentialsEncrypted()) "已加密" else "加密中…")
                InfoLine("加密方式", "Android Keystore · AES-256-GCM")
                InfoLine("存放位置", "仅本机，不同步到任何服务器")
                InfoLine("网页登录", if (SiteSession.has(context)) "已登录（站点片源可用）" else "未登录")
                InfoLine("站点凭证长度", "${SiteSession.cookie(context).length} 字节")
                InfoLine(
                    "App 账号凭证",
                    runCatching { Graph.account.credentials()?.cookie?.length ?: 0 }.getOrDefault(0).let { "$it 字节" }
                )
                Spacer(Modifier.height(10.dp))
                PillButton(
                    if (SiteSession.has(context)) "重新登录红果网页" else "登录红果网页（解锁播放）",
                    PlusColors.SurfaceHigh, PlusColors.Text
                ) { onSiteLogin() }
                Spacer(Modifier.height(6.dp))
                Text(
                    "App 自己的播放接口近期一直返回空响应，片源改走红果网页版接口，需要一次网页登录。在 App 内登录一次即可，Cookie 只存本机。",
                    color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 15.sp
                )
                if (loggedIn) {
                    Spacer(Modifier.height(12.dp))
                    PillButton("退出登录", PlusColors.SurfaceHigh, PlusColors.Text) { confirmLogout = true }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ 退出只清除本机的登录凭证：云端进度同步会停止，本地收藏与观看历史仍然保留；重新登录需要重新扫码或验证码。",
                        color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 15.sp
                    )
                }
            }

            // ── 应用更新 ─────────────────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("应用更新")
            Card {
                InfoLine("当前版本", version.ifBlank { "—" })
                InfoLine(
                    "最新版本",
                    when {
                        updateBusy -> "检查中…"
                        latest != null -> latest!!.version
                        updateChecked -> "已是最新"
                        else -> "未检查"
                    }
                )
                latest?.let { rel ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        rel.notes.ifBlank { "无更新说明" }.take(400),
                        color = PlusColors.SubText, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
                if (updateChecked && Updater.lastReport.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(Updater.lastReport, color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 15.sp)
                }
                updateError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = PlusColors.Accent, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        if (updateBusy) "请稍候…" else "检查更新",
                        PlusColors.SurfaceHigh, PlusColors.Text
                    ) {
                        if (updateBusy) return@PillButton
                        updateBusy = true
                        updateError = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                Updater.checkDetailed(version)
                            }
                            latest = result.getOrNull()
                            updateChecked = true
                            result.exceptionOrNull()?.let {
                                updateError = "更新服务器连不上：${it.message ?: "网络不可达"}"
                            }
                            updateBusy = false
                        }
                    }
                    if (latest != null) {
                        PillButton(
                            if (progress in 1..99) "下载中 ${progress}%" else "下载并安装",
                            PlusColors.Accent, Color.White
                        ) {
                            if (progress in 0..99) return@PillButton
                            val rel = latest ?: return@PillButton
                            updateError = null
                            progress = 0
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        Updater.download(context, rel) { p -> progress = p }
                                    }
                                }
                                result.onSuccess { file ->
                                    progress = 100
                                    runCatching { Updater.install(context, file) }
                                        .onFailure { updateError = it.message ?: "无法拉起安装器" }
                                }.onFailure {
                                    progress = -1
                                    updateError = it.message ?: "下载失败"
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动下载更新", color = PlusColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            autoText.ifBlank { if (autoOn) "已开启：空闲时自动下载" else "已关闭：不会自动下载" },
                            color = PlusColors.SubText, fontSize = 10.sp, lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = autoOn,
                        onCheckedChange = {
                            autoOn = it
                            AutoUpdate.setEnabled(context, it)
                            autoText = AutoUpdate.message
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlusColors.Accent,
                            uncheckedThumbColor = PlusColors.SubText,
                            uncheckedTrackColor = PlusColors.SurfaceHigh
                        )
                    )
                }
                if (readyApk != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${readyVersion ?: "新版本"} 已下载好",
                                color = PlusColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                            Text("点右侧即可直接安装，不用再等下载", color = PlusColors.SubText, fontSize = 10.sp)
                        }
                        PillButton("安装", PlusColors.Accent, Color.White) {
                            val apk = readyApk ?: return@PillButton
                            runCatching { Updater.install(context, apk) }
                                .onFailure { updateError = it.message ?: "无法拉起安装器" }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (updateChecked && latest == null && updateError == null)
                        "已是最新版本（$version）· ${Updater.lastReport.ifBlank { "—" }}"
                    else "更新包经高速 CDN 分发，可直接覆盖安装，不会丢收藏与历史。",
                    color = PlusColors.SubText, fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除缓存？", color = PlusColors.Text) },
            text = {
                Text(
                    "将删除本机保存的封面缩略图与片源缓存地址（约等于你看到的占用）。\n\n收藏、观看历史、登录状态都不会被删除，但下次打开剧集需要重新取流。",
                    color = PlusColors.SubText, fontSize = 12.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    CoverStore.clear(context)
                    Streams.clearDisk()
                    cacheInfo = "封面 0MB · 片源地址 0 条"
                }) { Text("确认清除", color = PlusColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = PlusColors.SubText) }
            },
            containerColor = PlusColors.Surface
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？", color = PlusColors.Text) },
            text = {
                Text(
                    "退出会清除本机加密保存的登录凭证，云端观看进度将不再同步。\n\n本地收藏和历史会保留。下次登录需要重新扫码或输入验证码。",
                    color = PlusColors.SubText, fontSize = 12.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    Graph.account.clear()
                    loggedIn = false
                    onRefreshMine()
                }) { Text("确认退出", color = PlusColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消", color = PlusColors.SubText) }
            },
            containerColor = PlusColors.Surface
        )
    }
}

// ── 自检实现 ────────────────────────────────────────────────────
private suspend fun collectChecks(): List<CheckRow> {
    val rows = mutableListOf<CheckRow>()
    suspend fun step(name: String, block: suspend () -> String) {
        val started = System.currentTimeMillis()
        val row = try {
            val detail = kotlinx.coroutines.withTimeout(30_000) { block() }
            CheckRow(name, true, "$detail · ${System.currentTimeMillis() - started}ms")
        } catch (error: Throwable) {
            CheckRow(
                name,
                false,
                "${error::class.simpleName}: ${error.message ?: "无错误信息"} · ${System.currentTimeMillis() - started}ms"
            )
        }
        rows.add(row)
    }

    var seriesId = ""
    var episodeId = ""

    step("① 首页接口") {
        val page = Graph.repo.home(1, ContentType.SHORT)
        seriesId = page.items.firstOrNull()?.id.orEmpty()
        "返回 ${page.items.size} 部，首页第一是「${page.items.firstOrNull()?.title ?: "—"}」"
    }

    step("② 排行榜接口") {
        val page = Graph.repo.ranking(RankBoard.TOTAL, 1)
        if (seriesId.isBlank()) seriesId = page.items.firstOrNull()?.id.orEmpty()
        "返回 ${page.items.size} 部"
    }

    step("③ 播放设备注册") {
        val profile = PlaybackDeviceSession.shared.renew(Graph.http)
        "device_id=${profile.values["device_id"]} iid=${profile.values["iid"]} cookie=${profile.cookie.length} 字节"
    }

    step("④ 剧集详情") {
        if (seriesId.isBlank()) error("没有拿到剧集 ID，先修前面两步")
        val detail = Graph.repo.detail(seriesId)
        episodeId = detail.episodes.firstOrNull().orEmpty()
        "「${detail.series.title}」共 ${detail.episodes.size} 集"
    }

    var dash: SiteDash.Plan? = null

    step("⑤ 取流接口（站点直连 → 自动换线）") {
        if (episodeId.isBlank()) error("没有拿到剧集 ID")
        dash = runCatching { SiteDash.resolve(seriesId, episodeId, 1080) }.getOrNull()
        if (dash != null) {
            val plan = dash!!
            return@step "站点直连命中：${plan.quality} ${plan.width}x${plan.height}，DASH 视频+音频（${plan.durationSeconds.toInt()} 秒）"
        }
        val attemptsText = {
            Streams.lastAttempts.joinToString("；") { a ->
                "${a.route}${if (a.ok) "成功" else "失败"}=${a.detail}"
            }
        }
        val stream = try {
            Streams.resolve(episodeId, 1080)
        } catch (error: Throwable) {
            error("${error.message ?: error::class.simpleName}｜四条线路：${attemptsText()}")
        }
        "清晰度 ${stream.quality}，地址长度 ${stream.url.length}；线路：${attemptsText()}"
    }

    step("⑥ 分段读取") {
        if (episodeId.isBlank()) error("没有拿到剧集 ID")
        dash?.let { plan ->
            val request = okhttp3.Request.Builder()
                .url(plan.video)
                .header("Range", "bytes=${plan.initVideo}")
                .build()
            Graph.http.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes()?.size ?: 0
                if (bytes < 8) error("站点片源首段只读到 $bytes 字节")
                val head = runCatching {
                    Graph.http.newCall(
                        okhttp3.Request.Builder().url(plan.audio)
                            .header("Range", "bytes=${plan.initAudio}").build()
                    ).execute().use { it.body?.bytes()?.size ?: 0 }
                }.getOrDefault(0)
                return@step "视频首段 $bytes 字节（HTTP ${response.code}），音频首段 $head 字节"
            }
        }
        Streams.cached(episodeId, 1080)
        val stream = try {
            Streams.resolve(episodeId, 1080)
        } catch (error: Throwable) {
            error("${error.message ?: error::class.simpleName}｜四条线路：${
                Streams.lastAttempts.joinToString("；") { a -> "${a.route}${if (a.ok) "成功" else "失败"}=${a.detail}" }
            }")
        }
        RemoteVideo(Graph.http, stream).use { remote ->
            remote.prepare()
            val head = remote.read(0, 1024 * 1024)
            val tail = remote.read((remote.total - 65536).coerceAtLeast(0), 65536)
            if (head.size < 8) error("读到 ${head.size} 字节，取流仍为空")
            "总长 ${remote.total / 1024 / 1024}MB，首段 ${head.size} 字节，尾段 ${tail.size} 字节"
        }
    }
    return rows
}

private fun buildReport(rows: List<CheckRow>, version: String): String = buildString {
    appendLine("红果Plus 自检报告 v$version")
    rows.forEach { row ->
        appendLine("${if (row.ok) "[PASS]" else "[FAIL]"} ${row.step} —— ${row.detail}")
    }
}

// ── 小组件 ──────────────────────────────────────────────────────
@Composable
private fun SectionTitle(text: String) {
    Text(text, color = PlusColors.SubText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Card(content: @Composable ColumnScopeAlias.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PlusColors.Surface).padding(14.dp)
    ) { content() }
}

private typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = PlusColors.SubText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = PlusColors.Text, fontSize = 11.sp)
    }
}

@Composable
private fun PillButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
}
