package com.voicepoco.app

data class ToolCall(
    val tool: String,
    val args: Map<String, String> = emptyMap()
)

object CommandParser {

    fun parse(text: String): ToolCall {
        val lower = text.lowercase().trim()
        if (lower.isEmpty()) return ToolCall("unknown")
        return when {
            lower.startsWith("go back") -> ToolCall("back")
            lower.startsWith("go home") -> ToolCall("home")
            lower.startsWith("scroll") -> ToolCall(
                "scroll",
                mapOf("direction" to if (lower.contains("up")) "up" else "down")
            )
            lower.startsWith("tap ") -> ToolCall("tap", mapOf("label" to lower.removePrefix("tap ")))
            lower.startsWith("type ") -> ToolCall("type", mapOf("text" to text.substring(5)))
            openVerb(lower) != null -> ToolCall("open_app", mapOf("app" to afterOpen(lower)))
            else -> ToolCall("unknown")
        }
    }

    private fun openVerb(lower: String): String? {
        for (v in arrayOf("open", "launch", "start")) {
            if (lower.startsWith(v)) return v
        }
        return null
    }

    private fun afterOpen(lower: String): String {
        val v = openVerb(lower) ?: return lower
        val q = lower.substring(v.length).trim()
        for (drop in arrayOf("please", "the ")) {
            if (q.startsWith(drop)) return q.removePrefix(drop).trim()
        }
        return q
    }
}
