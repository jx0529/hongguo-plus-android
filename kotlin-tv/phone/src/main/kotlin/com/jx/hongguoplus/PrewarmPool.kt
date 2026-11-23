// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import com.hongguotv.core.RemoteVideo

/**
 * 全局预热池：详情页打开时就先把前几集的流准备好，进播放器直接取，省掉取流+探测的等待。
 */
object PrewarmPool {
    private val entries = LinkedHashMap<String, RemoteVideo>(8, 0.75f, true)
    private const val MAX = 8

    @Synchronized
    fun key(episodeId: String, quality: Int) = "$episodeId|$quality"

    @Synchronized
    fun take(episodeId: String, quality: Int): RemoteVideo? = entries.remove(key(episodeId, quality))

    @Synchronized
    fun put(episodeId: String, quality: Int, video: RemoteVideo) {
        val k = key(episodeId, quality)
        entries.remove(k)?.let { runCatching { it.close() } }
        entries[k] = video
        while (entries.size > MAX) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.let { runCatching { it.close() } }
        }
    }

    @Synchronized
    fun clear() {
        entries.values.forEach { runCatching { it.close() } }
        entries.clear()
    }
}
