package com.mydialer.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Settings: dark mode and other options. */
class SettingsView(private val act: MainActivity) : ScrollView(act) {

    private val body = LinearLayout(act)

    init {
        setBackgroundColor(AppTheme.bg)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(Util.dp(act, 16), Util.dp(act, 16), Util.dp(act, 16), Util.dp(act, 16))
        addView(body)
        build()
    }

    fun refresh() {
        build()
    }

    private fun matchWrap(topDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Util.dp(act, topDp)
        return lp
    }

    private fun build() {
        body.removeAllViews()

        val title = Util.text(act, "Settings", 26f, AppTheme.text, true)
        title.setPadding(Util.dp(act, 4), 0, 0, 0)
        body.addView(title)

        // ----- Appearance -----
        val appearance = section("APPEARANCE")
        val themeValues = listOf("light", "dark", "system")
        val currentTheme = themeValues.indexOf(Prefs.getString(act, Prefs.THEME, "light")).coerceAtLeast(0)
        addRow(appearance, choiceRow(
            "Theme", "Light is the default. Dark is easier on the eyes at night.",
            listOf("Light", "Dark", "Follow phone"), currentTheme
        ) { index ->
            Prefs.putString(act, Prefs.THEME, themeValues[index])
            act.recreate()
        })

        // ----- Contacts -----
        val contacts = section("CONTACTS")
        val order = if (Prefs.getBool(act, Prefs.LAST_NAME_FIRST, false)) 1 else 0
        addRow(contacts, choiceRow(
            "Name order", "How names are shown and sorted.",
            listOf("First name first", "Last name first"), order
        ) { index ->
            Prefs.putBool(act, Prefs.LAST_NAME_FIRST, index == 1)
        })

        // ----- Calls and keypad -----
        val calls = section("CALLS AND KEYPAD")
        addRow(calls, switchRow("Keypad tones", "Play a sound when you tap a key.", Prefs.TONES, true))
        addRow(calls, switchRow("Vibrate on keypad tap", "A small buzz when you tap a key.", Prefs.HAPTIC, true))
        addRow(calls, switchRow("Ask before calling", "Shows a question before calling from Recents, Favorites and Contacts.", Prefs.CONFIRM, false))

        // ----- Start screen -----
        val start = section("START SCREEN")
        val startTab = Prefs.getInt(act, Prefs.START_TAB, 0).coerceIn(0, 3)
        addRow(start, choiceRow(
            "Open the app on", "The screen you see first.",
            listOf("Recents", "Favorites", "Contacts", "Keypad"), startTab
        ) { index ->
            Prefs.putInt(act, Prefs.START_TAB, index)
        })

        // ----- Contact photos -----
        val photos = section("CONTACT PHOTOS")
        addRow(photos, actionRow("Photo helper", "Go through contacts without a picture, one by one.") {
            act.startActivity(Intent(act, PhotoHelperActivity::class.java))
        })
        addRow(photos, actionRow("Import photos from a folder", "Name each picture after the contact, like Mum.jpg.") {
            act.startActivity(Intent(act, ImportPhotosActivity::class.java))
        })
        addRow(photos, actionRow("Sync contacts", "Open Android sync settings. Contacts saved to your Google account can show photos and are backed up.") {
            act.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
        })

        // ----- Call recording -----
        val recording = section("CALL RECORDING")
        addRow(recording, textsBlock(
            "Where recordings go",
            "Recordings are saved in Music > MyDialer. Android blocks apps from recording the other person directly, so turn Speaker on to record both sides. Tell the other person you are recording."
        ))

        // ----- Phone app -----
        val phone = section("PHONE APP")
        addRow(phone, defaultRow())
        addRow(phone, actionRow("App permissions", "Open Android settings to check permissions.") {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.fromParts("package", act.packageName, null)
            act.startActivity(i)
        })

        // ----- About -----
        val about = section("ABOUT")
        addRow(about, textsBlock("My Dialer", "Version " + versionName()))
    }

    // ---------- Building blocks ----------

    private fun section(name: String): LinearLayout {
        val label = Util.text(act, name, 12f, AppTheme.accent, true)
        label.setPadding(Util.dp(act, 4), 0, 0, 0)
        body.addView(label, matchWrap(22))

        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = Util.roundBg(act, AppTheme.card, 16)
        body.addView(card, matchWrap(8))
        return card
    }

    private fun addRow(card: LinearLayout, row: View) {
        if (card.childCount > 0) {
            val line = View(act)
            line.setBackgroundColor(AppTheme.divider)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(act, 1))
            lp.leftMargin = Util.dp(act, 16)
            lp.rightMargin = Util.dp(act, 16)
            card.addView(line, lp)
        }
        card.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun textsBlock(title: String, desc: String): LinearLayout {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Util.dp(act, 16), Util.dp(act, 14), Util.dp(act, 16), Util.dp(act, 14))
        box.addView(Util.text(act, title, 16f, AppTheme.text, true))
        if (desc.isNotEmpty()) {
            box.addView(Util.text(act, desc, 13f, AppTheme.muted))
        }
        return box
    }

    private fun switchRow(title: String, desc: String, key: String, default: Boolean): LinearLayout {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(textsBlock(title, desc), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(act)
        sw.isChecked = Prefs.getBool(act, key, default)
        sw.setOnCheckedChangeListener { _, on -> Prefs.putBool(act, key, on) }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.rightMargin = Util.dp(act, 16)
        row.addView(sw, lp)
        return row
    }

    private fun actionRow(title: String, desc: String, action: () -> Unit): LinearLayout {
        val row = textsBlock(title, desc)
        row.setOnClickListener { action() }
        return row
    }

    private fun choiceRow(
        title: String,
        desc: String,
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit
    ): LinearLayout {
        val box = textsBlock(title, desc)

        val scroll = HorizontalScrollView(act)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()

        fun style(sel: Int) {
            for (i in chips.indices) {
                val on = i == sel
                chips[i].setTextColor(if (on) 0xFFFFFFFF.toInt() else AppTheme.text)
                chips[i].background = Util.roundBg(act, if (on) AppTheme.brand else AppTheme.chip, 18)
            }
        }

        for (i in options.indices) {
            val chip = Util.text(act, options[i], 14f, AppTheme.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(Util.dp(act, 16), Util.dp(act, 8), Util.dp(act, 16), Util.dp(act, 8))
            chip.setOnClickListener {
                style(i)
                onPick(i)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = Util.dp(act, 8)
            row.addView(chip, lp)
            chips.add(chip)
        }
        style(selected)
        scroll.addView(row)
        box.addView(scroll, matchWrap(10))
        return box
    }

    private fun defaultRow(): LinearLayout {
        val isDefault = act.isDefaultDialer()
        val box = textsBlock(
            "Default Phone app",
            if (isDefault) "My Dialer is your default Phone app." else "My Dialer is not your default Phone app yet."
        )
        if (!isDefault) {
            val button = Util.text(act, "Make default", 14f, 0xFFFFFFFF.toInt(), true)
            button.gravity = Gravity.CENTER
            button.setPadding(Util.dp(act, 18), Util.dp(act, 10), Util.dp(act, 18), Util.dp(act, 10))
            button.background = Util.roundBg(act, AppTheme.brand, 20)
            button.setOnClickListener { act.askToBeDefaultDialer() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Util.dp(act, 10)
            box.addView(button, lp)
        }
        return box
    }

    @Suppress("DEPRECATION")
    private fun versionName(): String {
        return try {
            act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
