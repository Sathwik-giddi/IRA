package com.voicepoco.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.Html
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudBrain(private val context: Context) {

    companion object {
        private const val TAG = "VP_CLOUD"
        private const val ENDPOINT = "https://text.pollinations.ai/openai"
        private const val MODEL = "openai"
        private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
        private const val GEMINI_MODEL = "gemini-2.5-flash"
        private const val MAX_HISTORY = 14
        private const val MAX_RESULTS = 5
        private val EDGE_TOOLS = setOf("web_search", "clarify", "unsupported", "unknown", "refuse")

        fun isOnline(ctx: Context): Boolean {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private data class Turn(val role: String, val content: String)
    private data class WebResult(val title: String, val snippet: String)

    private val turns = ArrayList<Turn>()

    @Volatile private var cooldownUntil = 0L
    @Volatile private var geminiCooldownUntil = 0L

    private fun geminiKey(): String =
        context.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)
            .getString(BubbleService.KEY_GEMINI, "").orEmpty().trim()

    private fun cooldownMs(): Long =
        (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    fun enabled(): Boolean =
        context.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BubbleService.KEY_CLOUD, true)

    fun reset() {
        synchronized(turns) { turns.clear() }
    }

    fun recordUser(text: String) = add("user", text)

    fun recordAssistant(text: String) = add("assistant", text)

    private fun add(role: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        synchronized(turns) {
            turns.add(Turn(role, clean))
            while (turns.size > MAX_HISTORY) turns.removeAt(0)
        }
    }

    fun decide(utterance: String): BrainResult? {
        if (!enabled()) {
            AssistantService.LogI("cloud off")
            return null
        }
        if (!isOnline(context)) {
            AssistantService.LogI("cloud offline")
            return null
        }
        val started = System.currentTimeMillis()
        val detailed = SpeechStyle.wantsDetail(utterance)
        val cooling = cooldownMs()
        if (geminiKey().isBlank() && cooling > 0 &&
            !looksLikeResearch(utterance) && !looksLikeToolAction(utterance)
        ) {
            val secs = (cooling + 999L) / 1000L
            AssistantService.LogI("cloud cooldown " + secs + "s")
            return BrainResult("say", emptyMap(), "Cloud is cooling down. Try me in " + secs + " seconds.")
        }
        if (looksLikeResearch(utterance)) {
            AssistantService.LogI("cloud route: research detailed=" + detailed)
            return research(searchQuery(utterance), started, detailed)
        }
        if (!looksLikeToolAction(utterance)) {
            AssistantService.LogI("cloud route: chat detailed=" + detailed)
            return chat(utterance, started, detailed)
        }
        AssistantService.LogI("cloud route: tools detailed=" + detailed)
        return toolDecision(utterance, started, detailed)
    }

    private fun chat(utterance: String, started: Long, detailed: Boolean): BrainResult? {
        synchronized(turns) {
            AssistantService.LogI("cloud history=" + turns.size +
                " last=" + (turns.lastOrNull()?.content?.take(80) ?: "none"))
        }
        val raw = post(messages(chatPrompt(utterance, detailed)), SpeechStyle.maxTokens(detailed, 170))
            ?: return BrainResult("say", emptyMap(), "Cloud is slow right now. Try me again in a moment.")
        val obj = extractJson(raw)
        val text = SpeechStyle.fit(cleanSpeech(
            when {
                obj == null -> raw
                obj.optString("text").isNotBlank() -> obj.optString("text")
                else -> ""
            }
        ), detailed)
        if (text.isBlank()) {
            AssistantService.LogI("cloud chat empty raw=" + raw.take(120))
            return null
        }
        AssistantService.LogI(
            "cloud chat ms=" + (System.currentTimeMillis() - started) +
                " chars=" + text.length + " text=" + text.take(140)
        )
        return BrainResult("say", emptyMap(), text)
    }

    private fun toolDecision(utterance: String, started: Long, detailed: Boolean): BrainResult? {
        val raw = post(messages(systemPrompt(detailed)), SpeechStyle.maxTokens(detailed, 260)) ?: return null
        val obj = extractJson(raw)
        if (obj == null) {
            val text = SpeechStyle.fit(cleanSpeech(raw), detailed)
            if (text.isBlank()) return null
            AssistantService.LogI(
                "cloud reply raw ms=" + (System.currentTimeMillis() - started) +
                    " chars=" + text.length + " text=" + text.take(140)
            )
            return BrainResult("say", emptyMap(), text)
        }

        return when (obj.optString("action").lowercase()) {
            "tool" -> toolResult(obj, utterance, started, detailed)
            "research" -> {
                val q = obj.optString("query").trim().ifBlank { utterance }
                research(q, started, detailed)
            }
            "reply", "say", "" -> {
                val text = SpeechStyle.fit(cleanSpeech(obj.optString("text")), detailed)
                if (text.isBlank()) null else {
                    AssistantService.LogI(
                        "cloud reply ms=" + (System.currentTimeMillis() - started) +
                            " chars=" + text.length + " text=" + text.take(140)
                    )
                    BrainResult("say", emptyMap(), text)
                }
            }
            else -> {
                AssistantService.LogI("cloud bad action: " + obj.optString("action") + " raw=" + raw.take(140))
                null
            }
        }
    }

    private fun looksLikeResearch(text: String): Boolean {
        val t = text.lowercase()
        return Regex(
            "\\b(news|headlines?|latest|weather|forecast|temperature|price|cost|score|match|who won|winner|election|stock|sensex|nifty|crypto|bitcoin|release date|search|google|look up|research|internet|president|prime minister|current (?:news|price|weather|score|stock|president|prime minister)|what'?s new in|what happened (?:today|yesterday))\\b"
        ).containsMatchIn(t)
    }

    private fun looksLikeToolAction(text: String): Boolean {
        val t = text.lowercase()
        return Regex(
            "\\b(open|launch|start|kholo|khol|call|dial|message|sms|whatsapp|bhejo|bhej do|set (?:a |an |the )?(?:timer|alarm)|turn (?:on|off)|enable|disable|wifi|wi-fi|bluetooth|airplane|flight mode|do not disturb|dnd|flashlight|torch|volume|awaaz|brightness|roshni|screenshot|scroll|tap|click|type|read (?:my |the )?(?:notifications?|screen)|notifications?|battery|charge|mode|home screen|go home|go back|selfie|camera|photo|hotspot|screen off|lock|navigate|directions|location|pay|money|instagram|youtube|remember|remind|yaad|note kar|calendar|event|vibrate|ringer|pause|resume|next song|previous song|screen timeout)\\b"
        ).containsMatchIn(t)
    }

    private fun searchQuery(text: String): String =
        text.replace(Regex("(?i)^(?:hey )?ira[,\\s]+"), "")
            .replace(Regex("(?i)^(?:please )?(?:search|google|look up|find) (?:for )?"), "")
            .trim()

    private fun toolResult(obj: JSONObject, utterance: String, started: Long, detailed: Boolean): BrainResult? {
        val name = obj.optString("tool").trim()
        val def = ToolCatalog.byName(name) ?: run {
            AssistantService.LogI("cloud unknown tool: " + name)
            return null
        }
        val argsObj = obj.optJSONObject("args") ?: JSONObject()
        val args = LinkedHashMap<String, String>()
        for (k in argsObj.keys()) {
            val v = argsObj.opt(k)
            args[k] = when (v) {
                null, JSONObject.NULL -> ""
                is String -> v
                else -> v.toString()
            }
        }
        val normalized = ArgNormalizer.normalize(def, args, utterance)
        val speak = obj.optString("speak").trim().ifBlank { null }
        if (def.name == "web_search") {
            val q = normalized["query"].orEmpty().ifBlank { utterance }
            return research(q, started, detailed)
        }
        if (def.name == "clarify") {
            val q = normalized["question"].orEmpty().ifBlank { "What do you mean?" }
            return BrainResult("clarify", mapOf("question" to q), q)
        }
        if (def.name == "refuse") {
            return BrainResult("say", emptyMap(), speak ?: "I can't do that.")
        }
        if (def.name in setOf("unsupported", "unknown")) {
            return BrainResult("say", emptyMap(), speak ?: "I can't do that on this phone yet.")
        }
        if (def.name in EDGE_TOOLS) return null
        AssistantService.LogI(
            "cloud tool ms=" + (System.currentTimeMillis() - started) +
                " -> " + def.name + " " + normalized
        )
        return BrainResult(def.name, normalized, speak)
    }

    private fun research(query: String, started: Long, detailed: Boolean): BrainResult? {
        val results = webSearch(query)
        AssistantService.LogI("cloud research q='" + query + "' results=" + results.size)
        if (results.isEmpty()) {
            return BrainResult("say", emptyMap(), "I could not reach the web for that. Try again in a moment.")
        }
        val notes = results.joinToString("\n\n") { r ->
            "Title: " + r.title + "\nSnippet: " + r.snippet
        }
        val messages = JSONArray()
        messages.put(msg("system", researchPrompt(detailed)))
        synchronized(turns) {
            for (t in turns.takeLast(6)) messages.put(msg(t.role, t.content))
        }
        messages.put(msg("user",
            "Use these web results to answer the latest question. If they do not answer it, say so plainly.\n\n" +
                notes
        ))
        val raw = post(messages, SpeechStyle.maxTokens(detailed, 180))
        if (raw == null) {
            val fallback = results.take(2).joinToString(" ") { it.snippet }.take(420)
            if (fallback.isBlank()) return null
            AssistantService.LogI("cloud research fallback chars=" + fallback.length)
            return BrainResult("say", emptyMap(), SpeechStyle.fit("Here's what I found. " + fallback, detailed))
        }
        val obj = extractJson(raw)
        val text = SpeechStyle.fit(cleanSpeech(
            if (obj != null && obj.optString("text").isNotBlank()) obj.optString("text") else raw
        ), detailed)
        if (text.isBlank()) return null
        AssistantService.LogI(
            "cloud research answer ms=" + (System.currentTimeMillis() - started) +
                " chars=" + text.length + " text=" + text.take(160)
        )
        return BrainResult("say", emptyMap(), text)
    }

    private fun messages(system: String): JSONArray {
        val arr = JSONArray()
        arr.put(msg("system", system))
        synchronized(turns) {
            for (t in turns.takeLast(MAX_HISTORY)) arr.put(msg(t.role, t.content))
        }
        return arr
    }

    private fun msg(role: String, content: String): JSONObject =
        JSONObject().put("role", role).put("content", content)

    private fun systemPrompt(detailed: Boolean): String {
        val now = SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.US).format(Date())
        val tools = ToolCatalog.tools
            .filter { it.name !in EDGE_TOOLS }
            .joinToString("\n") { t ->
                val params = t.params.joinToString(", ") { p ->
                    if (p.allowed.isEmpty()) p.name else p.name + "=" + p.allowed.joinToString("|")
                }
                "- " + t.name + "(" + params + "): " + t.description
            }
        return """
You are IRA, the user's Android voice assistant. You are sharp, warm and playful. You speak Hinglish when the user does, and match their energy. You can tease them lightly, but never insult, use slurs, or punch down. Do not use markdown.

Length rule: ${SpeechStyle.rule(detailed)}

Current device time: $now.

Return only one JSON object, no markdown and no extra text:
{"action":"tool","tool":"NAME","args":{"key":"value"},"speak":"short confirmation"}
{"action":"research","query":"specific web search"}
{"action":"reply","text":"what to say"}

Rules:
- Use tool for real phone actions such as opening apps, calls, messages, settings, timers, alarms, media and screen control.
- Use research for news, weather, sports, prices, "latest", "today", "search", "look up", or anything that may be current. Also use it when the user explicitly asks for internet jokes or internet research.
- Use reply for ordinary conversation, explanations, advice, opinions, jokes, roasts and follow-ups.
- For a joke request, reply with one or two original jokes. Do not use research unless the user asks for internet jokes.
- Never invent a tool. If a phone action has no matching tool, say you cannot do it on this phone yet.
- Never claim an action happened unless a tool ran.
- For a destructive action, still choose the tool. The app will ask the user to confirm.
- If the request is unclear, ask one short question in reply.
- The `speak` field is a confirmation, not an explanation. Keep it under 12 words.
- Follow the conversation. Short follow-ups like "make it shorter", "why", "and then", or "roast it more" refer to your previous answer.

Tools:
$tools
""".trimIndent()
    }

    private fun chatPrompt(utterance: String, detailed: Boolean): String {
        val now = SimpleDateFormat("EEE, d MMM yyyy, h:mm a", Locale.US).format(Date())
        val followUp = Regex(
            "(?i)\\b(shorter|short|longer|detail|explain|why|and then|more|continue|continue that|roast it more|example)\\b"
        ).containsMatchIn(utterance)
        return """
You are IRA, the user's Android voice assistant. Reply with only the words to speak. No JSON, no markdown, no bullet points.

You are sharp, warm and playful. Match the user's language and energy. If they speak Hinglish, reply in Hinglish. You can tease them lightly, but never insult, use slurs, or punch down.

${SpeechStyle.rule(detailed)}

Rules:
- Answer the question directly. Do not restate it or introduce the answer.
- For a joke request, tell one short original joke.
- For a roast, be playful, not cruel.
- For an emotional or personal question, be warm and human.
- For a follow-up like "make it shorter", "why", "and then", or "roast it more", use the conversation above.${if (followUp) "\n- This is a follow-up. Obey the requested length change exactly. Do not repeat the previous answer." else ""}
- You cannot open apps, change settings, send messages, or search in this mode. Never pretend you did.
- If the user asks for current information and you are not sure, say you can look it up if they ask you to search.
- Do not mention being an AI model or a language model. You are IRA.
- Do not use emojis, markdown, or stage directions.

Current device time: $now.
""".trimIndent()
    }

    private fun researchPrompt(detailed: Boolean): String {
        val now = SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date())
        val length = if (detailed) {
            "The user asked for detail. Give a clear answer in under 170 words."
        } else {
            "The user did not ask for detail. Answer in one or two short sentences, under 38 words."
        }
        return """
You are IRA, the user's Android voice assistant. Answer from the supplied web results. Be direct and natural. Match the user's Hinglish if they used it. If the results are thin or disagree, say that plainly. Do not invent facts. Do not use markdown. $length Today is $now.
""".trimIndent()
    }

    private fun post(messages: JSONArray, maxTokens: Int): String? {
        val key = geminiKey()
        if (key.isNotBlank() && geminiCooldownUntil <= System.currentTimeMillis()) {
            val gem = postOnce(messages, maxTokens, true, key)
            if (gem != null) return gem
            geminiCooldownUntil = System.currentTimeMillis() + 60_000L
            AssistantService.LogI("cloud gemini failed, fallback pollinations")
        }
        if (cooldownMs() > 0) {
            AssistantService.LogI("cloud skipped: cooldown")
            return null
        }
        return postOnce(messages, maxTokens, false, "")
    }

    private fun postOnce(messages: JSONArray, maxTokens: Int, useGemini: Boolean, key: String): String? {
        AssistantService.LogI("cloud call provider=" + (if (useGemini) "gemini" else "pollinations"))
        val body = JSONObject()
            .put("model", if (useGemini) GEMINI_MODEL else MODEL)
            .put("messages", messages)
            .put("temperature", 0.8)
            .put("max_tokens", maxTokens)
        val conn = (URL(if (useGemini) GEMINI_ENDPOINT else ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "IRA/1.0 (Android)")
            if (useGemini) setRequestProperty("Authorization", "Bearer " + key)
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                if (!useGemini && code == 402 && text.contains("FREE_LIMIT")) {
                    cooldownUntil = System.currentTimeMillis() + 30_000L
                    AssistantService.LogI("cloud rate limited, cooldown 30s")
                }
                Log.e(TAG, "http " + code + " " + text.take(180))
                return null
            }
            val root = JSONObject(text)
            val choices = root.optJSONArray("choices") ?: run {
                Log.e(TAG, "no choices: " + text.take(180))
                return null
            }
            if (choices.length() == 0) {
                Log.e(TAG, "empty choices: " + text.take(180))
                return null
            }
            val choice = choices.getJSONObject(0)
            val message = choice.optJSONObject("message") ?: run {
                Log.e(TAG, "no message: " + text.take(180))
                return null
            }
            val content = message.optString("content").trim()
            if (content.isBlank()) {
                Log.e(TAG, "blank content finish=" + choice.optString("finish_reason") +
                    " reasoning=" + (message.optString("reasoning").length) +
                    " body=" + text.take(180))
            }
            content.ifBlank { null }
        } catch (e: Exception) {
            if (!useGemini) {
                cooldownUntil = System.currentTimeMillis() + 20_000L
                AssistantService.LogI("cloud failed, cooldown 20s")
            }
            Log.e(TAG, "post failed: " + e.message)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun webSearch(query: String): List<WebResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = get("https://html.duckduckgo.com/html/?q=$encoded") ?: return emptyList()
        val titleRe = Regex(
            "<a[^>]+class=\"result__a\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val snippetRe = Regex(
            "<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val titles = titleRe.findAll(html).map { plain(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        val snippets = snippetRe.findAll(html).map { plain(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        val n = minOf(titles.size, snippets.size, MAX_RESULTS)
        return (0 until n).map { WebResult(titles[it], snippets[it]) }
    }

    private fun get(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-IN,en;q=0.8,hi;q=0.6")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.e(TAG, "search http " + code)
                return null
            }
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            text.take(900_000)
        } catch (e: Exception) {
            Log.e(TAG, "search failed: " + e.message)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun plain(fragment: String): String {
        val text = Html.fromHtml(fragment, Html.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractJson(raw: String): JSONObject? {
        val s = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull()
    }

    private fun cleanSpeech(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("```[\\s\\S]*?```"), " ")
        t = t.replace(Regex("[*_#`>]+"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t.trim('"', '\'', ' ')
    }
}
