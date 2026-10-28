package com.voicepoco.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class BargeInDetector(private val context: Context, private val onSpeech: () -> Unit) {

    companion object {
        private const val RATE = 16000
        private const val FRAME = 320
        private const val WARMUP_MS = 350L
        private const val MIN_THRESHOLD = 220.0
        private const val MAX_THRESHOLD = 1800.0
    }

    private val running = AtomicBoolean(false)
    private val fired = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var record: AudioRecord? = null
    @Volatile private var aec: AcousticEchoCanceler? = null
    @Volatile private var audioMode = AudioManager.MODE_INVALID
    @Volatile private var speakerBefore = false
    @Volatile private var commDevice: AudioDeviceInfo? = null
    @Volatile private var forcedSpeaker = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        fired.set(false)
        worker = Thread({ loop() }, "ira-barge").apply {
            start()
        }
    }

    fun stop() {
        running.set(false)
        release()
        val t = worker
        if (t != null && t !== Thread.currentThread()) runCatching { t.join(450) }
        worker = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AssistantService.LogI("barge: no mic permission")
            running.set(false)
            return
        }
        val min = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) {
            AssistantService.LogI("barge: bad min buffer " + min)
            running.set(false)
            return
        }
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(min, FRAME * 2 * 12)
            )
        } catch (e: Exception) {
            AssistantService.LogI("barge: record create failed " + e)
            running.set(false)
            return
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            AssistantService.LogI("barge: record not initialized")
            runCatching { r.release() }
            running.set(false)
            return
        }
        record = r
        audioMode = AudioManager.MODE_INVALID
        speakerBefore = false
        commDevice = null
        forcedSpeaker = false
        runCatching {
            val am = context.getSystemService(AudioManager::class.java)
            if (am != null && am.mode != AudioManager.MODE_IN_CALL) {
                audioMode = am.mode
                speakerBefore = am.isSpeakerphoneOn
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    commDevice = am.communicationDevice
                    val speaker = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        forcedSpeaker = am.setCommunicationDevice(speaker)
                        AssistantService.LogI("barge: speaker=" + forcedSpeaker)
                    }
                }
            }
        }
        aec = runCatching {
            AcousticEchoCanceler.create(r.audioSessionId)?.also {
                it.enabled = true
                AssistantService.LogI("barge: aec=" + it.enabled)
            }
        }.getOrNull()
        val started = try {
            r.startRecording()
            r.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: Exception) {
            AssistantService.LogI("barge: start failed " + e)
            false
        }
        if (!started) {
            AssistantService.LogI("barge: not recording")
            release()
            running.set(false)
            return
        }

        val buf = ShortArray(FRAME)
        val onset = SystemClock.uptimeMillis()
        var noise = -1.0
        var voiced = 0
        var frames = 0
        var peak = 0.0
        AssistantService.LogI("barge: listening")
        try {
            while (running.get() && !fired.get()) {
                val n = r.read(buf, 0, buf.size)
                if (n <= 0) {
                    Thread.sleep(8)
                    continue
                }
                val now = SystemClock.uptimeMillis()
                if (now - onset < WARMUP_MS) continue
                val rms = rms(buf, n)
                peak = max(peak, rms)
                noise = if (noise < 0) rms else noise * 0.98 + rms * 0.02
                val threshold = (noise * 1.2 + 120.0).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
                frames++
                if (rms > threshold) {
                    voiced++
                    if (voiced >= 6) {
                        AssistantService.LogI(
                            "barge: speech rms=" + rms.toInt() + " noise=" + noise.toInt() +
                                " thr=" + threshold.toInt() + " peak=" + peak.toInt()
                        )
                        fire()
                        break
                    }
                } else {
                    voiced = 0
                }
                if (frames % 25 == 0) {
                    AssistantService.LogD(
                        "barge rms=" + rms.toInt() + " noise=" + noise.toInt() +
                            " thr=" + threshold.toInt() + " peak=" + peak.toInt()
                    )
                }
            }
        } catch (e: Exception) {
            AssistantService.LogI("barge: loop stopped " + e)
        } finally {
            release()
            running.set(false)
        }
    }

    private fun fire() {
        if (fired.compareAndSet(false, true)) onSpeech()
    }

    @SuppressLint("WrongConstant")
    private fun release() {
        val r = record
        record = null
        if (r != null) {
            runCatching {
                if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop()
            }
            runCatching { r.release() }
        }
        val a = aec
        aec = null
        runCatching { a?.release() }
        if (audioMode != AudioManager.MODE_INVALID) {
            runCatching {
                val am = context.getSystemService(AudioManager::class.java)
                if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && forcedSpeaker) {
                    val prev = commDevice
                    if (prev != null) am.setCommunicationDevice(prev) else am.clearCommunicationDevice()
                }
                am?.mode = audioMode
                am?.isSpeakerphoneOn = speakerBefore
            }
            audioMode = AudioManager.MODE_INVALID
            commDevice = null
            forcedSpeaker = false
        }
    }

    private fun rms(buf: ShortArray, n: Int): Double {
        if (n <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val v = abs(buf[i].toInt()).toDouble()
            sum += v * v
        }
        return sqrt(sum / n)
    }
}
