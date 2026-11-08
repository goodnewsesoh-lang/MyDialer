package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var banner: TextView
    private lateinit var nav: NavBarView
    private lateinit var recents: RecentsView
    private lateinit var favorites: FavoritesView
    private lateinit var contacts: ContactsView
    private lateinit var keypad: KeypadView
    private lateinit var settings: SettingsView

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

        nav = NavBarView(this, tabNames, tabIcons) { index -> select(index) }
        root.addView(nav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        AppTheme.style(this)

        recents = RecentsView(this)
        favorites = FavoritesView(this)
        contacts = ContactsView(this)
        keypad = KeypadView(this)
        settings = SettingsView(this)

        val startTab = intent.getIntExtra("tab", -1)
        selected = when {
            savedInstanceState != null -> savedInstanceState.getInt("tab")
            startTab in 0..4 -> startTab
            else -> Prefs.getInt(this, Prefs.START_TAB, 0)
        }
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
        val tab = intent.getIntExtra("tab", -1)
        if (tab in 0..4) select(tab)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateBanner()
        refreshAll()
    }

    private fun refreshAll() {
        if (selected == 0) Util.markMissedSeen(this)
        recents.refresh()
        favorites.refresh()
        contacts.refresh()
        keypad.refresh()
        settings.refresh()
        updateMissedDot()
    }

    /** Shows or hides the red dot on the Recents icon. */
    private fun updateMissedDot() {
        Thread {
            val count = if (selected == 0) 0 else Util.unseenMissedCount(this)
            runOnUiThread { nav.setBadge(0, count > 0) }
        }.start()
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "tel") {
            keypad.setNumber(Uri.decode(data.schemeSpecificPart ?: ""))
            select(3)
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
        nav.selected = index

        when (index) {
            0 -> {
                Util.markMissedSeen(this)
                recents.refresh()
            }
            1 -> favorites.refresh()
            2 -> contacts.refresh()
            3 -> keypad.refresh()
            4 -> settings.refresh()
        }
        updateMissedDot()
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
