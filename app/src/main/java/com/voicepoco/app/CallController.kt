package com.voicepoco.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

class CallController(
    private val ctx: Context,
    private val onRinging: (String) -> Unit,
    private val onIdle: () -> Unit
) {
    private var tm: TelephonyManager? = null
    private var listener: PhoneStateListener? = null

    fun start() {
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            AssistantService.LogI("call listener: no READ_PHONE_STATE")
            return
        }
        tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val l = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        val who = nameFor(ctx, phoneNumber) ?: phoneNumber ?: "Unknown caller"
                        AssistantService.LogI("ringing: " + who)
                        onRinging(who)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> onIdle()
                }
            }
        }
        listener = l
        @Suppress("DEPRECATION")
        runCatching { tm?.listen(l, PhoneStateListener.LISTEN_CALL_STATE) }
    }

    fun stop() {
        val l = listener ?: return
        @Suppress("DEPRECATION")
        runCatching { tm?.listen(l, PhoneStateListener.LISTEN_NONE) }
        listener = null
    }

    companion object {
        fun nameFor(ctx: Context, number: String?): String? {
            if (number.isNullOrBlank()) return null
            if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
            return runCatching {
                ctx.contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
        }

        fun answer(ctx: Context): String {
            if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val ok = runCatching { telecom?.acceptRingingCall(); true }.getOrDefault(false)
                if (ok) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching {
                            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            @Suppress("DEPRECATION")
                            am.isSpeakerphoneOn = true
                        }
                    }, 800)
                    return "Call answered"
                }
            }
            val clicked = AssistantService.instance?.clickTexts("Answer", "Accept", "Pick up", "Receive") == true
            return if (clicked) "Call answered" else "Could not answer. Allow call access for IRA."
        }

        fun decline(ctx: Context): String {
            if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val telecom = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { telecom?.endCall(); true }.getOrDefault(false)
                } else {
                    false
                }
                if (ok) return "Call declined"
            }
            val clicked = AssistantService.instance?.clickTexts("Decline", "Reject", "End call", "Dismiss") == true
            return if (clicked) "Call declined" else "Could not decline. Allow call access for IRA."
        }
    }
}
