package com.voicepoco.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings

object Modes {

    const val KEY = "vp_mode"
    const val CONVERSATION = "conversation"

    val all = listOf("conversation", "reels", "reading", "hands_on", "driving", "focus", "sleep", "quiet")

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE).getString(KEY, CONVERSATION) ?: CONVERSATION

    fun label(name: String): String = when (name) {
        "reels" -> "Reels"
        "reading" -> "Reading"
        "hands_on" -> "Hands-on"
        "driving" -> "Driving"
        "focus" -> "Focus"
        "sleep" -> "Sleep"
        "quiet" -> "Quiet"
        "conversation" -> "Conversation"
        else -> name
    }

    private fun normalize(raw: String): String {
        val t = raw.lowercase().trim().replace("-", "_").replace(" ", "_")
        return when (t) {
            "reel", "instagram", "feed" -> "reels"
            "read", "reading_mode" -> "reading"
            "hands", "handson", "hands_free", "manual" -> "hands_on"
            "drive", "driving_mode", "car" -> "driving"
            "dnd", "focus_mode", "work" -> "focus"
            "night", "sleep_mode", "goodnight", "good_night" -> "sleep"
            "silent", "mute", "quiet_mode" -> "quiet"
            "normal", "default" -> CONVERSATION
            else -> t
        }
    }

    fun apply(ctx: Context, raw: String): String {
        val name = normalize(raw)
        if (name !in all) return "I do not have a $raw mode."
        val prefs = ctx.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)
        val prev = current(ctx)
        if (prev == name) return label(name) + " mode is already on."
        if (prev == "sleep") restoreBrightness(ctx, prefs)
        prefs.edit().putString(KEY, name).apply()
        when (name) {
            "driving" -> {
                dnd(ctx, true)
                volume(ctx, true)
            }
            "focus" -> dnd(ctx, true)
            "sleep" -> {
                dnd(ctx, true)
                dim(ctx, prefs)
            }
            "quiet" -> Unit
            "reels" -> openReels(ctx)
            else -> Unit
        }
        AssistantService.LogI("mode -> " + name)
        return when (name) {
            "driving" -> "Driving mode. Calls get announced."
            "focus" -> "Focus mode. Notifications are quiet."
            "sleep" -> "Sleep mode. Alarm still rings."
            "quiet" -> "Quiet mode. I will stay on screen only."
            "reels" -> "Reels mode. Say next or ruko."
            "reading" -> "Reading mode. Say padho to hear the screen."
            "hands_on" -> "Hands-on mode. Tap the bubble to talk."
            "conversation" -> "Conversation mode."
            else -> label(name) + " mode."
        }
    }

    fun exit(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)
        val prev = current(ctx)
        if (prev == "sleep") restoreBrightness(ctx, prefs)
        dnd(ctx, false)
        ringer(ctx, AudioManager.RINGER_MODE_NORMAL)
        prefs.edit().putString(KEY, CONVERSATION).apply()
        AssistantService.LogI("mode off from " + prev)
        return "Mode off."
    }

    fun isStop(text: String): Boolean {
        val t = text.lowercase().trim().trim('.', '!', '?')
        return t in setOf("stop", "ruko", "band", "band karo", "chup", "bas", "quiet", "shut up", "mode off")
    }

    fun isNextReel(text: String): Boolean {
        val t = text.lowercase().trim().trim('.', '!', '?')
        return t in setOf("next", "agla", "aage", "next reel", "agli", "scroll", "niche")
    }

    fun isLikeReel(text: String): Boolean {
        val t = text.lowercase().trim()
        return t in setOf("like", "like karo", "pasand", "dil")
    }

    private fun dnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            AssistantService.LogI("dnd not granted")
            return
        }
        runCatching {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }

    private fun volume(ctx: Context, up: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0
            )
        }
    }

    private fun ringer(ctx: Context, mode: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { am.ringerMode = mode }
    }

    private fun dim(ctx: Context, prefs: android.content.SharedPreferences) {
        if (!Settings.System.canWrite(ctx)) return
        val cr = ctx.contentResolver
        runCatching {
            val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 120)
            prefs.edit().putInt("vp_prev_brightness", cur).apply()
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 12)
        }
    }

    private fun restoreBrightness(ctx: Context, prefs: android.content.SharedPreferences) {
        if (!Settings.System.canWrite(ctx)) return
        val prev = prefs.getInt("vp_prev_brightness", -1)
        if (prev < 0) return
        runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, prev) }
    }

    private fun openReels(ctx: Context) {
        val tries = listOf(
            "instagram://reels_home" to "com.instagram.android",
            "https://www.instagram.com/reels/" to "com.instagram.android",
            "https://www.youtube.com/shorts" to "com.google.android.youtube"
        )
        for ((url, pkg) in tries) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.setPackage(pkg)
            if (runCatching { ctx.startActivity(i) }.isSuccess) {
                AssistantService.LogI("reels open " + url)
                return
            }
        }
        ctx.packageManager.getLaunchIntentForPackage("com.instagram.android")?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(it) }
        }
    }
}
