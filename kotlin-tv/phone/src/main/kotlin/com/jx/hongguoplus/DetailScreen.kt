// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.Detail
import com.hongguotv.core.RemoteVideo
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    series: Series,
    onBack: () -> Unit,
    onPlay: (Series, List<String>, Int) -> Unit
) {
    var detail by remember { mutableStateOf<Detail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorite by remember { mutableStateOf(Graph.library.isFavorite(series.id)) }
    val history = remember(series.id) { Graph.library.historyOf(series.id) }
    val scroll = rememberScrollState()

    LaunchedEffect(series.id) {
        val result = withContext(Dispatchers.IO) { runCatching { ContentCache.detail(series.id) } }
        result.onSuccess { detail = it }.onFailure { error = it.message ?: "详情加载失败" }
    }

    // 详情一出来就把前 3 集的流预热好，点播放基本是秒开（起播目标 <400ms）
    LaunchedEffect(detail?.episodes?.firstOrNull()) {
        val list = detail?.episodes ?: return@LaunchedEffect
        if (list.isEmpty()) return@LaunchedEffect
        val quality = Graph.library.defaultQuality
        withContext(Dispatchers.IO) {
            list.take(2).forEach { id ->
                launch {
                    runCatching {
                        val info = Streams.resolve(id, quality)
                        PrewarmPool.put(id, quality, RemoteVideo(Graph.http, info, chunkBytes = 512 * 1024, aheadBytes = 2L * 1024 * 1024).prepare())
                    }
                }
            }
        }
    }

    val episodes = detail?.episodes ?: emptyList()
    val shown = detail?.series ?: series

    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(
                model = shown.cover,
                contentDescription = shown.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(2.dp)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x33000000),
                            0.55f to Color(0xB30B0B0D),
                            1f to PlusColors.Bg
                        )
                    )
            )
            Text(
                "‹",
                color = Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 12.dp)
                    .clickable { onBack() }
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 132.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = shown.cover,
                        contentDescription = shown.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        shown.title,
                        color = PlusColors.Text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    val meta = listOf(
                        if (episodes.isNotEmpty()) "全 ${episodes.size} 集" else shown.badge,
                        shown.tags
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                    Text(meta, color = PlusColors.SubText, fontSize = 11.sp, maxLines = 2)
                    detail?.social?.let { social ->
                        val socialText = listOf(
                            if (social.rating > 0) "评分 ${social.rating}" else "",
                            social.heatText,
                            social.favoriteText
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        if (socialText.isNotBlank()) {
                            Text(
                                socialText,
                                color = PlusColors.Gold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.Accent)
                    .clickable { if (episodes.isNotEmpty()) onPlay(shown, episodes, history?.episodeIndex ?: 0) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (history != null) "继续看第 ${history.episodeIndex + 1} 集" else "立即播放",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.SurfaceHigh)
                    .clickable {
                        favorite = Graph.library.toggleFavorite(shown)
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (favorite) "已收藏" else "收藏",
                    color = if (favorite) PlusColors.Accent else PlusColors.SubText,
                    fontSize = 14.sp
                )
            }
        }

        if (shown.description.isNotBlank()) {
            Text(
                shown.description,
                color = PlusColors.SubText,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        if (error != null) {
            ErrorView(message = error!!, onRetry = { error = null })
        }

        if (episodes.isNotEmpty()) {
            SectionTitle("选集", trailing = "共 ${episodes.size} 集")
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                episodes.indices.forEach { index ->
                    val active = history?.episodeIndex == index
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) PlusColors.Accent.copy(alpha = 0.22f) else PlusColors.SurfaceHigh)
                            .clickable { onPlay(shown, episodes, index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            color = if (active) PlusColors.Accent else PlusColors.Text,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        val related = detail?.related.orEmpty()
        if (related.isNotEmpty()) {
            SectionTitle("相关推荐")
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(related.size) { i ->
                    PosterCard(
                        series = related[i],
                        onClick = { onBack() },
                        modifier = Modifier.width(108.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
