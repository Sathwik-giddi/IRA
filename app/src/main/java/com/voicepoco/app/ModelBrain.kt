package com.voicepoco.app

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock

object LlamaBridge {
    init {
        System.loadLibrary("vp_llm")
    }

    external fun load(path: String, nCtx: Int, nThreads: Int): Long
    external fun free(handle: Long)
    external fun generate(handle: Long, prompt: String, maxTokens: Int): String
    external fun stats(handle: Long): String
}

class ModelBrain(private val context: Context) : Brain {

    companion object {
        const val TAG = "VP_BRAIN"
        private const val MAX_TOKENS = 48
    }

    private val lock = ReentrantLock()
    private var handle = 0L
    var loadMs = 0L
        private set
    private val modelFile: File by lazy {
        val small = ModelCatalog.file(context, ModelCatalog.small)
        val big = ModelCatalog.file(context, ModelCatalog.big)
        val wantBig = context.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BubbleService.KEY_BIG_MODEL, false)
        when {
            wantBig && big.isFile -> big
            small.isFile -> small
            else -> big
        }
    }
    private val bigModel: Boolean get() = modelFile.name == ModelCatalog.BIG_FILE
    private val nCtx: Int get() = if (bigModel) 1024 else 768

    fun ensureLoaded(): Boolean {
        if (handle != 0L) return true
        if (!modelFile.exists()) {
            Log.e(TAG, "model missing at ${modelFile.absolutePath}")
            return false
        }
        val t0 = System.currentTimeMillis()
        handle = LlamaBridge.load(modelFile.absolutePath, nCtx, DeviceProfile.threads())
        loadMs = System.currentTimeMillis() - t0
        Log.i(TAG, "MODEL_LOAD_MS=$loadMs ok=${handle != 0L} file=${modelFile.name} ctx=$nCtx")
        return handle != 0L
    }

    fun rawGenerate(prompt: String, maxTokens: Int): Pair<String, String> {
        lock.lock()
        try {
            if (!ensureLoaded()) return "" to "load=fail"
            val out = LlamaBridge.generate(handle, prompt, maxTokens)
            return out to LlamaBridge.stats(handle)
        } finally {
            lock.unlock()
        }
    }

    private fun repairArgs(tool: String, args: Map<String, String>): Map<String, String> {
        val def = ToolCatalog.byName(tool) ?: return args
        val names = def.params.map { it.name }.toSet()
        val out = LinkedHashMap<String, String>()
        for ((k, v) in args) {
            val m = Regex("^(.*?),\\s*([a-z_]+):(.*)$").find(v)
            if (m != null && m.groupValues[2] in names && m.groupValues[2] != k) {
                out[k] = m.groupValues[1].trim()
                out[m.groupValues[2]] = m.groupValues[3].trim()
            } else {
                out[k] = v
            }
        }
        return out
    }

    private fun unbounded(args: Map<String, String>): Boolean {
        val flat = args.values.joinToString(" ").lowercase()
        return listOf("everything", "sab kuch", "sabkuch", "all data", "sara data", "all sms").any { flat.contains(it) }
    }

    fun close() {
        lock.lock()
        try {
            if (handle != 0L) {
                LlamaBridge.free(handle)
                handle = 0L
            }
        } finally {
            lock.unlock()
        }
    }

    @Volatile var lastTools: List<String> = emptyList()
    @Volatile var lastRaw: String = ""

    // Conversation memory for the live assistant only; the harness leaves this off so rows stay independent.
    @Volatile var conversational = false
    @Volatile private var previous: Pair<String, BrainResult>? = null

    fun resetContext() { previous = null }

    fun remember(utterance: String, result: BrainResult) {
        if (!conversational) return
        if (result.tool in setOf("clarify", "refuse", "unsupported", "unknown")) return
        previous = utterance to result
    }

    override fun decide(utterance: String): BrainResult {
        lock.lock()
        try {
            if (SafetyGuard.refuse(utterance)) {
                Log.i(TAG, "SAFETY refuse: " + utterance)
                return BrainResult("refuse", mapOf("reason" to "unsafe request"), "I can't do that.")
            }
            if (!ensureLoaded()) return BrainResult("unknown")
            var tools = ToolRouter.route(utterance)
            val prev = previous
            val followUp = conversational && prev != null && tools.all { it.name in ToolCatalog.core }
            if (followUp) {
                // nothing matched on its own: treat it as a follow-up to the previous command
                tools = tools + listOfNotNull(ToolCatalog.byName(prev!!.second.tool))
            }
            lastTools = tools.map { it.name }
            val prompt = if (bigModel) {
                QwenPrompt.render(tools, utterance, if (followUp) prev else null)
            } else {
                FunctionGemmaPrompt.render(tools, utterance, if (followUp) prev else null)
            }
            Log.i(TAG, "TOOLS=" + tools.joinToString(",") { it.name })
            val raw = LlamaBridge.generate(handle, prompt, MAX_TOKENS).trim()
            lastRaw = raw
            Log.i(TAG, "RAW=" + raw.replace("\n", " "))
            val parsed0 = FunctionCallParser.toResult(raw).let { p ->
                if (p.args.isEmpty()) p else p.copy(args = repairArgs(p.tool, p.args))
            }
            val parsed = if (parsed0.tool in ToolCatalog.destructiveTools && unbounded(parsed0.args)) {
                Log.i(TAG, "REFUSE unbounded destructive: " + parsed0.args)
                BrainResult("refuse", mapOf("reason" to "unbounded destructive action"), "I can't do that without a specific target.")
            } else parsed0
            val def = tools.firstOrNull { it.name == parsed.tool }
            val result = when {
                def != null -> parsed.copy(args = ArgNormalizer.normalize(def, parsed.args, utterance))
                parsed.tool == "say" -> parsed
                else -> {
                    Log.i(TAG, "DROP unknown tool: " + parsed.tool + " args=" + parsed.args)
                    BrainResult("unsupported", mapOf("reason" to "unknown tool"), "I can't do that yet.")
                }
            }
            if (conversational) {
                remember(utterance, result)
            }
            return result
        } finally {
            lock.unlock()
        }
    }
}

