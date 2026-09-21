package com.cloudbox.app

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.cloudbox.app.data.api.ApiClient
import com.cloudbox.app.data.local.TokenManager
import java.io.File

class CloudBoxApplication : Application(), SingletonImageLoader.Factory {
    companion object {
        private var videoCacheInstance: SimpleCache? = null

        @OptIn(UnstableApi::class)
        @Synchronized
        fun getVideoCache(context: Context): SimpleCache {
            if (videoCacheInstance == null) {
                val cacheDir = File(context.cacheDir, "media3_video_cache")
                val databaseProvider = StandaloneDatabaseProvider(context)
                val evictor = LeastRecentlyUsedCacheEvictor(100L * 1024 * 1024) // 100 MB video cache
                videoCacheInstance = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return videoCacheInstance!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { ApiClient.okHttpClient }
                    )
                )
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}

