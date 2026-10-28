package com.voicepoco.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object Ui {
    const val INK = 0xFF09100F.toInt()
    const val SURFACE = 0xFF131C19.toInt()
    const val BONE = 0xFFECF2EC.toInt()
    const val MUTED = 0xFFA4B1AA.toInt()
    const val LINE = 0xFF2C3A35.toInt()
    const val BORDER = 0xFF5C7268.toInt()
    const val ACCENT = 0xFF83CBB9.toInt()
    const val DANGER = 0xFFE05B52.toInt()
    const val WARN = 0xFFF2C14E.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    fun rounded(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    fun text(c: Context, value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

    fun pressable(c: Context, v: View, color: Int = withAlpha(BONE, 0x33)): View {
        v.background = RippleDrawable(ColorStateList.valueOf(color), v.background, null)
        return v
    }

    fun row(c: Context, title: String, sub: String, action: String, active: Boolean, dot: Boolean = false, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(c, 16), 0, dp(c, 16))
            isClickable = true
            isFocusable = true
        }
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(c, title, 16f, BONE, true))
        left.addView(text(c, sub, 13f, MUTED).apply {
            setPadding(0, dp(c, 4), 0, 0)
        })
        row.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (dot && active) {
            row.addView(View(c).apply {
                background = rounded(ACCENT, dp(c, 4))
                contentDescription = "$title, on"
            }, LinearLayout.LayoutParams(dp(c, 8), dp(c, 8)))
        } else {
            val tag = text(c, action, 13f, if (active) MUTED else ACCENT, true).apply {
                setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8))
                background = rounded(if (active) Color.TRANSPARENT else withAlpha(ACCENT, 0x22), dp(c, 8))
            }
            row.addView(tag)
        }
        row.foreground = RippleDrawable(ColorStateList.valueOf(withAlpha(BONE, 0x22)), null, null)
        row.setOnClickListener { onClick() }
        return row
    }

    fun divider(c: Context): View = View(c).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1))
    }
}
