package com.mydialer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * The bottom menu, drawn as one single shape so nothing can be cut off.
 * The selected tab lifts up into a circle that cuts into the bar.
 */
class NavBarView(
    ctx: Context,
    private val names: List<String>,
    icons: List<Int>,
    private val onPick: (Int) -> Unit
) : View(ctx) {

    var selected = 0
        set(value) {
            field = value
            invalidate()
        }

    private val badges = BooleanArray(names.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawables = icons.map { ctx.getDrawable(it)!!.mutate() }
    private val barRect = RectF()

    private fun d(value: Float): Float = value * resources.displayMetrics.density

    init {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics
        )
    }

    fun setBadge(index: Int, on: Boolean) {
        if (index in badges.indices && badges[index] != on) {
            badges[index] = on
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), d(92f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val side = d(12f)
        val barTop = d(20f)
        val barHeight = d(64f)
        barRect.set(side, barTop, width - side, barTop + barHeight)

        // The bar
        paint.style = Paint.Style.FILL
        paint.color = AppTheme.card
        canvas.drawRoundRect(barRect, d(28f), d(28f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d(1f)
        paint.color = AppTheme.divider
        canvas.drawRoundRect(barRect, d(28f), d(28f), paint)
        paint.style = Paint.Style.FILL

        val itemWidth = barRect.width() / names.size
        for (i in names.indices) {
            val cx = barRect.left + itemWidth * i + itemWidth / 2f
            val isSelected = i == selected

            if (isSelected) {
                val cy = d(29f)
                // The ring has the colour of the screen, so it looks like a cut-out in the bar
                paint.color = AppTheme.bg
                canvas.drawCircle(cx, cy, d(29f), paint)
                paint.color = AppTheme.card
                canvas.drawCircle(cx, cy, d(24f), paint)
                drawIcon(canvas, i, cx, cy, AppTheme.accent)
            } else {
                drawIcon(canvas, i, cx, barTop + d(24f), AppTheme.muted)
            }

            textPaint.color = if (isSelected) AppTheme.text else AppTheme.muted
            textPaint.isFakeBoldText = isSelected
            canvas.drawText(names[i], cx, barTop + d(56f), textPaint)

            if (badges[i]) {
                val bx = if (isSelected) cx + d(14f) else cx + d(11f)
                val by = if (isSelected) d(14f) else barTop + d(15f)
                paint.color = AppTheme.card
                canvas.drawCircle(bx, by, d(7f), paint)
                paint.color = AppTheme.red
                canvas.drawCircle(bx, by, d(5f), paint)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, index: Int, cx: Float, cy: Float, color: Int) {
        val half = d(12f).toInt()
        val drawable = drawables[index]
        drawable.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val side = d(12f)
                val itemWidth = (width - 2 * side) / names.size
                val index = ((event.x - side) / itemWidth).toInt().coerceIn(0, names.size - 1)
                performClick()
                onPick(index)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
