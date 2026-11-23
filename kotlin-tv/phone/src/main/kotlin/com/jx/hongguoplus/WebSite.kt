// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * 内嵌网页：两种用途
 * 1. `capture = true` —— 红果网页登录页，登录成功后把 Cookie 存下来（解锁站点片源）；
 * 2. `capture = false` —— 直接用官方网页播放器兜底播放某一集。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSiteScreen(
    url: String,
    title: String,
    capture: Boolean,
    onClose: () -> Unit,
    onCaptured: (String) -> Unit = {},
    onProgress: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var cookieState by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    // 网页播放器没有进度回调，直接读页面里 <video> 的 currentTime 记回本地
    LaunchedEffect(capture) {
        if (capture) return@LaunchedEffect
        while (true) {
            delay(4000)
            runCatching {
                (context as? android.app.Activity)?.runOnUiThread {
                    webRef?.evaluateJavascript(
                        "(document.querySelector('video')||{}).currentTime||0"
                    ) { raw ->
                        val seconds = raw?.trim('"', ' ')?.toDoubleOrNull() ?: 0.0
                        if (seconds > 1.0) onProgress((seconds * 1000).toLong())
                    }
                }
            }
        }
    }

    // 登录页是个 SPA，Cookie 是登录后异步下发的，所以轮询而不是等某个固定时机
    LaunchedEffect(capture, tick) {
        if (!capture) return@LaunchedEffect
        while (!saved) {
            val raw = SiteSession.readFromWebView()
            if (raw.isNotBlank()) cookieState = raw
            delay(1200)
        }
    }

    Column(Modifier.fillMaxSize().background(PlusColors.Bg).safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("‹", color = PlusColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { onClose() })
            Text(title, color = PlusColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (capture) {
                val loggedIn = SiteSession.looksLoggedIn(cookieState)
                Text(
                    if (loggedIn) "保存登录" else "刷新",
                    color = if (loggedIn) PlusColors.Gold else PlusColors.SubText,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        if (loggedIn) {
                            SiteSession.save(context, cookieState)
                            saved = true
                            onCaptured(cookieState)
                            onClose()
                        } else {
                            tick++
                        }
                    }
                )
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
        if (capture) {
            Text(
                "这里是电脑版网页：点右上角的「登录」用手机号或扫码登录红果（登录一次即可，之后自动记住）。登录成功后点上方「保存登录」。",
                color = PlusColors.SubText, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadsImagesAutomatically = true
                        settings.userAgentString = if (capture) {
                            // 手机 UA 打开的是「下载 App」落地页，电脑版才有登录 / 扫码入口
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                        } else {
                            settings.userAgentString?.replace("; wv", "")?.replace(" wv", "")
                        }
                        if (capture) {
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webRef = this
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(url)
                    }
                },
                update = { }
            )
            if (!loaded) {
                LaunchedEffect(Unit) {
                    delay(900)
                    loaded = true
                }
            }
            if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlusColors.Gold)
                }
            }
        }
    }
}
