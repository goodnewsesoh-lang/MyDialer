package com.mydialer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

/** Choose who is called when you hold a number key on the keypad. */
class SpeedDialActivity : Activity() {

    private lateinit var card: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(AppTheme.bg)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val pad = Util.dp(this, 16)
        body.setPadding(pad, pad, pad, pad)
        scroll.addView(body)

        body.addView(Kit.title(this, "Speed dial"))
        val hint = Util.text(this, "Tap a key to choose a contact. On the keypad, hold that key to call. Long-press here to clear.", 13f, AppTheme.muted)
        hint.setPadding(Util.dp(this, 4), 0, Util.dp(this, 4), 0)
        body.addView(hint, Kit.lp(this, 4))

        card = Kit.card(this)
        body.addView(card, Kit.lp(this, 16))

        setContentView(scroll)
        AppTheme.style(this)
        build()
    }

    private fun build() {
        card.removeAllViews()
        for (digit in 2..9) {
            val number = SpeedDial.number(this, digit)
            val name = SpeedDial.name(this, digit)
            val sub = if (number.isEmpty()) "Not set" else if (name.isNotEmpty() && name != number) "$name · $number" else number
            val row = Kit.rowText(this, "Key $digit", sub, AppTheme.text) {
                val i = Intent(this, AddCallActivity::class.java)
                i.putExtra("pick", true)
                startActivityForResult(i, 100 + digit)
            }
            row.setOnLongClickListener {
                if (number.isNotEmpty()) {
                    AlertDialog.Builder(this, AppTheme.dialogTheme())
                        .setTitle("Clear key $digit?")
                        .setPositiveButton("Clear") { _, _ ->
                            SpeedDial.clear(this, digit)
                            build()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                true
            }
            Kit.addRow(this, card, row)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val digit = requestCode - 100
        if (resultCode == RESULT_OK && data != null && digit in 2..9) {
            val number = data.getStringExtra("number") ?: ""
            val name = data.getStringExtra("name") ?: number
            if (number.isNotEmpty()) SpeedDial.set(this, digit, name, number)
            build()
        }
    }
}
