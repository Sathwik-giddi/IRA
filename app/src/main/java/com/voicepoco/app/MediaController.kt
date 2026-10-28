package com.voicepoco.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

object MediaController {

    fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    fun playPause(ctx: Context, play: Boolean) {
        key(ctx, if (play) KeyEvent.KEYCODE_MEDIA_PLAY else KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun next(ctx: Context) = key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(ctx: Context) = key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun openAndPlay(ctx: Context, query: String): String {
        val pm = ctx.packageManager
        val q = query.lowercase().trim()
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val match = apps.firstOrNull {
            val label = (it.loadLabel(pm) ?: "").toString().lowercase()
            q.isNotEmpty() && (label == q || label.contains(q) || q.contains(label))
        }
        if (match != null) {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(match.activityInfo.packageName, match.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Thread.sleep(700)
            playPause(ctx, true)
            return "Playing in " + match.loadLabel(pm)
        }
        val music = Intent(Intent.ACTION_VIEW).setType("audio/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(music) }.isSuccess) {
            Thread.sleep(700)
            playPause(ctx, true)
            return "Playing"
        }
        return "No music app found to play " + query
    }
}
