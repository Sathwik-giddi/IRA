package com.voicepoco.app

object FastLane {

    private val stateOn = setOf("on", "chalu", "chalao", "jalao", "enable", "start")
    private val stateOff = setOf("off", "band", "band karo", "bujha", "bujhao", "disable", "off karo")

    fun match(raw: String): BrainResult? {
        val t = raw.lowercase().trim().trim('.', '!', '?', ',')
        if (t.isBlank()) return null

        mode(t)?.let { return it }
        answer(t)?.let { return it }
        message(t)?.let { return it }
        call(t)?.let { return it }
        media(t)?.let { return it }
        open(t)?.let { return it }
        volume(t)?.let { return it }
        brightness(t)?.let { return it }
        torch(t)?.let { return it }
        radios(t)?.let { return it }
        timer(t)?.let { return it }
        alarm(t)?.let { return it }
        clock(t)?.let { return it }
        system(t)?.let { return it }
        return null
    }

    private fun mode(t: String): BrainResult? {
        if (t == "good night" || t == "goodnight") return BrainResult("set_mode", mapOf("name" to "sleep"))
        val m = Regex("^(?:switch to|start|turn on|enable)\\s+(.+?)\\s+mode$").find(t)
            ?: Regex("^(.+?)\\s+mode$").find(t)
        val name = m?.groupValues?.get(1)?.trim() ?: return null
        if (Modes.all.none { it == name || Modes.label(it).lowercase() == name }) return null
        return BrainResult("set_mode", mapOf("name" to name))
    }

    private fun answer(t: String): BrainResult? {
        if (t in setOf("answer", "answer the call", "pick up", "uthao", "haan uthao", "attend", "receive")) {
            return BrainResult("answer_call")
        }
        if (t in setOf("decline", "reject", "kaat do", "kaato", "cut the call", "nahi uthana", "decline call")) {
            return BrainResult("decline_call")
        }
        return null
    }

    private fun message(t: String): BrainResult? {
        val app = if (t.contains("whatsapp")) "whatsapp" else ""
        Regex("^(.+?)\\s+ko\\s+(?:whatsapp|sms|message|msg|text)\\s+(?:bhejo|bhej do|bhej|karo|kar do)\\s*(.*)$")
            .find(t)?.let {
                val contact = cleanContact(it.groupValues[1])
                val body = it.groupValues[2].trim()
                if (contact.isNotBlank() && body.isNotBlank())
                    return BrainResult("send_message", mapOf("contact" to contact, "text" to body, "app" to app))
            }
        Regex("^(?:send|text|message)\\s+(?:a\\s+)?(?:message\\s+)?(?:to\\s+)?(.+?)\\s+(?:saying|that)\\s+(.+)$")
            .find(t)?.let {
                return BrainResult("send_message", mapOf("contact" to cleanContact(it.groupValues[1]), "text" to it.groupValues[2].trim(), "app" to app))
            }
        Regex("^whatsapp\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$").find(t)?.let {
            return BrainResult("send_message", mapOf("contact" to cleanContact(it.groupValues[1]), "text" to it.groupValues[2].trim(), "app" to "whatsapp"))
        }
        return null
    }

    private fun call(t: String): BrainResult? {
        Regex("^(.+?)\\s+ko\\s+(?:call|phone)\\s*(?:karo|kar do|lagao|lagado|milao)?$").find(t)?.let {
            val c = cleanContact(it.groupValues[1])
            if (c.isNotBlank()) return BrainResult("call_contact", mapOf("contact" to c))
        }
        Regex("^(?:call|dial|phone)\\s+(?:to\\s+)?(.+)$").find(t)?.let {
            val c = cleanContact(it.groupValues[1])
            if (c.isNotBlank()) return BrainResult("call_contact", mapOf("contact" to c))
        }
        return null
    }

