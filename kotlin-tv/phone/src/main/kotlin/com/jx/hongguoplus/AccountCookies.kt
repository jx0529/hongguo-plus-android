// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 在原来的内存 Cookie 罐外面，再挂一份「App 账号登录态」。
 *
 * 取流接口（video_tab / player 两套，Native 和 Phoenix 都走它们）以前是匿名也能拿到片源，
 * 现在服务端会把未登录设备直接回成 HTTP 200 + 0 字节；而 App 里明明扫过码登录过，
 * 这份账号 Cookie 却没有进过取流请求——这里补上，登录后所有请求自动带上。
 */
class AccountCookies(
    private val inner: CookieJar,
    private val accountCookie: () -> String
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        inner.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val base = inner.loadForRequest(url)
        val raw = runCatching { accountCookie() }.getOrDefault("")
        if (raw.isBlank()) return base
        val extra = raw.split(';').mapNotNull { pair ->
            val text = pair.trim()
            if (text.isEmpty() || !text.contains('=')) return@mapNotNull null
            val name = text.substringBefore('=').trim()
            val value = text.substringAfter('=').trim()
            if (name.isEmpty() || value.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            }.getOrNull()
        }
        // 同名的以账号 Cookie 为准
        val merged = base.filter { kept -> extra.none { it.name == kept.name } }
        return merged + extra
    }
}
