package com.voicepoco.app

import android.content.Context

object LegalDocs {
    const val VERSION = "2026-10-28-v1"
    const val KEY_ACCEPTED_VERSION = "legal_accepted_version"
    const val KEY_ACCEPTED_AT = "legal_accepted_at"
    const val EXTRA_DOC = "legal_doc"

    data class Doc(
        val id: String,
        val title: String,
        val subtitle: String,
        val asset: String
    )

    val docs = listOf(
        Doc("privacy", "Privacy Policy", "How IRA handles data", "PRIVACY_POLICY.md"),
        Doc("terms", "Terms of Service", "Rules for using IRA", "TERMS_OF_SERVICE.md"),
        Doc("acceptable", "Acceptable Use", "What you must not do", "ACCEPTABLE_USE.md"),
        Doc("permissions", "Permissions and Data", "What each permission does", "PERMISSIONS_AND_DATA.md")
    )

    fun find(id: String?): Doc? = docs.firstOrNull { it.id == id }

    fun isAccepted(context: Context): Boolean =
        prefs(context).getString(KEY_ACCEPTED_VERSION, "") == VERSION

    fun accept(context: Context) {
        prefs(context).edit()
            .putString(KEY_ACCEPTED_VERSION, VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun html(context: Context, doc: Doc): String =
        documentHtml(context.assets.open("legal/${doc.asset}").bufferedReader().use { it.readText() })

    private fun prefs(context: Context) =
        context.getSharedPreferences(BubbleService.PREFS, Context.MODE_PRIVATE)

    private fun documentHtml(markdown: String): String {
        val body = markdownToHtml(markdown)
        return """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            :root { color-scheme: dark; }
            body {
                margin: 0;
                padding: 20px 20px 48px;
                background: #09100F;
                color: #ECF2EC;
                font: 15px/1.58 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                overflow-wrap: break-word;
            }
            h1, h2, h3, h4 { color: #83CBB9; line-height: 1.25; margin: 28px 0 10px; }
            h1 { font-size: 26px; margin-top: 0; }
            h2 { font-size: 20px; }
            h3 { font-size: 17px; }
            h4 { font-size: 15px; }
            p { margin: 0 0 12px; }
            ul, ol { margin: 0 0 14px; padding-left: 22px; }
            li { margin: 4px 0; }
            a { color: #83CBB9; }
            code {
                background: #131C19;
                border: 1px solid #2C3A35;
                border-radius: 5px;
                padding: 1px 4px;
                font-size: 13px;
            }
            hr { border: 0; border-top: 1px solid #2C3A35; margin: 22px 0; }
            table { width: 100%; border-collapse: collapse; margin: 12px 0 18px; font-size: 13px; }
            th, td {
                border: 1px solid #5C7268;
                padding: 8px;
                text-align: left;
                vertical-align: top;
                overflow-wrap: anywhere;
            }
            th { background: #131C19; color: #83CBB9; }
            tr:nth-child(even) td { background: #0E1714; }
            </style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }

    private fun markdownToHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").lines()
        val out = StringBuilder()
        var i = 0
        var listTag: String? = null

        fun closeList() {
            listTag?.let { out.append("</").append(it).append(">") }
            listTag = null
        }

        while (i < lines.size) {
            val line = lines[i].trimEnd()
            if (line.isBlank()) {
                closeList()
                i++
                continue
            }
            if (line.trim() == "---") {
                closeList()
                out.append("<hr/>")
                i++
                continue
            }
            if (line.trimStart().startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                closeList()
                val header = splitRow(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    rows.add(splitRow(lines[i]))
                    i++
                }
                out.append("<table><thead><tr>")
                header.forEach { out.append("<th>").append(inline(it)).append("</th>") }
                out.append("</tr></thead><tbody>")
                rows.forEach { row ->
                    out.append("<tr>")
                    row.forEach { out.append("<td>").append(inline(it)).append("</td>") }
                    out.append("</tr>")
                }
                out.append("</tbody></table>")
                continue
            }
            val heading = Regex("^(#{1,4})\\s+(.*)$").find(line)
            if (heading != null) {
                closeList()
                val level = heading.groupValues[1].length
                out.append("<h").append(level).append(">")
                    .append(inline(heading.groupValues[2]))
                    .append("</h").append(level).append(">")
                i++
                continue
            }
            val unordered = Regex("^\\s*[-*]\\s+(.*)$").find(line)
            val ordered = Regex("^\\s*\\d+\\.\\s+(.*)$").find(line)
            if (unordered != null || ordered != null) {
                val tag = if (unordered != null) "ul" else "ol"
                if (listTag != tag) {
                    closeList()
                    out.append("<").append(tag).append(">")
                    listTag = tag
                }
                val content = (unordered ?: ordered)!!.groupValues[1]
                out.append("<li>").append(inline(content)).append("</li>")
                i++
                continue
            }
            closeList()
            val para = StringBuilder(line.trim())
            i++
            while (i < lines.size && lines[i].isNotBlank() && !isBlockStart(lines, i)) {
                para.append(' ').append(lines[i].trim())
                i++
            }
            out.append("<p>").append(inline(para.toString())).append("</p>")
        }
        closeList()
        return out.toString()
    }

    private fun isBlockStart(lines: List<String>, index: Int): Boolean {
        val line = lines[index].trimStart()
        if (line.startsWith("#")) return true
        if (line == "---") return true
        if (Regex("^[-*]\\s+.*").matches(line)) return true
        if (Regex("^\\d+\\.\\s+.*").matches(line)) return true
        return line.startsWith("|") && index + 1 < lines.size && isTableSeparator(lines[index + 1])
    }

    private fun isTableSeparator(line: String): Boolean =
        line.trim().matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"))

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun inline(value: String): String {
        var text = escapeHtml(value)
        text = text.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        text = text.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        text = text.replace(Regex("\\[([^]]+)]\\((https?://[^)]+)\\)"), "<a href=\"$2\">$1</a>")
        return text
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