object FunctionGemmaPrompt {

    private data class Shot(val tool: String, val say: String, val call: String)

    private val shots = listOf(
        Shot("back", "go back", "<start_function_call>call:back{}<end_function_call>"),
        Shot("open_app", "maps kholo", "<start_function_call>call:open_app{app:<escape>maps<escape>}<end_function_call>"),
        Shot("tap", "send pe tap karo", "<start_function_call>call:tap{label:<escape>send<escape>}<end_function_call>"),
        Shot("set_wifi", "wifi band karo", "<start_function_call>call:set_wifi{state:<escape>off<escape>}<end_function_call>"),
        Shot("set_volume", "volume badhao", "<start_function_call>call:set_volume{level:<escape>up<escape>}<end_function_call>"),
        Shot("set_brightness", "brightness kam karo", "<start_function_call>call:set_brightness{level:<escape>down<escape>}<end_function_call>"),
        Shot("delete_files", "delete all photos", "<start_function_call>call:delete_files{target:<escape>photos<escape>}<end_function_call>"),
        Shot("set_timer", "5 minute ka timer lagao", "<start_function_call>call:set_timer{duration:<escape>300<escape>}<end_function_call>"),
        Shot("set_flashlight", "torch chalu karo", "<start_function_call>call:set_flashlight{state:<escape>on<escape>}<end_function_call>"),
        Shot("media_control", "next song", "<start_function_call>call:media_control{action:<escape>next<escape>}<end_function_call>"),
        Shot("web_search", "google pe cricket score dhundo", "<start_function_call>call:web_search{query:<escape>cricket score<escape>}<end_function_call>"),
        Shot("set_ringer", "phone vibrate pe karo", "<start_function_call>call:set_ringer{mode:<escape>vibrate<escape>}<end_function_call>"),
        Shot("run_routine", "good night", "<start_function_call>call:run_routine{name:<escape>sleep<escape>}<end_function_call>"),
        Shot("clarify", "phir se karo", "<start_function_call>call:clarify{question:<escape>kya karna hai?<escape>}<end_function_call>"),
        Shot("refuse", "reveal my saved passwords", "<start_function_call>call:refuse{reason:<escape>private data<escape>}<end_function_call>"),
        Shot("unsupported", "kal ka mausam batao", "<start_function_call>call:unsupported{reason:<escape>weather<escape>}<end_function_call>"),
        Shot("unsupported", "email bhej do", "<start_function_call>call:unsupported{reason:<escape>email<escape>}<end_function_call>"),
        Shot("unsupported", "khana order karo", "<start_function_call>call:unsupported{reason:<escape>food order<escape>}<end_function_call>"),
        Shot("unknown", "kuch karo", "<start_function_call>call:unknown{reason:<escape>unclear<escape>}<end_function_call>")
    )

