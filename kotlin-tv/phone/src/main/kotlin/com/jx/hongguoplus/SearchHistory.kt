// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import org.json.JSONArray

/** 搜索记录：本机保存最近 20 条，可单条删除或一键清空。 */
object SearchHistory {
    private const val KEY = "history"
    private const val LIMIT = 20

    private fun prefs(context: Context) =
        context.getSharedPreferences("hongguoplus_search_history", Context.MODE_PRIVATE)

    fun load(context: Context): List<String> = runCatching {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    fun add(context: Context, keyword: String) {
        val word = keyword.trim()
        if (word.isBlank()) return
        val next = (listOf(word) + load(context).filterNot { it == word }).take(LIMIT)
        write(context, next)
    }

    fun remove(context: Context, keyword: String) {
        write(context, load(context).filterNot { it == keyword })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun write(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }
}
