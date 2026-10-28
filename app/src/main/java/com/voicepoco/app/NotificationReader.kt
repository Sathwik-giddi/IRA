package com.voicepoco.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationReader : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!LegalDocs.isAccepted(this)) {
            synchronized(recent) { recent.clear() }
            return
        }
        if (AppState.isFrozen(this)) return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        synchronized(recent) {
            recent.addFirst("$label: $title $text".trim())
            while (recent.size > 20) recent.removeLast()
        }
    }

    companion object {
        private val recent = ArrayDeque<String>()
        fun latest(n: Int): List<String> = synchronized(recent) { recent.take(n) }
    }
}
