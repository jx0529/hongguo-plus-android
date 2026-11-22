// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.Series

private sealed interface Route {
    data object Home : Route
    data object Theater : Route
    data object Rank : Route
    data object Mine : Route
    data object Search : Route
    data class Detail(val series: Series) : Route
}

private data class PlayerArgs(
    val series: Series,
    val episodes: List<String>,
    val index: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlusTheme { AppRoot() }
        }
    }
}

@Composable
private fun AppRoot() {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var player by remember { mutableStateOf<PlayerArgs?>(null) }

    BackHandler(enabled = player != null || route != Route.Home) {
        when {
            player != null -> player = null
            route is Route.Detail -> route = Route.Home
            route == Route.Search -> route = Route.Home
            else -> route = Route.Home
        }
    }

    Box(Modifier.fillMaxSize().background(PlusColors.Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (val current = route) {
                    Route.Home -> HomeScreen(onOpen = { route = Route.Detail(it) })
                    Route.Theater -> TheaterScreen(onOpen = { route = Route.Detail(it) })
                    Route.Rank -> RankScreen(onOpen = { route = Route.Detail(it) })
                    Route.Mine -> MineScreen(onOpen = { route = Route.Detail(it) })
                    Route.Search -> SearchScreen(
                        onOpen = { route = Route.Detail(it) },
                        onBack = { route = Route.Home }
                    )
                    is Route.Detail -> DetailScreen(
                        series = current.series,
                        onBack = { route = Route.Home },
                        onPlay = { series, episodes, index ->
                            player = PlayerArgs(series, episodes, index)
                        }
                    )
                }
            }
            BottomBar(
                active = when (route) {
                    Route.Theater -> 1
                    Route.Rank -> 2
                    Route.Mine -> 4
                    Route.Search -> 3
                    else -> 0
                },
                onSelect = { index ->
                    route = when (index) {
                        0 -> Route.Home
                        1 -> Route.Theater
                        2 -> Route.Rank
                        3 -> Route.Search
                        else -> Route.Mine
                    }
                }
            )
        }

        player?.let { args ->
            PlayerScreen(
                series = args.series,
                episodeIds = args.episodes,
                startIndex = args.index,
                onClose = { player = null }
            )
        }
    }
}

@Composable
private fun BottomBar(active: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("推荐", "剧场", "排行", "搜索", "我的")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlusColors.Surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    label,
                    color = if (active == index) PlusColors.Accent else PlusColors.SubText,
                    fontSize = 13.sp,
                    fontWeight = if (active == index) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .background(
                            if (active == index) PlusColors.Accent else Color.Transparent,
                            androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                ) {}
            }
        }
    }
}
