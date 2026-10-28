package com.voicepoco.app

object ArgNormalizer {

    private val onWords = setOf("on", "chalu", "enable", "enabled")
    private val offWords = setOf("off", "band", "bandh", "disable", "disabled")
    private val upWords = setOf("up", "badhao", "badha", "badhado", "zyada", "increase", "louder", "brighter", "high")
    private val downWords = setOf("down", "kam", "ghatao", "ghatado", "decrease", "quieter", "dimmer", "low")
    private val split = Regex("[^a-z0-9-]+")

    private val cleanable = setOf("app", "label", "target", "direction", "level", "state", "time")
    private val filler = setOf("pe", "par", "mein", "me", "ko", "ka", "ki", "karo", "kar", "do", "please", "zara", "thoda")
    private val verbs = mapOf(
        "open_app" to setOf("kholo", "khol", "kholna", "open", "launch", "start", "run", "app"),
        "tap" to setOf("tap", "click", "dabao", "dab", "press", "icon", "button")
    )

    fun normalize(tool: ToolCatalog.Tool, args: Map<String, String>, utterance: String): Map<String, String> {
        if (args.isEmpty()) return args
        val words = utterance.lowercase().split(split).filter { it.isNotBlank() }.toSet()
        val out = clean(tool, args, utterance, words)
        for (p in tool.params) {
            val v = out[p.name] ?: continue
            when {
                p.name == "duration" || p.name == "seconds" -> toSeconds(v)?.let { out[p.name] = it }
                p.name == "query" -> {
                    val q = v.lowercase().split(split).filter { it.isNotBlank() && it !in queryNoise }.joinToString(" ")
                    if (q.isNotBlank()) out[p.name] = q
                }
                tool.name == "run_routine" && p.name == "name" && v.lowercase() !in p.allowed -> {
                    routineFor(utterance.lowercase())?.let { out[p.name] = it }
                }
                tool.name == "remember" && p.name == "note" && !utterance.lowercase().contains(v.lowercase().trim()) -> {
                    val n = utterance.trim().replace(Regex("^(please\\s+)?(remember|yaad rakhna|yaad rakho)(\\s+(that|ki))?\\s+", RegexOption.IGNORE_CASE), "")
                    if (n.isNotBlank()) out[p.name] = n
                }
                p.name == "time" && tool.name == "run_routine" && !v.any { it.isDigit() } -> out.remove(p.name)
                p.allowed.isNotEmpty() && !p.allowed.contains("on") && !p.allowed.contains("up") && v.lowercase() !in p.allowed -> {
                    val hit = p.allowed.firstOrNull { it in v.lowercase().split(split) || it in words }
                    if (hit != null) out[p.name] = hit
                }
            }
        }
        for (p in tool.params) {
            if (out[p.name] == null) continue
            val resolved = when {
                p.allowed.contains("on") && p.allowed.contains("off") -> polarity(words, offWords, "off", onWords, "on")
                p.allowed.contains("up") && p.allowed.contains("down") -> polarity(words, downWords, "down", upWords, "up")
                else -> null
            }
            if (resolved != null) out[p.name] = resolved
        }
        return out
    }

    private val queryNoise = setOf("dhundo", "googling", "google", "search", "for", "pe", "karo", "batao", "dhoondo")

    private val routineWords = mapOf(
        "sleep" to listOf("sleep", "sone", "so raha", "so rahi", "night"),
        "driving" to listOf("driving", "gaadi", "drive"),
        "meeting" to listOf("meeting"),
        "outdoors" to listOf("outdoor", "dhoop", "outside"),
        "battery_low" to listOf("battery", "dying", "marne")
    )

    private fun routineFor(u: String): String? =
        routineWords.entries.firstOrNull { e -> e.value.any { u.contains(it) } }?.key

    private fun toSeconds(v: String): String? {
        val m = Regex("(\\d+)\\s*([a-z]*)").find(v.lowercase()) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val unit = m.groupValues[2]
        return when {
            unit.startsWith("min") -> n * 60
            unit.startsWith("hour") || unit.startsWith("hr") || unit.startsWith("ghant") -> n * 3600
            else -> n
        }.toString()
    }

    private fun clean(
        tool: ToolCatalog.Tool,
        args: Map<String, String>,
        utterance: String,
        uttWords: Set<String>
    ): LinkedHashMap<String, String> {
        val drop = filler + verbs.getOrDefault(tool.name, emptySet())
        val out = LinkedHashMap<String, String>()
        for ((k, v) in args) {
            if (k !in cleanable) { out[k] = v; continue }
            val kept = v.lowercase().trim().split(split)
                .filter { it.isNotBlank() && it !in drop }
                .joinToString(" ")
            if (kept.isNotBlank()) {
                out[k] = kept
            } else {
                val fallback = utterance.lowercase().split(split)
                    .filter { it.isNotBlank() && it !in drop }
                    .take(2)
                    .joinToString(" ")
                out[k] = fallback
            }
        }
        return out
    }

    private fun polarity(
        words: Set<String>,
        lowSet: Set<String>, lowValue: String,
        highSet: Set<String>, highValue: String
    ): String? {
        val low = words.any { it in lowSet }
        val high = words.any { it in highSet }
        return when {
            low && !high -> lowValue
            high && !low -> highValue
            else -> null
        }
    }
}
