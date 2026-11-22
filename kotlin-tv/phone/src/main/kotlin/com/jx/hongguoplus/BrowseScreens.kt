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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hongguotv.core.ContentType
import com.hongguotv.core.RankBoard
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TheaterScreen(onOpen: (Series) -> Unit) {
    var typeIndex by remember { mutableIntStateOf(0) }
    val types = listOf(ContentType.SHORT, ContentType.COMIC, ContentType.AI)
    var filters by remember { mutableStateOf<List<Pair<String, List<Pair<String, String>>>>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(index: Int, filter: Map<String, String>, resetPage: Boolean) {
        if (loading) return
        val target = if (resetPage) 1 else page + 1
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Graph.repo.theater(target, types[index], filter) }
            }
            result.onSuccess { pageData ->
                val fresh = pageData.catalog.items
                items = if (resetPage) fresh else (items + fresh).distinctBy { it.id }
                hasMore = pageData.catalog.hasMore
                page = target
                if (pageData.filters.isNotEmpty()) {
                    filters = pageData.filters.take(3).map { group ->
                        group.label to group.options.take(12).map { it.id to it.label }
                    }
                }
            }.onFailure {
                if (resetPage) items = emptyList()
                error = it.message ?: "剧场加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(typeIndex) { refresh(typeIndex, selected, true) }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("剧场")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEachIndexed { index, type ->
                Pill("${type.label}", index == typeIndex) {
                    typeIndex = index
                    selected = emptyMap()
                    items = emptyList()
                    page = 1
                }
            }
        }
        if (filters.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            filters.forEach { (label, options) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = PlusColors.SubText,
                        fontSize = 11.sp,
                        modifier = Modifier.width(34.dp)
                    )
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(options.size) { i ->
                            val (id, name) = options[i]
                            val active = selected[label] == id
                            Pill(name, active) {
                                selected = if (active) selected - label else selected + (label to id)
                                items = emptyList()
                                page = 1
                                refresh(typeIndex, selected, true)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        PagedGrid(
            items = items,
            loading = loading,
            hasMore = hasMore,
            error = error,
            onOpen = onOpen,
            onLoadMore = { refresh(typeIndex, selected, false) }
        )
    }
}

@Composable
fun RankScreen(onOpen: (Series) -> Unit) {
    val boards = listOf(RankBoard.TOTAL, RankBoard.REAL, RankBoard.COMIC, RankBoard.AI)
    var boardIndex by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh(index: Int, resetPage: Boolean) {
        if (loading) return
        val target = if (resetPage) 1 else page + 1
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Graph.repo.ranking(boards[index], target) }
            }
            result.onSuccess { pageData ->
                items = if (resetPage) pageData.items else (items + pageData.items).distinctBy { it.id }
                hasMore = pageData.hasMore
                page = target
            }.onFailure {
                if (resetPage) items = emptyList()
                error = it.message ?: "榜单加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(boardIndex) { refresh(boardIndex, true) }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("排行榜", trailing = boards[boardIndex].sourceText)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            boards.forEachIndexed { index, board ->
                Pill(board.buttonLabel, index == boardIndex) {
                    boardIndex = index
                    items = emptyList()
                    page = 1
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty() && error != null) {
            ErrorView(message = error!!, onRetry = { refresh(boardIndex, true) })
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, s -> s.id }) { index, series ->
                    RankRow(index + 1 + (page - 1) * 20, series) { onOpen(series) }
                }
                if (loading || (hasMore && items.isNotEmpty())) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (loading) "加载中…" else "上拉加载更多",
                                color = PlusColors.SubText,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { refresh(boardIndex, false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, series: Series, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            color = when {
                rank == 1 -> PlusColors.Gold
                rank <= 3 -> Color(0xFFFF8A5B)
                else -> PlusColors.SubText
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(26.dp)
        )
        Box(
            Modifier.size(width = 62.dp, height = 84.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PlusColors.SurfaceHigh)
        ) {
            AsyncImage(
                model = series.cover,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                series.title,
                color = PlusColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 18.sp,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(series.badge, series.tags).filter { it.isNotBlank() }.joinToString(" · "),
                color = PlusColors.SubText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SearchScreen(onOpen: (Series) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var staged by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun doSearch(keyword: String, resetPage: Boolean) {
        if (keyword.isBlank()) return
        if (loading) return
        staged = keyword
        val target = if (resetPage) 1 else page + 1
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Graph.repo.searchCombined(keyword, target) }
            }
            result.onSuccess { pageData ->
                items = if (resetPage) pageData.items else (items + pageData.items).distinctBy { it.id }
                hasMore = pageData.hasMore
                page = target
                suggestions = emptyList()
            }.onFailure {
                if (resetPage) items = emptyList()
                error = it.message ?: "搜索失败"
            }
            loading = false
        }
    }

    LaunchedEffect(query) {
        if (query.length >= 1 && query != staged) {
            suggestions = withContext(Dispatchers.IO) {
                runCatching { Graph.repo.suggest(query) }.getOrDefault(emptyList())
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", color = PlusColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.SurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = PlusColors.Text, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(query, true) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜剧名、演员、题材", color = PlusColors.SubText, fontSize = 14.sp)
                            }
                            inner()
                        }
                    }
                )
                Text(
                    "搜索",
                    color = PlusColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { doSearch(query, true) }
                )
            }
        }
        if (suggestions.isNotEmpty() && items.isEmpty()) {
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(suggestions) { _, word ->
                    Text(
                        word,
                        color = PlusColors.SubText,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                query = word
                                doSearch(word, true)
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        } else {
            PagedGrid(
                items = items,
                loading = loading,
                hasMore = hasMore,
                error = error,
                onOpen = onOpen,
                onLoadMore = { doSearch(staged, false) }
            )
        }
    }
}
