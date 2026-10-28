package com.voicepoco.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class VoiceOrbView(context: Context) : View(context) {
    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private var state = State.IDLE
    private var running = false
    private var lastFrame = 0L
    private var phase = 0f
    private var pulsePhase = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(0.8f)
    }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val now = frameTimeNanos / 1_000_000L
            val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(0L, 48L)
            lastFrame = now
            phase += dt * 0.001f * spinSpeed
            pulsePhase += dt * 0.001f
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val spinSpeed: Float
        get() = when (state) {
            State.IDLE -> 0.18f
            State.LISTENING -> 0.62f
            State.THINKING -> 1.5f
            State.SPEAKING -> 0.85f
        }

    private val intensity: Float
        get() = when (state) {
            State.IDLE -> 0.55f
            State.LISTENING -> 1f
            State.THINKING -> 0.9f
            State.SPEAKING -> 1f
        }

    private val pulseAmp: Float
        get() = when (state) {
            State.IDLE -> 0.012f
            State.LISTENING -> 0.035f
            State.THINKING -> 0.05f
            State.SPEAKING -> 0.065f
        }

    fun setState(s: State) {
        state = s
        contentDescription = when (s) {
            State.IDLE -> "IRA"
            State.LISTENING -> "IRA, listening"
            State.THINKING -> "IRA, thinking"
            State.SPEAKING -> "IRA, speaking"
        }
        if (running) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running) return
        running = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.43f
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                Ui.withAlpha(Ui.ACCENT, 70),
                Ui.withAlpha(Ui.ACCENT, 20),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.43f
        val discR = r + dp(5f)

        discPaint.color = Ui.withAlpha(Ui.INK, 228)
        canvas.drawCircle(cx, cy, discR, discPaint)
        canvas.drawCircle(cx, cy, r, glowPaint)

        ringPaint.color = if (state == State.IDLE) {
            Ui.withAlpha(Ui.LINE, 150)
        } else {
            Ui.withAlpha(Ui.ACCENT, 190)
        }
        canvas.drawCircle(cx, cy, discR - dp(0.5f), ringPaint)

        val scale = 1f + pulseAmp * sin(pulsePhase * 6.2831855f)
        drawSphere(canvas, cx, cy, r * scale)
    }

    private fun drawSphere(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val tilt = -0.34f
        val roll = 0.16f
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        val cosR = cos(roll)
        val sinR = sin(roll)
        val lonCount = 26
        val latCount = 6
        val steps = 40

        var i = 0
        while (i < lonCount) {
            val lon = (i.toFloat() / lonCount) * 6.2831855f
            path.reset()
            var s = 0
            while (s <= steps) {
                val lat = -1.5707964f + (s.toFloat() / steps) * 3.1415927f
                val warp = 0.42f * sin(lat * 2.1f + phase * 1.15f)
                val a = lon + warp
                val x0 = cos(lat) * cos(a)
                val y0 = sin(lat)
                val z0 = cos(lat) * sin(a)
                val x1 = x0 * cosR - y0 * sinR
                val y1 = x0 * sinR + y0 * cosR
                val y2 = y1 * cosT - z0 * sinT
                val z2 = y1 * sinT + z0 * cosT
                val persp = 1f / (1f - z2 * 0.18f)
                val px = cx + x1 * radius * persp
                val py = cy + y2 * radius * persp
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                s++
            }
            val front = (sin(lon + phase) + 1f) * 0.5f
            val alpha = ((0.18f + 0.82f * front) * intensity * 255f).toInt().coerceIn(0, 255)
            linePaint.color = Ui.withAlpha(Ui.ACCENT, alpha)
            linePaint.strokeWidth = dp(if (i % 5 == 0) 0.95f else 0.65f)
            canvas.drawPath(path, linePaint)
            i++
        }

        i = 1
        while (i <= latCount) {
            val lat = -1.5707964f + (i.toFloat() / (latCount + 1)) * 3.1415927f
            path.reset()
            var s = 0
            while (s <= steps) {
                val a = (s.toFloat() / steps) * 6.2831855f
                val warp = 0.30f * sin(lat * 3f + phase * 1.4f)
                val aa = a + warp
                val x0 = cos(lat) * cos(aa)
                val y0 = sin(lat)
                val z0 = cos(lat) * sin(aa)
                val x1 = x0 * cosR - y0 * sinR
                val y1 = x0 * sinR + y0 * cosR
                val y2 = y1 * cosT - z0 * sinT
                val z2 = y1 * sinT + z0 * cosT
                val persp = 1f / (1f - z2 * 0.18f)
                val px = cx + x1 * radius * persp
                val py = cy + y2 * radius * persp
                if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                s++
            }
            val alpha = ((0.25f + 0.35f * intensity) * 255f).toInt().coerceIn(0, 255)
            linePaint.color = Ui.withAlpha(Ui.ACCENT, alpha)
            linePaint.strokeWidth = dp(0.55f)
            canvas.drawPath(path, linePaint)
            i++
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
