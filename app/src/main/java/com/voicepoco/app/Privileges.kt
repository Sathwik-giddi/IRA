package com.voicepoco.app

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit

object Privileges {

    @Volatile private var checked = false
    @Volatile private var ok = false

    fun hasRoot(): Boolean {
        if (checked) return ok
        checked = true
        ok = runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(3, TimeUnit.SECONDS) && out.contains("uid=0")
        }.getOrDefault(false)
        AssistantService.LogI("root=" + ok)
        return ok
    }

    fun root(cmd: String): Boolean {
        if (!hasRoot()) return false
        return runCatching {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val done = p.waitFor(4, TimeUnit.SECONDS)
            val code = if (done) p.exitValue() else -1
            if (!done) runCatching { p.destroyForcibly() }
            AssistantService.LogI("root cmd '" + cmd + "' exit=" + code)
            done && code == 0
        }.getOrDefault(false)
    }

    fun out(cmd: String): String? {
        if (!hasRoot()) return null
        return runCatching {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            if (p.waitFor(4, TimeUnit.SECONDS) && p.exitValue() == 0) text.trim() else null
        }.getOrNull()
    }

    fun isDeviceOwner(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
            .isDeviceOwnerApp(ctx.packageName)
    }.getOrDefault(false)

    fun shizukuInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
    }.getOrDefault(false)

    fun setWifi(on: Boolean): Boolean = root("svc wifi " + if (on) "enable" else "disable")

    fun setData(on: Boolean): Boolean = root("svc data " + if (on) "enable" else "disable")

    fun setAirplane(on: Boolean): Boolean =
        root("settings put global airplane_mode_on " + (if (on) 1 else 0) +
            " && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + on)

    fun setDark(on: Boolean): Boolean = root("cmd uimode night " + if (on) "yes" else "no")

    fun setLowPower(on: Boolean): Boolean =
        root("settings put global low_power " + (if (on) 1 else 0))

    fun keyevent(code: Int): Boolean = root("input keyevent " + code)

    fun tap(x: Int, y: Int): Boolean = root("input tap $x $y")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int): Boolean =
        root("input swipe $x1 $y1 $x2 $y2 $ms")

    fun enableAccessibility(ctx: Context): Boolean {
        val service = ctx.packageName + "/" + AssistantService::class.java.name
        val current = out("settings get secure enabled_accessibility_services")
        val next = if (current.isNullOrBlank()) service else current + ":" + service
        return root("settings put secure enabled_accessibility_services " + next) &&
            root("settings put secure accessibility_enabled 1")
    }

    fun grantMicrophone(): Boolean = root("pm grant com.voicepoco.app android.permission.RECORD_AUDIO")

    fun screenOff(): Boolean = root("input keyevent 26")

    fun summary(ctx: Context): String = when {
        hasRoot() -> "Root detected"
        isDeviceOwner(ctx) -> "Device owner"
        shizukuInstalled(ctx) -> "Shizuku installed"
        Build.VERSION.SDK_INT >= 26 -> "Stock access"
        else -> "Limited"
    }
}
