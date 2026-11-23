// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 用 WebView 里的真实登录态请求站点的取流接口。
 *
 * 直接用 OkHttp 带 Cookie 请求时，站点对「前几集之外」的集数一律回
 * 403 protected_playback_unavailable，说明它认的不只是那串 Cookie，
 * 还需要浏览器环境（同源请求头、随请求下发的其它票据）。
 * 这里把请求放进 WebView：页面本身在 hongguoduanju.com 源上，fetch 自动带上
 * 完整会话，拿到的 JSON 再交给原生 ExoPlayer 播——播放依然是原生的，不需要网页播放器。
 */
object SiteWebFetch {

    private const val TAG_KEY = "window.__hgRes"

    /** 返回 (HTTP 状态码, 响应体) */
    suspend fun playInfo(
        context: Context,
        seriesId: String,
        episodeId: String,
        definition: String
    ): Pair<Int, String>? {
        if (!seriesId.matches(Regex("[0-9]{1,30}")) || !episodeId.matches(Regex("[0-9]{1,30}"))) return null
        return withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Pair<Int, String>?>()
            val handler = Handler(Looper.getMainLooper())
            val web = WebView(context.applicationContext)
            var closed = false

            fun finish(result: Pair<Int, String>?) {
                if (closed) return
                closed = true
                if (!done.isCompleted) done.complete(result)
                runCatching { web.stopLoading(); web.destroy() }
            }

            @SuppressLint("SetJavaScriptEnabled")
            runCatching {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.userAgentString = SiteSession.UA
            }.onFailure {
                finish(null)
                return@withContext null
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

            fun poll(times: Int) {
                if (closed) return
                web.evaluateJavascript("$TAG_KEY||''") { raw ->
                    val value = raw?.trim()?.trim('"').orEmpty()
                    if (value.isBlank() || value == "null") {
                        if (times <= 0) finish(null) else handler.postDelayed({ poll(times - 1) }, 250)
                    } else {
                        // 格式：#status#body
                        val status = value.substringAfter('#').substringBefore('#').toIntOrNull() ?: -1
                        val body = value.substringAfter('#').substringAfter('#')
                        finish(status to body)
                    }
                }
            }

            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val js = """
                        (function(){
                          $TAG_KEY = '';
                          fetch('/api/video/protected/play-info?aid=8662',{
                            method:'POST', credentials:'include',
                            headers:{'Content-Type':'application/json'},
                            body: JSON.stringify({series_id:'$seriesId', item_id:'$episodeId', definition:'$definition'})
                          }).then(function(r){ return r.text().then(function(t){ $TAG_KEY = '#'+r.status+'#'+t; }); })
                            .catch(function(e){ $TAG_KEY = '#-1#'+String(e); });
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(js, null)
                    handler.postDelayed({ poll(80) }, 200)
                }
            }
            web.loadUrl(SiteSession.SITE + "/")

            withTimeoutOrNull(20_000) { done.await() } ?: run { finish(null); null }
        }
    }
}
