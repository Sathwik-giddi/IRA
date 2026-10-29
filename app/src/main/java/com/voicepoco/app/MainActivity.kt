package com.voicepoco.app

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.view.View
import android.widget.TextView
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var orb: TextView
    private lateinit var headline: TextView
    private lateinit var kicker: TextView
    private lateinit var rows: LinearLayout
    private lateinit var orbHint: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var modeHint: TextView
    private lateinit var freezeButton: TextView
    private lateinit var freezeHint: TextView
    private var legalDialog: Dialog? = null
    private val ui = Handler(Looper.getMainLooper())
    private val hideHint = Runnable { if (::modeHint.isInitialized) modeHint.visibility = View.GONE }

    private val pad by lazy { Ui.dp(this, 24) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = Ui.INK
        window.navigationBarColor = Ui.INK
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
        if (!LegalDocs.isAccepted(this)) {
            stopService(Intent(this, BubbleService::class.java))
        }
        if (!AppState.isFrozen(this) && LegalDocs.isAccepted(this)) {
            intent?.getStringExtra("tool")?.let { t ->
                val i = Intent(this, BubbleService::class.java).apply {
                    putExtra("tool", t)
                    for (k in listOf("contact", "text", "app", "dry", "name", "state", "level", "action", "query")) intent.getStringExtra(k)?.let { putExtra(k, it) }
                }
                try { startForegroundService(i) } catch (_: Exception) {}
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.INK)
            setPadding(pad, pad, pad, pad)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(Ui.text(this, "IRA", 18f, Ui.BONE, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        kicker = Ui.text(this, "OFFLINE", 12f, Ui.MUTED, true)
        top.addView(kicker)
        root.addView(top)

        headline = Ui.text(this, "", 30f, Ui.BONE, true).apply {
            setPadding(0, Ui.dp(this@MainActivity, 28), 0, 0)
            setLineSpacing(Ui.dp(this@MainActivity, 3).toFloat(), 1f)
        }
        root.addView(headline)

        val orbWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, Ui.dp(this@MainActivity, 28), 0, Ui.dp(this@MainActivity, 28))
        }
        orb = Ui.text(this, "ira", 22f, Ui.BONE, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Start or stop listening"
            background = Ui.pressable(this@MainActivity, this, Ui.withAlpha(Ui.ACCENT, 0x33)).background
            setOnClickListener { toggleVoice() }
        }
        orbWrap.addView(orb, LinearLayout.LayoutParams(Ui.dp(this@MainActivity, 132), Ui.dp(this@MainActivity, 132)))
        orbHint = Ui.text(this, "", 12f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 14), 0, 0)
        }
        orbWrap.addView(orbHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(orbWrap)

        freezeButton = Ui.text(this, "Freeze IRA", 15f, Ui.BONE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 15), 0, Ui.dp(this@MainActivity, 15))
            background = Ui.rounded(Ui.DANGER, Ui.dp(this@MainActivity, 24))
            isClickable = true
            isFocusable = true
            contentDescription = "Freeze IRA and stop all services"
            setOnClickListener { toggleFreeze() }
        }
        root.addView(freezeButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@MainActivity, 4) })
        freezeHint = Ui.text(this, "", 12f, Ui.MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
        }
        root.addView(freezeHint)

        root.addView(Ui.text(this, "Mode", 12f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 8))
        })
        modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val modeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(modeRow)
        }
        root.addView(modeScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(View(this), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 10)
        ))
        modeHint = Ui.text(this, "", 12f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 10), 0, 0)
            visibility = View.GONE
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        }
        root.addView(modeHint)

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rows)

        root.addView(Ui.text(this, "Phone commands run here. Chat can use the web.", 12f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 20), 0, 0)
        })

        root.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            if (!LegalDocs.isAccepted(this)) showLegalConsent() else maybeShowGuide()
        }, 350)

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(root)
        }
        setContentView(scroll)
    }

    private fun toggleVoice() {
        if (!LegalDocs.isAccepted(this)) {
            showLegalConsent()
            return
        }
        if (AppState.isFrozen(this)) {
            Toast.makeText(this, "IRA is frozen. Tap Resume first.", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG
                ),
                1
            )
            return
        }
        val on = prefs.getBoolean("wake_requested", false)
        if (on) {
            stopService(Intent(this, BubbleService::class.java))
            prefs.edit().putBoolean("wake_requested", false).apply()
        } else {
            prefs.edit().putBoolean("wake_requested", true).apply()
            startWantedService()
        }
        refresh()
    }

    private fun startWantedService() {
        if (!LegalDocs.isAccepted(this)) return
        if (AppState.isFrozen(this)) return
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("wake_requested", false)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
            return
        }
        val i = Intent(this, BubbleService::class.java)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
    }

    private fun toggleFreeze() {
        if (AppState.isFrozen(this)) {
            AppState.setFrozen(this, false)
            startWantedService()
            Toast.makeText(this, "IRA resumed", Toast.LENGTH_SHORT).show()
        } else {
            AppState.setFrozen(this, true)
            stopService(Intent(this, BubbleService::class.java))
            Toast.makeText(this, "IRA frozen", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    private fun askOverlay() {
        if (!Settings.canDrawOverlays(this)) startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun askScreen() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun askSms() {
        requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS), 3)
    }

    private fun askCalls() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG
            ), 4
        )
    }

    private fun toggleAuto() {
        val p = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        p.edit().putBoolean(BubbleService.KEY_AUTO_ANSWER, !p.getBoolean(BubbleService.KEY_AUTO_ANSWER, false)).apply()
        refresh()
    }

    private fun chip(label: String, active: Boolean, onClick: () -> Unit): TextView {
        return Ui.text(this, label, 13f, if (active) Ui.INK else Ui.BONE, active).apply {
            setPadding(Ui.dp(this@MainActivity, 13), Ui.dp(this@MainActivity, 8),
                Ui.dp(this@MainActivity, 13), Ui.dp(this@MainActivity, 8))
            background = Ui.rounded(
                if (active) Ui.ACCENT else Ui.SURFACE,
                Ui.dp(this@MainActivity, 20),
                if (active) 0 else Ui.dp(this@MainActivity, 1),
                Ui.BORDER
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun toggleAct() {
        val p = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        p.edit().putBoolean(BubbleService.KEY_ACT, !p.getBoolean(BubbleService.KEY_ACT, BubbleService.PREF_ACT_DEFAULT)).apply()
        refresh()
    }

    private fun toggleModel() {
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        val small = ModelCatalog.installed(this, ModelCatalog.small)
        val big = ModelCatalog.installed(this, ModelCatalog.big)
        if (!small || !big) {
            Toast.makeText(this,
                "Install both GGUF models to switch. Run scripts/fetch-models.sh, then reinstall.",
                Toast.LENGTH_LONG).show()
            return
        }
        val next = !prefs.getBoolean(BubbleService.KEY_BIG_MODEL, false)
        prefs.edit().putBoolean(BubbleService.KEY_BIG_MODEL, next).apply()
        Toast.makeText(this,
            if (next) "Qwen 1.5B selected. Restart IRA to apply." else "FunctionGemma 270M selected. Restart IRA to apply.",
            Toast.LENGTH_SHORT).show()
        refresh()
    }

    override fun onStart() {
        super.onStart()
        if (LegalDocs.isAccepted(this)) startWantedService()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        legalDialog?.dismiss()
        legalDialog = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startWantedService()
        }
        refresh()
    }

    private fun refresh() {
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val sms = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val frozen = AppState.isFrozen(this)
        val a11y = AssistantService.instance != null
        val listening = !frozen && prefs.getBoolean("wake_requested", false)
        val act = prefs.getBoolean(BubbleService.KEY_ACT, BubbleService.PREF_ACT_DEFAULT)
        val calls = checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val auto = prefs.getBoolean(BubbleService.KEY_AUTO_ANSWER, false)
        val cloudOn = prefs.getBoolean(BubbleService.KEY_CLOUD, true)
        val cloudLive = cloudOn && CloudBrain.isOnline(this)
        val smallModel = ModelCatalog.installed(this, ModelCatalog.small)
        val bigModel = ModelCatalog.installed(this, ModelCatalog.big)
        val activeBig = bigModel && (prefs.getBoolean(BubbleService.KEY_BIG_MODEL, false) || !smallModel)
        val modelInstalled = smallModel || bigModel
        val recommendedModel = DeviceProfile.recommendedModel(this)
        val mode = Modes.current(this)

        kicker.text = when {
            frozen -> "PAUSED"
            listening -> "LISTENING"
            cloudLive -> "ONLINE"
            else -> "OFFLINE"
        }
        kicker.setTextColor(if (frozen) Ui.WARN else if (listening || cloudLive) Ui.ACCENT else Ui.MUTED)

        headline.text = when {
            frozen -> "IRA is frozen."
            !mic -> "Give it a voice."
            listening -> "Say ira, then just talk."
            cloudLive -> "Ask me anything."
            else -> "Ready when you are."
        }
        orb.text = when {
            frozen -> "off"
            mic -> "ira"
            else -> "allow"
        }
        orbHint.text = when {
            frozen -> "Tap Resume below"
            !mic -> "Needs the microphone"
            listening -> "Tap to stop"
            else -> "Tap to start"
        }
        orb.contentDescription = when {
            frozen -> "IRA is frozen"
            listening -> "Stop listening"
            else -> "Start listening"
        }
        val r = Ui.dp(this, 66)
        orb.background = Ui.rounded(
            Ui.SURFACE,
            r,
            if (listening) Ui.dp(this, 3) else Ui.dp(this, 2),
            when {
                frozen -> Ui.WARN
                listening -> Ui.ACCENT
                else -> Ui.BORDER
            }
        )
        orb.setTextColor(if (mic && !frozen) Ui.BONE else Ui.MUTED)
        orb.isEnabled = !frozen
        orb.alpha = if (frozen) 0.45f else 1f

        freezeButton.text = if (frozen) "Resume IRA" else "Freeze IRA"
        freezeButton.contentDescription = if (frozen) "Resume IRA and restart services" else "Freeze IRA and stop all services"
        freezeButton.setTextColor(if (frozen) Ui.INK else Ui.BONE)
        freezeButton.background = Ui.rounded(if (frozen) Ui.WARN else Ui.DANGER, Ui.dp(this, 24))
        freezeHint.text = if (frozen) {
            "Frozen. Nothing is listening or acting."
        } else {
            "Stops the wake word, bubble, screen actions and notification reading."
        }

        modeRow.removeAllViews()
        for (m in Modes.all) {
            val c = chip(Modes.label(m), m == mode) { applyMode(m) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = Ui.dp(this, 8)
            modeRow.addView(c, lp)
        }

        rows.removeAllViews()
        rows.addView(Ui.row(this, "Microphone",
            when {
                frozen -> "Paused with IRA"
                mic -> "Wake word and commands"
                else -> "Needed before anything else"
            },
            when {
                frozen -> "Paused"
                mic -> "On"
                else -> "Allow"
            },
            mic && !frozen, true) { toggleVoice() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Screen control",
            when {
                frozen -> "Frozen with IRA"
                a11y -> "Tap, scroll, type, WhatsApp"
                else -> "Taps and scrolling need this"
            },
            when {
                frozen -> "Paused"
                a11y -> "On"
                else -> "Enable"
            },
            a11y && !frozen, true) { if (!frozen && !a11y) askScreen() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Calls",
            if (calls) "Announce, answer, decline" else "Voice calls need call access",
            if (calls) "On" else "Allow",
            calls, true) { if (!calls) askCalls() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Messages",
            if (sms && contacts) "Silent SMS, contacts" else "SMS and contact lookup",
            if (sms && contacts) "On" else "Allow",
            sms && contacts, true) { if (!(sms && contacts)) askSms() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Floating bubble",
            if (overlay) "Stays above other apps" else "So the orb follows you",
            if (overlay) "On" else "Allow",
            overlay, true) { if (!overlay) askOverlay() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Auto answer",
            "Only in Driving mode",
            if (auto) "On" else "Off",
            auto) { toggleAuto() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Act on commands",
            if (act) "It can change your phone" else "Reads commands, does nothing",
            if (act) "On" else "Off",
            act) { toggleAct() })
        rows.addView(Ui.divider(this))
        val root = Privileges.hasRoot()
        val owner = Privileges.isDeviceOwner(this)
        val shizuku = Privileges.shizukuInstalled(this)
        val accessSub = when {
            root -> "Root. Radio toggles switch here."
            owner -> "Device owner. Some toggles switch here."
            shizuku -> "Shizuku. Radios switch through it."
            else -> "Stock. Radio toggles open Settings."
        }
        val accessTag = when {
            root -> "Root"
            owner -> "Owner"
            shizuku -> "Shizuku"
            else -> "Stock"
        }
        rows.addView(Ui.row(this, "Access",
            accessSub,
            accessTag,
            false) {
            val detail = "Root: " + Privileges.hasRoot() +
                "\nDevice owner: " + Privileges.isDeviceOwner(this) +
                "\nShizuku: " + Privileges.shizukuInstalled(this) +
                "\nWithout root I open system panels instead of flipping radios."
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
        })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Online brain",
            if (cloudOn) "Chat, context and web research" else "On-device fallback only",
            if (cloudOn) "On" else "Off",
            cloudOn) {
            val next = !cloudOn
            prefs.edit().putBoolean(BubbleService.KEY_CLOUD, next).apply()
            Toast.makeText(this,
                if (next) "IRA will use the web for chat and research" else "IRA will stay on this phone",
                Toast.LENGTH_SHORT).show()
            refresh()
        })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Local model",
            when {
                !modelInstalled -> "No GGUF installed. Recommended: " + recommendedModel.label + "."
                activeBig -> "Qwen 1.5B. Better reasoning, more RAM."
                recommendedModel == ModelCatalog.big -> "FunctionGemma 270M. Qwen 1.5B fits this phone."
                else -> "FunctionGemma 270M. Fast on 4 GB."
            },
            when {
                !modelInstalled -> "Missing"
                activeBig -> "1.5B"
                else -> "270M"
            },
            activeBig, true) { toggleModel() })
        rows.addView(Ui.divider(this))
        val cloudKey = prefs.getString(BubbleService.KEY_GEMINI, "").orEmpty()
        rows.addView(Ui.row(this, "Cloud key",
            if (cloudKey.isNotBlank()) "Gemini. Stored on this phone." else "Optional. Removes the free limit.",
            if (cloudKey.isNotBlank()) "On" else "Add",
            cloudKey.isNotBlank(), true) { showCloudKeyDialog() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "How to use", "Guide, examples, tooltips", "Open", false) { showGuide() })
        rows.addView(Ui.divider(this))
        rows.addView(Ui.row(this, "Privacy and terms", "What IRA sees, sends, and why", "Open", false) { showLegalHub() })
    }

    private fun showCloudKeyDialog() {
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        val existing = prefs.getString(BubbleService.KEY_GEMINI, "").orEmpty()
        val d = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 20),
                Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 16))
            background = Ui.rounded(
                Ui.SURFACE, Ui.dp(this@MainActivity, 18),
                Ui.dp(this@MainActivity, 1), Ui.BORDER
            )
        }
        box.addView(Ui.text(this, "Cloud key", 22f, Ui.BONE, true))
        box.addView(Ui.text(this,
            "Paste a free Gemini key from aistudio.google.com/apikey. It stays on this phone.",
            13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 14))
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        })
        val input = EditText(this).apply {
            hint = "AIza..."
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Ui.BONE)
            setHintTextColor(Ui.MUTED)
            textSize = 14f
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12),
                Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12))
            background = Ui.rounded(Ui.INK, Ui.dp(this@MainActivity, 12),
                Ui.dp(this@MainActivity, 1), Ui.BORDER)
        }
        box.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, Ui.dp(this@MainActivity, 14), 0, 0)
        }
        if (existing.isNotBlank()) {
            val remove = Ui.text(this, "Remove", 14f, Ui.MUTED, true).apply {
                setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8),
                    Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    prefs.edit().remove(BubbleService.KEY_GEMINI).apply()
                    Toast.makeText(this@MainActivity, "Cloud key removed", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                    refresh()
                }
            }
            buttons.addView(remove, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(this@MainActivity, 4) })
        }
        val save = Ui.text(this, "Save", 14f, Ui.INK, true).apply {
            setPadding(Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8),
                Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 8))
            background = Ui.rounded(Ui.ACCENT, Ui.dp(this@MainActivity, 20))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val key = input.text.toString().trim()
                if (key.length < 20) {
                    Toast.makeText(this@MainActivity, "That key looks too short", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString(BubbleService.KEY_GEMINI, key).apply()
                    Toast.makeText(this@MainActivity, "Cloud key saved", Toast.LENGTH_SHORT).show()
                    d.dismiss()
                    refresh()
                }
            }
        }
        buttons.addView(save, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        box.addView(buttons)
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(Ui.dp(this, 320), LinearLayout.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    private fun applyMode(m: String) {
        if (AppState.isFrozen(this)) {
            Toast.makeText(this, "IRA is frozen. Tap Resume first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (m == "reels") startReelsService()
        val msg = Modes.apply(this, m)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        showModeHint(m)
        refresh()
    }

    private fun startReelsService() {
        if (!LegalDocs.isAccepted(this)) {
            showLegalConsent()
            return
        }
        if (AppState.isFrozen(this)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean("wake_requested", true).apply()
        val i = Intent(this, BubbleService::class.java).putExtra("listen", 15000L)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
    }

    private fun showModeHint(mode: String) {
        val a11y = AssistantService.instance != null
        val listening = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE).getBoolean("wake_requested", false)
        val text = when (mode) {
            "reels" -> when {
                !a11y -> "Reels mode. Screen control is off, so next will not scroll. Tap here to turn it on."
                !listening -> "Reels mode. Tap the orb, then say next or ruko."
                else -> "Reels mode. Say next to scroll, ruko to stop."
            }
            "driving" -> "Driving mode. Calls are announced. Auto answer stays off until you enable it."
            "reading" -> "Reading mode. Say padho to hear the screen. Screen control must be on."
            "focus" -> "Focus mode. Notifications go quiet. Say ruko to exit."
            "sleep" -> "Sleep mode. Screen dims, alarms still ring. Say ruko to exit."
            "hands_on" -> "Hands-on mode. Wake word pauses. Tap the bubble to talk."
            "quiet" -> "Quiet mode. No spoken replies. Say ruko to exit."
            else -> "Conversation mode. Say ira, wait for the beep, then speak."
        }
        modeHint.text = text
        modeHint.visibility = View.VISIBLE
        val clickable = mode == "reels" && !a11y
        modeHint.isClickable = clickable
        modeHint.isFocusable = clickable
        modeHint.setTextColor(if (clickable) Ui.ACCENT else Ui.MUTED)
        modeHint.setOnClickListener { if (clickable) askScreen() }
        ui.removeCallbacks(hideHint)
        ui.postDelayed(hideHint, if (clickable) 15000 else 8000)
    }

    private fun openLegal(id: String) {
        startActivity(Intent(this, LegalActivity::class.java).putExtra(LegalDocs.EXTRA_DOC, id))
    }

    private fun legalLink(label: String, onClick: () -> Unit): TextView {
        return Ui.text(this, label, 13f, Ui.ACCENT, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minHeight = Ui.dp(this@MainActivity, 44)
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun legalLinkRow(vararg links: Pair<String, () -> Unit>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            links.forEach { (label, action) ->
                addView(legalLink(label, action), LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
            }
        }
    }

    private fun maybeShowGuide() {
        if (!LegalDocs.isAccepted(this)) return
        val p = getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE)
        if (!p.getBoolean("guide_seen", false)) showGuide()
    }

    private fun showLegalConsent() {
        if (isFinishing || isDestroyed) return
        if (legalDialog?.isShowing == true) return
        val d = Dialog(this)
        legalDialog = d
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 20),
                Ui.dp(this@MainActivity, 22), Ui.dp(this@MainActivity, 16))
            background = Ui.rounded(
                Ui.SURFACE, Ui.dp(this@MainActivity, 20),
                Ui.dp(this@MainActivity, 1), Ui.BORDER
            )
        }
        box.addView(Ui.text(this, "Before you start", 22f, Ui.BONE, true))
        box.addView(Ui.text(this,
            "IRA is a voice assistant. It needs your agreement before it can start. Android asks for microphone, notification, and other permissions separately when a feature needs them, and you can decline those.",
            13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 10))
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        })
        box.addView(Ui.text(this,
            "Wake-word audio stays on this phone. Most command audio is transcribed by Android's speech service, which may use Google. When cloud chat is on, your request and recent conversation can go to Pollinations or Google Gemini. You can turn cloud chat off in the app.",
            13f, Ui.BONE).apply {
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 10))
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        })
        box.addView(Ui.text(this,
            "By tapping Accept, you confirm that you are at least 18 years old, or that you have permission from a parent or legal guardian, and you agree to the Terms of Service, Privacy Policy, and Acceptable Use Policy. Read the Permissions and Data guide to see what each Android permission does.",
            13f, Ui.BONE).apply {
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        })
        box.addView(legalLinkRow(
            "Privacy Policy" to { openLegal("privacy") },
            "Terms of Service" to { openLegal("terms") }
        ), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        box.addView(legalLinkRow(
            "Acceptable Use" to { openLegal("acceptable") },
            "Permissions and Data" to { openLegal("permissions") }
        ), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val accept = Ui.text(this, "Accept and continue", 15f, Ui.INK, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 14), 0, Ui.dp(this@MainActivity, 14))
            background = Ui.rounded(Ui.ACCENT, Ui.dp(this@MainActivity, 24))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                LegalDocs.accept(this@MainActivity)
                d.dismiss()
                refresh()
                startWantedService()
                maybeShowGuide()
            }
        }
        box.addView(accept, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@MainActivity, 4) })
        val decline = Ui.text(this, "Decline and close", 14f, Ui.MUTED, true).apply {
            gravity = Gravity.CENTER
            minHeight = Ui.dp(this@MainActivity, 44)
            setPadding(0, Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                d.dismiss()
                finish()
            }
        }
        box.addView(decline, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(box, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        d.setContentView(scroll)
        d.setCanceledOnTouchOutside(false)
        d.setOnCancelListener { finish() }
        d.setOnDismissListener { if (legalDialog === d) legalDialog = null }
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        val dm = resources.displayMetrics
        val width = (dm.widthPixels - Ui.dp(this, 32)).coerceAtMost(Ui.dp(this, 380))
        val height = (dm.heightPixels - Ui.dp(this, 80)).coerceAtMost(Ui.dp(this, 620))
        d.window?.setLayout(width, height)
    }

    private fun showLegalHub() {
        if (!LegalDocs.isAccepted(this)) {
            showLegalConsent()
            return
        }
        val d = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 18),
                Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 12))
            background = Ui.rounded(
                Ui.SURFACE, Ui.dp(this@MainActivity, 20),
                Ui.dp(this@MainActivity, 1), Ui.BORDER
            )
        }
        box.addView(Ui.text(this, "Privacy and terms", 22f, Ui.BONE, true))
        box.addView(Ui.text(this, "Read the documents that apply to this app.", 13f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 6), 0, Ui.dp(this@MainActivity, 10))
        })
        LegalDocs.docs.forEachIndexed { index, doc ->
            box.addView(Ui.row(this, doc.title, doc.subtitle, "Read", false) {
                d.dismiss()
                openLegal(doc.id)
            })
            if (index < LegalDocs.docs.lastIndex) box.addView(Ui.divider(this))
        }
        val close = Ui.text(this, "Close", 15f, Ui.INK, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12))
            background = Ui.rounded(Ui.ACCENT, Ui.dp(this@MainActivity, 24))
            isClickable = true
            isFocusable = true
            setOnClickListener { d.dismiss() }
        }
        box.addView(close, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(this@MainActivity, 14) })
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dm = resources.displayMetrics
        d.window?.setLayout(
            (dm.widthPixels - Ui.dp(this, 32)).coerceAtMost(Ui.dp(this, 380)),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        d.show()
    }

    private fun showGuide() {
        if (!LegalDocs.isAccepted(this)) {
            showLegalConsent()
            return
        }
        val d = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 22),
                Ui.dp(this@MainActivity, 24), Ui.dp(this@MainActivity, 18))
            background = Ui.rounded(
                Ui.SURFACE, Ui.dp(this@MainActivity, 20),
                Ui.dp(this@MainActivity, 1), Ui.BORDER
            )
        }
        box.addView(Ui.text(this, "How to use", 22f, Ui.BONE, true))
        box.addView(Ui.text(this, "Three steps", 12f, Ui.MUTED, true).apply {
            setPadding(0, Ui.dp(this@MainActivity, 4), 0, Ui.dp(this@MainActivity, 14))
        })
        for (step in listOf(
            "1.  Say ira, or tap the orb.",
            "2.  Wait for the beep, then say what you want.",
            "3.  Say ruko or stop to leave a mode."
        )) {
            box.addView(Ui.text(this, step, 15f, Ui.BONE).apply {
                setPadding(0, Ui.dp(this@MainActivity, 4), 0, Ui.dp(this@MainActivity, 4))
            })
        }
        box.addView(Ui.text(this, "Try", 12f, Ui.MUTED, true).apply {
            setPadding(0, Ui.dp(this@MainActivity, 16), 0, Ui.dp(this@MainActivity, 6))
        })
        for (e in listOf(
            "tell me a joke",
            "what's new in AI today",
            "open whatsapp",
            "call nanna garu",
            "battery kitna hai"
        )) {
            box.addView(Ui.text(this, "·  $e", 14f, Ui.MUTED).apply {
                setPadding(0, Ui.dp(this@MainActivity, 2), 0, Ui.dp(this@MainActivity, 2))
            })
        }
        box.addView(Ui.text(this,
            "Phone commands run on this phone. Chat and research use the web when Online brain is on. Screen control is needed for tapping and WhatsApp.",
            12f, Ui.MUTED).apply {
            setPadding(0, Ui.dp(this@MainActivity, 16), 0, Ui.dp(this@MainActivity, 16))
            setLineSpacing(Ui.dp(this@MainActivity, 2).toFloat(), 1f)
        })
        val got = Ui.text(this, "Got it", 15f, Ui.INK, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@MainActivity, 12), 0, Ui.dp(this@MainActivity, 12))
            background = Ui.rounded(Ui.ACCENT, Ui.dp(this@MainActivity, 24))
            isClickable = true
            isFocusable = true
            setOnClickListener { d.dismiss() }
        }
        box.addView(got, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(Ui.dp(this, 320), LinearLayout.LayoutParams.WRAP_CONTENT)
        d.setOnDismissListener {
            getSharedPreferences(BubbleService.PREFS, MODE_PRIVATE).edit().putBoolean("guide_seen", true).apply()
        }
        d.show()
    }
}
