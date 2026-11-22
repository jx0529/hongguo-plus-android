// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import com.hongguotv.core.EmptyPlaybackResponseException
import com.hongguotv.core.PhoenixPlaybackDeviceSession
import com.hongguotv.core.PhoenixPlaybackSource
import com.hongguotv.core.PlaybackSourceException
import com.hongguotv.core.StreamInfo
import com.hongguotv.core.UnplayableVideoException
import com.hongguotv.core.UnsupportedVideoFormatException
import okhttp3.CookieJar

/**
 * 取流线路：先走 Native（多清晰度模型），失败再走 Phoenix。
 *
 * 红果的播放接口有两条通道，不同片源/设备下可用性不同。上游电脑版会在 Native 返回空时
 * 自动切到 Phoenix 重取，这里补上同样的能力，并把每一步的结果记下来给自检页看。
 */
object Streams {

    data class Attempt(val route: String, val ok: Boolean, val detail: String)

    /** 最近一次取流的线路尝试记录，供自检页展示。 */
    @Volatile
    var lastAttempts: List<Attempt> = emptyList()
        private set

    private val phoenix: PhoenixPlaybackSource by lazy {
        PhoenixPlaybackSource(
            Graph.http.newBuilder().cookieJar(CookieJar.NO_COOKIES).build(),
            PhoenixPlaybackDeviceSession.shared
        )
    }

    fun resolve(id: String, maxQuality: Int = 1080): StreamInfo {
        val attempts = mutableListOf<Attempt>()

        try {
            val info = Graph.repo.stream(id, maxQuality)
            attempts += Attempt("Native", true, "清晰度 ${info.quality}，地址 ${info.url.length} 字符")
            lastAttempts = attempts
            return info
        } catch (error: Throwable) {
            attempts += Attempt("Native", false, describe(error))
            if (!shouldFallback(error)) {
                lastAttempts = attempts
                throw error
            }
        }

        try {
            val info = phoenix.stream(id, maxQuality)
            attempts += Attempt("Phoenix", true, "清晰度 ${info.quality}，地址 ${info.url.length} 字符")
            lastAttempts = attempts
            return info
        } catch (error: Throwable) {
            attempts += Attempt("Phoenix", false, describe(error))
            lastAttempts = attempts
            throw error
        }
    }

    /** 只有"这条线路不可用"才换线；主动取消、格式不支持不必重试。 */
    private fun shouldFallback(error: Throwable): Boolean = when (error) {
        is java.io.InterruptedIOException -> false
        is UnsupportedVideoFormatException -> false
        is EmptyPlaybackResponseException -> true
        is PlaybackSourceException -> true
        is UnplayableVideoException -> true
        else -> true
    }

    private fun describe(error: Throwable): String {
        val detail = (error as? EmptyPlaybackResponseException)?.detail
        val name = error.javaClass.simpleName
        val message = error.message ?: "无错误信息"
        return if (detail.isNullOrBlank()) "$name：$message" else "$name：$message（$detail）"
    }
}
