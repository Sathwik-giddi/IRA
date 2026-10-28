package com.voicepoco.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.util.concurrent.atomic.AtomicBoolean

class VoiceEngine(
    private val ctx: Context,
    private val onState: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStopWord: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onWake: (() -> Unit)? = null
) {
    companion object {
        val WAKE = listOf("ira")
        val STOP = listOf("ruko", "band", "stop", "chup")
        private const val RATE = 16000
        private const val RMS_GATE = 340.0
        private const val SILENCE_MS = 950L
        private const val WAKE_COOLDOWN = 2500L
    }

    enum class State { WAKE, COMMAND, PAUSED }

    @Volatile private var state = State.WAKE
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var model: Model? = null
    private var wakeRec: Recognizer? = null
    private var cmdRec: Recognizer? = null
    private var audio: AudioRecord? = null

    private var cmdStart = 0L
    private var maxMs = 8000L
    private var lastVoice = 0L
    private var heard = false
    private var lastWake = 0L
    private var lastPartial = ""
    private var thermalSaid = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread { loop() }.apply { name = "ira-voice"; isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        state = State.PAUSED
        thread?.join(1500)
        closeAll()
    }

    fun pause() {
        state = State.PAUSED
        closeMic()
    }

    fun resumeWake() {
        closeCmd()
        wakeRec?.reset()
        lastPartial = ""
        state = State.WAKE
    }

    fun listenCommand(ms: Long = 6000, feed: ShortArray? = null, n: Int = 0) {
        if (model == null) return
        closeCmd()
        cmdRec = try { Recognizer(model, RATE.toFloat()) } catch (e: Exception) { onError("stt: " + e.message); return }
        cmdStart = SystemClock.uptimeMillis()
        maxMs = ms
        lastVoice = cmdStart
        heard = false
        lastPartial = ""
        state = State.COMMAND
        if (feed != null && n > 0) runCatching { cmdRec?.acceptWaveForm(feed, n) }
        onState("listening")
    }

    private fun loop() {
        try {
            if (model == null) {
                val path = StorageService.sync(ctx, "model", "model")
                model = Model(path)
                AssistantService.LogI("engine model loaded: " + path)
            }
        } catch (e: Exception) {
            AssistantService.LogI("engine model failed: " + e.message)
            onError("Wake model failed to load")
            running.set(false)
            return
        }
        wakeRec = try {
            Recognizer(model, RATE.toFloat(), grammar(WAKE + STOP))
        } catch (e: Exception) {
            onError("wake: " + e.message)
            null
        }
        if (wakeRec == null) return
        onState("ready")
        val buf = ShortArray(2000)
        var fails = 0
        val pm = ctx.getSystemService(PowerManager::class.java)
        while (running.get()) {
            if (state == State.PAUSED) { Thread.sleep(80); continue }
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
                pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            } else {
                false
            }
            if (thermal) {
                if (!thermalSaid) { thermalSaid = true; onError("Phone is warm, wake word paused") }
                closeMic()
                Thread.sleep(700)
                continue
            }
            thermalSaid = false
            val r = audio ?: openRecord()
            if (r == null) { Thread.sleep(250); continue }
            if (state == State.PAUSED) { closeMic(); Thread.sleep(80); continue }
            val n = try { r.read(buf, 0, buf.size) } catch (e: Exception) { 0 }
            if (n <= 0) {
                fails++
                if (fails > 8) { closeMic(); fails = 0 }
                Thread.sleep(150)
                continue
            }
            fails = 0
            val rms = rms(buf, n)
            if (state == State.WAKE) wakeStep(buf, n, rms) else if (state == State.COMMAND) commandStep(buf, n, rms)
        }
        closeAll()
    }

    private fun wakeStep(buf: ShortArray, n: Int, rms: Double) {
        val w = wakeRec ?: return
        if (rms < RMS_GATE) return
        val now = SystemClock.uptimeMillis()
        val done = runCatching { w.acceptWaveForm(buf, n) }.getOrDefault(false)
        val txt = if (done) {
            val t = text(runCatching { w.finalResult }.getOrDefault(""))
            runCatching { w.reset() }
            t
        } else {
            text(runCatching { w.partialResult }.getOrDefault(""))
        }
        if (txt.isBlank()) return
        val stop = STOP.firstOrNull { txt.contains(it) }
        if (stop != null) {
            if (now - lastWake > 1200) {
                lastWake = now
                runCatching { w.reset() }
                AssistantService.LogI("stop word: " + stop)
                onStopWord(stop)
            }
            return
        }
        val hit = WAKE.firstOrNull { txt.contains(it) } ?: return
        if (now - lastWake < WAKE_COOLDOWN) { runCatching { w.reset() }; return }
        lastWake = now
        runCatching { w.reset() }
        AssistantService.LogI("wake hit: " + hit + " in '" + txt + "'")
        if (onWake != null) {
            state = State.PAUSED
            closeMic()
            onWake.invoke()
        } else {
            listenCommand(8000, buf, n)
        }
    }

    private fun commandStep(buf: ShortArray, n: Int, rms: Double) {
        val c = cmdRec ?: return
        runCatching { c.acceptWaveForm(buf, n) }
        val now = SystemClock.uptimeMillis()
        if (rms > RMS_GATE) { lastVoice = now; heard = true }
        val p = text(runCatching { c.partialResult }.getOrDefault(""))
        if (p.isNotEmpty() && p != lastPartial) {
            lastPartial = p
            onPartial(clean(p))
        }
        val silent = heard && now - lastVoice > SILENCE_MS
        val long = now - cmdStart > maxMs
        val empty = !heard && now - cmdStart > 3500
        if (silent || long || empty) {
            val t = clean(text(runCatching { c.finalResult }.getOrDefault("")))
            closeCmd()
            state = State.WAKE
            wakeRec?.reset()
            onState("idle")
            AssistantService.LogI("command final: '" + t + "' silent=" + silent + " empty=" + empty)
            onFinal(t)
        }
    }

    private fun openRecord(): AudioRecord? {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4
            )
        } catch (e: Exception) { return null }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
        return try {
            rec.startRecording()
            audio = rec
            AssistantService.LogI("mic open")
            if (state == State.PAUSED) { closeMic(); return null }
            rec
        } catch (e: Exception) {
            rec.release()
            AssistantService.LogI("mic failed: " + e.message)
            null
        }
    }

    private fun closeMic() {
        val a = audio ?: return
        audio = null
        runCatching { a.stop() }
        runCatching { a.release() }
    }

    private fun closeCmd() {
        cmdRec?.let { runCatching { it.close() } }
        cmdRec = null
    }

    private fun closeAll() {
        closeMic()
        closeCmd()
        wakeRec?.let { runCatching { it.close() } }
        wakeRec = null
        model?.let { runCatching { it.close() } }
        model = null
    }

    private fun grammar(words: List<String>): String =
        words.joinToString(",", "[", "]") { "\"" + it + "\"" }

    private fun text(json: String): String = runCatching {
        val o = JSONObject(json)
        o.optString("text", o.optString("partial", ""))
    }.getOrElse { "" }

    private fun clean(raw: String): String {
        var t = raw.lowercase().trim()
        for (w in WAKE + STOP) {
            t = t.replace(Regex("(^|\\s)" + Regex.escape(w) + "(\\s|$)"), " ").trim()
        }
        return t.replace(Regex("\\s+"), " ").replace(Regex("^[,.!?;:]+"), "").trim()
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        var e = 0.0
        var i = 0
        while (i < n) { val s = buf[i].toDouble(); e += s * s; i += 4 }
        return Math.sqrt(e / Math.max(1, n / 4))
    }
}
