// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import com.hongguotv.core.Series
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val series: Series,
    val episodeIndex: Int,
    val episodeCount: Int,
    val updatedAt: Long,
    val positionMs: Long = 0L
)

/**
 * 本地追剧库：收藏 + 观看历史，纯本地（SharedPreferences + JSON），不依赖登录。
 * 数据结构保持向后兼容，后续可以接云端同步。
 */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("hongguoplus_library", Context.MODE_PRIVATE)

    fun favorites(): List<Series> = runCatching {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toSeries() }
    }.getOrDefault(emptyList())

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun toggleFavorite(series: Series): Boolean {
        val list = favorites().toMutableList()
        val removed = list.removeAll { it.id == series.id }
        if (!removed) list.add(0, series)
        writeFavorites(list)
        return !removed
    }

    fun removeFavorite(id: String) {
        writeFavorites(favorites().filterNot { it.id == id })
    }

    fun clearFavorites() = writeFavorites(emptyList())

    private fun writeFavorites(list: List<Series>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun history(): List<HistoryEntry> = runCatching {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val series = o.optJSONObject("series")?.toSeries() ?: return@mapNotNull null
            HistoryEntry(
                series = series,
                episodeIndex = o.optInt("episodeIndex", 0),
                episodeCount = o.optInt("episodeCount", 0),
                updatedAt = o.optLong("updatedAt", 0L),
                positionMs = o.optLong("positionMs", 0L)
            )
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun historyOf(id: String): HistoryEntry? = history().firstOrNull { it.series.id == id }

    fun putHistory(series: Series, episodeIndex: Int, episodeCount: Int, positionMs: Long = 0L) {
        val list = history().filterNot { it.series.id == series.id }.toMutableList()
        list.add(
            0,
            HistoryEntry(series, episodeIndex, episodeCount, System.currentTimeMillis(), positionMs)
        )
        val array = JSONArray()
        list.take(MAX_HISTORY).forEach { entry ->
            array.put(
                JSONObject()
                    .put("series", entry.series.toJson())
                    .put("episodeIndex", entry.episodeIndex)
                    .put("episodeCount", entry.episodeCount)
                    .put("updatedAt", entry.updatedAt)
                    .put("positionMs", entry.positionMs)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    /** 只更新本剧进度，不改变列表顺序语义（但会刷新时间为最近观看）。 */
    fun putProgress(series: Series, episodeIndex: Int, episodeCount: Int, positionMs: Long) =
        putHistory(series, episodeIndex, episodeCount, positionMs)

    fun progressOf(id: String): Pair<Int, Long> {
        val entry = historyOf(id) ?: return 0 to 0L
        return entry.episodeIndex to entry.positionMs
    }

    fun removeHistory(id: String) {
        val list = history().filterNot { it.series.id == id }
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("series", entry.series.toJson())
                    .put("episodeIndex", entry.episodeIndex)
                    .put("episodeCount", entry.episodeCount)
                    .put("updatedAt", entry.updatedAt)
                    .put("positionMs", entry.positionMs)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    /** 播放偏好：默认清晰度、倍速、静音开播。 */
    var defaultQuality: Int
        get() = prefs.getInt(KEY_QUALITY, 1080)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    var defaultSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HISTORY = "history"
        private const val KEY_QUALITY = "quality"
        private const val KEY_SPEED = "speed"
        private const val MAX_HISTORY = 200
    }
}

private fun Series.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("cover", cover)
    .put("description", description)
    .put("badge", badge)
    .put("tags", tags)

private fun JSONObject.toSeries(): Series = Series(
    id = optString("id"),
    title = optString("title"),
    cover = optString("cover"),
    description = optString("description"),
    badge = optString("badge"),
    tags = optString("tags")
)
