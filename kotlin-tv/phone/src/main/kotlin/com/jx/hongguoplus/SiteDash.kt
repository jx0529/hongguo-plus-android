// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 站点直连取流：走红果网页版播放器用的那条接口
 * `POST https://hongguoduanju.com/api/video/protected/play-info?aid=8662`。
 *
 * 它不需要 App 的签名与设备注册（网页版自己就是这么拿的），返回的是 DASH 片源：
 * 视频与音频各一个 mp4（ftyp + sidx），用 `init_range` / `index_range` 做单文件分段。
 * 因此这里把两路拼成一份最小 MPD，交给 Media3 的 DASH 播放器直接播。
 *
 * 之所以要这条线路：App 的 video_model 接口近期对第三方客户端一律回
 * `HTTP 200 + 0 字节`，四条线路（Native / Phoenix × 指定档 / 自动档）全空，
 * 但同一台设备用这条网页接口能稳定拿到片源。
 */
object SiteDash {

    data class Plan(
        val video: String,
        val audio: String,
        val initVideo: String,
        val indexVideo: String,
        val initAudio: String,
        val indexAudio: String,
        val durationSeconds: Double,
        val bandwidthVideo: Int,
        val bandwidthAudio: Int,
        val width: Int,
        val height: Int,
        val quality: String
    ) {
        val heightPx: Int get() = height
    }

    private const val ENDPOINT = "https://hongguoduanju.com/api/video/protected/play-info?aid=8662"

    private val http: OkHttpClient by lazy {
        Graph.http.newBuilder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 清晰度档位：站点只有 360p / 480p / 720p。 */
    private fun candidates(maxQuality: Int): List<String> = when {
        maxQuality >= 720 -> listOf("720p", "480p", "360p")
        maxQuality >= 480 -> listOf("480p", "360p")
        else -> listOf("360p")
    }

    fun resolve(seriesId: String, episodeId: String, maxQuality: Int): Plan? {
        if (!seriesId.matches(Regex("[0-9]{1,30}")) || !episodeId.matches(Regex("[0-9]{1,30}"))) return null
        var lastError: Throwable? = null
        for (definition in candidates(maxQuality)) {
            val plan = runCatching { request(seriesId, episodeId, definition) }
                .onFailure { lastError = it }
                .getOrNull()
            if (plan != null) return plan
        }
        lastError?.let { throw it }
        return null
    }

    private fun request(seriesId: String, episodeId: String, definition: String): Plan? {
        val body = JSONObject()
            .put("series_id", seriesId)
            .put("item_id", episodeId)
            .put("definition", definition)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        // 站点接口对「第 2 集以后」的片源做保护，未登录只会回 403 protected_playback_unavailable；
        // 登录态（扫码/验证码拿到的站内 Cookie）带上就能正常下发。
        val cookie = SiteSession.playbackCookie()
        val builder = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", UA)
            .header("Referer", "https://hongguoduanju.com/player/$seriesId/$episodeId")
            .header("Accept", "application/json")
        if (cookie.isNotBlank()) builder.header("Cookie", cookie)
        val request = builder.post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("站点播放接口 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw java.io.IOException("站点播放接口返回空响应")
            val root = JSONObject(text)
            val base = root.optJSONObject("base_resp")
            val code = base?.optInt("StatusCode", 0) ?: 0
            if (code != 0) throw java.io.IOException("站点播放接口业务异常：$code")
            val info = root.optJSONObject("play_info") ?: throw java.io.IOException("站点播放接口没有 play_info")
            val video = info.optString("main_url")
            val audioObj = info.optJSONObject("audio") ?: return null
            val audio = audioObj.optString("main_url")
            if (video.isBlank() || audio.isBlank()) return null
            return Plan(
                video = video,
                audio = audio,
                initVideo = info.optString("init_range"),
                indexVideo = info.optString("index_range"),
                initAudio = audioObj.optString("init_range"),
                indexAudio = audioObj.optString("index_range"),
                durationSeconds = info.optDouble("duration", 0.0),
                bandwidthVideo = info.optInt("bitrate", 2_000_000),
                bandwidthAudio = audioObj.optInt("bitrate", 128_000),
                width = info.optInt("width", 720),
                height = info.optInt("height", 1280),
                quality = info.optString("definition", definition)
            )
        }
    }

    /**
     * 拼一份最小 MPD（单文件 + SegmentBase）并写到缓存目录。
     *
     * 之前用的是 `data:application/dash+xml;base64,...`，Media3 的 DashMediaSource 要先
     * 从 URI 推断 MIME，data: 没有扩展名推不出来，于是 prepare 直接失败——自检能过
     * （分段 206 正常）但播放器就是不出画面，原因就在这里。
     */
    fun mpdFile(plan: Plan): java.io.File {
        val mpd = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" """)
            append("""profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" """)
            append("""mediaPresentationDuration="${duration(plan.durationSeconds)}" minBufferTime="PT2S">""")
            append("""<Period duration="${duration(plan.durationSeconds)}">""")
            append("""<AdaptationSet mimeType="video/mp4" segmentAlignment="true" startWithSAP="1">""")
            append("""<Representation id="v" bandwidth="${plan.bandwidthVideo}" """)
            append("""width="${plan.width}" height="${plan.height}" codecs="avc1.4d401f">""")
            append("<BaseURL>").append(escape(plan.video)).append("</BaseURL>")
            append("""<SegmentBase indexRange="${plan.indexVideo}" timescale="1000" presentationTimeOffset="0"><Initialization range="${plan.initVideo}"/></SegmentBase>""")
            append("</Representation></AdaptationSet>")
            append("""<AdaptationSet mimeType="audio/mp4" segmentAlignment="true">""")
            append("""<Representation id="a" bandwidth="${plan.bandwidthAudio}" codecs="mp4a.40.2">""")
            append("<BaseURL>").append(escape(plan.audio)).append("</BaseURL>")
            append("""<SegmentBase indexRange="${plan.indexAudio}" timescale="1000" presentationTimeOffset="0"><Initialization range="${plan.initAudio}"/></SegmentBase>""")
            append("</Representation></AdaptationSet>")
            append("</Period></MPD>")
        }
        val dir = java.io.File(Graph.appContext.cacheDir, "site-mpd").apply { mkdirs() }
        val file = java.io.File(dir, "site-" + plan.video.hashCode() + ".mpd")
        if (!file.exists() || file.length() < 100) file.writeText(mpd, Charsets.UTF_8)
        return file
    }

    private fun duration(seconds: Double): String {
        if (seconds <= 0.0) return "PT0S"
        val ms = (seconds * 1000).toLong()
        return "PT${ms / 1000}.${ms % 1000}S"
    }

    private fun escape(url: String): String = url
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
}
