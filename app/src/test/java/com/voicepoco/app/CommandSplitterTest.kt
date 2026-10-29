package com.voicepoco.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandSplitterTest {

    @Test
    fun splitsTwoCommands() {
        assertEquals(listOf("open maps", "go home"), CommandSplitter.parts("open maps and go home"))
    }

    @Test
    fun keepsLongRequestsTogether() {
        val text = "open maps and go home and open camera and open settings"
        assertEquals(listOf(text), CommandSplitter.smart(text))
    }

    @Test
    fun smartSplitsKnownMediaCommands() {
        assertEquals(listOf("next song", "pause"), CommandSplitter.smart("next song and pause"))
    }
}
