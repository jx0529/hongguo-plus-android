// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import com.hongguotv.core.Series
import java.io.File

/**
 * 收藏 / 历史 / 点赞过的剧封面存一份到本地，
 * 列表滚动和重开 App 都不用再走网络（弱网下也不会一片空白）。
 */
object CoverStore {
    fun file(context: Context, id: String): File =
        File(File(context.filesDir, "covers"), "$id.jpg")

    /** 本地已有的封面路径；没有就返回 null。 */
    fun local(context: Context, id: String): String? =
        file(context, id).takeIf { it.exists() && it.length() > 0 }?.absolutePath

    /** 后台把一个封面落盘（已存在或失败都安全）。 */
    fun ensure(context: Context, series: Series) {
        if (series.cover.isBlank()) return
        val target = file(context, series.id)
        if (target.exists() && target.length() > 0) return
        runCatching {
            target.parentFile?.mkdirs()
            val request = okhttp3.Request.Builder()
                .url(series.cover)
                .header("User-Agent", "HongguoPlus")
                .build()
            Graph.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                val tmp = File(target.absolutePath + ".tmp")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                if (tmp.length() > 0) {
                    if (target.exists()) target.delete()
                    tmp.renameTo(target)
                } else {
                    tmp.delete()
                }
            }
        }
    }

    /** 批量补齐（列表打开时调一次）。 */
    fun ensureAll(context: Context, list: List<Series>) {
        list.take(40).forEach { runCatching { ensure(context, it) } }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, "covers").deleteRecursively() }
    }

    fun sizeBytes(context: Context): Long =
        runCatching { File(context.filesDir, "covers").walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            .getOrDefault(0L)
}
