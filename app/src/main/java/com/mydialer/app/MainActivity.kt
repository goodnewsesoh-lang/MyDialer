package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var banner: TextView
    private lateinit var recents: RecentsView
    private lateinit var favorites: FavoritesView
    private lateinit var contacts: ContactsView
    private lateinit var keypad: KeypadView
    private lateinit var settings: SettingsView

    private val navIcons = ArrayList<ImageView>()
    private val navLabels = ArrayList<TextView>()
    private var selected = 0

    private val tabNames = listOf("Recents", "Favorites", "Contacts", "Keypad", "Settings")
    private val tabIcons = listOf(
        R.drawable.ic_history,
        R.drawable.ic_star,
        R.drawable.ic_person,
        R.drawable.ic_dialpad,
        R.drawable.ic_settings
    )

    private val permissions: Array<String>
        get() {
            val list = arrayListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            return list.toTypedArray()
        }

    private fun dp(value: Int): Int = Util.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppTheme.bg)

        banner = Util.text(this, "Make My Dialer your default Phone app. Tap here.", 13f, 0xFFFFFFFF.toInt(), true)
        banner.gravity = Gravity.CENTER
        banner.setBackgroundColor(AppTheme.brand)
        banner.setPadding(dp(16), dp(10), dp(16), dp(10))
        banner.setOnClickListener { askToBeDefaultDialer() }
        root.addView(banner)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildNav())

        setContentView(root)
        AppTheme.style(this)

        recents = RecentsView(this)
        favorites = FavoritesView(this)
        contacts = ContactsView(this)
        keypad = KeypadView(this)
        settings = SettingsView(this)

        selected = savedInstanceState?.getInt("tab") ?: Prefs.getInt(this, Prefs.START_TAB, 0)
        select(selected)

        askForPermissions()
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", selected)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateBanner()
        refreshAll()
    }

    private fun refreshAll() {
        recents.refresh()
        favorites.refresh()
        contacts.refresh()
        settings.refresh()
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "tel") {
            keypad.setNumber(Uri.decode(data.schemeSpecificPart ?: ""))
            select(3)
        }
    }

    // ---------- Bottom navigation bar ----------

    private fun buildNav(): FrameLayout {
        val wrap = FrameLayout(this)
        wrap.clipChildren = false
        wrap.setBackgroundColor(AppTheme.bg)
        wrap.setPadding(dp(12), dp(22), dp(12), dp(8))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.clipChildren = false
        bar.background = Util.roundBg(this, AppTheme.card, 28)
        bar.elevation = dp(6).toFloat()

        for (i in tabNames.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.clipChildren = false
            item.setPadding(0, dp(10), 0, dp(8))

            val slot = FrameLayout(this)
            slot.clipChildren = false
            val icon = ImageView(this)
            icon.setImageResource(tabIcons[i])
            slot.addView(icon, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            item.addView(slot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)))

            val label = Util.text(this, tabNames[i], 11f, AppTheme.muted, false)
            label.gravity = Gravity.CENTER
            label.maxLines = 1
            val labelLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            labelLp.topMargin = dp(4)
            item.addView(label, labelLp)

            item.setOnClickListener { select(i) }
            bar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            navIcons.add(icon)
            navLabels.add(label)
        }
        wrap.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return wrap
    }

    private fun updateNav() {
        for (i in navIcons.indices) {
            val on = i == selected
            val icon = navIcons[i]
            val size = dp(if (on) 50 else 24)
            val gravity = if (on) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            icon.layoutParams = FrameLayout.LayoutParams(size, size, gravity)
            icon.translationY = if (on) -dp(26).toFloat() else 0f
            val pad = if (on) dp(13) else 0
            icon.setPadding(pad, pad, pad, pad)
            if (on) {
                val circle = GradientDrawable()
                circle.shape = GradientDrawable.OVAL
                circle.setColor(AppTheme.card)
                circle.setStroke(dp(5), AppTheme.bg)
                icon.background = circle
            } else {
                icon.background = null
            }
            icon.setColorFilter(if (on) AppTheme.accent else AppTheme.muted)
            navLabels[i].setTextColor(if (on) AppTheme.text else AppTheme.muted)
            navLabels[i].setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun select(index: Int) {
        selected = index
        content.removeAllViews()
        val view: View = when (index) {
            0 -> recents
            1 -> favorites
            2 -> contacts
            3 -> keypad
            else -> settings
        }
        content.addView(view)
        updateNav()
        when (index) {
            0 -> recents.refresh()
            1 -> favorites.refresh()
            2 -> contacts.refresh()
            4 -> settings.refresh()
        }
    }

    // ---------- Permissions and default phone app ----------

    private fun askForPermissions() {
        val missing = permissions.filter { !Util.has(this, it) }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAll()
    }

    fun isDefaultDialer(): Boolean {
        val tm = getSystemService(TelecomManager::class.java)
        return packageName == tm.defaultDialerPackage
    }

    private fun updateBanner() {
        banner.visibility = if (isDefaultDialer()) View.GONE else View.VISIBLE
    }

    fun askToBeDefaultDialer() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 1001)
                return
            }
        }
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
        intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateBanner()
        settings.refresh()
    }
}
