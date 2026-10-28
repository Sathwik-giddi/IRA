package com.voicepoco.app

data class BrainResult(
    val tool: String,
    val args: Map<String, String> = emptyMap(),
    val speech: String? = null
)

interface Brain {
    fun decide(utterance: String): BrainResult
}

class RulesBrain : Brain {
    override fun decide(utterance: String): BrainResult {
        val c = CommandParser.parse(utterance)
        return BrainResult(c.tool, c.args)
    }
}
