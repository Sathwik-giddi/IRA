package com.voicepoco.app

object ConfirmPolicy {

    enum class Verdict { ALLOW, CONFIRM, REFUSE }

    fun verdict(r: BrainResult): Verdict = when {
        r.tool == "clarify" -> Verdict.ALLOW
        r.tool in setOf("refuse", "unsupported", "unknown") -> Verdict.REFUSE
        r.tool in ToolCatalog.destructiveTools -> Verdict.CONFIRM
        else -> Verdict.ALLOW
    }

    fun isYes(t: String): Boolean {
        val w = t.lowercase().trim()
        return listOf("haan", "han", "yes", "yeah", "ok", "okay", "confirm", "sure", "karo", "kar do", "bilkul", "theek")
            .any { w == it || w.startsWith("$it ") }
    }

    fun isNo(t: String): Boolean {
        val w = t.lowercase().trim()
        return listOf("nahi", "nahin", "no", "nope", "cancel", "mat karo", "band")
            .any { w == it || w.startsWith("$it ") }
    }

    fun isDone(t: String): Boolean {
        val w = t.lowercase().trim()
        return listOf("thanks", "thank you", "thankyou", "shukriya", "dhanyavaad", "bas", "that's all", "thats all", "stop", "bye", "ok thanks", "nothing")
            .any { w == it || w.startsWith("$it ") }
    }

    fun question(r: BrainResult): String = when (r.tool) {
        "call_contact" -> "Call " + (r.args["contact"] ?: "that contact") + "?"
        "send_message" -> "Send to " + (r.args["contact"] ?: "that contact") + "?"
        "uninstall_app" -> "Uninstall " + (r.args["app"] ?: "that app") + "?"
        "set_data" -> "Turn mobile data " + (r.args["state"] ?: "off") + "?"
        "post_social" -> "Post on " + (r.args["app"] ?: "that app") + "?"
        "pay_money" -> r.args["reason"] ?: "Pay " + (r.args["amount"] ?: "") + "? This cannot be undone."
        "delete_files" -> "This deletes files and cannot be undone. Continue?"
        "share_location" -> "Share your location with " + (r.args["contact"] ?: "that contact") + "?"
        else -> r.args["reason"] ?: "Confirm?"
    }
}
