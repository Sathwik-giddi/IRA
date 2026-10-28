package com.voicepoco.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Bundle as OsBundle
import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicLong

class NodeLite(
    val label: String,
    val cls: String,
    val clickable: Boolean,
    val editable: Boolean,
    val cx: Int,
    val cy: Int
)

class AssistantService : AccessibilityService() {

    companion object {
        const val TAG = "POCO_TREE"
        fun LogI(msg: String) = Log.i(TAG, "assist: " + msg)
        fun LogD(msg: String) = Log.d(TAG, "assist: " + msg)
        const val DEBOUNCE_MS = 250L
        @Volatile var instance: AssistantService? = null
            private set
        @Volatile var nodes: List<NodeLite> = emptyList()
        @Volatile var lastPkg: String = ""
        private val lastChange = AtomicLong(0)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (AppState.isFrozen(this)) nodes = emptyList()
        Log.i(TAG, "connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (frozen()) {
            nodes = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastChange.get() < DEBOUNCE_MS) return
        lastChange.set(now)
        capture()
    }

    override fun onInterrupt() {
    }

    private fun frozen(): Boolean = AppState.isFrozen(this)

    private fun capture() {
        if (frozen()) {
            nodes = emptyList()
            return
        }
        val root = rootInActiveWindow ?: return
        lastPkg = root.packageName?.toString() ?: ""
        nodes = listOf()
        val out = ArrayList<NodeLite>(60)
        collect(root, out, intArrayOf(0))
        nodes = out
    }

