package com.example

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.util.DevicePerf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File

class VragApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var diContainer: AppContainer
            private set
    }

    override fun onCreate() {
        super.onCreate()
        DevicePerf.init(this)
        diContainer = DefaultAppContainer(this)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        // Cap concurrent bitmap decodes to 1 on low-end 4-core CPUs (e.g. MT6739W)
        // so Main Thread & RenderThread always have free cores during fast list flings.
        val decodeDispatcher = Dispatchers.IO.limitedParallelism(DevicePerf.coilParallelism)
        val memPercent = if (DevicePerf.isLowRam) 0.08 else 0.10
        val diskBytes = if (DevicePerf.isLowRam) 16L * 1024L * 1024L else 24L * 1024L * 1024L

        return ImageLoader.Builder(this)
            .fetcherDispatcher(decodeDispatcher)
            .decoderDispatcher(decodeDispatcher)
            .transformationDispatcher(decodeDispatcher)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "vrag_art_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(false)
            .allowHardware(true)
            .respectCacheHeaders(false)
            .build()
    }
}