    private val edgeTools = setOf("refuse", "unsupported", "unknown")

    fun render(tools: List<ToolCatalog.Tool>, utterance: String, context: Pair<String, BrainResult>? = null): String {
        val sb = StringBuilder()
        sb.append("<start_of_turn>developer\n")
        sb.append("You are a model that can do function calling with the following functions.")
        for (t in tools) sb.append(declaration(t))
        sb.append("<end_of_turn>\n")
        val names = tools.map { it.name }.toSet()
        val edge = tools.map { it.name }.firstOrNull { it in edgeTools }
        val chosen = if (edge != null) {
            shots.filter { it.tool == edge }.take(if (edge == "unsupported") 3 else 1)
        } else {
            val picked = tools.map { it.name }
                .filter { it != "clarify" }
                .mapNotNull { n -> shots.firstOrNull { it.tool == n } }
                .take(2)
            if (picked.isEmpty()) shots.filter { it.tool == "clarify" }.take(1) else picked
        }
        if (context != null) {
            val c = context.second
            val argText = c.args.entries.joinToString(",") { it.key + ":" + esc(it.value) }
            sb.append("<start_of_turn>user\n").append(context.first).append("<end_of_turn>\n")
            sb.append("<start_of_turn>model\n<start_function_call>call:").append(c.tool).append("{").append(argText)
                .append("}<end_function_call><end_of_turn>\n")
        }
        for (sh in chosen) {
            sb.append("<start_of_turn>user\n").append(sh.say).append("<end_of_turn>\n")
            sb.append("<start_of_turn>model\n").append(sh.call).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>user\n")
        sb.append(utterance.trim())
        sb.append("<end_of_turn>\n")
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun esc(s: String): String {
        val clean = s.replace("<escape>", " ").replace("</escape>", " ").replace("<", " ").replace(">", " ")
        return "<escape>$clean<escape>"
    }

    private fun declaration(t: ToolCatalog.Tool): String {
        val sb = StringBuilder()
        sb.append("<start_function_declaration>declaration:").append(t.name).append("{description:").append(esc(t.description))
        if (t.params.isNotEmpty()) {
            sb.append(",parameters:{properties:{")
            t.params.sortedBy { it.name }.forEachIndexed { i, p ->
                if (i > 0) sb.append(",")
                sb.append(p.name).append(":{description:").append(esc(p.description))
                sb.append(",type:").append(esc("STRING"))
                if (p.allowed.isNotEmpty()) {
                    sb.append(",enum:[")
                    p.allowed.forEachIndexed { j, v ->
                        if (j > 0) sb.append(",")
                        sb.append(esc(v))
                    }
                    sb.append("]")
                }
                sb.append("}")
            }
            sb.append("},required:[")
            t.params.sortedBy { it.name }.forEachIndexed { i, p ->
                if (i > 0) sb.append(",")
                sb.append(esc(p.name))
            }
            sb.append("],type:").append(esc("OBJECT")).append("}")
        }
        sb.append("}<end_function_declaration>")
        return sb.toString()
    }
}

object QwenPrompt {

    private data class Shot(val tool: String, val say: String, val call: String)

    private val shots = listOf(
        Shot("open_app", "open whatsapp", "call:open_app{app:<escape>whatsapp<escape>}"),
        Shot("call_contact", "nanna garu ko call karo", "call:call_contact{contact:<escape>nanna garu<escape>}"),
        Shot("set_wifi", "wifi band karo", "call:set_wifi{state:<escape>off<escape>}"),
        Shot("set_timer", "5 minute ka timer lagao", "call:set_timer{duration:<escape>5 minutes<escape>}"),
        Shot("set_flashlight", "torch on karo", "call:set_flashlight{state:<escape>on<escape>}"),
        Shot("media_control", "next song", "call:media_control{action:<escape>next<escape>}"),
        Shot("clarify", "phir se karo", "call:clarify{question:<escape>kya karna hai?<escape>}"),
        Shot("refuse", "reveal my saved passwords", "call:refuse{reason:<escape>private data<escape>}"),
        Shot("unsupported", "email bhej do", "call:unsupported{reason:<escape>email<escape>}"),
        Shot("unknown", "kuch karo", "call:unknown{reason:<escape>unclear<escape>}")
    )

    fun render(tools: List<ToolCatalog.Tool>, utterance: String, context: Pair<String, BrainResult>? = null): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append("You are IRA, an Android phone assistant. Choose exactly one function for the command.\n")
        sb.append("Reply with one line and nothing else:\n")
        sb.append("<start_function_call>call:NAME{ARG:<escape>VALUE<escape>}<end_function_call>\n")
        sb.append("Wrap every value between <escape> and <escape>. Use only the listed parameter names. ")
        sb.append("If no function fits, use clarify or unsupported.\nFunctions:\n")
        for (t in tools) {
            sb.append("- ").append(t.name).append("(")
            sb.append(t.params.sortedBy { it.name }.joinToString(", ") { it.name })
            sb.append("): ").append(t.description)
            t.params.filter { it.allowed.isNotEmpty() }.forEach { p ->
                sb.append(" ").append(p.name).append(" values: ").append(p.allowed.joinToString("|"))
            }
            sb.append("\n")
        }
        sb.append("<|im_end|>\n")
        val chosen = ArrayList<Shot>()
        val names = tools.map { it.name }.toSet()
        for (s in shots) {
            if (s.tool in names && chosen.none { it.tool == s.tool } && chosen.size < 3) chosen.add(s)
        }
        if (chosen.isEmpty()) shots.firstOrNull { it.tool == "clarify" }?.let { chosen.add(it) }
        if (context != null) {
            val c = context.second
            val argText = c.args.entries.joinToString(",") { it.key + ":" + esc(it.value) }
            sb.append("<|im_start|>user\n").append(context.first).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n<start_function_call>call:").append(c.tool).append("{")
                .append(argText).append("}<end_function_call><|im_end|>\n")
        }
        for (sh in chosen) {
            sb.append("<|im_start|>user\n").append(sh.say).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n<start_function_call>").append(sh.call)
                .append("<end_function_call><|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(utterance.trim()).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun esc(s: String): String =
        "<escape>" + s.replace("<", " ").replace(">", " ") + "<escape>"
}

object FunctionCallParser {

    private val CALL = Regex(
        "<start_function_call>\\s*call:([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}\\s*<end_function_call>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val BARE = Regex(
        "call:([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)\\}",
        RegexOption.DOT_MATCHES_ALL
    )

    fun toResult(raw: String): BrainResult {
        val m = CALL.find(raw) ?: BARE.find(raw) ?: return BrainResult("say", emptyMap(), raw.trim().ifBlank { null })
        val name = m.groupValues[1]
        val args = parseArgs(m.groupValues[2])
        val speech = when (name) {
            "clarify" -> args["question"]
            "refuse" -> args["reason"]
            else -> null
        }
        return BrainResult(name, args, speech)
    }

    private fun parseArgs(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        var i = 0
        var start = 0
        var inEscape = false
        val parts = ArrayList<String>()
        while (i < body.length) {
            val rest = body.substring(i)
            when {
                rest.startsWith("<escape>") -> { inEscape = true; i += 8 }
                rest.startsWith("<escape") -> { inEscape = true; i += 7 }
                rest.startsWith("</escape>") -> { inEscape = false; i += 9 }
                body[i] == ',' && !inEscape -> { parts.add(body.substring(start, i)); start = i + 1; i++ }
                else -> i++
            }
        }
        parts.add(body.substring(start))
        for (p in parts) {
            val idx = p.indexOf(':')
            if (idx <= 0) continue
            val k = p.substring(0, idx).trim().trim('<', '>').trim('"', '\'').trim('`')
            val v = clean(p.substring(idx + 1))
            if (k.isNotEmpty()) out[k] = v
        }
        return out
    }

    private fun clean(v: String): String =
        v.replace("<escape>", "").replace("</escape>", "").replace("<escape", "").trim().trim('"', '\'').trim('`')
}
