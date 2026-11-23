// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import android.webkit.CookieManager

/**
 * 红果网页版（hongguoduanju.com）的登录态。
 *
 * App 自己的 video_model 接口目前对第三方客户端一律回 HTTP 200 + 0 字节，
 * 只剩网页版那条 play-info 还能下发片源，但它要求登录：未登录时第 2 集以后
 * 全部回 403 protected_playback_unavailable。
 *
 * 所以这里在 App 内嵌一个网页登录页，用户在里面用手机号/验证码登录一次，
 * 我们把 WebView 的 Cookie 取出来存本机，之后取流与兜底播放都带上它。
 */
object SiteSession {
    const val SITE = "https://hongguoduanju.com"
    private const val FILE = "hongguoplus_site"
    private const val KEY = "cookie"

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cookie(context: Context = Graph.appContext): String =
        runCatching { prefs(context).getString(KEY, "").orEmpty() }.getOrDefault("")

    fun has(context: Context = Graph.appContext): Boolean = cookie(context).isNotBlank()

    fun save(context: Context, value: String) {
        prefs(context).edit().putString(KEY, value.trim()).apply()
    }

    fun clear(context: Context = Graph.appContext) {
        prefs(context).edit().remove(KEY).apply()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    /** 从 WebView 的 CookieManager 读站点 Cookie（登录完成后调用）。 */
    fun readFromWebView(): String =
        runCatching { CookieManager.getInstance().getCookie(SITE).orEmpty() }.getOrDefault("")

    /** 判断这份 Cookie 里是否已经拿到登录票据（不同接口下发的字段名不一样，做宽松匹配）。 */
    fun looksLoggedIn(raw: String): Boolean {
        if (raw.isBlank()) return false
        val keys = listOf("sessionid", "sessionid_ss", "sid_guard", "sid_tt", "passport", "passport_csrf_token", "uid_tt", "sid_ucp_v1")
        return keys.any { raw.contains("$it=") }
    }

    /** 取流用的 Cookie：先站点登录态，其次 App 账号里的那份。 */
    fun playbackCookie(context: Context = Graph.appContext): String {
        val site = cookie(context)
        if (site.isNotBlank()) return site
        return runCatching { Graph.account.credentials()?.cookie.orEmpty() }.getOrDefault("")
    }
}
