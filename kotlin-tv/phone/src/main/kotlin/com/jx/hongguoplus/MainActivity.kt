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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Route {
    data object Home : Route
    data object Theater : Route
    data object Rank : Route
    data object Mine : Route
    data object Search : Route
    data object Login : Route
    data class Detail(val series: Series) : Route
    data object Settings : Route
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
            route is Route.Detail || route == Route.Login || route == Route.Settings -> route = Route.Home
            route == Route.Search -> route = Route.Home
            else -> route = Route.Home
        }
    }

    val appContext = LocalContext.current
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var updateProgress by remember { mutableStateOf(-1) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val updateScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        release = withContext(Dispatchers.IO) {
            runCatching { Updater.check(BuildConfig.VERSION_NAME) }.getOrNull()
        }
    }

    Box(Modifier.fillMaxSize().background(PlusColors.Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (val current = route) {
                    Route.Home -> HomeScreen(onOpen = { route = Route.Detail(it) })
                    Route.Theater -> TheaterScreen(onOpen = { route = Route.Detail(it) })
                    Route.Rank -> RankScreen(onOpen = { route = Route.Detail(it) })
                    Route.Mine -> MineScreen(
                        onOpen = { route = Route.Detail(it) },
                        onLogin = { route = Route.Login },
                        onSettings = { route = Route.Settings }
                    )
                    Route.Settings -> SettingsScreen(
                        onBack = { route = Route.Mine },
                        onLogin = { route = Route.Login },
                        onRefreshMine = { route = Route.Mine }
                    )
                    Route.Login -> LoginScreen(
                        onDone = { route = Route.Mine },
                        onBack = { route = Route.Mine }
                    )
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
                    Route.Mine, Route.Login, Route.Settings -> 4
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

        release?.let { info ->
            AlertDialog(
                onDismissRequest = { if (updateProgress < 0) release = null },
                title = { Text("发现新版本 ${info.version}") },
                text = {
                    Column {
                        if (info.notes.isNotBlank()) {
                            Text(info.notes, fontSize = 12.sp, color = PlusColors.SubText)
                        }
                        if (updateProgress >= 0) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { updateProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("正在下载 $updateProgress%", fontSize = 11.sp, color = PlusColors.SubText)
                        }
                        updateError?.let {
                            Text(it, fontSize = 11.sp, color = PlusColors.Accent, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = updateProgress < 0,
                        onClick = {
                            updateError = null
                            updateProgress = 0
                            updateScope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val apk = Updater.download(
                                            context = appContext,
                                            release = info
                                        ) { percent -> updateProgress = percent }
                                        apk
                                    }
                                }
                                result.onSuccess { apk ->
                                    updateProgress = -1
                                    release = null
                                    runCatching { Updater.install(appContext, apk) }
                                }.onFailure {
                                    updateError = it.message ?: "下载失败"
                                    updateProgress = -1
                                }
                            }
                        }
                    ) { Text(if (updateProgress >= 0) "下载中…" else "立即更新") }
                },
                dismissButton = {
                    TextButton(onClick = { release = null }, enabled = updateProgress < 0) { Text("稍后") }
                }
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
