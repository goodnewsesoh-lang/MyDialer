package com.mydialer.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager

/** Saved settings. */
object Prefs {
    const val THEME = "theme"
    const val LAST_NAME_FIRST = "last_name_first"
    const val TONES = "tones"
    const val HAPTIC = "haptic"
    const val CONFIRM = "confirm"
    const val START_TAB = "start_tab"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getString(ctx: Context, key: String, def: String): String = sp(ctx).getString(key, def) ?: def
    fun putString(ctx: Context, key: String, value: String) {
        sp(ctx).edit().putString(key, value).apply()
    }

    fun getBool(ctx: Context, key: String, def: Boolean): Boolean = sp(ctx).getBoolean(key, def)
    fun putBool(ctx: Context, key: String, value: Boolean) {
        sp(ctx).edit().putBoolean(key, value).apply()
    }

    fun getInt(ctx: Context, key: String, def: Int): Int = sp(ctx).getInt(key, def)
    fun putInt(ctx: Context, key: String, value: Int) {
        sp(ctx).edit().putInt(key, value).apply()
    }
}

/** Light and dark colours. Light is the default. */
object AppTheme {
    // Colours that stay the same in both modes
    const val brand = 0xFF4F46E5.toInt()
    const val red = 0xFFE5484D.toInt()
    const val green = 0xFF22A06B.toInt()
    const val amber = 0xFFF5A623.toInt()

    var dark = false
    var bg = 0xFFF5F6FA.toInt()
    var card = 0xFFFFFFFF.toInt()
    var text = 0xFF1B1B2F.toInt()
    var muted = 0xFF6B7280.toInt()
    var divider = 0xFFE5E7EB.toInt()
    var chip = 0xFFE8E9F5.toInt()
    var accent = 0xFF4F46E5.toInt()

    /** Reads the saved choice (light, dark or system) and sets the colours. */
    fun apply(ctx: Context) {
        val mode = Prefs.getString(ctx, Prefs.THEME, "light")
        dark = when (mode) {
            "dark" -> true
            "system" ->
                (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
        if (dark) {
            bg = 0xFF0E0F14.toInt()
            card = 0xFF1A1C25.toInt()
            text = 0xFFF1F1F8.toInt()
            muted = 0xFF9CA3B5.toInt()
            divider = 0xFF2A2D3A.toInt()
            chip = 0xFF262938.toInt()
            accent = 0xFF8F8AFF.toInt()
        } else {
            bg = 0xFFF5F6FA.toInt()
            card = 0xFFFFFFFF.toInt()
            text = 0xFF1B1B2F.toInt()
            muted = 0xFF6B7280.toInt()
            divider = 0xFFE5E7EB.toInt()
            chip = 0xFFE8E9F5.toInt()
            accent = 0xFF4F46E5.toInt()
        }
    }

    /** Colours the status bar and bottom bar of the phone to match. */
    @Suppress("DEPRECATION")
    fun style(act: Activity) {
        val w = act.window
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        w.statusBarColor = bg
        w.navigationBarColor = bg
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        var flags = w.decorView.systemUiVisibility
        flags = if (dark) flags and light.inv() else flags or light
        w.decorView.systemUiVisibility = flags
    }

    fun dialogTheme(): Int =
        if (dark) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert
}
