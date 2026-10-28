package com.voicepoco.app

import java.util.Locale

object FollowUp {

    private val repeatPhrases = setOf(
        "what",
        "whats",
        "what what",
        "whats it",
        "whats that",
        "whats this",
        "what is it",
        "what is that",
        "what was that",
        "what did you say",
        "what do you mean",
        "huh",
        "pardon",
        "come again",
        "say again",
        "again",
        "repeat",
        "repeat please",
        "kya",
        "kya bola",
        "kya bola tha",
        "kya kaha",
        "kya kaha tha",
        "kya bole",
        "kya tha",
        "kya matlab",
        "kya keh rahe ho",
        "kya bol rahe ho",
        "phir se",
        "phir se bolo",
        "phir bolo",
        "dobara",
        "dobara bolo",
        "samajh nahi aaya",
        "samjha nahi",
        "matlab",
        "matlab kya"
    )

    fun isRepeat(raw: String): Boolean {
        val t = raw.lowercase(Locale.US)
            .replace("'", "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return t.isNotEmpty() && t in repeatPhrases
    }
}
