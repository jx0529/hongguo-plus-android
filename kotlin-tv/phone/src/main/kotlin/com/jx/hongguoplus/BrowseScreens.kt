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
import kotlinx.coroutines.delay
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
    var autoSorted by remember { mutableStateOf(false) }
    var pendingAuto by remember { mutableIntStateOf(0) }
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
                    // 首次进入剧场默认按「最新」排序，不用手动点
                    if (!autoSorted && selected.isEmpty()) {
                        val sortGroup = pageData.filters.firstOrNull { g ->
                            g.label.contains("排序") || g.label.contains("sort", true)
                        }
                        val newest = sortGroup?.options?.firstOrNull { o ->
                            o.label.contains("最新") || o.label.contains("上新")
                        }
                        if (sortGroup != null && newest != null) {
                            autoSorted = true
                            selected = selected + (sortGroup.label to newest.id)
                            items = emptyList()
                            page = 1
                            pendingAuto++
                        }
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

    LaunchedEffect(pendingAuto) {
        if (pendingAuto > 0) refresh(typeIndex, selected, true)
    }

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
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val boards = listOf(RankBoard.TOTAL, RankBoard.HOT_SEARCH, RankBoard.MUST_WATCH, RankBoard.REAL, RankBoard.COMIC, RankBoard.AI)
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
                if (resetPage) RankCache.save(appContext, boards[index], pageData.items)
            }.onFailure {
                if (resetPage && items.isEmpty()) error = it.message ?: "榜单加载失败"
            }
            loading = false
        }
    }

    // 先出本地缓存（秒开），再拉当天最新的榜单覆盖
    LaunchedEffect(boardIndex) {
        val context = appContext
        val cached = RankCache.load(context, boards[boardIndex])
        if (cached.items.isNotEmpty() && items.isEmpty()) {
            items = cached.items
        }
        refresh(boardIndex, true)
    }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("排行榜", trailing = boards[boardIndex].sourceText)
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(boards.size) { index ->
                val board = boards[index]
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SearchHistory.load(context)) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var staged by remember { mutableStateOf("") }
    var related by remember { mutableStateOf<List<Series>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun doSearch(keyword: String, resetPage: Boolean) {
        if (keyword.isBlank()) return
        if (loading) return
        staged = keyword
        SearchHistory.add(context, keyword)
        history = SearchHistory.load(context)
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

    // 边打字边联想：关键词 + 直接相关的剧（300ms 防抖）
    LaunchedEffect(query) {
        if (query.length >= 1 && query != staged) {
            delay(300)
            if (query == staged) return@LaunchedEffect
            val words = withContext(Dispatchers.IO) {
                runCatching { Graph.repo.suggest(query) }.getOrDefault(emptyList())
            }
            suggestions = words.take(8)
            related = if (query.length >= 2) {
                withContext(Dispatchers.IO) {
                    runCatching { Graph.repo.searchCombined(query, 1).items.take(6) }
                        .getOrDefault(emptyList())
                }
            } else emptyList()
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
                if (query.isNotEmpty()) {
                    Text(
                        "✕",
                        color = PlusColors.SubText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                query = ""
                                items = emptyList()
                                suggestions = emptyList()
                                related = emptyList()
                                staged = ""
                            }
                            .padding(horizontal = 6.dp)
                    )
                }
                Text(
                    "搜索",
                    color = PlusColors.Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { doSearch(query, true) }
                )
            }
        }
        // 搜索记录：点一下直接搜，最右侧可单条删除
        if (query.isBlank() && items.isEmpty() && history.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "搜索记录",
                        color = PlusColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "清空",
                        color = PlusColors.SubText,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            SearchHistory.clear(context)
                            history = emptyList()
                        }
                    )
                }
                history.take(12).forEach { word ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            word,
                            color = PlusColors.SubText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    query = word
                                    doSearch(word, true)
                                }
                                .padding(vertical = 11.dp)
                        )
                        Text(
                            "✕",
                            color = PlusColors.SubText,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    SearchHistory.remove(context, word)
                                    history = SearchHistory.load(context)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if ((suggestions.isNotEmpty() || related.isNotEmpty()) && items.isEmpty()) {
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
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            "相关剧集",
                            color = PlusColors.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                        )
                    }
                    items(related.size) { i ->
                        val series = related[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(series) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(width = 46.dp, height = 64.dp)
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (series.badge.isNotBlank()) {
                                    Text(
                                        series.badge,
                                        color = PlusColors.SubText,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                        }
                    }
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
