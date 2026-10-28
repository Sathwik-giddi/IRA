package com.voicepoco.app

import java.text.BreakIterator
import java.util.Locale

object SpeechStyle {

    private val detail = Regex(
        "\\b(detail|detailed|explain|explanation|elaborate|in depth|deep dive|step by step|steps|walk me through|guide|how to|full answer|long answer|longer answer|compare|comparison|pros and cons|examples?|teach me|tell me more|more about|vistar|savistar|pura|poora|samjhao|samjha do|khul ke|ache se|acc se|detail me|detail mein)\\b",
        RegexOption.IGNORE_CASE
    )

    private val short = Regex(
        "\\b(short|brief|one line|one sentence|two sentences|tldr|tl;dr|chhota|short me|jaldi batao)\\b",
        RegexOption.IGNORE_CASE
    )

    fun wantsDetail(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank() || short.containsMatchIn(t)) return false
        return detail.containsMatchIn(t)
    }

    fun fit(text: String, detailed: Boolean): String {
        var t = text.trim()
        if (t.isBlank()) return t
        t = t.replace(Regex("(?i)^(sure|certainly|of course|absolutely|great question|good question|okay|ok|alright)[,.!:\\s-]+"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        if (detailed) {
            if (t.length <= 1400) return t
            return cutWords(t, 230)
        }
        val two = firstSentences(t, 2)
        return if (words(two) <= 38) two else cutWords(two, 38)
    }

    fun maxTokens(detailed: Boolean, short: Int): Int =
        if (detailed) maxOf(short, 700) else short

    fun rule(detailed: Boolean): String =
        if (detailed) {
            "The user asked for detail. Give a clear, complete answer in under 170 words. Do not pad or repeat yourself."
        } else {
            "The user did not ask for detail. Reply with only the answer in one or two short sentences, under 38 words. No background, list, caveat, or example unless it is the answer."
        }

    private fun firstSentences(text: String, max: Int): String {
        val it = BreakIterator.getSentenceInstance(Locale.US)
        it.setText(text)
        val start = it.first()
        var end = it.next()
        var count = 0
        while (end != BreakIterator.DONE && count < max) {
            count++
            if (count == max) return text.substring(start, end).trim()
            end = it.next()
        }
        return text
    }

    private fun cutWords(text: String, max: Int): String {
        val parts = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size <= max) return text
        var out = parts.take(max).joinToString(" ")
        out = out.trimEnd(' ', ',', ';', ':', '-', '–')
        if (!out.endsWith('.') && !out.endsWith('!') && !out.endsWith('?')) out += "."
        return out
    }

    private fun words(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotBlank() }
}
