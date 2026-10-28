package com.voicepoco.app

object SafetyGuard {

    private val patterns = listOf(
        "ignore previous", "ignore all previous", "ignore the above", "system prompt",
        "you are now", "admin mode", "root access", "jailbreak",
        "bina puche", "sab contacts", "everyone", "all my photos",
        "send all", "delete everything", "sab kuch delete", "sara data",
        "factory reset", "wipe", "play protect", "find my device", "developer options",
        "password", "passwords", "otp", "chupke", "bina batae", "silently"
    )

    fun refuse(utterance: String): Boolean {
        val padded = " " + utterance.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        return patterns.any { p -> if (p.contains(" ")) padded.contains(" $p ") else Regex("(^| )" + Regex.escape(p) + "( |$)").containsMatchIn(padded) }
    }
}
