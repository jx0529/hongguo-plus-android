// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.app.Application
import android.content.Context
import com.hongguotv.core.ContentRepository
import com.hongguotv.core.MemoryCookies
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 全局单例：网络、内容仓储、本地追剧库。 */
object Graph {
    @Volatile private var initialized = false

    lateinit var repo: ContentRepository
        private set
    lateinit var library: LibraryStore
        private set
    lateinit var account: AccountStore
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 站点 CDN 会在重定向下发 ttwid，缺少 Cookie 的取流请求只会拿到空响应
            .cookieJar(MemoryCookies())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            repo = ContentRepository(http)
            library = LibraryStore(context.applicationContext)
            account = AccountStore(context.applicationContext)
            appContext = context.applicationContext
            initialized = true
        }
    }
}

class PlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // 空闲时静默把新版本下好，用户下次进来直接点安装
        runCatching { AutoUpdate.syncInBackground(this) }
    }
}
