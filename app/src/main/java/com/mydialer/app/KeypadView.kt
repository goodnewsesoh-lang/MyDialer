package com.mydialer.app

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView

/** The number keypad. The keys stretch to fit any screen, so Call and delete are always visible. */
class KeypadView(private val act: Activity) : LinearLayout(act) {

    private val display = TextView(act)
    private val sb = StringBuilder()
    private var tone: ToneGenerator? = null

    private val letters = mapOf(
        "1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
        "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
        "*" to "", "0" to "+", "#" to ""
    )

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AppTheme.bg)
        setPadding(Util.dp(act, 12), Util.dp(act, 8), Util.dp(act, 12), Util.dp(act, 8))

        display.textSize = 32f
        display.gravity = Gravity.CENTER
        display.setTextColor(AppTheme.text)
        display.setSingleLine(true)
        display.ellipsize = TextUtils.TruncateAt.START
        addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(act, 64)))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        val grid = LinearLayout(act)
        grid.orientation = LinearLayout.VERTICAL
        for (rowKeys in rows) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in rowKeys) {
                val key = LinearLayout(act)
                key.orientation = LinearLayout.VERTICAL
                key.gravity = Gravity.CENTER
                key.background = Util.roundBg(act, AppTheme.card, 22)
                key.addView(Util.text(act, k, 26f, AppTheme.text, true).also { it.gravity = Gravity.CENTER })
                val sub = Util.text(act, letters[k] ?: "", 10f, AppTheme.muted)
                sub.gravity = Gravity.CENTER
                key.addView(sub)
                key.setOnClickListener {
                    if (Prefs.getBool(act, Prefs.HAPTIC, true)) {
                        key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    press(k)
                }
                if (k == "0") {
                    key.setOnLongClickListener {
                        press("+")
                        true
                    }
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                lp.setMargins(Util.dp(act, 5), Util.dp(act, 5), Util.dp(act, 5), Util.dp(act, 5))
                row.addView(key, lp)
            }
            grid.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(act)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL

        bottom.addView(TextView(act), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val call = LinearLayout(act)
        call.orientation = LinearLayout.HORIZONTAL
        call.gravity = Gravity.CENTER
        call.background = Util.roundBg(act, AppTheme.green, 32)
        val callIcon = Util.icon(act, R.drawable.ic_call, 0xFFFFFFFF.toInt())
        call.addView(callIcon, LinearLayout.LayoutParams(Util.dp(act, 22), Util.dp(act, 22)))
        val callText = Util.text(act, "Call", 18f, 0xFFFFFFFF.toInt(), true)
        val callTextLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        callTextLp.leftMargin = Util.dp(act, 8)
        call.addView(callText, callTextLp)
        call.setOnClickListener { Util.callOrAsk(act, null, sb.toString()) }
        val callLp = LinearLayout.LayoutParams(0, Util.dp(act, 60), 2f)
        callLp.setMargins(Util.dp(act, 5), Util.dp(act, 5), Util.dp(act, 5), Util.dp(act, 5))
        bottom.addView(call, callLp)

        val back = Util.icon(act, R.drawable.ic_backspace, AppTheme.muted)
        val backPad = Util.dp(act, 16)
        back.setPadding(backPad, backPad, backPad, backPad)
        back.setOnClickListener {
            if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
            show()
        }
        back.setOnLongClickListener {
            sb.setLength(0)
            show()
            true
        }
        bottom.addView(back, LinearLayout.LayoutParams(0, Util.dp(act, 60), 1f))

        addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(act, 70)))
    }

    fun setNumber(number: String) {
        sb.setLength(0)
        sb.append(number)
        show()
    }

    private fun press(key: String) {
        sb.append(key)
        show()
        beep(key)
    }

    private fun beep(key: String) {
        if (!Prefs.getBool(act, Prefs.TONES, true)) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_DTMF, 70)
            val t = when (key) {
                "0" -> ToneGenerator.TONE_DTMF_0
                "1" -> ToneGenerator.TONE_DTMF_1
                "2" -> ToneGenerator.TONE_DTMF_2
                "3" -> ToneGenerator.TONE_DTMF_3
                "4" -> ToneGenerator.TONE_DTMF_4
                "5" -> ToneGenerator.TONE_DTMF_5
                "6" -> ToneGenerator.TONE_DTMF_6
                "7" -> ToneGenerator.TONE_DTMF_7
                "8" -> ToneGenerator.TONE_DTMF_8
                "9" -> ToneGenerator.TONE_DTMF_9
                "*" -> ToneGenerator.TONE_DTMF_S
                "#" -> ToneGenerator.TONE_DTMF_P
                else -> -1
            }
            if (t >= 0) tone?.startTone(t, 100)
        } catch (e: Exception) {
            // no sound, that is fine
        }
    }

    private fun show() {
        display.text = sb.toString()
    }

    override fun onDetachedFromWindow() {
        tone?.release()
        tone = null
        super.onDetachedFromWindow()
    }
}
