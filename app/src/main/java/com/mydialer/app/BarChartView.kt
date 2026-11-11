package com.mydialer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A simple bar chart. Each bar is one day of talk time, in minutes. */
class BarChartView(ctx: Context) : View(ctx) {

    private val density = ctx.resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var showValues = true

    fun setData(values: List<Float>, labels: List<String>, showValues: Boolean) {
        this.values = values
        this.labels = labels
        this.showValues = showValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = 22f * density
        val topH = 20f * density
        val baseY = h - labelH
        val chartH = baseY - topH
        val slot = w / values.size
        val barW = slot * 0.6f
        val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)

        linePaint.color = AppTheme.divider
        linePaint.strokeWidth = 1.5f * density
        canvas.drawLine(0f, baseY, w, baseY, linePaint)

        textPaint.textAlign = Paint.Align.CENTER
        for (i in values.indices) {
            val cx = slot * i + slot / 2f
            val v = values[i]
            val barH = if (v <= 0f) 2f * density else chartH * (v / maxV)
            barPaint.color = AppTheme.brand
            barPaint.alpha = if (i == values.size - 1) 255 else 170
            val rect = RectF(cx - barW / 2f, baseY - barH, cx + barW / 2f, baseY)
            canvas.drawRoundRect(rect, 4f * density, 4f * density, barPaint)

            if (showValues && v > 0f) {
                textPaint.color = AppTheme.text
                textPaint.textSize = 11f * density
                canvas.drawText(Math.round(v).toString() + "m", cx, baseY - barH - 5f * density, textPaint)
            }
            val label = labels.getOrNull(i) ?: ""
            if (label.isNotEmpty()) {
                textPaint.color = AppTheme.muted
                textPaint.textSize = 11f * density
                canvas.drawText(label, cx, h - 6f * density, textPaint)
            }
        }
    }
}
