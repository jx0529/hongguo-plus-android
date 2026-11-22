// SPDX-License-Identifier: GPL-3.0-only
package com.jx.hongguoplus

import android.app.Application
import android.content.Context
import com.hongguotv.core.ContentRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 全局单例：网络、内容仓储、本地追剧库。 */
object Graph {
    @Volatile private var initialized = false

    lateinit var repo: ContentRepository
        private set
    lateinit var library: LibraryStore
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            repo = ContentRepository(http)
            library = LibraryStore(context.applicationContext)
            initialized = true
        }
    }
}

class PlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
