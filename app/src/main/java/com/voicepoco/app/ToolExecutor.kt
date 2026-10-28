package com.voicepoco.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.app.NotificationManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ToolExecutor {

    fun execute(ctx: Context, r: BrainResult, openApp: (String) -> String?): String? {
        val a = r.args
        return when (r.tool) {
            "back" -> AssistantService.instance?.backCommand()
            "home" -> AssistantService.instance?.homeCommand()
            "scroll" -> AssistantService.instance?.scrollCommand(a["direction"] ?: "down")
            "tap" -> AssistantService.instance?.tapCommand(a["label"] ?: "")
            "type" -> AssistantService.instance?.typeCommand(a["text"] ?: "")
            "open_app" -> openApp(a["app"] ?: "")
            "set_volume" -> volume(ctx, a)
            "set_alarm" -> alarm(ctx, a)
            "call_contact" -> call(ctx, a["contact"] ?: "")
            "send_message" -> MessageSender.send(ctx, a["contact"] ?: "", a["text"] ?: "", (a["app"] ?: "").lowercase(), a["dry"] == "1")
            "set_wifi" -> wifi(ctx, a)
            "set_data" -> data(ctx, a)
            "set_bluetooth" -> bluetooth(ctx, a)
            "set_brightness" -> brightness(ctx, a)
            "set_dnd" -> dnd(ctx, a)
            "post_social" -> openApp(a["app"] ?: "")
            "uninstall_app" -> uninstall(ctx, a["app"] ?: "")
            "set_timer" -> timer(ctx, a["duration"] ?: "")
            "add_event" -> event(ctx, a["title"] ?: "", a["time"] ?: "")
            "set_flashlight" -> torch(ctx, (a["state"] ?: "on").lowercase() == "on")
            "set_hotspot" -> hotspot(ctx)
            "set_airplane" -> airplane(ctx, a)
            "set_dark_mode" -> dark(ctx, a)
            "set_battery_saver" -> saver(ctx, a)
            "set_screen_timeout" -> screenTimeout(ctx, a["seconds"] ?: "")
            "set_ringer" -> ringer(ctx, (a["mode"] ?: "normal").lowercase())
            "open_settings" -> screen(ctx, settingsAction(a["page"] ?: ""))
            "take_screenshot" -> screenshot()
            "media_control" -> media(ctx, (a["action"] ?: "play").lowercase())
            "play_music" -> MediaController.openAndPlay(ctx, a["query"] ?: "")
            "next_reel" -> nextReel()
            "answer_call" -> CallController.answer(ctx)
            "decline_call" -> CallController.decline(ctx)
            "set_mode" -> Modes.apply(ctx, a["name"] ?: "")
            "read_notifications" -> notifications(ctx)
            "battery_status" -> battery(ctx)
            "tell_time" -> time(ctx)
            "tell_date" -> date()
            "web_search" -> search(ctx, a["query"] ?: "")
            "run_routine" -> routine(ctx, a["name"] ?: "", a["time"] ?: "", openApp)
            "remember" -> remember(ctx, a["note"] ?: "")
            "recall" -> recall(ctx)
            "read_screen" -> AssistantService.instance?.readScreen() ?: "Not executed: accessibility is off."
            "delete_files" -> "I do not delete files. Use the Files app so nothing is lost."
            "pay_money" -> "Not executed: payments are not automated. Open your UPI app to pay."
            "share_location" -> "Not executed: share_location has no handler."
            "say" -> (r.speech ?: a["text"] ?: "").trim().ifBlank { "Samajh nahi aaya. Dobara bolo." }
            else -> "Not executed: no handler for ${r.tool}."
        }
    }

    private fun volume(ctx: Context, a: Map<String, String>): String? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = a["level"]?.toIntOrNull()
        if (level != null) {
            val v = (level.coerceIn(0, 100) * max / 100).coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            return null
        }
        val dir = (a["direction"] ?: a["level"] ?: "up").lowercase()
        val up = dir == "up" || dir == "badhao" || dir == "zyada"
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0
        )
        return null
    }

    private fun screen(ctx: Context, action: String): String? {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    // Apps cannot flip Wi-Fi/data/Bluetooth directly on Android 10+; open the system panel for the user.
    private fun panel(ctx: Context, action: String): String? {
        screen(ctx, action)
        return "Opened the settings panel. Toggle it there; apps cannot switch this directly."
    }

    private fun on(a: Map<String, String>): Boolean = (a["state"] ?: "on").lowercase() != "off"

    private fun wifi(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        return if (Privileges.setWifi(state)) {
            "Wi-Fi " + if (state) "on" else "off"
        } else {
            panel(ctx, Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        }
    }

    private fun data(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        return if (Privileges.setData(state)) {
            "Mobile data " + if (state) "on" else "off"
        } else {
            panel(ctx, Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        }
    }

    private fun bluetooth(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        if (Privileges.root("cmd bluetooth_manager " + if (state) "enable" else "disable")) {
            return "Bluetooth " + if (state) "on" else "off"
        }
        screen(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
        return "Opened Bluetooth settings."
    }

    private fun airplane(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        if (Privileges.setAirplane(state)) return "Airplane mode " + if (state) "on" else "off"
        screen(ctx, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        return "Airplane mode needs root on this phone."
    }

    private fun dark(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        if (Privileges.setDark(state)) return "Dark mode " + if (state) "on" else "off"
        screen(ctx, Settings.ACTION_DISPLAY_SETTINGS)
        return "Opened display settings. Dark mode needs root."
    }

    private fun saver(ctx: Context, a: Map<String, String>): String? {
        val state = on(a)
        if (Privileges.setLowPower(state)) return "Battery saver " + if (state) "on" else "off"
        screen(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS)
        return "Opened battery saver settings."
    }

    private fun nextReel(): String? {
        val svc = AssistantService.instance ?: return "Accessibility is off."
        return svc.scrollCommand("down")
    }

    private fun brightness(ctx: Context, a: Map<String, String>): String? {
        if (!Settings.System.canWrite(ctx)) {
            if (brightnessRoot((a["level"] ?: "up").lowercase() != "down")) return null
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Allow 'Modify system settings' for IRA, then ask again."
        }
        val cr = ctx.contentResolver
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val up = (a["level"] ?: "up").lowercase() == "up"
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (cur + if (up) 50 else -50).coerceIn(10, 255))
        return null
    }

    private fun brightnessRoot(up: Boolean): Boolean {
        val cur = Privileges.out("settings get system screen_brightness")?.toIntOrNull() ?: 128
        val next = (cur + if (up) 50 else -50).coerceIn(10, 255)
        return Privileges.root("settings put system screen_brightness_mode 0") &&
            Privileges.root("settings put system screen_brightness $next")
    }

    private fun dnd(ctx: Context, a: Map<String, String>): String? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            screen(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return "Allow Do Not Disturb access for IRA, then ask again."
        }
        val on = (a["state"] ?: "on").lowercase() == "on"
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return null
    }

    private fun uninstall(ctx: Context, name: String): String? {
        val pm = ctx.packageManager
        val q = name.lowercase().trim()
        val pkg = pm.getInstalledApplications(0).firstOrNull {
            q.isNotEmpty() && pm.getApplicationLabel(it).toString().lowercase() == q
        }?.packageName ?: return "Not executed: could not find an app named '$name'."
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return null
    }

    private fun start(ctx: Context, i: Intent): Boolean =
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    private fun timer(ctx: Context, d: String): String? {
        val secs = Regex("\\d+").find(d)?.value?.toIntOrNull() ?: return "Not executed: no timer length heard."
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(ctx, i)) null else "Not executed: no clock app handled the timer."
    }

    private fun event(ctx: Context, title: String, time: String): String? {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
        val m = Regex("(\\d{1,2}):(\\d{2})").find(time)
        if (m != null) {
            val c = java.util.Calendar.getInstance()
            c.set(java.util.Calendar.HOUR_OF_DAY, m.groupValues[1].toInt())
            c.set(java.util.Calendar.MINUTE, m.groupValues[2].toInt())
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, c.timeInMillis)
        }
        return if (start(ctx, i)) null else "Not executed: no calendar app found."
    }

    private fun torch(ctx: Context, on: Boolean): String? {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Not executed: this phone reports no flashlight."
        cm.setTorchMode(id, on)
        return null
    }

    private fun hotspot(ctx: Context): String? {
        val i = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
        return if (start(ctx, i)) null else screen(ctx, Settings.ACTION_WIRELESS_SETTINGS)
    }

    private fun screenTimeout(ctx: Context, secs: String): String? {
        val n = Regex("\\d+").find(secs)?.value?.toIntOrNull() ?: return "Not executed: no time heard."
        if (!Settings.System.canWrite(ctx)) {
            start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            return "Allow 'Modify system settings' for IRA, then ask again."
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, n * 1000)
        return null
    }

    private fun ringer(ctx: Context, mode: String): String? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val m = when (mode) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return try { am.ringerMode = m; null } catch (_: SecurityException) {
            screen(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "Allow Do Not Disturb access for IRA, then ask again."
        }
    }

    private fun settingsAction(page: String): String = when (page.lowercase()) {
        "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "nfc" -> Settings.ACTION_NFC_SETTINGS
        "sound" -> Settings.ACTION_SOUND_SETTINGS
        "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        "apps" -> Settings.ACTION_APPLICATION_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        "language" -> Settings.ACTION_LOCALE_SETTINGS
        "date" -> Settings.ACTION_DATE_SETTINGS
        "display" -> Settings.ACTION_DISPLAY_SETTINGS
        "wifi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
        else -> Settings.ACTION_SETTINGS
    }

    private fun screenshot(): String? {
        val svc = AssistantService.instance ?: return "Not executed: accessibility is off."
        return if (svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) null
        else "Screenshot failed."
    }

    private fun media(ctx: Context, action: String): String? {
        when (action) {
            "pause" -> MediaController.playPause(ctx, false)
            "next" -> MediaController.next(ctx)
            "previous" -> MediaController.previous(ctx)
            else -> MediaController.playPause(ctx, true)
        }
        return null
    }

    private fun notifications(ctx: Context): String? {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?.contains(ctx.packageName) == true
        if (!enabled) {
            screen(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            return "Allow notification access for IRA, then ask again."
        }
        val items = NotificationReader.latest(5)
        return if (items.isEmpty()) "No new notifications since access was allowed." else items.joinToString(". ")
    }

    private fun battery(ctx: Context): String? {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "Battery is at " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " percent."
    }

    private fun time(ctx: Context): String {
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return "It's " + fmt.format(Calendar.getInstance().time) + "."
    }

    private fun date(): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        return "Today is " + fmt.format(Calendar.getInstance().time) + "."
    }

    private fun search(ctx: Context, q: String): String? {
        if (q.isBlank()) return "Not executed: nothing to search for."
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
        if (start(ctx, i)) return null
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))) null
        else "Not executed: no browser found."
    }

    private fun camera(ctx: Context, mode: String): String? {
        val a = if (mode == "video") MediaStore.INTENT_ACTION_VIDEO_CAMERA else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        return if (start(ctx, Intent(a))) null else "Not executed: no camera app found."
    }

    // A routine is a fixed sequence of the same tools the model can call one by one.
    private fun routine(ctx: Context, name: String, time: String, openApp: (String) -> String?): String? {
        val notes = ArrayList<String>()
        fun note(r: String?) { if (!r.isNullOrBlank()) notes.add(r) }
        when (name.lowercase()) {
            "sleep" -> {
                note(dnd(ctx, mapOf("state" to "on")))
                note(brightness(ctx, mapOf("level" to "down")))
                if (time.isNotBlank()) note(alarm(ctx, mapOf("time" to time)))
            }
            "driving" -> {
                note(dnd(ctx, mapOf("state" to "on")))
                note(volume(ctx, mapOf("level" to "up")))
                note(openApp("maps"))
            }
            "meeting" -> {
                note(ringer(ctx, "vibrate"))
                note(openApp("calendar"))
            }
            "outdoors" -> {
                note(brightness(ctx, mapOf("level" to "up")))
                note(brightness(ctx, mapOf("level" to "up")))
            }
            "battery_low" -> {
                note(brightness(ctx, mapOf("level" to "down")))
                note(screen(ctx, Settings.ACTION_BATTERY_SAVER_SETTINGS))
                note(battery(ctx))
            }
            else -> return "Not executed: unknown routine '$name'."
        }
        return notes.joinToString(" ").ifBlank { null }
    }

    private fun remember(ctx: Context, note: String): String? {
        if (note.isBlank()) return "Not executed: nothing to remember."
        val sp = ctx.getSharedPreferences("vp_memory", Context.MODE_PRIVATE)
        val all = (sp.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() } + note.trim()
        sp.edit().putString("notes", all.takeLast(50).joinToString("\n")).apply()
        return null
    }

    private fun recall(ctx: Context): String? {
        val sp = ctx.getSharedPreferences("vp_memory", Context.MODE_PRIVATE)
        val all = (sp.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() }
        return if (all.isEmpty()) "Nothing saved yet." else all.takeLast(5).joinToString(". ")
    }

    private fun alarm(ctx: Context, a: Map<String, String>): String? {
        val m = Regex("(\\d{1,2})[:. ]?(\\d{2})?").find(a["time"] ?: "") ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h !in 0..23 || min !in 0..59) return null
        val i = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
            .putExtra(android.provider.AlarmClock.EXTRA_HOUR, h)
            .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, min)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return null
    }

    private fun call(ctx: Context, name: String): String? {
        val p = MessageSender.resolve(ctx, name)
        if (p.matches > 1) return "There are ${p.matches} contacts named $name. Which one?"
        val num = p.number ?: number(ctx, name)
        if (num.isNullOrBlank()) return "I could not find $name in contacts."
        val direct = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val i = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.data = Uri.parse("tel:" + Uri.encode(num))
        AssistantService.LogI("call " + p.label + " tel=" + num + " direct=" + direct)
        return if (start(ctx, i)) (if (direct) "Calling ${p.label}" else "Dialer open for ${p.label}") else "Could not start the call."
    }

    private fun sms(ctx: Context, name: String, body: String): String? {
        val num = number(ctx, name)
        val i = Intent(Intent.ACTION_SENDTO).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.data = Uri.parse("smsto:" + Uri.encode(num ?: ""))
        if (body.isNotBlank()) i.putExtra("sms_body", body)
        ctx.startActivity(i)
        return null
    }

    private fun number(ctx: Context, name: String): String? {
        if (name.isBlank()) return null
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name))
        ctx.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }
}
