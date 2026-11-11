package com.mydialer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/** A track with a green knob. Slide the knob to the right end to answer the call. */
class SlideToAnswerView(ctx: Context, private val onAnswer: () -> Unit) : View(ctx) {

    private val density = ctx.resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon = ctx.getDrawable(R.drawable.ic_call)?.mutate()
    private var knobX = 0f
    private var dragging = false
    private var grabOffset = 0f

    init {
        trackPaint.color = 0x33FFFFFF
        knobPaint.color = AppTheme.green
        textPaint.color = 0xFFDDDDDD.toInt()
        textPaint.textSize = 15f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        icon?.setTint(0xFFFFFFFF.toInt())
    }

    private fun margin() = 4f * density
    private fun radius() = height / 2f - margin()
    private fun minX() = margin() + radius()
    private fun maxX() = width - margin() - radius()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), h / 2f, h / 2f, trackPaint)

        if (knobX < minX()) knobX = minX()
        val progress = ((knobX - minX()) / (maxX() - minX()).coerceAtLeast(1f)).coerceIn(0f, 1f)
        textPaint.alpha = ((1f - progress) * 255).toInt()
        val ty = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("Slide to answer  ›››", width / 2f + radius() / 2f, ty, textPaint)

        val r = radius()
        canvas.drawCircle(knobX, h / 2f, r, knobPaint)
        icon?.let {
            val s = (r * 0.9f).toInt()
            it.setBounds((knobX - s / 2).toInt(), (h / 2f - s / 2).toInt(), (knobX + s / 2).toInt(), (h / 2f + s / 2).toInt())
            it.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val near = Math.abs(event.x - knobX) <= radius() * 1.6f
                if (!near) return false
                dragging = true
                grabOffset = event.x - knobX
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    knobX = (event.x - grabOffset).coerceIn(minX(), maxX())
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    val progress = (knobX - minX()) / (maxX() - minX()).coerceAtLeast(1f)
                    val done = event.actionMasked == MotionEvent.ACTION_UP && progress > 0.85f
                    knobX = minX()
                    invalidate()
                    if (done) onAnswer()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
