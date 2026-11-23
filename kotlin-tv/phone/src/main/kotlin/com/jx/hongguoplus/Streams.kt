// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import com.hongguotv.core.EmptyPlaybackResponseException
import com.hongguotv.core.PhoenixPlaybackDeviceSession
import com.hongguotv.core.PhoenixPlaybackSource
import com.hongguotv.core.PlaybackQualityMode
import com.hongguotv.core.PlaybackSourceException
import com.hongguotv.core.StreamInfo
import com.hongguotv.core.UnplayableVideoException
import com.hongguotv.core.UnsupportedVideoFormatException
import okhttp3.CookieJar

/**
 * 取流线路：Native → Phoenix 两级，每级都支持「指定清晰度 → 自动取最高可用」降档。
 *
 * 红果不同片源的清晰度档位不一致：有的只有 720，按 1080 去找会被直接拒绝
 * （"未提供达到指定清晰度的片源"）。所以每级先按用户选的档位找，失败就退到最优可用。
 */
object Streams {

    data class Attempt(val route: String, val ok: Boolean, val detail: String)

    @Volatile
    var lastAttempts: List<Attempt> = emptyList()
        private set

    private val phoenix: PhoenixPlaybackSource by lazy {
        // 复用主客户端的 CookieJar：设备注册/取流需要站点的 ttwid 等 Cookie，
        // 之前用 NO_COOKIES 会直接注册失败（PhoenixPlaybackDeviceRegistrationException）。
        PhoenixPlaybackSource(Graph.http, PhoenixPlaybackDeviceSession.shared)
    }

    /** 取流结果短期缓存：同一集重复进入不再重复请求接口。 */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, StreamInfo>>()
    private const val CACHE_MS = 8 * 60 * 1000L

    fun cached(id: String, maxQuality: Int): StreamInfo? {
        val hit = cache["$id|$maxQuality"] ?: return null
        return if (System.currentTimeMillis() - hit.first < CACHE_MS) hit.second else null
    }

    fun resolve(id: String, maxQuality: Int = 1080): StreamInfo {
        cached(id, maxQuality)?.let { return it }
        return resolveFresh(id, maxQuality)
    }

    private fun resolveFresh(id: String, maxQuality: Int): StreamInfo {
        val attempts = mutableListOf<Attempt>()
        val errors = mutableListOf<Throwable>()

        fun record(route: String, error: Throwable) {
            attempts += Attempt(route, false, describe(error))
            errors += error
        }

        fun recordSuccess(route: String, info: StreamInfo): StreamInfo {
            attempts += Attempt(route, true, "清晰度 ${info.quality}，地址 ${info.url.length} 字符")
            lastAttempts = attempts.toList()
            cache["$id|$maxQuality"] = System.currentTimeMillis() to info
            return info
        }

        // ① Native：按用户档位
        try {
            return recordSuccess("Native", Graph.repo.stream(id, maxQuality))
        } catch (error: Throwable) {
            record("Native", error)
            if (!shouldFallback(error)) {
                lastAttempts = attempts.toList()
                throw error
            }
        }

        // ② Native：自动最高可用（解决片源只有 720/480 的情况）
        if (maxQuality != PlaybackQualityMode.AUTO_COMPAT) {
            try {
                return recordSuccess("Native·自动档", Graph.repo.stream(id, PlaybackQualityMode.AUTO_COMPAT))
            } catch (error: Throwable) {
                record("Native·自动档", error)
            }
        }

        // ③ Phoenix：按用户档位
        try {
            return recordSuccess("Phoenix", phoenix.stream(id, maxQuality))
        } catch (error: Throwable) {
            record("Phoenix", error)
            if (!shouldFallback(error)) {
                lastAttempts = attempts.toList()
                throw error
            }
        }

        // ④ Phoenix：自动最高可用
        try {
            return recordSuccess("Phoenix·自动档", phoenix.stream(id, PlaybackQualityMode.AUTO_COMPAT))
        } catch (error: Throwable) {
            record("Phoenix·自动档", error)
        }

        lastAttempts = attempts.toList()
        throw errors.lastOrNull() ?: UnplayableVideoException("本集片源暂不可用，请换一集")
    }

    /** 只有"这条线路不可用"才换线；主动取消不必重试。 */
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
