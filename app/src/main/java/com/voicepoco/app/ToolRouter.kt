package com.voicepoco.app

object ToolRouter {

    private val split = Regex("[^a-z0-9-]+")

    fun route(utterance: String, limit: Int = 6): List<ToolCatalog.Tool> {
        val lower = utterance.lowercase()
        val ws = lower.split(split).filter { it.isNotBlank() }.toSet()
        val padded = " " + lower.replace(split, " ").trim() + " "

        val ranked = ToolCatalog.tools
            .map { t -> t to score(t, ws, padded) }
            .filter { it.second >= 2 }
            .sortedByDescending { it.second }

        val un = ranked.firstOrNull { it.first.name == "unsupported" }
        val topOther = ranked.firstOrNull { it.first.name != "unsupported" }?.second ?: 0
        if (un != null && un.second >= topOther) {
            return listOfNotNull(ToolCatalog.byName("clarify"), ToolCatalog.byName("unsupported"))
        }

        val picked = LinkedHashMap<String, ToolCatalog.Tool>()
        for (name in ToolCatalog.core) ToolCatalog.byName(name)?.let { picked[it.name] = it }
        for ((t, _) in ranked) {
            if (picked.size >= limit + ToolCatalog.core.size) break
            picked[t.name] = t
        }
        if (picked.size == ToolCatalog.core.size) {
            for ((t, _) in ranked) {
                if (picked.size >= 4) break
                picked[t.name] = t
            }
        }
        return picked.values.toList()
    }

    private fun score(t: ToolCatalog.Tool, ws: Set<String>, padded: String): Int {
        var s = 0
        for (h in t.hints) {
            s += if (h.contains(" ")) {
                if (padded.contains(" $h ")) 3 else 0
            } else {
                if (h in ws) 3 else 0
            }
        }
        if (t.nameHints) for (part in t.name.split("_")) if (part.length > 2 && part in ws) s += 2
        return s
    }
}
