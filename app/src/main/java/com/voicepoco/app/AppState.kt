package com.voicepoco.app

import android.content.Context

object AppState {
    const val PREFS = "vp_prefs"
    const val KEY_FROZEN = "frozen"

    fun isFrozen(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FROZEN, false)

    fun setFrozen(ctx: Context, frozen: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FROZEN, frozen).apply()
    }
}
