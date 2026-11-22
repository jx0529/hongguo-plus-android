// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

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
    var error by remember { mutableStateOf<String?>(null) }
    var mfaHint by remember { mutableStateOf<String?>(null) }
    val login = remember { Graph.account.newLogin() }
    val scope = rememberCoroutineScope()

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
                        if (busy) return@clickable
                        val digits = phone.filter { it.isDigit() }
                        if (digits.length != 11) {
                            error = "请输入 11 位手机号"
                            return@clickable
                        }
                        busy = true
                        error = null
                        mfaHint = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { login.send(digits) }
                            }
                            result.onSuccess {
                                sentInfo = "验证码已发送至 ${it.maskedMobile}"
                                counting = it.retrySeconds.coerceIn(30, 120)
                            }.onFailure { error = friendly(it) }
                            busy = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(
                    when {
                        busy -> "发送中…"
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
                                    mfaHint = "平台要求你先向 ${failure.challenge.channelMobile} 发送一条验证短信，" +
                                        "发送后点「我已发送，继续登录」"
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
        if (mfaHint != null) {
            Spacer(Modifier.height(12.dp))
            Text(mfaHint!!, color = PlusColors.Gold, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PlusColors.SurfaceHigh)
                    .clickable {
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { login.verifyUpSmsAndLogin(code) }
                            }
                            result.onSuccess { credentials ->
                                Graph.account.save(credentials)
                                onDone()
                            }.onFailure { error = friendly(it) }
                            busy = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("我已发送，继续登录", color = PlusColors.Text, fontSize = 13.sp)
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
