package com.voicepoco.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File

class HarnessService : Service() {

    companion object {
        private const val TAG = "POCO_HARNESS"
        private const val CHANNEL = "vp_harness"
        private const val NOTIF_ID = 41
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val backend = intent?.getStringExtra("backend") ?: "rules"
        startForeground(NOTIF_ID, notification(backend))
        Thread {
            try {
                runBatch(backend)
            } catch (t: Throwable) {
                Log.e(TAG, "HARNESS_FAIL " + t.javaClass.simpleName + ": " + t.message)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun notification(backend: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Harness", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("VP harness")
            .setContentText("backend=" + backend)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun runBatch(backend: String) {
        val inFile = File(filesDir, "vp_cmds.jsonl")
        val outFile = File(filesDir, "vp_results.jsonl")
        Log.i(TAG, "INPUT exists=" + inFile.exists() + " len=" + inFile.length())
        if (!inFile.exists()) {
            Log.e(TAG, "HARNESS_FAIL missing input")
            return
        }

        if (backend == "bench") {
            val mb = ModelBrain(applicationContext)
            if (!mb.ensureLoaded()) {
                Log.e(TAG, "HARNESS_FAIL model load failed")
                return
            }
            val sb = StringBuilder("MODEL_LOAD_MS=" + mb.loadMs + "\n")
            val variants = listOf(
                "open" to listOf("open_app"),
                "open+clarify" to listOf("open_app", "clarify"),
                "open+refuse" to listOf("open_app", "refuse"),
                "open+clarify+refuse" to listOf("open_app", "clarify", "refuse")
            )
            for ((vname, names) in variants) {
                val tools = names.mapNotNull { ToolCatalog.byName(it) }
                val prompt = FunctionGemmaPrompt.render(tools, "whatsapp kholo")
                val (out, st) = mb.rawGenerate(prompt, 40)
                sb.append("VAR=").append(vname).append(" ").append(st)
                    .append(" tool=").append(FunctionCallParser.toResult(out).tool)
                    .append(" out=").append(out.trim().replace("\n", " ")).append("\n")
            }
            val utts = listOf(
                "wifi band karo",
                "wifi chalu karo",
                "brightness kam karo",
                "volume thoda badhao",
                "alarm lagao 7 baje",
                "call mom",
                "delete all photos"
            )
            for (u in utts) {
                val tools = ToolRouter.route(u)
                val prompt = FunctionGemmaPrompt.render(tools, u)
                val (out, st) = mb.rawGenerate(prompt, 40)
                val parsed = FunctionCallParser.toResult(out)
                val def = tools.firstOrNull { it.name == parsed.tool }
                val finalArgs = if (def != null) ArgNormalizer.normalize(def, parsed.args, u) else parsed.args
                sb.append("UTT=").append(u)
                    .append(" tools=").append(tools.joinToString(",") { it.name })
                    .append(" ").append(st)
                    .append(" tool=").append(parsed.tool)
                    .append(" args=").append(finalArgs)
                    .append(" out=").append(out.trim().replace("\n", " ")).append("\n")
            }
            File(filesDir, "vp_bench.txt").writeText(sb.toString())
            Log.i(TAG, "BENCH_DONE")
            mb.close()
            return
        }

        if (backend == "model") {
            val mb = ModelBrain(applicationContext)
            if (!mb.ensureLoaded()) {
                Log.e(TAG, "HARNESS_FAIL model load failed")
                return
            }
            Log.i(TAG, "HARNESS_MODEL_LOAD_MS=" + mb.loadMs)
            runAll(mb, inFile, outFile)
            mb.close()
        } else {
            runAll(RulesBrain(), inFile, outFile)
        }
    }

    private fun runAll(brain: Brain, inFile: File, outFile: File) {
        var n = 0
        outFile.bufferedWriter().use { w ->
            inFile.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val o = JSONObject(line)
                val id = o.optString("id")
                val utt = o.optString("utterance", "")
                val t0 = SystemClock.elapsedRealtimeNanos()
                var res: BrainResult? = null
                var err: String? = null
                try {
                    res = brain.decide(utt)
                } catch (t: Throwable) {
                    err = t.javaClass.simpleName + ": " + t.message
                }
                val dt = SystemClock.elapsedRealtimeNanos() - t0
                val out = JSONObject()
                out.put("id", id)
                if (err != null) {
                    out.put("error", err)
                } else if (res != null) {
                    out.put("tool", res.tool)
                    out.put("args", JSONObject(res.args as Map<*, *>))
                    if (res.speech != null) out.put("speech", res.speech)
                    if (brain is ModelBrain) {
                        out.put("tools", org.json.JSONArray(brain.lastTools))
                        out.put("raw", brain.lastRaw)
                    }
                }
                out.put("latency_ns", dt)
                w.write(out.toString())
                w.newLine()
                w.flush()
                n++
                Log.i(TAG, "ROW " + id + " ns=" + dt)
            }
        }
        Log.i(TAG, "HARNESS_DONE n=$n")
        File(filesDir, "vp_marker.txt").writeText("done n=$n")
    }
}
