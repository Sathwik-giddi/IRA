package com.voicepoco.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "vp_bubble"
        const val PREFS = "vp_prefs"
        const val KEY_ACT = "act_mode"
        const val PREF_ACT_DEFAULT = true
        const val KEY_AUTO_ANSWER = "auto_answer"
        const val KEY_BIG_MODEL = "model_big"
        const val KEY_CLOUD = "cloud_brain"
        const val KEY_GEMINI = "gemini_key"
        private const val FOLLOW_UP_TTL_MS = 45_000L
        val WAKE_WORDS = VoiceEngine.WAKE
    }

    private lateinit var main: Handler
    private lateinit var wm: WindowManager

    private var engine: VoiceEngine? = null
    private var calls: CallController? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val ttsDone = ConcurrentHashMap<String, () -> Unit>()
    @Volatile private var ttsId: String? = null
    @Volatile private var ttsEndedAt = 0L
    @Volatile private var barge: BargeInDetector? = null

    private val brain by lazy { ModelBrain(this).also { it.conversational = true } }
    private val cloud by lazy { CloudBrain(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    private var pending: BrainResult? = null
    private var lastHeard = ""
    private var lastAnswer = ""
    private var lastAnswerAt = 0L
    @Volatile private var lastTool = ""
    private var turns = 0
    private var ringing = false
    private var caller = ""
    private var ringTries = 0
    private var stt: CommandStt? = null

    private var bubbleView: VoiceOrbView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cardView: LinearLayout? = null
    private var cardText: TextView? = null
    private var downX = 0f; private var downY = 0f; private var moved = false

    private val wakeOn = AtomicBoolean(false)
    private var tone: android.media.ToneGenerator? = null
    private val idleHide = Runnable { dismissSession() }

    override fun onCreate() {
        super.onCreate()
        if (AppState.isFrozen(this)) {
            AssistantService.LogI("frozen -> stopSelf")
            stopSelf()
            return
        }
        AssistantService.LogI("onCreate")
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        main = Handler(Looper.getMainLooper())

        tts = TextToSpeech(this) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        if (id != null) startBarge(id)
                    }
                    override fun onDone(id: String?) {
                        if (id != null && id == ttsId) {
                            ttsId = null
                            ttsEndedAt = SystemClock.elapsedRealtime()
                            stopBarge()
                        }
                        finishTts(id)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { endTts(id) }
                    override fun onError(id: String?, errorCode: Int) { endTts(id) }
                    override fun onStop(id: String?, interrupted: Boolean) {
                        if (id != null && id == ttsId) {
                            ttsId = null
                            stopBarge()
                        }
                    }
                })
            }
        }

        engine = VoiceEngine(
            this,
            onState = { s -> runOnMain { onEngineState(s) } },
            onPartial = { t -> if (t.isNotBlank()) runOnMain { cardText?.text = "“$t”" } },
            onFinal = { t -> runOnMain { onUtteranceDone(t) } },
            onStopWord = { w -> runOnMain { onStopWord(w) } },
            onError = { e -> runOnMain { showNote(e) } },
            onWake = {
                runOnMain {
                    if (ttsId == null && tts?.isSpeaking != true &&
                        SystemClock.elapsedRealtime() - ttsEndedAt > 700L
                    ) startListening(12000)
                }
            }
        )

        stt = CommandStt(
            this,
            onState = { s -> runOnMain { onEngineState(s) } },
            onPartial = { t -> if (t.isNotBlank()) runOnMain { cardText?.text = "“$t”" } },
            onFinal = { t -> runOnMain { onSttFinal(t) } },
            onError = { e -> runOnMain { if (e.isNotBlank()) showNote(e) } }
        )

        calls = CallController(this, onRinging = { who -> runOnMain { onRinging(who) } }, onIdle = { runOnMain { ringing = false } })
        calls?.start()

        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val flags = if (Build.VERSION.SDK_INT >= 33) android.content.Context.RECEIVER_EXPORTED else 0
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val t = i?.getStringExtra("text") ?: return
                    AssistantService.LogI("debug text: " + t)
                    onUtteranceDone(t)
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_TEXT"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val s = i?.getStringExtra("state") ?: "listening"
                    AssistantService.LogI("debug orb: " + s)
                    runOnMain { showBubble(s) }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_ORB"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    AssistantService.LogI("debug barge")
                    runOnMain { onBargeIn(ttsId) }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_BARGE"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val t = i?.getStringExtra("text") ?: return
                    AssistantService.LogI("debug speak chars=" + t.length)
                    runOnMain { speakThen(t) {} }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_SPEAK"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val tool = i?.getStringExtra("tool") ?: return
                    val args = HashMap<String, String>()
                    for (k in listOf("contact", "text", "app", "dry", "name", "state", "level", "action", "query")) {
                        i.getStringExtra(k)?.let { args[k] = it }
                    }
                    AssistantService.LogI("debug tool: " + tool + " " + args)
                    worker.execute {
                        val out = executeTool(BrainResult(tool, args))
                        AssistantService.LogI("debug tool out: " + out)
                        runOnMain { finishCommand(out) }
                    }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_TOOL"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    AssistantService.LogI("debug mic")
                    startListening(6000)
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_MIC"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val name = i?.getStringExtra("name") ?: return
                    worker.execute {
                        val p = MessageSender.resolve(this@BubbleService, name)
                        AssistantService.LogI("lookup '" + name + "' -> label=" + p.label + " num=" + p.number + " matches=" + p.matches)
                    }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_LOOKUP"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    worker.execute {
                        val out = StringBuilder()
                        runCatching {
                            contentResolver.query(
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                                null, null, null
                            )?.use { cur ->
                                val seen = HashSet<String>()
                                while (cur.moveToNext() && seen.size < 40) {
                                    val n = cur.getString(0) ?: continue
                                    if (seen.add(n)) out.append(n).append(" | ")
                                }
                            }
                        }
                        AssistantService.LogI("contacts: " + out)
                    }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_CONTACTS"), flags)
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: Intent?) {
                    val name = i?.getStringExtra("name") ?: return
                    worker.execute {
                        val out = StringBuilder()
                        runCatching {
                            contentResolver.query(
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(
                                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                                ),
                                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                                arrayOf("%" + name + "%"), null
                            )?.use { cur ->
                                while (cur.moveToNext()) out.append(cur.getString(0)).append("=").append(cur.getString(1)).append(" | ")
                            }
                        }
                        AssistantService.LogI("find '" + name + "': " + out)
                    }
                }
            }, android.content.IntentFilter("com.voicepoco.app.DEBUG_FIND"), flags)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AppState.isFrozen(this)) {
            AssistantService.LogI("frozen -> stopSelf")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AssistantService.LogI("mic permission missing -> stopSelf")
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        intent?.getStringExtra("tool")?.let { t ->
            val args = HashMap<String, String>()
            for (k in listOf("contact", "text", "app", "dry", "name", "state", "level", "action", "query")) {
                intent.getStringExtra(k)?.let { args[k] = it }
            }
            worker.execute {
                val out = executeTool(BrainResult(t, args))
                AssistantService.LogI("tool out: " + out)
                runOnMain {
                    if (out.isNotBlank()) showNote(out) else dismissSession()
                }
            }
        }
        intent?.getLongExtra("listen", 0L)?.takeIf { it > 0 }?.let { ms ->
            main.postDelayed({ startListening(ms) }, 400)
        }
        intent?.getStringExtra("debug_text")?.let { t ->
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                AssistantService.LogI("debug service text: " + t)
                main.postDelayed({ onUtteranceDone(t) }, 500)
            }
        }
        if (wakeOn.compareAndSet(false, true)) {
            AssistantService.LogI("engine starting")
            engine?.start()
            if (Modes.current(this) == "hands_on") {
                engine?.pause()
                showBubble("idle")
            }
        }
        updateNotification()
        return START_STICKY
    }

    private fun startListening(ms: Long) {
        AssistantService.LogI("listen start ms=" + ms + " tts=" + ttsId)
        stopSpeaking()
        engine?.pause()
        main.postDelayed({
            engine?.pause()
            val c = stt
            if (c == null) {
                engine?.resumeWake()
            } else {
                if (Modes.current(this) != "quiet") {
                    if (tone == null) tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 45)
                    runCatching { tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120) }
                }
                c.listen(ms)
            }
        }, 220)
    }

    private fun onSttFinal(t: String) {
        if (Modes.current(this) != "hands_on") engine?.resumeWake()
        onUtteranceDone(t)
    }

    private fun onEngineState(s: String) {
        AssistantService.LogD("engine state " + s)
        when (s) {
            "listening" -> {
                main.removeCallbacks(idleHide)
                showBubble("listening")
                if (cardText == null) showCard("Listening…")
            }
            "idle" -> showBubble("idle")
            else -> Unit
        }
    }

    private fun onUtteranceDone(text: String) {
        if (busy.get()) { AssistantService.LogI("busy, drop: " + text); return }
        if (text.isBlank()) {
            if (ringing) { ringTries++; if (ringTries > 1) endRing() else askRing() }
            else dismissSession()
            return
        }
        if (Modes.isStop(text)) {
            val msg = Modes.exit(this)
            updateNotification()
            engine?.resumeWake()
            speakThen(msg) { dismissSession() }
            return
        }
        if (ringing) { ringAnswer(text); return }
        if (Modes.current(this) == "reels" && Modes.isNextReel(text)) {
            val out = AssistantService.instance?.scrollCommand("down") ?: "Accessibility is off"
            lastHeard = text
            showCard("Next. " + out)
            startListening(15000)
            return
        }
        handle(text)
    }

    private fun handle(text: String) {
        cloud.recordUser(text)
        if (FollowUp.isRepeat(text) && repeatLast(text)) return
        if (!busy.compareAndSet(false, true)) return
        lastAnswer = ""
        lastAnswerAt = 0L
        lastTool = ""
        engine?.pause()
        lastHeard = text
        showCard("“$text”\n…")
        showBubble("thinking")

        val waiting = pending
        if (waiting != null) {
            pending = null
            if (ConfirmPolicy.isYes(text)) {
                worker.execute {
                    val out = executeTool(waiting)
                    runOnMain { finishCommand(out) }
                }
                return
            }
            if (ConfirmPolicy.isNo(text)) {
                busy.set(false)
                finishCommand("Okay, I did not do it.")
                return
            }
        }

        val parts = CommandSplitter.smart(text)
        worker.execute {
            val out = if (parts.size > 1) {
                val notes = ArrayList<String>()
                for (p in parts) {
                    val r = decide(p) ?: continue
                    if (ConfirmPolicy.verdict(r) != ConfirmPolicy.Verdict.ALLOW) continue
                    val line = executeTool(r)
                    if (line.isNotBlank()) notes.add(line)
                    if (r.tool in setOf("set_mode", "set_wifi", "set_data", "set_airplane")) main.post { updateNotification() }
                }
                notes.joinToString(" ").ifBlank { "" }
            } else {
                val r = decide(text)
                if (r == null) "" else when (ConfirmPolicy.verdict(r)) {
                    ConfirmPolicy.Verdict.REFUSE -> r.speech ?: (r.args["question"] ?: r.args["reason"] ?: "")
                    ConfirmPolicy.Verdict.CONFIRM -> {
                        pending = r
                        ConfirmPolicy.question(r)
                    }
                    ConfirmPolicy.Verdict.ALLOW -> {
                        if (r.tool in setOf("set_mode", "set_wifi", "set_data", "set_airplane")) main.post { updateNotification() }
                        executeTool(r)
                    }
                }
            }
            runOnMain { finishCommand(out) }
        }
    }

    private fun decide(text: String): BrainResult? {
        val fast = FastLane.match(text)
        if (fast != null) {
            AssistantService.LogI("fast lane: " + text + " -> " + fast.tool + " " + fast.args)
            brain.remember(text, fast)
            return fast
        }
        if (SafetyGuard.refuse(text)) {
            AssistantService.LogI("cloud safety refuse: " + text)
            return BrainResult("refuse", mapOf("reason" to "unsafe request"), "I can't do that.")
        }
        if (cloud.enabled()) {
            val cloudResult = runCatching { cloud.decide(text) }.getOrElse {
                AssistantService.LogI("cloud error: " + it)
                null
            }
            if (cloudResult != null) {
                AssistantService.LogI("cloud out: '" + text + "' -> " + cloudResult.tool + " " + cloudResult.args)
                return cloudResult
            }
            AssistantService.LogI("cloud miss, local fallback: " + text)
        }
        val r = runCatching { brain.decide(text) }.getOrElse {
            AssistantService.LogI("brain error: " + it)
            return BrainResult("unknown")
        }
        AssistantService.LogI("brain out: '" + text + "' -> " + r.tool + " " + r.args)
        return r
    }

    private fun repeatLast(text: String): Boolean {
        val answer = lastAnswer
        if (answer.isBlank()) return false
        if (SystemClock.elapsedRealtime() - lastAnswerAt > FOLLOW_UP_TTL_MS) return false
        AssistantService.LogI("follow-up repeat: " + text + " -> " + answer)
        engine?.pause()
        lastHeard = text
        finishCommand(answer)
        return true
    }

    private fun finishCommand(out: String) {
        busy.set(false)
        if (out.isNotBlank()) {
            lastAnswer = out
            lastAnswerAt = SystemClock.elapsedRealtime()
            cloud.recordAssistant(out)
        }
        if (lastHeard.isBlank()) { dismissSoon(); return }
        showCard(if (out.isBlank()) "“$lastHeard”" else "“$lastHeard”\n$out")
        if (ConfirmPolicy.isDone(lastHeard)) { dismissSoon(); return }
        turns++
        if (turns >= 8) { dismissSoon(); return }
        if (Modes.current(this) == "hands_on") { dismissSoon(); return }
        val window = if (Modes.current(this) == "reels") 15000L else 6000L
        speakThen(out) {
            if (busy.get()) return@speakThen
            startListening(window)
        }
    }

    private fun dismissSoon() {
        main.removeCallbacks(idleHide)
        main.postDelayed(idleHide, 900)
    }

    private fun dismissSession() {
        main.removeCallbacks(idleHide)
        stopSpeaking()
        busy.set(false)
        pending = null
        turns = 0
        lastHeard = ""
        ringing = false
        brain.resetContext()
        cloud.reset()
        hideBubble()
        hideCard()
        if (Modes.current(this) != "hands_on") engine?.resumeWake()
    }

    private fun onRinging(who: String) {
        ringing = true
        caller = who
        ringTries = 0
        engine?.pause()
        val driving = Modes.current(this) == "driving"
        val auto = driving && getApplicationPrefs().getBoolean(KEY_AUTO_ANSWER, false)
        val line = "Call from $who."
        showBubble("idle")
        if (auto) {
            showCard(line + " Answering.")
            speakThen(line) {
                val out = CallController.answer(this)
                AssistantService.LogI("auto " + out)
                showCard(out)
                ringing = false
                engine?.resumeWake()
            }
        } else {
            showCard(line + " Say answer or decline.")
            speakThen(line + " Say answer or decline.") { startListening(6000) }
        }
    }

    private fun askRing() {
        speakThen("Say answer or decline.") { engine?.listenCommand(6000) }
    }

    private fun ringAnswer(text: String) {
        val t = text.lowercase()
        val yes = listOf("answer", "uthao", "pick up", "attend", "receive", "haan").any { t.contains(it) }
        val no = listOf("decline", "reject", "kaat", "cut", "nahi", "end").any { t.contains(it) }
        val out = when {
            yes -> CallController.answer(this)
            no -> CallController.decline(this)
            else -> "Say answer or decline."
        }
        showCard(out)
        if (yes || no) { ringing = false }
        else { ringTries++; if (ringTries > 1) endRing() else askRing() }
    }

    private fun endRing() {
        ringing = false
        dismissSession()
    }

    private fun onStopWord(word: String) {
        val mode = Modes.current(this)
        if (mode == Modes.CONVERSATION) return
        val msg = Modes.exit(this)
        updateNotification()
        showCard(msg)
        speakThen(msg) { dismissSession() }
    }

    private fun executeTool(r: BrainResult): String {
        lastTool = r.tool
        if (r.tool == "clarify") return r.args["question"] ?: ""
        if (r.tool in setOf("refuse", "unsupported", "unknown")) return r.speech ?: r.args["reason"] ?: ""
        if (r.tool == "say") return r.speech ?: r.args["text"] ?: "Samajh nahi aaya. Dobara bolo."
        if (!getApplicationPrefs().getBoolean(KEY_ACT, PREF_ACT_DEFAULT)) {
            AssistantService.LogI("act off, skip " + r.tool)
            return ""
        }
        val needsA11y = r.tool in setOf("tap", "scroll", "type", "back", "home", "take_screenshot", "read_screen", "next_reel")
        if (needsA11y && AssistantService.instance == null) {
            return "Accessibility is off. Enable it in IRA."
        }
        val out = runCatching { ToolExecutor.execute(this, r) { q -> openByLabel(q) } }.getOrElse {
            AssistantService.LogI("exec error: " + it)
            ""
        } ?: ""
        return if (out.isNotBlank()) out else (r.speech ?: "")
    }

    private fun openByLabel(q: String): String? {
        if (q.isEmpty()) return "Which app?"
        val pm = packageManager
        val launchables = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val match = launchables.firstOrNull {
            (it.loadLabel(pm)?.toString() ?: "").lowercase().contains(q.lowercase())
        }
        return if (match == null) {
            "Cannot find $q"
        } else {
            try {
                applicationContext.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(match.activityInfo.packageName, match.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                null
            } catch (e: Exception) {
                "Couldn't open $q"
            }
        }
    }

    private fun getApplicationPrefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun startBarge(id: String) {
        if (id != ttsId) return
        stopBarge()
        val d = BargeInDetector(this) { runOnMain { onBargeIn(id) } }
        barge = d
        d.start()
    }

    private fun stopBarge() {
        val b = barge
        barge = null
        b?.stop()
    }

    private fun onBargeIn(id: String?) {
        if (id == null || id != ttsId) return
        AssistantService.LogI("barge: interrupt")
        stopSpeaking()
        showCard("Listening…")
        startListening(8000)
    }

    private fun stopSpeaking() {
        stopBarge()
        val id = ttsId
        ttsId = null
        if (id != null) ttsDone.remove(id)
        runCatching { if (tts?.isSpeaking == true) tts?.stop() }
    }

    private fun speakThen(text: String, done: () -> Unit) {
        if (text.isBlank() || Modes.current(this) == "quiet" || !ttsReady) {
            main.postDelayed(done, 250)
            return
        }
        stopSpeaking()
        engine?.pause()
        val id = "vp" + SystemClock.uptimeMillis()
        ttsId = id
        ttsDone[id] = done
        showBubble("speaking")
        AssistantService.LogI("tts speak chars=" + text.length + " text=" + text.take(120))
        val started = runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }.isSuccess
        if (!started) {
            if (ttsId == id) ttsId = null
            ttsDone.remove(id)?.invoke()
            return
        }
        main.postDelayed({ finishTts(id) }, 12000)
    }

    private fun endTts(id: String?) {
        if (id != null && id == ttsId) {
            ttsId = null
            stopBarge()
        }
        finishTts(id)
    }

    private fun finishTts(id: String?) {
        val cb = id?.let { ttsDone.remove(it) } ?: return
        if (id == ttsId) {
            ttsId = null
            stopBarge()
            runCatching { tts?.stop() }
        }
        main.post { cb() }
    }

    private fun showBubble(mode: String) {
        if (!canOverlay()) return
        val state = when (mode) {
            "listening" -> VoiceOrbView.State.LISTENING
            "thinking" -> VoiceOrbView.State.THINKING
            "speaking" -> VoiceOrbView.State.SPEAKING
            else -> VoiceOrbView.State.IDLE
        }
        val b = bubbleView
        if (b != null) {
            b.setState(state)
            return
        }
        val v = VoiceOrbView(this).apply {
            setState(state)
            contentDescription = "IRA"
        }
        val size = dp(76)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(18); params.y = dp(90)
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.rawX; downY = ev.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(ev.rawX - downX) > dp(10) || Math.abs(ev.rawY - downY) > dp(10)) {
                        moved = true
                        params.x += (ev.rawX - downX).toInt()
                        params.y += (ev.rawY - downY).toInt()
                        downX = ev.rawX; downY = ev.rawY
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !busy.get()) {
                        if (ringing) askRing() else startListening(8000)
                    }
                    true
                }
                else -> false
            }
        }
        bubbleView = v
        bubbleParams = params
        runCatching { wm.addView(v, params) }
    }

    private fun hideBubble() {
        val v = bubbleView ?: return
        bubbleView = null
        bubbleParams = null
        runCatching { wm.removeView(v) }
    }

    private fun showCard(text: String) {
        if (!canOverlay()) return
        val existing = cardText
        if (existing != null && cardView != null) { existing.text = text; return }
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Ui.BONE)
            setPadding(dp(16), dp(14), dp(6), dp(14))
            maxLines = 6
        }
        val close = TextView(this).apply {
            this.text = "✕"
            textSize = 16f
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { dismissSession() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Ui.BORDER)
            }
            elevation = dp(8).toFloat()
            addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close)
        }
        val lp = WindowManager.LayoutParams(
            resources.displayMetrics.widthPixels - dp(28),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(46)
        var startY = 0f
        row.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = ev.rawY; main.removeCallbacks(idleHide); true }
                MotionEvent.ACTION_OUTSIDE -> { dismissSession(); true }
                MotionEvent.ACTION_UP -> {
                    if (ev.rawY - startY > dp(36)) dismissSession()
                    else if (!busy.get() && !ringing) main.postDelayed(idleHide, 8000)
                    true
                }
                else -> true
            }
        }
        cardView = row
        cardText = tv
        runCatching { wm.addView(row, lp) }
    }

    private fun showNote(text: String) {
        if (text.isBlank()) return
        showCard(text)
    }

    private fun hideCard() {
        val v = cardView
        cardView = null
        cardText = null
        if (v != null) runCatching { wm.removeView(v) }
    }

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private fun updateNotification() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "IRA", NotificationManager.IMPORTANCE_LOW))
        }
        val mode = Modes.label(Modes.current(this))
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IRA · $mode")
            .setContentText("Say ira, or tap the bubble")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            }.onFailure { startForeground(1, notif) }
        } else {
            startForeground(1, notif)
        }
    }

    private fun startAsForeground() {
        updateNotification()
    }

    override fun onDestroy() {
        wakeOn.set(false)
        stopSpeaking()
        engine?.stop()
        calls?.stop()
        for (cb in ttsDone.values) runCatching { cb() }
        ttsDone.clear()
        tts?.shutdown(); tts = null
        hideBubble()
        hideCard()
        stt?.stop()
        runCatching { tone?.release() }
        tone = null
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Kept for the debug tool path when it needs the last spoken line.
    fun say(text: String) {
        speakThen(text) {}
    }
}
