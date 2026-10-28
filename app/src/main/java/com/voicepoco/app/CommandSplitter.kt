package com.voicepoco.app

object CommandSplitter {

    private val sep = Regex("\\s+(?:and|aur|then|phir)\\s+", RegexOption.IGNORE_CASE)
    private val fillers = setOf("please", "pls", "zara", "thoda")

    fun parts(text: String): List<String> = text.split(sep).map { it.trim() }.filter { it.isNotEmpty() }

    fun smart(text: String): List<String> {
        val raw = parts(text)
        if (raw.size < 2 || raw.size > 3) return listOf(text)
        val usable = raw.filterNot { it.lowercase().trim() in fillers }
        if (usable.size < 2) return listOf(text)
        val ok = usable.all { p ->
            val fast = FastLane.match(p)
            if (fast != null) fast.tool !in ToolCatalog.destructiveTools
            else {
                val r = ToolRouter.route(p, 2).firstOrNull { it.name !in ToolCatalog.core }
                r != null
            }
        }
        return if (ok) usable else listOf(text)
    }
}
