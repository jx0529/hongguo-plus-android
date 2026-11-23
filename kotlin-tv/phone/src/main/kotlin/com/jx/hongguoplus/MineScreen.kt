// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MineScreen(onOpen: (Series) -> Unit, onLogin: () -> Unit, onSettings: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var gridMode by remember {
        mutableStateOf(
            context.getSharedPreferences("hongguoplus_ui", android.content.Context.MODE_PRIVATE)
                .getBoolean("mine_grid", false)
        )
    }
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var cloudRefresh by remember { mutableIntStateOf(0) }
    var unfinished by remember { mutableStateOf<List<UnfinishedItem>?>(null) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var nickname by remember(refresh) { mutableStateOf(Graph.account.nickname) }
    var profile by remember { mutableStateOf<com.hongguotv.core.CloudProfile?>(null) }
    val loggedIn = remember(refresh) { Graph.account.isLoggedIn() }
    val favorites = remember(refresh) { Graph.library.favorites() }
    val history = remember(refresh) { Graph.library.history() }

    LaunchedEffect(refresh, loggedIn) {
        if (!loggedIn) {
            profile = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching { Graph.account.profile() }.onSuccess {
                profile = it
                nickname = it.nickname.ifBlank { nickname }
            }
        }
    }

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            runCatching { CoverStore.ensureAll(context, Graph.library.favorites() + Graph.library.history().map { it.series }) }
        }
    }

    LaunchedEffect(cloudRefresh, loggedIn) {
        val local = Graph.library.history()
            .filter { it.episodeCount > 0 }
            .map {
                UnfinishedItem(
                    it.series,
                    it.episodeIndex,
                    it.episodeCount,
                    (((it.episodeIndex + 1).toFloat() / it.episodeCount) * 100).toInt().coerceIn(1, 100)
                )
            }
        if (!loggedIn) {
            unfinished = local
            return@LaunchedEffect
        }
        cloudError = null
        unfinished = null
        val result = withContext(Dispatchers.IO) {
            runCatching { mergeCloudProgress(Graph.account.progress()) }
        }
        result.onSuccess { cloud ->
            val cloudIds = cloud.map { it.series.id }.toSet()
            unfinished = cloud + local.filterNot { it.series.id in cloudIds }
        }.onFailure {
            cloudError = it.message ?: "云端进度加载失败"
            unfinished = local
        }
        refresh++
    }

    Column(Modifier.fillMaxSize()) {
        // ── 账号卡片 ────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PlusColors.Surface)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(PlusColors.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = profile?.avatarUrl.orEmpty()
                    if (avatar.isNotBlank()) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            (if (loggedIn) (nickname.ifBlank { "红" }) else "未").take(1),
                            color = PlusColors.Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (loggedIn) nickname.ifBlank { profile?.nickname?.ifBlank { "红果用户" } ?: "红果用户" } else "未登录",
                        color = PlusColors.Text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (loggedIn) "UID ${profile?.id?.ifBlank { "—" } ?: "—"}" else "登录后可同步进度、收藏与未看完",
                        color = PlusColors.SubText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (loggedIn) Color(0xFF49C86B) else Color(0xFF7A7A7A))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (loggedIn) "红果账号已连接 · 进度可同步" else "本地模式 · 数据只存本机",
                            color = PlusColors.SubText,
                            fontSize = 10.sp
                        )
                    }
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PlusColors.SurfaceHigh)
                        .clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = PlusColors.Text, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("收藏", favorites.size, 0),
                    Triple("历史", history.size, 1),
                    Triple("没看完", unfinished?.size ?: 0, 2)
                ).forEach { (label, count, target) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tab == target) PlusColors.SurfaceHigh else Color(0x14FFFFFF))
                            .clickable { tab = target }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$count",
                            color = if (tab == target) PlusColors.Accent else PlusColors.Text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(label, color = PlusColors.SubText, fontSize = 10.sp)
                    }
                }
            }

            if (loggedIn) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "退出登录",
                        color = PlusColors.SubText,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                Graph.account.clear()
                                profile = null
                                refresh++
                                cloudRefresh++
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Text(
                        "设置与自检",
                        color = PlusColors.SubText,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSettings() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    cloudError?.let {
                        Text(it, color = PlusColors.Accent, fontSize = 10.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(PlusColors.Accent)
                        .clickable { onLogin() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("登录红果账号", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill("收藏 ${favorites.size}", tab == 0) { tab = 0 }
            Pill("历史 ${history.size}", tab == 1) { tab = 1 }
            Pill("没看完", tab == 2) { tab = 2 }
            Spacer(Modifier.weight(1f))
            Pill(if (gridMode) "列表" else "三列") {
                gridMode = !gridMode
                context.getSharedPreferences("hongguoplus_ui", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("mine_grid", gridMode).apply()
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            0 -> SeriesList(
                grid = gridMode,
                items = favorites,
                emptyHint = "还没有收藏，看到喜欢的剧点个收藏",
                subtitle = { series -> series.badge.ifBlank { "已收藏" } },
                onOpen = onOpen,
                onRemove = {
                    Graph.library.removeFavorite(it.id)
                    refresh++
                }
            )

            1 -> SeriesList(
                grid = gridMode,
                items = history.map { it.series },
                emptyHint = "还没有观看记录",
                subtitle = { series ->
                    history.firstOrNull { it.series.id == series.id }
                        ?.let { "看到第 ${it.episodeIndex + 1}/${it.episodeCount} 集" }
                        ?: ""
                },
                onOpen = onOpen,
                onRemove = {
                    Graph.library.removeHistory(it.id)
                    refresh++
                }
            )

            else -> {
                val items = unfinished
                when {
                    !loggedIn -> ErrorView(
                        message = "登录红果账号后，这里会列出你没看完的剧",
                        modifier = Modifier.fillMaxSize()
                    )

                    items == null -> LoadingView("正在读取红果云端观看记录…")
                    cloudError != null -> ErrorView(message = cloudError!!, onRetry = { cloudRefresh++ })
                    items.isEmpty() -> ErrorView(
                        message = "没有未看完的剧，或者云端还没有进度",
                        onRetry = { cloudRefresh++ },
                        modifier = Modifier.fillMaxSize()
                    )

                    gridMode -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.series.id }) { item ->
                            Column {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PlusColors.SurfaceHigh)
                                        .clickable { onOpen(item.series) }
                                ) {
                                    CoverImage(item.series, Modifier.fillMaxSize())
                                }
                                Text(
                                    item.series.title,
                                    color = PlusColors.Text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                Text(
                                    "第 ${item.episodeIndex + 1} 集 · ${item.progressPercent}%",
                                    color = PlusColors.SubText,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                "刷新云端进度",
                                color = PlusColors.SubText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cloudRefresh++ }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.series.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 58.dp, height = 82.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PlusColors.SurfaceHigh)
                                        .clickable { onOpen(item.series) }
                                ) {
                                    CoverImage(item.series, Modifier.fillMaxSize())
                                }
                                Column(
                                    Modifier.weight(1f).clickable { onOpen(item.series) }.padding(start = 10.dp)
                                ) {
                                    Text(
                                        item.series.title,
                                        color = PlusColors.Text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "看到第 ${item.episodeIndex + 1}/${item.episodeCount} 集 · 本集已看 ${item.progressPercent}%",
                                        color = PlusColors.SubText,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    "继续看",
                                    color = PlusColors.Accent,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { onOpen(item.series) }.padding(8.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                "刷新云端进度",
                                color = PlusColors.SubText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cloudRefresh++ }
                                    .padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesList(
    grid: Boolean = false,
    items: List<Series>,
    emptyHint: String,
    subtitle: (Series) -> String,
    onOpen: (Series) -> Unit,
    onRemove: (Series) -> Unit
) {
    if (items.isEmpty()) {
        ErrorView(message = emptyHint, modifier = Modifier.fillMaxSize())
        return
    }
    if (grid) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { series ->
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PlusColors.SurfaceHigh)
                            .clickable { onOpen(series) }
                    ) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        series.title,
                        color = PlusColors.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        subtitle(series),
                        color = PlusColors.SubText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { onRemove(series) }
                    )
                }
            }
        }
        return
    }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { series ->
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PlusColors.SurfaceHigh)
                            .clickable { onOpen(series) }
                    ) {
                        AsyncImage(
                            model = series.cover,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x99000000))
                                .clickable { onRemove(series) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("×", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    Text(
                        series.title,
                        color = PlusColors.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        subtitle(series),
                        color = PlusColors.SubText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { series ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 58.dp, height = 82.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlusColors.SurfaceHigh)
                        .clickable { onOpen(series) }
                ) {
                    CoverImage(series, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).clickable { onOpen(series) }.padding(start = 10.dp)) {
                    Text(
                        series.title,
                        color = PlusColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle(series),
                        color = PlusColors.SubText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    "移除",
                    color = PlusColors.SubText,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onRemove(series) }.padding(8.dp)
                )
            }
        }
    }
}

/** 列表封面：优先读本地已保存的缩略图，没有再走网络并顺手落盘。 */
@androidx.compose.runtime.Composable
private fun CoverImage(series: Series, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var local by remember(series.id) { mutableStateOf(CoverStore.local(context, series.id)) }
    LaunchedEffect(series.id) {
        if (local == null) {
            withContext(Dispatchers.IO) { runCatching { CoverStore.ensure(context, series) } }
            local = CoverStore.local(context, series.id)
        }
    }
    AsyncImage(
        model = local ?: series.cover,
        contentDescription = series.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
