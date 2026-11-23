// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.WebLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 红果官方扫码登录：官网下发二维码 → 用红果 App 扫码并确认 → 轮询拿到登录态。
 * 不走短信，不需要上行短信校验，登录成功后账号 Cookie 直接可用于站内全部接口。
 */
@Composable
fun QrLoginPanel(onDone: () -> Unit) {
    var qrImage by remember { mutableStateOf<Bitmap?>(null) }
    var left by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("正在获取二维码…") }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(round) {
        error = null
        qrImage = null
        status = "正在获取二维码…"
        val login = WebLogin(Graph.http)
        val requested = withContext(Dispatchers.IO) { runCatching { login.qr() } }
        val qr = requested.getOrNull()
        if (qr == null) {
            error = requested.exceptionOrNull()?.message ?: "二维码获取失败，请稍后重试"
            status = ""
            return@LaunchedEffect
        }
        qrImage = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeByteArray(qr.image, 0, qr.image.size) }.getOrNull()
        }
        left = qr.expiresIn
        status = "打开红果 App → 我的 → 扫一扫，确认后自动登录"

        while (true) {
            if (left <= 0) {
                status = ""
                error = "二维码已过期，点「刷新二维码」重新获取"
                return@LaunchedEffect
            }
            delay(2000)
            left -= 2
            val polled = withContext(Dispatchers.IO) { runCatching { login.poll() } }
            var finished = false
            polled.onSuccess { ok ->
                if (ok) {
                    val creds = withContext(Dispatchers.IO) { runCatching { login.credentials() } }
                    creds.onSuccess { credentials ->
                        Graph.account.save(credentials)
                        withContext(Dispatchers.IO) { runCatching { Graph.account.profile(credentials) } }
                        status = "登录成功"
                        finished = true
                        onDone()
                    }.onFailure { failure ->
                        error = failure.message ?: "已扫码但取登录态失败，请重试"
                        finished = true
                    }
                }
            }.onFailure { failure ->
                val message = failure.message.orEmpty()
                if (message.contains("过期")) {
                    status = ""
                    error = "二维码已过期，点「刷新二维码」重新获取"
                    finished = true
                } else {
                    status = "扫码确认中…剩余 ${left}s"
                }
            }
            if (finished) return@LaunchedEffect
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlusColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = qrImage
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "红果登录二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(200.dp)
                )
            } else {
                Text("获取中…", color = PlusColors.SubText, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (status.isNotBlank()) {
            Text(status, color = PlusColors.SubText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (left > 0) {
            Spacer(Modifier.height(4.dp))
            Text("有效期 ${left}s", color = PlusColors.Gold, fontSize = 11.sp)
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = PlusColors.Accent, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.Accent)
                    .clickable { round++ }
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text("刷新二维码", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "扫码用的是红果自己的账号体系，登录后「没看完」「收藏」「观看进度」都能同步。",
            color = PlusColors.SubText,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}
