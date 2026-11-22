// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 从 GitHub Releases 检查新版本并下载安装包（与电脑版的自动更新等价）。 */
object Updater {

    data class Release(
        val version: String,
        val tag: String,
        val notes: String,
        val apkUrl: String,
        val size: Long
    )

    private const val API = "https://api.github.com/repos/jx0529/hongguo-plus-android/releases/latest"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** 阻塞调用，请在 IO 线程执行。返回 null 表示已是最新或检查失败。 */
    fun check(currentVersion: String): Release? =
        runCatching { fetch(currentVersion) }.getOrNull()

    private fun fetch(currentVersion: String): Release? {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HongguoPlus")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank() || !isNewer(version, currentVersion)) return null
            val assets = json.optJSONArray("assets") ?: return null
            var url = ""
            var size = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = asset.optString("browser_download_url")
                    size = asset.optLong("size")
                    break
                }
            }
            if (url.isBlank()) return null
            Release(version, tag, json.optString("body").take(3000), url, size)
        }
    }

    /** 下载到 cache/updates，返回安装包文件。 */
    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "HongguoPlus-${release.version}.apk")
        if (target.exists() && release.size > 0 && target.length() == release.size) return target

        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "HongguoPlus").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalError("下载失败（HTTP ${response.code}）")
            val body = response.body ?: throw IllegalError("下载响应为空")
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            var written = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        }
        return target
    }

    /** 拉起系统安装器。 */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    class IllegalError(message: String) : Exception(message)

    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(value: String) = value.split(".", "-").mapNotNull { it.toIntOrNull() }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
