package com.riz.kurs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

private class Blob(val fx: Float, val fy: Float, val fr: Float, val color: Int, val phase: Float)

/** Latar mesh gradient yang blob-nya bergerak pelan — biar panel kaca ada yang "dilihat". */
class GlassBackground(context: Context) : View(context) {
    private val basePaint = Paint()
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val blobs = listOf(
        Blob(0.10f, 0.08f, 0.95f, 0x556366F1, 0.9f),
        Blob(0.95f, 0.32f, 0.80f, 0x4D22D3EE, 1.3f),
        Blob(0.45f, 0.95f, 0.90f, 0x44EC4899, 0.7f),
    )

    private var t = 0f

    private val tick = object : Runnable {
        override fun run() {
            t += 0.012f
            invalidate()
            postDelayed(this, 40) // ~25fps, cukup buat gerakan halus
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, 40)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        basePaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF0B1020.toInt(), 0xFF10182B.toInt(), 0xFF0B1020.toInt()),
            null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, basePaint)

        for (b in blobs) {
            val cx = w * b.fx + sin((t * b.phase).toDouble()).toFloat() * w * 0.06f
            val cy = h * b.fy + sin((t * b.phase + 1.7).toDouble()).toFloat() * h * 0.05f
            val r = w * b.fr
            blobPaint.shader = RadialGradient(cx, cy, r, b.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, blobPaint)
        }
    }
}
