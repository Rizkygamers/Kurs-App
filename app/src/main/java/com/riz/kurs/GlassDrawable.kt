package com.riz.kurs

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Panel kaca: isi translusen + glow "refraksi", gloss atas, sheen diagonal,
 * shade bawah, dan rim bertingkat (terang di atas, redup di bawah).
 */
class GlassDrawable(
    private val radius: Float,
    private val strokeWidth: Float,
    private val fillTop: Int,
    private val fillBottom: Int,
    private val glowA: Int = Color.TRANSPARENT,
    private val glowB: Int = Color.TRANSPARENT,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@GlassDrawable.strokeWidth
    }
    private val rect = RectF()
    private val path = Path()

    override fun onBoundsChange(b: Rect) {
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        val h = rect.height()

        fillPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom, fillTop, fillBottom, Shader.TileMode.CLAMP,
        )
        glossPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + h * 0.45f, 0x46FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        shadePaint.shader = LinearGradient(
            0f, rect.top + h * 0.55f, 0f, rect.bottom, 0x00000000, 0x30000000, Shader.TileMode.CLAMP,
        )
        // kilau diagonal: pita cahaya melintasi panel
        sheenPaint.shader = LinearGradient(
            rect.left, rect.bottom, rect.right, rect.top,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x12FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.32f, 0.45f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom, 0xB3FFFFFF.toInt(), 0x1AFFFFFF, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)

        canvas.drawRect(rect, fillPaint)

        if (glowA != Color.TRANSPARENT) {
            glowPaint.shader = RadialGradient(
                rect.left + rect.width() * 0.12f,
                rect.top + rect.height() * 0.04f,
                rect.width() * 0.8f,
                glowA, Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(rect, glowPaint)
        }
        if (glowB != Color.TRANSPARENT) {
            glowPaint.shader = RadialGradient(
                rect.right - rect.width() * 0.10f,
                rect.top + rect.height() * 0.60f,
                rect.width() * 0.75f,
                glowB, Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(rect, glowPaint)
        }

        canvas.drawRect(rect, glossPaint)
        canvas.drawRect(rect, sheenPaint)
        canvas.drawRect(rect, shadePaint)
        canvas.restore()

        canvas.drawPath(path, rimPaint)
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radius)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}

    @Deprecated("Deprecated in Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
