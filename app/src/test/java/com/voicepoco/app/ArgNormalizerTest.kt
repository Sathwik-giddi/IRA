package com.voicepoco.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgNormalizerTest {

    @Test
    fun bandMeansOff() {
        val tool = ToolCatalog.byName("set_wifi")!!
        val out = ArgNormalizer.normalize(tool, mapOf("state" to "on"), "wifi band karo")
        assertEquals("off", out["state"])
    }

    @Test
    fun chaluMeansOn() {
        val tool = ToolCatalog.byName("set_wifi")!!
        val out = ArgNormalizer.normalize(tool, mapOf("state" to "off"), "wifi chalu karo")
        assertEquals("on", out["state"])
    }

    @Test
    fun badhaoMeansUp() {
        val tool = ToolCatalog.byName("set_volume")!!
        val out = ArgNormalizer.normalize(tool, mapOf("level" to "down"), "volume badhao")
        assertEquals("up", out["level"])
    }

    @Test
    fun convertsMinutesToSeconds() {
        val tool = ToolCatalog.byName("set_timer")!!
        val out = ArgNormalizer.normalize(tool, mapOf("duration" to "5 minute"), "5 minute ka timer lagao")
        assertEquals("300", out["duration"])
    }
}
