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
    private lateinit var callLog: CallLogView
    private lateinit var contacts: ContactsView
    private lateinit var keypad: KeypadView
    private val tabs = ArrayList<TextView>()
    private var selected = 0

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFFFFFFF.toInt())

        banner = Util.text(this, "Tap here to make My Dialer your default Phone app", 15f, 0xFFFFFFFF.toInt(), true)
        banner.gravity = Gravity.CENTER
        banner.setBackgroundColor(Util.BLUE)
        banner.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 14))
        banner.setOnClickListener { askToBeDefaultDialer() }
        root.addView(banner)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setBackgroundColor(0xFFF5F5F5.toInt())
        val names = listOf("Recents", "Contacts", "Keypad")
        for (i in names.indices) {
            val t = Util.text(this, names[i], 15f, Util.GREY, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, Util.dp(this, 16), 0, Util.dp(this, 16))
            t.setOnClickListener { select(i) }
            tabBar.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            tabs.add(t)
        }
        root.addView(tabBar)

        setContentView(root)

        callLog = CallLogView(this)
        contacts = ContactsView(this)
        keypad = KeypadView(this)
        select(0)

        askForPermissions()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        banner.visibility = if (isDefaultDialer()) View.GONE else View.VISIBLE
        callLog.refresh()
        contacts.refresh()
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "tel") {
            keypad.setNumber(Uri.decode(data.schemeSpecificPart ?: ""))
            select(2)
        }
    }

    private fun select(index: Int) {
        selected = index
        content.removeAllViews()
        val view: View = when (index) {
            0 -> callLog
            1 -> contacts
            else -> keypad
        }
        content.addView(view)
        for (i in tabs.indices) {
            tabs[i].setTextColor(if (i == index) Util.BLUE else Util.GREY)
        }
    }

    private fun askForPermissions() {
        val missing = permissions.filter { !Util.has(this, it) }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        callLog.refresh()
        contacts.refresh()
    }

    private fun isDefaultDialer(): Boolean {
        val tm = getSystemService(TelecomManager::class.java)
        return packageName == tm.defaultDialerPackage
    }

    private fun askToBeDefaultDialer() {
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
        banner.visibility = if (isDefaultDialer()) View.GONE else View.VISIBLE
    }
}