    private fun collect(node: AccessibilityNodeInfo, out: ArrayList<NodeLite>, i: IntArray) {
        if (i[0] >= 150) return
        i[0]++
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val label = if (text.isNotEmpty()) text else desc
        val b = Rect()
        node.getBoundsInScreen(b)
        if (label.isNotEmpty() && b.width() > 24 && b.height() > 16 &&
            (node.isClickable || node.isEditable)
        ) {
            out.add(
                NodeLite(
                    label.take(60).replace("\n", " "),
                    node.className?.toString()?.substringAfterLast('.') ?: "",
                    node.isClickable, node.isEditable,
                    b.centerX(), b.centerY()
                )
            )
        }
        for (k in 0 until node.childCount) {
            val child = node.getChild(k) ?: continue
            collect(child, out, i)
            if (i[0] >= 150) return
        }
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x, y + 0.1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 90)
        val gd = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gd, null, null)
    }

    fun tapCommand(phrase: String): String {
        if (frozen()) return "IRA is frozen"
        val q = phrase.lowercase().trim()
        val best = nodes
            .filter { n -> n.clickable }
            .map { n -> n to n.label.lowercase() }
            .filter { (_, l) -> l.contains(q) || q.contains(l) || l.split(' ').any { q.contains(it) && it.length > 3 } }
            .maxByOrNull { (n, _) -> n.label.length }
        return if (best != null) {
            tapAt(best.first.cx.toFloat(), best.first.cy.toFloat())
            "Tapping ${best.first.label.take(30)}"
        } else {
            "No tappable element matching '$q'"
        }
    }

    fun scrollCommand(direction: String): String {
        if (frozen()) return "IRA is frozen"
        val dm = resources.displayMetrics
        val midX = dm.widthPixels / 2f
        val down = direction.lowercase().contains("up")
        val startY: Float
        val endY: Float
        if (down) {
            startY = dm.heightPixels * 0.75f
            endY = dm.heightPixels * 0.25f
        } else {
            startY = dm.heightPixels * 0.25f
            endY = dm.heightPixels * 0.75f
        }
        val path = Path().apply { moveTo(midX, startY); lineTo(midX, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 220)
        val gd = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gd, null, null)
        return "Scrolling $direction"
    }

    fun typeCommand(phrase: String): String {
        if (frozen()) return "IRA is frozen"
        val root = rootInActiveWindow ?: return "No screen to type into"
        var target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null || !target.isEditable) {
            target = nodes.firstOrNull { n -> n.editable }?.let { f ->
                findEditableAt(root, f.cx, f.cy)
            }
        }
        if (target == null || !target.isEditable) return "No input field found"
        val current = target.text?.toString() ?: ""
        val args = Bundle().apply {
            putCharSequence(
                "ACTION_ARGUMENT_SET_TEXT_CHARSEQUANCE",
                if (current.isNotEmpty()) "$current $phrase" else phrase
            )
        }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Typed"
        } else {
            "Failed to set text"
        }
    }

    private fun findEditableAt(root: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 200) {
            val n = stack.removeFirst()
            val b = Rect()
            n.getBoundsInScreen(b)
            if (n.isEditable && b.left <= x && x <= b.right && b.top <= y && y <= b.bottom) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return null
    }

    fun readScreen(): String {
        if (frozen()) return "IRA is frozen."
        val root = rootInActiveWindow ?: return "Nothing readable on screen."
        val parts = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (parts.size >= 40 || depth > 25) return
            val t = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
            if (t.isNotEmpty() && (parts.isEmpty() || parts.last() != t)) parts.add(t.take(120))
            for (k in 0 until n.childCount) n.getChild(k)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return if (parts.isEmpty()) "Nothing readable on screen." else parts.joinToString(". ").take(700)
    }

    fun clickTexts(vararg labels: String): Boolean {
        if (frozen()) return false
        val wanted = labels.map { it.lowercase() }
        for (n in nodes) {
            if (!n.clickable) continue
            val l = n.label.lowercase()
            if (wanted.any { l == it || l.contains(it) }) {
                if (tapAt(n.cx.toFloat(), n.cy.toFloat())) return true
            }
        }
        val root = rootInActiveWindow ?: return false
        for (w in labels) {
            val hit = runCatching { root.findAccessibilityNodeInfosByText(w) }.getOrNull()?.firstOrNull() ?: continue
            var x: AccessibilityNodeInfo? = hit
            var d = 0
            while (x != null && d < 6) {
                if (x.isClickable && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                x = x.parent; d++
            }
        }
        return false
    }

    fun backCommand(): String {
        if (frozen()) return "IRA is frozen"
        return if (performGlobalAction(GLOBAL_ACTION_BACK)) "Back" else "Back failed"
    }

    fun homeCommand(): String {
        if (frozen()) return "IRA is frozen"
        return if (performGlobalAction(GLOBAL_ACTION_HOME)) "Home" else "Home failed"
    }

    fun whatsappSend(contact: String, text: String, dry: Boolean): String {
        if (frozen()) return "IRA is frozen"
        val latch = CountDownLatch(1)
        val out = AtomicReference("WhatsApp timeout")
        val main = Handler(mainLooper)
        var step = 0
        var tries = 0
        val started = SystemClock.uptimeMillis()
        lateinit var tick: Runnable
        fun done(msg: String) { if (latch.count > 0) { out.set(msg); latch.countDown() } }
        fun next() { step++; tries = 0 }
        fun root() = rootInActiveWindow
        fun byId(id: String) = root()?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
        fun clicked(n: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            var x = n
            var d = 0
            while (x != null && d < 6) {
                if (x.isClickable && x.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                x = x.parent; d++
            }
            return false
        }
        fun setText(id: String, value: String): Boolean {
            val n = byId(id) ?: return false
            n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
            val b = OsBundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            return n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, b)
        }
        tick = object : Runnable {
            override fun run() {
                if (frozen()) { done("IRA is frozen"); return }
                if (SystemClock.uptimeMillis() - started > 16000) { done("WhatsApp timed out at step " + step); return }
                val ok: Boolean = when (step) {
                    0 -> {
                        val i = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        if (i == null) { done("WhatsApp is not installed"); return }
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); true
                    }
                    1 -> setText("com.whatsapp:id/search_input", contact) || clicked(byId("com.whatsapp:id/search_input"))
                    2 -> clicked(byId("com.whatsapp:id/contact_row_container"))
                        || clicked(root()?.findAccessibilityNodeInfosByText(contact)?.firstOrNull())
                    3 -> setText("com.whatsapp:id/entry", text)
                    4 -> if (dry) { done("WhatsApp draft ready for " + contact + " (dry run)"); return } else
                        clicked(byId("com.whatsapp:id/send")) || clicked(byId("com.whatsapp:id/send_btn"))
                    else -> { done("Message sent on WhatsApp to " + contact); return }
                }
                if (ok) { next(); main.postDelayed(this, 700) }
                else {
                    tries++
                    if (tries > 14) { done("WhatsApp stuck at step " + step); return }
                    main.postDelayed(this, 450)
                }
            }
        }
        main.post(tick)
        return try { latch.await(18, java.util.concurrent.TimeUnit.SECONDS); out.get() }
        catch (e: Exception) { "WhatsApp failed: " + e.message }
    }
}
