package com.mydialer.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Numbers you blocked. Tap one to unblock it. */
class BlockedActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppTheme.bg)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(Util.dp(this, 16), Util.dp(this, 16), Util.dp(this, 16), Util.dp(this, 4))
        top.addView(Kit.title(this, "Blocked numbers"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val add = Util.icon(this, R.drawable.ic_add, 0xFFFFFFFF.toInt())
        val pad = Util.dp(this, 10)
        add.setPadding(pad, pad, pad, pad)
        add.background = Util.roundBg(this, AppTheme.brand, 22)
        add.setOnClickListener { askNumber() }
        top.addView(add, LinearLayout.LayoutParams(Util.dp(this, 44), Util.dp(this, 44)))
        root.addView(top)

        message = Util.text(this, "", 15f, AppTheme.muted)
        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(this, 24), Util.dp(this, 24), Util.dp(this, 24), Util.dp(this, 24))
        root.addView(message)

        val scroll = ScrollView(this)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        AppTheme.style(this)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        list.removeAllViews()
        if (!Util.canBlock(this)) {
            message.text = "To block numbers, make My Dialer your default Phone app first (Settings > Default Phone app)."
            return
        }
        val numbers = Util.blockedNumbers(this)
        message.text = if (numbers.isEmpty()) "No blocked numbers.\n\nLong-press a call in Recents and choose Block this number." else ""
        for (n in numbers) {
            val caller = Util.lookup(this, n)
            val row = RowView(this)
            row.bind(
                Util.avatar(this, caller.name ?: n, caller.thumbUri, Util.dp(this, 48)),
                caller.name ?: n,
                if (caller.name != null) n + " · Tap to unblock" else "Tap to unblock",
                AppTheme.text, R.drawable.ic_block, AppTheme.red, ""
            )
            row.setOnClickListener {
                AlertDialog.Builder(this, AppTheme.dialogTheme())
                    .setTitle("Unblock " + (caller.name ?: n) + "?")
                    .setPositiveButton("Unblock") { _, _ ->
                        Util.unblock(this, n)
                        reload()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            list.addView(row)
        }
    }

    private fun askNumber() {
        if (!Util.canBlock(this)) {
            Toast.makeText(this, "Make My Dialer your default Phone app first", Toast.LENGTH_LONG).show()
            return
        }
        val field = EditText(this)
        field.hint = "Phone number"
        field.inputType = InputType.TYPE_CLASS_PHONE
        Util.styleField(this, field)
        val box = LinearLayout(this)
        box.setPadding(Util.dp(this, 20), Util.dp(this, 8), Util.dp(this, 20), 0)
        box.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this, AppTheme.dialogTheme())
            .setTitle("Block a number")
            .setView(box)
            .setPositiveButton("Block") { _, _ ->
                val n = field.text.toString().trim()
                if (n.isNotEmpty() && !Util.block(this, n)) {
                    Toast.makeText(this, "Could not block this number", Toast.LENGTH_SHORT).show()
                }
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
