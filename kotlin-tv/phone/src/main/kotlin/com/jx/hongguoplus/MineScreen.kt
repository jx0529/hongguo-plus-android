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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.Series

@Composable
fun MineScreen(onOpen: (Series) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val favorites = remember(refresh) { Graph.library.favorites() }
    val history = remember(refresh) { Graph.library.history() }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("我的追剧", trailing = "本地保存 · 不上传")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Pill("收藏 ${favorites.size}", tab == 0) { tab = 0 }
            Pill("历史 ${history.size}", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))
        val list = if (tab == 0) favorites else history.map { it.series }
        if (list.isEmpty()) {
            ErrorView(
                message = if (tab == 0) "还没有收藏，看到喜欢的剧点个 ❤" else "还没有观看记录",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.id }) { series ->
                    val entry = if (tab == 1) history.firstOrNull { it.series.id == series.id } else null
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
                            AsyncImage(
                                model = series.cover,
                                contentDescription = series.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { onOpen(series) }
                                .padding(start = 10.dp)
                        ) {
                            Text(
                                series.title,
                                color = PlusColors.Text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (entry != null) "看到第 ${entry.episodeIndex + 1}/${entry.episodeCount} 集" else (if (series.badge.isBlank()) "已收藏" else series.badge),
                                color = PlusColors.SubText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            "移除",
                            color = PlusColors.SubText,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable {
                                    if (tab == 0) Graph.library.removeFavorite(series.id)
                                    else Graph.library.removeHistory(series.id)
                                    refresh++
                                }
                                .padding(8.dp)
                        )
                    }
                }
                item {
                    Text(
                        if (tab == 0) "清空收藏" else "清空历史",
                        color = PlusColors.SubText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (tab == 0) Graph.library.clearFavorites() else Graph.library.clearHistory()
                                refresh++
                            }
                            .padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
