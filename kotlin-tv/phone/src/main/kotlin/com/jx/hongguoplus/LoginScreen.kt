// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hongguotv.core.PhoneLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sentInfo by remember { mutableStateOf<String?>(null) }
    var counting by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var challenge by remember { mutableStateOf<PhoneLogin.UpSmsChallenge?>(null) }
    var smsOpened by remember { mutableStateOf(false) }
    var challengeAt by remember { mutableStateOf(0L) }
    var challengeLeft by remember { mutableStateOf(0) }
    var verifyingNote by remember { mutableStateOf<String?>(null) }
    val login = remember { Graph.account.newLogin() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 拿到上行短信挑战后，自动带着号码和正文跳到短信界面（每轮只自动跳一次，避免反复打断）
    LaunchedEffect(challenge) {
        val c = challenge ?: return@LaunchedEffect
        if (!smsOpened) {
            smsOpened = true
            openSmsComposer(context, c.channelMobile, c.smsContent)
        }
    }

    // 上行短信校验窗口很短，给用户一个明确倒计时
    LaunchedEffect(challengeAt) {
        if (challengeAt <= 0L) {
            challengeLeft = 0
            return@LaunchedEffect
        }
        while (true) {
            val left = (60 - (System.currentTimeMillis() - challengeAt) / 1000).toInt()
            challengeLeft = left.coerceAtLeast(0)
            if (left <= 0) break
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(counting) {
        if (counting > 0) {
            kotlinx.coroutines.delay(1000)
            counting -= 1
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = PlusColors.Text, fontSize = 24.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.padding(6.dp))
            Text("登录红果账号", color = PlusColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "登录后可以同步「没看完」、收藏与观看进度。账号信息只保存在本机。",
            color = PlusColors.SubText,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(18.dp))

        Field(label = "手机号", value = phone, hint = "输入手机号", keyboard = KeyboardType.Phone) { phone = it }
        Spacer(Modifier.height(12.dp))
        Field(label = "验证码", value = code, hint = "短信验证码", keyboard = KeyboardType.Number) { code = it }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.SurfaceHigh)
                    .clickable {
                        if (sending || busy) return@clickable
                        val digits = phone.filter { it.isDigit() }
                        if (digits.length != 11) {
                            error = "请输入 11 位手机号"
                            return@clickable
                        }
                        sending = true
                        error = null
                        challenge = null
                        smsOpened = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { login.send(digits) }
                            }
                            result.onSuccess {
                                sentInfo = "验证码已发送至 ${it.maskedMobile}"
                                counting = it.retrySeconds.coerceIn(30, 120)
                            }.onFailure { error = friendly(it) }
                            sending = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(
                    when {
                        sending -> "发送中…"
                        counting > 0 -> "${counting}s 后重发"
                        else -> "获取验证码"
                    },
                    color = PlusColors.Text,
                    fontSize = 13.sp
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.Accent)
                    .clickable {
                        if (busy) return@clickable
                        if (code.length < 4) {
                            error = "请输入 4–8 位验证码"
                            return@clickable
                        }
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { login.verify(code) }
                            }
                            result.onSuccess { credentials ->
                                Graph.account.save(credentials)
                                withContext(Dispatchers.IO) {
                                    runCatching { Graph.account.profile(credentials) }
                                }
                                onDone()
                            }.onFailure { failure ->
                                if (failure is PhoneLogin.UpSmsRequired) {
                                    challenge = failure.challenge
                                    challengeAt = System.currentTimeMillis()
                                } else {
                                    error = friendly(failure)
                                }
                            }
                            busy = false
                        }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (busy) "登录中…" else "登录", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (sentInfo != null) {
            Text(sentInfo!!, color = PlusColors.SubText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        challenge?.let { c ->
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PlusColors.SurfaceHigh)
                    .padding(14.dp)
            ) {
                Text(
                    if (challengeLeft > 0) "红果需要你再发一条验证短信（${challengeLeft}s 内有效）"
                    else "这次校验已过期，请重新获取验证码",
                    color = if (challengeLeft > 0) PlusColors.Gold else PlusColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "这是红果账号的安全校验：用你本人的手机号，把下面这条短信发给它给的号码，平台才认这次登录。",
                    color = PlusColors.SubText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("发送到：${c.channelMobile}", color = PlusColors.Text, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("短信内容（原样发送，不要改）：", color = PlusColors.SubText, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    c.smsContent,
                    color = PlusColors.Gold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(PlusColors.Accent)
                            .clickable {
                                smsOpened = true
                                openSmsComposer(context, c.channelMobile, c.smsContent)
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("打开短信，已自动填好", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PlusColors.Surface)
                            .clickable {
                                clipboard.setText(AnnotatedString(c.smsContent))
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("复制内容", color = PlusColors.Text, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(PlusColors.Surface)
                        .clickable {
                            if (busy) return@clickable
                            busy = true
                            error = null
                            verifyingNote = "正在确认平台是否收到短信…"
                            scope.launch {
                                var lastError: Throwable? = null
                                var done = false
                                repeat(10) { attempt ->
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { login.verifyUpSmsAndLogin(code) }
                                    }
                                    result.onSuccess { credentials ->
                                        Graph.account.save(credentials)
                                        challenge = null
                                        verifyingNote = null
                                        busy = false
                                        done = true
                                        onDone()
                                    }.onFailure { failure ->
                                        lastError = failure
                                        val text = friendly(failure)
                                        // 过期/风控类错误重试无意义，直接结束
                                        if (text.contains("1066") || text.contains("过期")) {
                                            verifyingNote = null
                                            error = "校验窗口已过（平台只给了很短时间）\n处理办法：点上面的「获取验证码」重新发一次，短信一到手立刻点「继续登录」。"
                                            busy = false
                                            done = true
                                        }
                                    }
                                    if (done) return@launch
                                    if (attempt < 9) {
                                        verifyingNote = "第 ${attempt + 2} 次确认中…（短信可能有 1–3 秒延迟）"
                                        kotlinx.coroutines.delay(2000)
                                    }
                                }
                                if (!done) {
                                    verifyingNote = null
                                    busy = false
                                    lastError?.let { error = friendly(it) }
                                }
                            }
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (busy) "校验中…" else "短信已发出，继续登录",
                        color = PlusColors.Text,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "发完短信立刻点上面的按钮，程序会自动重试等平台收到短信；" +
                        "超过 ${challengeLeft.coerceAtLeast(0)} 秒没成功就回到上面重新「获取验证码」，再发一次。",
                    color = PlusColors.SubText,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = PlusColors.Accent, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = PlusColors.SubText, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PlusColors.SurfaceHigh)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = PlusColors.Text, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(hint, color = PlusColors.SubText, fontSize = 15.sp)
                        inner()
                    }
                }
            )
        }
    }
}

private fun friendly(failure: Throwable): String = when (failure) {
    is PhoneLogin.Failure -> failure.message ?: "登录失败"
    else -> failure.message ?: "登录失败，请稍后重试"
}

/**
 * 拉起系统短信界面，并把号码和正文预填好，用户只要点发送。
 * 依次尝试 smsto（带 sms_body，绝大多数短信 App 支持）→ 纯 smsto → 通用分享。
 */
private fun openSmsComposer(context: Context, number: String, body: String) {
    val digits = number.filter { it.isDigit() || it == '+' }
    val candidates = listOf(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")).apply {
            putExtra("sms_body", body)
            putExtra("exit_on_sent", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra("address", digits)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // 换下一个方案
        }
    }
}
