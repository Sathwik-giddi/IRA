package com.voicepoco.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class CommandStt(
    private val ctx: Context,
    private val onState: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var done = false
    private var lastPartial = ""

    fun listen(maxMs: Long) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            AssistantService.LogI("stt unavailable")
            onFinal("")
            return
        }
        done = false
        lastPartial = ""
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } catch (e: Exception) {
            AssistantService.LogI("stt create failed: " + e.message)
            onFinal("")
            return
        }
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                AssistantService.LogI("stt ready")
                onState("listening")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (done) return
                AssistantService.LogI("stt error " + error + " " + lastPartial)
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    finish("")
                } else {
                    finish(lastPartial, errorText(error))
                }
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                finish(t.ifBlank { lastPartial })
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank() && t != lastPartial) {
                    lastPartial = t
                    onPartial(t)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        }
        AssistantService.LogI("stt start en-IN")
        try {
            r.startListening(i)
        } catch (e: Exception) {
            AssistantService.LogI("stt start failed: " + e.message)
            finish("")
            return
        }
        main.postDelayed(watchdog, maxMs)
    }

    private val watchdog = Runnable {
        val r = sr ?: return@Runnable
        if (done) return@Runnable
        AssistantService.LogI("stt timeout, stopping")
        runCatching { r.stopListening() }
        main.postDelayed({ if (!done) finish(lastPartial) }, 1200)
    }

    private fun finish(text: String, err: String? = null) {
        if (done) return
        done = true
        main.removeCallbacksAndMessages(null)
        sr?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        sr = null
        AssistantService.LogI("stt final '" + text + "'" + if (err == null) "" else " err=" + err)
        if (err != null) onError(err)
        onFinal(text.trim())
    }

    fun stop() {
        done = true
        main.removeCallbacksAndMessages(null)
        sr?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        sr = null
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Speech audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service is offline"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
        else -> "Speech error " + code
    }
}
