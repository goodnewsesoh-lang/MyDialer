package com.mydialer.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Small building blocks shared by the new screens. */
object Kit {

    fun lp(ctx: Context, topDp: Int = 0, sideDp: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.topMargin = Util.dp(ctx, topDp)
        p.leftMargin = Util.dp(ctx, sideDp)
        p.rightMargin = Util.dp(ctx, sideDp)
        return p
    }

    fun title(ctx: Context, value: String): TextView {
        val t = Util.text(ctx, value, 26f, AppTheme.text, true)
        t.setPadding(Util.dp(ctx, 4), Util.dp(ctx, 4), 0, Util.dp(ctx, 4))
        return t
    }

    fun sectionLabel(ctx: Context, value: String): TextView {
        val t = Util.text(ctx, value, 12f, AppTheme.accent, true)
        t.setPadding(Util.dp(ctx, 4), 0, 0, 0)
        return t
    }

    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = Util.roundBg(ctx, AppTheme.card, 16)
        return c
    }

    fun divider(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(AppTheme.divider)
        return v
    }

    /** Adds a row to a card, with a thin line above it when it is not the first. */
    fun addRow(ctx: Context, card: LinearLayout, row: View) {
        if (card.childCount > 0) {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(ctx, 1))
            lp.leftMargin = Util.dp(ctx, 16)
            lp.rightMargin = Util.dp(ctx, 16)
            card.addView(divider(ctx), lp)
        }
        card.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    fun rowText(ctx: Context, title: String, sub: String, titleColor: Int = AppTheme.text, onClick: (() -> Unit)? = null): LinearLayout {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Util.dp(ctx, 16), Util.dp(ctx, 14), Util.dp(ctx, 16), Util.dp(ctx, 14))
        box.addView(Util.text(ctx, title, 16f, titleColor, true))
        if (sub.isNotEmpty()) box.addView(Util.text(ctx, sub, 13f, AppTheme.muted))
        if (onClick != null) box.setOnClickListener { onClick() }
        return box
    }

    fun pill(ctx: Context, label: String, color: Int, onClick: () -> Unit): TextView {
        val t = Util.text(ctx, label, 15f, 0xFFFFFFFF.toInt(), true)
        t.gravity = Gravity.CENTER
        t.setPadding(Util.dp(ctx, 18), Util.dp(ctx, 12), Util.dp(ctx, 18), Util.dp(ctx, 12))
        t.background = Util.roundBg(ctx, color, 22)
        t.setOnClickListener { onClick() }
        return t
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    /** The last 9 digits, so +234 803... and 0803... match each other. */
    fun numberKey(number: String): String = number.filter { it.isDigit() }.takeLast(9)
}
