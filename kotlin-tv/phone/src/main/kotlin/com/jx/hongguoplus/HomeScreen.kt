// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.CatalogPage
import com.hongguotv.core.ContentType
import com.hongguotv.core.RankBoard
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 通用三列剧集网格，滚到底自动翻页。 */
@Composable
fun PagedGrid(
    items: List<Series>,
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpen: (Series) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    val shouldLoadMore by remember(items.size) {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            items.isNotEmpty() && last >= items.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loading, error, items.size) {
        if (shouldLoadMore && hasMore && !loading && error == null) onLoadMore()
    }
    Box(modifier.fillMaxSize()) {
        if (items.isEmpty() && error != null) {
            ErrorView(message = error, onRetry = onLoadMore, modifier = Modifier.fillMaxSize())
        } else if (items.isEmpty() && !loading) {
            ErrorView(message = "这里还没有内容，稍后再试试", modifier = Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = state,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.id }) { series ->
                    PosterCard(series = series, onClick = { onOpen(series) })
                }
                if (loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = PlusColors.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onOpen: (Series) -> Unit) {
    var typeIndex by remember { mutableIntStateOf(0) }
    val types = listOf(ContentType.SHORT, ContentType.COMIC, ContentType.AI)
    var items by remember { mutableStateOf<List<Series>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun loadFirst(index: Int) {
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { hotCatalog(types[index], 1) }
            }
            result.onSuccess {
                items = it.items
                hasMore = it.hasMore
                page = 1
                gridState.scrollToItem(0)
            }.onFailure {
                items = emptyList()
                error = it.message ?: "内容加载失败"
            }
            loading = false
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        val next = page + 1
        val index = typeIndex
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { hotCatalog(types[index], next) }
            }
            result.onSuccess {
                items = (items + it.items).distinctBy { s -> s.id }
                hasMore = it.hasMore
                page = next
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(typeIndex) {
        if (items.isEmpty() || page != 1 || error != null) loadFirst(typeIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("红果Plus", color = PlusColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "cangora",
                    color = PlusColors.SubText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEachIndexed { index, type ->
                Pill(text = "${type.label}推荐", selected = index == typeIndex) {
                    typeIndex = index
                    loadFirst(index)
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
            state = gridState,
            onLoadMore = { loadMore() }
        )
    }
}


/** 首页优先走推荐流；推荐为空或接口异常时退到榜单，保证一进来就有红果最火的内容。 */
internal fun hotCatalog(type: ContentType, page: Int): CatalogPage {
    val preferred = runCatching { Graph.repo.home(page, type) }.getOrNull()
    if (preferred != null && preferred.items.isNotEmpty()) return preferred
    val board = when (type) {
        ContentType.COMIC -> RankBoard.COMIC
        ContentType.AI -> RankBoard.AI
        ContentType.SHORT -> RankBoard.REAL
    }
    val boardPage = runCatching { Graph.repo.ranking(board, page) }.getOrNull()
    if (boardPage != null && boardPage.items.isNotEmpty()) return boardPage
    return Graph.repo.ranking(RankBoard.TOTAL, page)
}
