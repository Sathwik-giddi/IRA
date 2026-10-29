package com.voicepoco.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProfile {

    fun ramMb(context: Context): Int = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L)).toInt()
    }.getOrDefault(0)

    fun is64Bit(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    fun recommendedModel(context: Context): ModelCatalog.Model =
        ModelPolicy.recommended(ramMb(context), is64Bit())

    fun threads(): Int = ModelPolicy.threads(Runtime.getRuntime().availableProcessors())
}
