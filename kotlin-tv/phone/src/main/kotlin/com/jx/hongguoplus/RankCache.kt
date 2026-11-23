// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import com.hongguotv.core.RankBoard
import com.hongguotv.core.Series
import org.json.JSONArray

/**
 * 榜单本地缓存：打开就有内容，且每天首次打开会用当天最新的榜单覆盖。
 */
object RankCache {
    private const val FILE = "hongguoplus_rank_cache"

    data class Entry(val items: List<Series>, val day: Long)

    fun load(context: Context, board: RankBoard): Entry {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString(board.name, null) ?: return Entry(emptyList(), 0L)
        val day = sp.getLong("${board.name}_day", 0L)
        val items = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Series.fromJson(it) }
            }
        }.getOrDefault(emptyList())
        return Entry(items, day)
    }

    fun save(context: Context, board: RankBoard, items: List<Series>) {
        if (items.isEmpty()) return
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(board.name, arr.toString())
            .putLong("${board.name}_day", today())
            .apply()
    }

    fun isToday(day: Long) = day == today()

    private fun today(): Long = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
}
