// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import com.hongguotv.core.AccountCredentials
import com.hongguotv.core.CloudAccountClient
import com.hongguotv.core.CloudFavorite
import com.hongguotv.core.CloudProgress
import com.hongguotv.core.CloudProfile
import com.hongguotv.core.PhoneLogin

/** 登录态与云端数据：凭证只保存在本机 SharedPreferences，不上传任何第三方。 */
class AccountStore(context: Context) {

    private val prefs = context.getSharedPreferences("hongguoplus_account", Context.MODE_PRIVATE)

    fun credentials(): AccountCredentials? {
        val stored = prefs.getString(KEY_CREDENTIALS, null) ?: return null
        val raw = SecretBox.decrypt(stored) ?: return null
        val parsed = runCatching { AccountCredentials.fromJson(raw) }.getOrNull() ?: return null
        // 旧数据是明文：读到就立刻改写成密文，用户无需重新登录
        if (!SecretBox.isEncrypted(stored)) {
            prefs.edit().putString(KEY_CREDENTIALS, SecretBox.encrypt(raw)).apply()
        }
        return parsed
    }

    fun save(credentials: AccountCredentials) {
        prefs.edit().putString(KEY_CREDENTIALS, SecretBox.encrypt(credentials.toJson())).apply()
    }

    /** 凭证是否已用 Keystore 加密保存。 */
    fun credentialsEncrypted(): Boolean =
        SecretBox.isEncrypted(prefs.getString(KEY_CREDENTIALS, null))

    fun clear() {
        prefs.edit().remove(KEY_CREDENTIALS).remove(KEY_NICKNAME).apply()
    }

    fun isLoggedIn(): Boolean = credentials() != null

    var nickname: String
        get() = prefs.getString(KEY_NICKNAME, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_NICKNAME, value).apply()

    fun newLogin(): PhoneLogin = PhoneLogin(Graph.http)

    fun client(): CloudAccountClient = CloudAccountClient(Graph.http)

    fun profile(credentials: AccountCredentials? = credentials()): CloudProfile {
        if (credentials == null) return CloudProfile("", "")
        return client().profile(credentials).also { nickname = it.nickname }
    }

    /** 云端观看记录，用于「没看完」列表。 */
    fun progress(credentials: AccountCredentials? = credentials()): List<CloudProgress> {
        if (credentials == null) return emptyList()
        return client().history(credentials)
    }

    fun favorites(credentials: AccountCredentials? = credentials()): List<CloudFavorite> {
        if (credentials == null) return emptyList()
        return client().favorites(credentials)
    }

    fun uploadProgress(seriesId: String, episodeId: String, episodeIndex: Int, position: Long, duration: Long, episodeCount: Int) {
        val credentials = credentials() ?: return
        val progress = CloudProgress(
            series = com.hongguotv.core.Series(seriesId, ""),
            episodeId = episodeId,
            episodeIndex = episodeIndex,
            position = position,
            duration = duration,
            updatedAt = System.currentTimeMillis(),
            episodeCount = episodeCount
        )
        runCatching { client().upload(credentials, progress, episodeCount) }
    }

    companion object {
        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_NICKNAME = "nickname"
    }
}

/** 把云端进度合并进本地历史，并给出「没看完」的列表。 */
data class UnfinishedItem(
    val series: com.hongguotv.core.Series,
    val episodeIndex: Int,
    val episodeCount: Int,
    val progressPercent: Int
)

fun mergeCloudProgress(items: List<CloudProgress>): List<UnfinishedItem> =
    items.mapNotNull { item ->
        if (item.series.id.isBlank() || item.duration <= 0L) return@mapNotNull null
        val local = Graph.library.historyOf(item.series.id)
        if (local == null) {
            Graph.library.putHistory(
                series = item.series,
                episodeIndex = item.episodeIndex,
                episodeCount = item.episodeCount,
                positionMs = item.position
            )
            val ratio = (item.position.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
            return@mapNotNull UnfinishedItem(item.series, item.episodeIndex, item.episodeCount, (ratio * 100).toInt())
        }
        // 本机进度更新（集数更靠后）就以本机为准，云端只用来补缺
        if (local.episodeIndex >= item.episodeIndex) {
            val percent = if (local.episodeCount > 0) {
                (((local.episodeIndex + 1).toFloat() / local.episodeCount) * 100).toInt().coerceIn(1, 100)
            } else 0
            return@mapNotNull UnfinishedItem(local.series, local.episodeIndex, local.episodeCount, percent)
        }
        Graph.library.putHistory(
            series = item.series,
            episodeIndex = item.episodeIndex,
            episodeCount = item.episodeCount,
            positionMs = item.position
        )
        val ratio = (item.position.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
        UnfinishedItem(item.series, item.episodeIndex, item.episodeCount, (ratio * 100).toInt())
    }
