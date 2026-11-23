// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 安装完成后由系统回调（MY_PACKAGE_REPLACED），自动把 App 重新拉起来。
 * 这样「点更新 → 系统安装 → 自动重启」是一气呵成的，不用用户再点一次图标。
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        runCatching { context.startActivity(launch) }.onFailure {
            // 部分定制系统限制后台启动：退化为用包管理器打开（用户点一下即可）
            val pkgIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (pkgIntent != null) {
                pkgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(pkgIntent) }
            }
        }
    }
}