    private fun media(t: String): BrainResult? {
        if (t in setOf("next", "next song", "next track", "agla", "agla gaana", "agla song")) {
            return BrainResult("media_control", mapOf("action" to "next"))
        }
        if (t in setOf("previous", "previous song", "pichla", "pichla gaana", "pichla song")) {
            return BrainResult("media_control", mapOf("action" to "previous"))
        }
        if (t in setOf("pause", "pause music", "roko", "gaana roko", "music pause", "stop music")) {
            return BrainResult("media_control", mapOf("action" to "pause"))
        }
        if (t in setOf("play", "play music", "resume", "chalao", "gaana chalao")) {
            return BrainResult("media_control", mapOf("action" to "play"))
        }
        Regex("^(?:play|bajao)\\s+(.+)$").find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.isNotBlank()) return BrainResult("play_music", mapOf("query" to q))
        }
        return null
    }

    private fun open(t: String): BrainResult? {
        Regex("^(?:open|launch|start|kholo|khol do|khol)\\s+(?:the\\s+)?(.+)$").find(t)?.let {
            val app = stripApp(it.groupValues[1])
            if (app.isNotBlank()) return BrainResult("open_app", mapOf("app" to app))
        }
        Regex("^(.+?)\\s+(?:kholo|khol do|khol|open karo|open)$").find(t)?.let {
            val app = stripApp(it.groupValues[1])
            if (app.isNotBlank()) return BrainResult("open_app", mapOf("app" to app))
        }
        return null
    }

    private fun volume(t: String): BrainResult? {
        if (!Regex("\\b(volume|awaaz|awaz|sound)\\b").containsMatchIn(t)) return null
        return when {
            Regex("\\b(up|badhao|badha|zyada|louder|increase|full)\\b").containsMatchIn(t) ->
                BrainResult("set_volume", mapOf("level" to "up"))
            Regex("\\b(down|kam|ghatao|ghata|decrease|quieter|low)\\b").containsMatchIn(t) ->
                BrainResult("set_volume", mapOf("level" to "down"))
            else -> null
        }
    }

    private fun brightness(t: String): BrainResult? {
        if (!Regex("\\b(brightness|roshni|screen light)\\b").containsMatchIn(t)) return null
        return when {
            Regex("\\b(up|badhao|badha|zyada|brighter|increase|full)\\b").containsMatchIn(t) ->
                BrainResult("set_brightness", mapOf("level" to "up"))
            Regex("\\b(down|kam|ghatao|ghata|dimmer|decrease|low)\\b").containsMatchIn(t) ->
                BrainResult("set_brightness", mapOf("level" to "down"))
            else -> null
        }
    }

    private fun torch(t: String): BrainResult? {
        if (!Regex("\\b(torch|flashlight|flash)\\b").containsMatchIn(t)) return null
        val off = Regex("\\b(off|band|bujha|bujhao|disable)\\b").containsMatchIn(t)
        val on = Regex("\\b(on|chalu|chalao|jalao|enable)\\b").containsMatchIn(t)
        return when {
            off -> BrainResult("set_flashlight", mapOf("state" to "off"))
            on -> BrainResult("set_flashlight", mapOf("state" to "on"))
            else -> null
        }
    }

    private fun radios(t: String): BrainResult? {
        val off = Regex("\\b(off|band|band karo|disable)\\b").containsMatchIn(t)
        val on = Regex("\\b(on|chalu|chalao|enable)\\b").containsMatchIn(t)
        if (!off && !on) return null
        val state = if (off) "off" else "on"
        if (Regex("\\b(wifi|wi-fi)\\b").containsMatchIn(t)) return BrainResult("set_wifi", mapOf("state" to state))
        if (Regex("\\b(bluetooth|bt)\\b").containsMatchIn(t)) return BrainResult("set_bluetooth", mapOf("state" to state))
        if (Regex("\\b(mobile data|data|4g|5g)\\b").containsMatchIn(t)) return BrainResult("set_data", mapOf("state" to state))
        if (Regex("\\b(airplane|aeroplane|flight mode)\\b").containsMatchIn(t)) return BrainResult("set_airplane", mapOf("state" to state))
        return null
    }

    private fun timer(t: String): BrainResult? {
        val m = Regex("^(?:set\\s+)?(?:a\\s+)?timer\\s+(?:for\\s+)?(.+)$").find(t) ?: return null
        val secs = seconds(m.groupValues[1]) ?: return null
        return BrainResult("set_timer", mapOf("duration" to secs.toString()))
    }

    private fun alarm(t: String): BrainResult? {
        val m = Regex("^(?:set\\s+)?(?:an?\\s+)?alarm\\s+(?:for\\s+|at\\s+)?(.+)$").find(t) ?: return null
        val raw = m.groupValues[1]
        val clock = Regex("(\\d{1,2})[:.](\\d{2})").find(raw)
        if (clock != null) {
            val h = clock.groupValues[1].toIntOrNull() ?: return null
            val mm = clock.groupValues[2].toIntOrNull() ?: return null
            return BrainResult("set_alarm", mapOf("time" to "%02d:%02d".format(h, mm)))
        }
        val hour = Regex("(\\d{1,2})").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return BrainResult("set_alarm", mapOf("time" to "%02d:00".format(hour)))
    }

    private fun clock(t: String): BrainResult? {
        if (Regex("\\b(what(?:'s| is)? the time|what time is it|current time|time now|time kya|time batao|kitne baje|abhi kitne baje|samay kya|samay batao)\\b").containsMatchIn(t)) {
            return BrainResult("tell_time")
        }
        if (Regex("\\b(what(?:'s| is)? the date|what date is it|what day is it|today'?s date|aaj ki tareekh|aaj kya tareekh|aaj kya din|aaj kaun sa din|date kya|date batao)\\b").containsMatchIn(t)) {
            return BrainResult("tell_date")
        }
        return null
    }

    private fun system(t: String): BrainResult? {
        return when {
            t == "go back" || t == "back" || t == "wapas" || t == "peeche" || t == "piche" ->
                BrainResult("back")
            t == "go home" || t == "home" || t == "ghar" || t == "home screen" ->
                BrainResult("home")
            Regex("^scroll\\s+(up|down)$").matches(t) ->
                BrainResult("scroll", mapOf("direction" to t.substringAfter(' ')))
            t == "scroll" || t == "niche" || t == "neeche" || t == "scroll down" ->
                BrainResult("scroll", mapOf("direction" to "down"))
            t == "scroll up" || t == "upar" || t == "up" ->
                BrainResult("scroll", mapOf("direction" to "up"))
            t.contains("battery") || t == "kitni battery" || t.contains("charge kitna") ->
                BrainResult("battery_status")
            t.contains("screenshot") || t.contains("screen shot") ->
                BrainResult("take_screenshot")
            t == "read screen" || t == "read the screen" || t == "screen padho" || t == "screen par kya hai" ->
                BrainResult("read_screen")
            else -> null
        }
    }

    private fun cleanContact(s: String): String =
        s.trim().removePrefix("to ").removeSuffix(" ko").removePrefix("meri ").removePrefix("mere ").trim()

    private fun stripApp(s: String): String =
        s.trim().removeSuffix(" app").removeSuffix(" application").removePrefix("app ").trim()

    private fun seconds(s: String): Int? {
        val n = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: return null
        val l = s.lowercase()
        return when {
            l.contains("hour") || l.contains("ghanta") -> n * 3600
            l.contains("min") || l.contains("minute") -> n * 60
            else -> n
        }
    }
}
