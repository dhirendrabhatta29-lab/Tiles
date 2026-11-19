package com.matte.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Niagara-style right-edge scrub bar. Letters near the finger bulge outward and enlarge,
 * a bubble shows the active letter, and touches outside the slim bar pass through to the list.
 */
internal class AlphabetSlider(context: Context, private val u: Float) : View(context) {

    var onLetter: (Char) -> Unit = {}
    var available: Set<Char> = emptySet()
        set(value) { field = value; invalidate() }
    var ink: Int = Color.WHITE
    var accent: Int = 0xFF6D8764.toInt()

    private val letters: List<Char> = ('A'..'Z').toList() + '#'
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barWidth get() = u * 30f
    private val padV get() = u * 12f
    private var touchY = -1f
    private var active = -1

    private fun step(): Float = max(1f, (height - padV * 2) / letters.size)

    override fun onDraw(canvas: Canvas) {
        val step = step()
        val barCenterX = width - barWidth / 2f
        for (i in letters.indices) {
            val cy = padV + step * (i + 0.5f)
            var shift = 0f
            var scale = 1f
            if (touchY >= 0f) {
                val d = abs(cy - touchY) / (step * 3.2f)
                val k = exp(-d * d)
                shift = -k * u * 34f
                scale = 1f + k * 0.9f
            }
            val isActive = i == active
            paint.textSize = u * 10.5f * scale
            paint.color = if (isActive) accent else ink
            paint.alpha = if (isActive || letters[i] in available) 255 else 90
            canvas.drawText(letters[i].toString(), barCenterX + shift, cy + paint.textSize / 3f, paint)
        }
        if (touchY >= 0f && active >= 0) {
            val r = u * 30f
            val cx = width - barWidth - u * 44f
            val cy = touchY.coerceIn(padV + r, max(padV + r, height - padV - r))
            bubble.color = accent
            canvas.drawCircle(cx, cy, r, bubble)
            paint.color = TileColors.solid(accent).foreground
            paint.alpha = 255
            paint.textSize = u * 30f
            canvas.drawText(letters[active].toString(), cx, cy + paint.textSize / 3f, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.x < width - barWidth - u * 8f) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                touchY = e.y
                update(e.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchY = e.y
                update(e.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchY = -1f
                active = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun update(y: Float) {
        val index = ((y - padV) / step()).toInt().coerceIn(0, letters.size - 1)
        if (index != active) {
            active = index
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetter(letters[index])
        }
        invalidate()
    }
}
