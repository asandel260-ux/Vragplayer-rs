package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Hardware performance profiler initialized once in VragApplication.onCreate().
 * Automatically tunes Coil concurrency, GPU shader passes, and default visual effects
 * so ultra-entry-level phones (e.g. Samsung Galaxy A02 — quad-core Cortex-A53, PowerVR GE8100,
 * 2-3 GB RAM) hold 60fps during scrolling and playback without frame drops.
 */
object DevicePerf {
    @Volatile
    var isLowRam: Boolean = false
        private set

    @Volatile
    var isLowEnd: Boolean = false
        private set

    @Volatile
    var supportsNativeBlur: Boolean = false
        private set

    val coilParallelism: Int
        get() = if (isLowEnd) 1 else 2

    val miniPlayerPollMs: Long
        get() = if (isLowEnd) 1000L else 650L

    val scrubberPollMs: Long
        get() = if (isLowEnd) 400L else 280L

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRamFlag = am?.isLowRamDevice == true
        val memClass = am?.memoryClass ?: 256
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val apiLevel = Build.VERSION.SDK_INT

        isLowRam = lowRamFlag || memClass <= 192
        // Quad-core A53 devices (<= 4 cores), <= 256 MB heap class, or Android 11 and below
        // lack hardware RenderEffect blur and have tight pixel fill-rate budgets.
        isLowEnd = isLowRam || memClass <= 256 || cpuCores <= 4 || apiLevel < Build.VERSION_CODES.S
        supportsNativeBlur = apiLevel >= Build.VERSION_CODES.S && !isLowRam
    }
}
