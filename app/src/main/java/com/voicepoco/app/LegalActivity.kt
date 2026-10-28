package com.voicepoco.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

class LegalActivity : Activity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = Ui.INK
        window.navigationBarColor = Ui.INK
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }

        val doc = LegalDocs.find(intent.getStringExtra(LegalDocs.EXTRA_DOC))
        if (doc == null) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.INK)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@LegalActivity, 12), Ui.dp(this@LegalActivity, 10),
                Ui.dp(this@LegalActivity, 18), Ui.dp(this@LegalActivity, 10))
        }
        val back = Ui.text(this, "‹", 30f, Ui.BONE, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            minWidth = Ui.dp(this@LegalActivity, 44)
            minHeight = Ui.dp(this@LegalActivity, 44)
            isClickable = true
            isFocusable = true
            setPadding(Ui.dp(this@LegalActivity, 12), 0, Ui.dp(this@LegalActivity, 12), Ui.dp(this@LegalActivity, 2))
            setOnClickListener { finish() }
        }
        top.addView(back)
        top.addView(Ui.text(this, doc.title, 18f, Ui.BONE, true).apply {
            setPadding(Ui.dp(this@LegalActivity, 4), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)
        root.addView(Ui.divider(this))

        val webView = WebView(this).apply {
            setBackgroundColor(Ui.INK)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.textZoom = (resources.configuration.fontScale * 100).toInt().coerceIn(80, 200)
            isVerticalScrollBarEnabled = true
            isFocusable = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    val scheme = url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    }
                    return true
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        web = webView
        webView.loadDataWithBaseURL(null, LegalDocs.html(this, doc), "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }
}
