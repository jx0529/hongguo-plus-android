// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 自动更新的后台任务：App 退到后台甚至被划掉，系统仍会在联网时把它跑完。
 * 逻辑都在 [AutoUpdate.runSync]（检查 → 多镜像 + CDN 下载），这里只负责调度外壳。
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AutoUpdate.enabled(applicationContext)) return Result.success()
        val ok = AutoUpdate.runSync(applicationContext)
        val message = AutoUpdate.message
        return if (!ok && (message.contains("失败") || message.contains("未完成"))) Result.retry() else Result.success()
    }
}
