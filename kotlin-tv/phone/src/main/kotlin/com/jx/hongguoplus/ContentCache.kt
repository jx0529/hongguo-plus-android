// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import com.hongguotv.core.Detail
import java.util.concurrent.ConcurrentHashMap

/**
 * 详情页内存缓存：同一部剧 10 分钟内重复进入不再打接口（接口单次约 1.5 秒）。
 */
object ContentCache {
    private const val TTL = 10 * 60 * 1000L
    private val details = ConcurrentHashMap<String, Pair<Long, Detail>>()

    fun detail(id: String): Detail {
        details[id]?.let { (at, value) ->
            if (System.currentTimeMillis() - at < TTL) return value
        }
        val fresh = Graph.repo.detail(id)
        details[id] = System.currentTimeMillis() to fresh
        return fresh
    }

    fun clear() = details.clear()
}
