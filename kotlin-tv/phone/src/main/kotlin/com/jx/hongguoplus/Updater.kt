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
        val size: Long,
        val altUrl: String = ""
    )

    private const val API = "https://api.github.com/repos/jx0529/hongguo-plus-android/releases/latest"
    // 高速 CDN：jsDelivr 直接读仓库 dist 分支（国内通常秒开），比 GitHub 稳定得多
    private const val CDN_MANIFEST = "https://cdn.jsdelivr.net/gh/jx0529/hongguo-plus-android@dist/latest.json"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** 阻塞调用，请在 IO 线程执行。返回 null 表示已是最新或检查失败。 */
    fun check(currentVersion: String): Release? = checkDetailed(currentVersion).getOrNull()

    /**
     * 区分「已是最新」和「检查失败」：
     * CDN 清单能读到就以其为准；读不到才回退 GitHub API，仍失败则抛出真实错误。
     */
    fun checkDetailed(currentVersion: String): Result<Release?> = runCatching {
        val direct = runCatching { fetch(currentVersion) }
        if (direct.isSuccess) return@runCatching direct.getOrNull()
        val cdn = runCatching { fetchFromCdn(currentVersion) }
        if (cdn.isSuccess) return@runCatching cdn.getOrNull()
        throw direct.exceptionOrNull() ?: java.io.IOException("更新服务器不可达")
    }

    /** 先从 CDN 上的更新清单取：一条小 JSON，国内基本不受墙影响。 */
    private fun fetchFromCdn(currentVersion: String): Release? {
        val url = "$CDN_MANIFEST?t=${System.currentTimeMillis() / 3600_000L}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HongguoPlus")
            .header("Cache-Control", "no-cache")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val json = JSONObject(body)
            val version = json.optString("version").removePrefix("v")
            if (version.isBlank() || !isNewer(version, currentVersion)) return null
            val apkUrl = json.optString("url")
            if (apkUrl.isBlank()) return null
            Release(
                version = version,
                tag = "v$version",
                notes = json.optString("notes").take(3000),
                apkUrl = apkUrl,
                size = json.optLong("size"),
                altUrl = json.optString("github")
            )
        }
    }

    private fun fetch(currentVersion: String): Release? {
        var lastError: Throwable? = null
        for (api in listOf(
            API,
            "https://gh-proxy.com/$API",
            "https://v6.gh-proxy.org/$API",
            "https://ghfast.top/$API"
        )) {
            try {
                return fetchOne(api, currentVersion)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: java.io.IOException("更新服务器不可达")
    }

    private fun fetchOne(url: String, currentVersion: String): Release? {
        val request = Request.Builder()
            .url(url)
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
        return downloadResume(context, release, onProgress)
    }

    private fun legacyDownload(context: Context, release: Release, onProgress: (Int) -> Unit): File {
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

    /** 是否允许本应用安装未知来源的包；不允许时先引导去开权限。 */
    fun canInstall(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 走 PackageInstaller 会话安装：比 ACTION_VIEW 更可靠（不受个别 ROM 的“打开文件”拦截），
     * 安装完成的回调里自动把 App 拉起来。
     */
    fun install(context: Context, apk: File) {
        if (!canInstall(context)) {
            openInstallPermission(context)
            throw IllegalError("请先允许「红果Plus」安装应用，然后回到更新弹窗重试")
        }
        val ok = runCatching {
            val installer = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 256 * 1024)
                        session.fsync(output)
                    }
                }
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
                val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                val pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    flags
                )
                session.commit(pending.intentSender)
            }
        }.isSuccess

        if (!ok) {
            // 兜底：老路拉起系统安装器
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 国内直连 github.com 经常被墙：按顺序尝试多个加速镜像。 */
    private fun mirrors(url: String): List<String> {
        if (url.contains("cdn.jsdelivr.net")) {
            return listOf(url, "https://fastly.jsdelivr.net" + url.substringAfter("cdn.jsdelivr.net"))
        }
        return listOf(
            url,
            "https://gh-proxy.com/$url",
            "https://v6.gh-proxy.org/$url",
            "https://ghfast.top/$url",
            "https://ghproxy.net/$url"
        )
    }

    private fun fetchTo(url: String, target: File, startBytes: Long, release: Release, onProgress: (Int) -> Unit): Long {
        val builder = Request.Builder().url(url).header("User-Agent", "HongguoPlus")
        if (startBytes > 0) builder.header("Range", "bytes=$startBytes-")
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalError("HTTP ${response.code}")
            val resuming = startBytes > 0 && response.code == 206
            var have = if (resuming) startBytes else 0L
            val body = response.body ?: throw IllegalError("空响应")
            val total = (body.contentLength() + have).takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                java.io.FileOutputStream(target, resuming).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        have += read
                        if (total > 0) onProgress(((have * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            return have
        }
    }

    /** 下载（断点续传 + 多镜像重试）：国内直连失败时自动换加速线。 */
    private fun downloadResume(context: Context, release: Release, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "HongguoPlus-${release.version}.apk")
        if (target.exists() && release.size > 0 && target.length() == release.size) return target

        var lastError: Throwable? = null
        val sources = buildList {
            addAll(mirrors(release.apkUrl))
            if (release.altUrl.isNotBlank()) addAll(mirrors(release.altUrl))
        }
        for (url in sources) {
            val have = if (target.exists()) target.length() else 0L
            if (release.size > 0 && have == release.size) return target
            runCatching { fetchTo(url, target, have, release, onProgress) }
                .onSuccess {
                    if (release.size <= 0 || target.length() == release.size) return target
                }
                .onFailure { lastError = it }
        }
        target.delete()
        throw IllegalError("下载失败：${lastError?.message ?: "网络不可达"}（已尝试镜像，可点「浏览器打开」手动下载）")
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
