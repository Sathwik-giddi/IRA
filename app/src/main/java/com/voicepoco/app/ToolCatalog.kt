package com.voicepoco.app

object ToolCatalog {

    data class Param(val name: String, val description: String, val allowed: List<String> = emptyList())

    data class Tool(
        val name: String,
        val description: String,
        val params: List<Param> = emptyList(),
        val destructive: Boolean = false,
        val hints: List<String> = emptyList(),
        val nameHints: Boolean = true
    )

    val core: List<String> = listOf("clarify")

    val tools: List<Tool> = listOf(
        Tool("open_app", "Open app", listOf(Param("app", "app name")), hints = listOf("open","kholo","khol","kholna","launch","start","run")),
        Tool("tap", "Tap on screen", listOf(Param("label", "label")), hints = listOf("tap","click","dabao","dab","press","touch","select")),
        Tool("type", "Type text", listOf(Param("text", "text")), hints = listOf("type","likho","likh","enter","search","query","field")),
        Tool("scroll", "Scroll screen", listOf(Param("direction", "up or down", listOf("up","down"))), hints = listOf("scroll","niche","neeche","upar","up","down","page","feed")),
        Tool("back", "Go back", hints = listOf("back","wapas","peeche","piche","return")),
        Tool("home", "Go home", hints = listOf("home","ghar","desktop","minimize","main screen")),
        Tool("set_wifi", "Wi-Fi on or off. band means off", listOf(Param("state", "on enables, off disables", listOf("on","off"))), hints = listOf("wifi","wi-fi","internet","network")),
        Tool("set_bluetooth", "Bluetooth on or off. band means off", listOf(Param("state", "on enables, off disables", listOf("on","off"))), hints = listOf("bluetooth","bt","earphone","headphone")),
        Tool("set_brightness", "Brightness up or down. kam means down", listOf(Param("level", "up for brighter, down for dimmer", listOf("up","down"))), hints = listOf("brightness","bright","dim","roshni","screen light")),
        Tool("set_volume", "Volume up or down. kam means down", listOf(Param("level", "up for louder, down for quieter", listOf("up","down"))), hints = listOf("volume","awaaz","awaz","sound","loud","mute","silent")),
        Tool("set_data", "Mobile data on or off. band means off", listOf(Param("state", "on enables, off disables", listOf("on","off"))), destructive = true, hints = listOf("mobile data","internet","4g","5g")),
        Tool("post_social", "Post content on a social app", listOf(Param("app", "app name")), destructive = true, hints = listOf("post","tweet","publish")),
        Tool("set_dnd", "Do not disturb on or off. band means off", listOf(Param("state", "on enables, off disables", listOf("on","off"))), hints = listOf("dnd","do not disturb","disturb","notifications","silent mode")),
        Tool("set_alarm", "Set alarm", listOf(Param("time", "24h HH:MM, 7 baje is 07:00")), hints = listOf("alarm","wake","uthana","uthao","subah","bajao")),
        Tool("call_contact", "Call contact", listOf(Param("contact", "contact name")), hints = listOf("call","dial","milao","baat")),
        Tool("send_message", "Text a contact", listOf(Param("contact", "contact name"), Param("text", "message"), Param("app", "app name")), hints = listOf("message","msg","sms","bhej","bhejo","text","whatsapp")),
        Tool("delete_files", "Delete files", listOf(Param("target", "target")), destructive = true, hints = listOf("delete","remove","hatao","cleanup","free storage")),
        Tool("uninstall_app", "Uninstall app", listOf(Param("app", "app name")), destructive = true, hints = listOf("uninstall","remove app","hata do")),
        Tool("pay_money", "Send money", listOf(Param("amount", "amount"), Param("to", "recipient")), destructive = true, hints = listOf("pay","paise","paisa","transfer","upi","send money")),
        Tool("share_location", "Share location", listOf(Param("contact", "contact name")), destructive = true, hints = listOf("location","jagah","share location","where am i")),
        Tool("set_timer", "Start a countdown timer", listOf(Param("duration", "seconds as a number, 5 minutes is 300")), hints = listOf("timer","countdown","stopwatch"), nameHints = false),
        Tool("add_event", "Add a calendar event or reminder", listOf(Param("title", "event title"), Param("time", "24h HH:MM if said")), hints = listOf("add event","calendar event","set reminder","remind me","yaad dilao"), nameHints = false),
        Tool("set_flashlight", "Flashlight on or off", listOf(Param("state", "on or off", listOf("on","off"))), hints = listOf("flashlight","torch","flash"), nameHints = false),
        Tool("set_hotspot", "Open hotspot settings", hints = listOf("hotspot","tethering"), nameHints = false),
        Tool("set_airplane", "Open airplane mode settings", hints = listOf("airplane","aeroplane","flight mode"), nameHints = false),
        Tool("set_dark_mode", "Open display settings for dark mode", hints = listOf("dark mode","dark theme","night mode"), nameHints = false),
        Tool("set_screen_timeout", "Set screen timeout", listOf(Param("seconds", "seconds as a number")), hints = listOf("screen timeout","screen off","sleep time"), nameHints = false),
        Tool("set_battery_saver", "Open battery saver settings", hints = listOf("battery saver","power saving"), nameHints = false),
        Tool("set_ringer", "Ringer mode", listOf(Param("mode", "silent, vibrate or normal", listOf("silent","vibrate","normal"))), hints = listOf("ringer","vibrate","vibration"), nameHints = false),
        Tool("open_settings", "Open a settings page", listOf(Param("page", "settings page", listOf("location","nfc","display","sound","storage","apps","accessibility","language","date"))), hints = listOf("gps","nfc","font size","location settings","storage settings","language settings"), nameHints = false),
        Tool("take_screenshot", "Take a screenshot", hints = listOf("screenshot","screen capture"), nameHints = false),
        Tool("media_control", "Control media playback", listOf(Param("action", "play, pause, next or previous", listOf("play","pause","next","previous"))), hints = listOf("pause","next song","previous song","song","music","gaana"), nameHints = false),
        Tool("read_notifications", "Read recent notifications", hints = listOf("read notifications","my notifications","unread","notifications padho","notifications padh","notifications sunao","meri notifications"), nameHints = false),
        Tool("battery_status", "Tell battery level", hints = listOf("battery level","battery percentage","kitni battery","battery kitni"), nameHints = false),
        Tool("tell_time", "Tell the current time", hints = listOf("what time is it","what's the time","what is the time","current time","time now","time kya","time batao","kitne baje","samay kya","samay batao"), nameHints = false),
        Tool("tell_date", "Tell today's date and weekday", hints = listOf("what date is it","what's the date","what day is it","today's date","aaj ki tareekh","aaj kya tareekh","aaj kya din","aaj kaun sa din","date kya","date batao"), nameHints = false),
        Tool("web_search", "Search the web in a browser", listOf(Param("query", "search text")), hints = listOf("dhundo","search for","googling","google pe"), nameHints = false),
        Tool("run_routine", "Run a routine of several settings for a situation", listOf(Param("name", "sleep, driving, meeting, outdoors or battery_low", listOf("sleep","driving","meeting","outdoors","battery_low")), Param("time", "HH:MM only if said")), hints = listOf("sone ja raha","going to sleep","good night","goodnight","so raha","so rahi","driving","gaadi chala","meeting mein","meeting ke liye","for a meeting","outdoors","dhoop","battery dying","battery is dying","marne wali","is dying"), nameHints = false),
        Tool("remember", "Remember a personal note or preference", listOf(Param("note", "what to remember")), hints = listOf("remember that","remember my","yaad rakhna","yaad rakho","note kar"), nameHints = false),
        Tool("recall", "Tell the remembered notes", hints = listOf("what do you remember","kya yaad hai","my notes","meri notes","notes batao","recall"), nameHints = false),
        Tool("read_screen", "Read out what is on the screen", hints = listOf("read screen","read the screen","screen par kya","what is on my screen","screen padho"), nameHints = false),
        Tool("answer_call", "Answer the ringing call", hints = listOf("answer call","answer the call","pick up","uthao","attend call"), nameHints = false),
        Tool("decline_call", "Decline the ringing call", hints = listOf("decline call","reject call","kaat do","cut the call","nahi uthana"), nameHints = false),
        Tool("play_music", "Open a music app and play", listOf(Param("query", "song or app")), hints = listOf("play song","play music","gaana bajao","music chalao"), nameHints = false),
        Tool("next_reel", "Scroll to the next reel", hints = listOf("next reel","agla reel","next video","agla video"), nameHints = false),
        Tool("set_mode", "Switch the assistant mode", listOf(Param("name", "mode", listOf("conversation","reels","reading","hands_on","driving","focus","sleep","quiet"))), hints = listOf("driving mode","sleep mode","focus mode","reels mode","reading mode","hands on mode","quiet mode","conversation mode","good night")),
        Tool("clarify", "Ask short question when unclear", listOf(Param("question", "question")), hints = listOf("unclear","what","kya")),
        Tool("refuse", "Refuse unsafe or impossible request", listOf(Param("reason", "short reason")), hints = listOf("unsafe","illegal","private","hack","bypass","password","passwords","chupke","otp","secret","spy","bina batae","factory","play protect","find my device","screen lock","system prompt","previous instructions","admin","root","forward","developer options","silently","sabko","sab contacts","all contacts","wipe","security settings","all my photos","send all","permission")),
        Tool("unsupported", "Not doable on this phone, e.g. weather, email, cab, food order, bill, AC, web, recharge", listOf(Param("reason", "short reason")), hints = listOf("weather","mausam","recharge","news","web","online","email","cab","taxi","biriyani","order","bill","balance","air conditioner","ac","fan","heater","earphones","flight book","flight ticket","book flight","train","hotel","pizza","movie","book")),
        Tool("unknown", "Request is unclear or incomplete", listOf(Param("reason", "short reason")), hints = listOf("unclear","incomplete","gibberish"))
    )

    val destructiveTools: Set<String> = tools.filter { it.destructive }.map { it.name }.toSet()

    fun byName(name: String): Tool? = tools.firstOrNull { it.name == name }
}
