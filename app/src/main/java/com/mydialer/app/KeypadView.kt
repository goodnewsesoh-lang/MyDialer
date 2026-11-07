package com.mydialer.app

import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** The number keypad. */
class KeypadView(private val act: Activity) : LinearLayout(act) {

    private val display = TextView(act)
    private val sb = StringBuilder()

    init {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(Util.dp(act, 16), Util.dp(act, 16), Util.dp(act, 16), Util.dp(act, 16))

        display.textSize = 32f
        display.gravity = Gravity.CENTER
        display.setTextColor(0xFF212121.toInt())
        display.setSingleLine(true)
        addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(act, 72)))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        for (rowKeys in keys) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            for (k in rowKeys) {
                val key = Util.text(act, k, 28f, 0xFF212121.toInt())
                key.gravity = Gravity.CENTER
                key.background = Util.roundBg(act, 0xFFF0F0F0.toInt(), 36)
                key.setOnClickListener { press(k) }
                val lp = LinearLayout.LayoutParams(Util.dp(act, 80), Util.dp(act, 72))
                lp.setMargins(Util.dp(act, 8), Util.dp(act, 6), Util.dp(act, 8), Util.dp(act, 6))
                row.addView(key, lp)
            }
            addView(row)
        }

        val bottom = LinearLayout(act)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER

        val spacer = TextView(act)
        bottom.addView(spacer, LinearLayout.LayoutParams(Util.dp(act, 80), Util.dp(act, 72)))

        val call = Util.text(act, "Call", 18f, 0xFFFFFFFF.toInt(), true)
        call.gravity = Gravity.CENTER
        call.background = Util.roundBg(act, Util.GREEN, 36)
        call.setOnClickListener { Util.call(act, sb.toString()) }
        val callLp = LinearLayout.LayoutParams(Util.dp(act, 96), Util.dp(act, 72))
        callLp.setMargins(Util.dp(act, 8), Util.dp(act, 12), Util.dp(act, 8), Util.dp(act, 6))
        bottom.addView(call, callLp)

        val back = Util.text(act, "DEL", 16f, 0xFF212121.toInt(), true)
        back.gravity = Gravity.CENTER
        back.setOnClickListener {
            if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            show()
        }
        back.setOnLongClickListener {
            sb.setLength(0)
            show()
            true
        }
        bottom.addView(back, LinearLayout.LayoutParams(Util.dp(act, 80), Util.dp(act, 72)))
        addView(bottom)
    }

    fun setNumber(number: String) {
        sb.setLength(0)
        sb.append(number)
        show()
    }

    private fun press(key: String) {
        sb.append(key)
        show()
    }

    private fun show() {
        display.text = sb.toString()
    }
}
