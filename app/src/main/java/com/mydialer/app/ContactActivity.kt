package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One contact: call history, talk time, buttons, own SIM, own ringtone, block. */
class ContactActivity : Activity() {

    private class History(val callId: Long, val type: Int, val date: Long, val duration: Long, val accountId: String?)

    private var contactId = -1L
    private lateinit var body: LinearLayout
    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)
        contactId = intent.getLongExtra("contactId", -1L)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(AppTheme.bg)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val pad = Util.dp(this, 16)
        body.setPadding(pad, pad, pad, pad)
        scroll.addView(body)
        setContentView(scroll)
        AppTheme.style(this)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (contactId < 0 || !Util.has(this, Manifest.permission.READ_CONTACTS)) {
            finish()
            return
        }
        Thread {
            var name: String? = null
            var photo: String? = null
            var starred = false
            var ringtone: String? = null
            try {
                contentResolver.query(
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                    arrayOf(
                        Util.nameColumn(this), ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.STARRED, ContactsContract.Contacts.CUSTOM_RINGTONE
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        photo = c.getString(1)
                        starred = c.getInt(2) == 1
                        ringtone = c.getString(3)
                    }
                }
            } catch (e: Exception) {
                // handled below
            }
            val numbers = Util.numbersOf(this, contactId)
            val keys = numbers.map { Kit.numberKey(it) }.toSet()
            val hist = ArrayList<History>()
            if (keys.isNotEmpty() && Util.has(this, Manifest.permission.READ_CALL_LOG)) {
                try {
                    contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(
                            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE,
                            CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID
                        ),
                        null, null, CallLog.Calls.DATE + " DESC"
                    )?.use { c ->
                        var scanned = 0
                        while (c.moveToNext() && scanned < 5000 && hist.size < 200) {
                            scanned++
                            val n = c.getString(1) ?: continue
                            if (keys.contains(Kit.numberKey(n))) {
                                hist.add(History(c.getLong(0), c.getInt(2), c.getLong(3), c.getLong(4), c.getString(5)))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // no history
                }
            }
            val sims = Util.sims(this)
            val blocked = numbers.isNotEmpty() && Util.canBlock(this) && Util.isBlocked(this, numbers[0])
            val finalName = name
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (finalName == null) {
                    finish()
                } else {
                    build(finalName, photo, starred, ringtone, numbers, hist, sims, blocked)
                }
            }
        }.start()
    }

    private fun circleAction(iconRes: Int, label: String, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val icon = Util.icon(this, iconRes, 0xFFFFFFFF.toInt())
        val p = Util.dp(this, 14)
        icon.setPadding(p, p, p, p)
        icon.background = Util.roundBg(this, AppTheme.brand, 26)
        box.addView(icon, LinearLayout.LayoutParams(Util.dp(this, 52), Util.dp(this, 52)))
        val t = Util.text(this, label, 12f, AppTheme.text)
        t.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Util.dp(this, 4)
        box.addView(t, lp)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun build(
        name: String, photo: String?, starred: Boolean, ringtone: String?,
        numbers: List<String>, hist: List<History>, sims: List<Util.Sim>, blocked: Boolean
    ) {
        body.removeAllViews()

        val avatar = ImageView(this)
        avatar.setImageBitmap(Util.avatar(this, name, photo, Util.dp(this, 110)))
        val alp = LinearLayout.LayoutParams(Util.dp(this, 110), Util.dp(this, 110))
        alp.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(avatar, alp)

        val nameView = Util.text(this, name, 24f, AppTheme.text, true)
        nameView.gravity = Gravity.CENTER
        body.addView(nameView, Kit.lp(this, 10))

        // Buttons
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        val first = if (numbers.isNotEmpty()) numbers[0] else ""
        val list = ArrayList<LinearLayout>()
        if (first.isNotEmpty()) {
            list.add(circleAction(R.drawable.ic_call, "Call") { Util.callOrAsk(this, name, first, contactId) })
            list.add(circleAction(R.drawable.ic_sms, "SMS") { Util.openSms(this, first) })
            list.add(circleAction(R.drawable.ic_chat, "WhatsApp") { Util.openWhatsApp(this, first) })
        }
        list.add(circleAction(R.drawable.ic_edit, "Edit") {
            val i = Intent(this, AddContactActivity::class.java)
            i.putExtra("contactId", contactId)
            startActivity(i)
        })
        list.add(circleAction(R.drawable.ic_star, if (starred) "Unfavorite" else "Favorite") {
            Util.setStarred(this, contactId, !starred)
            load()
        })
        for (a in list) actions.addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(actions, Kit.lp(this, 16))

        // Numbers
        val numCard = Kit.card(this)
        for (n in numbers) {
            Kit.addRow(this, numCard, Kit.rowText(this, n, "Tap to call") {
                Util.callOrAsk(this, name, n, contactId)
            })
        }
        if (numbers.isNotEmpty()) body.addView(numCard, Kit.lp(this, 18))

        // Talk time summary
        var talk = 0L
        for (h in hist) talk += h.duration
        val sumCard = Kit.card(this)
        Kit.addRow(this, sumCard, Kit.rowText(this, "Calls", hist.size.toString()))
        Kit.addRow(this, sumCard, Kit.rowText(this, "Total talk time", if (talk == 0L) "None yet" else Kit.duration(talk)))
        Kit.addRow(
            this, sumCard,
            Kit.rowText(this, "Last call", if (hist.isEmpty()) "Never" else dateFormat.format(Date(hist[0].date)))
        )
        body.addView(sumCard, Kit.lp(this, 14))

        // Options
        body.addView(Kit.sectionLabel(this, "FOR THIS CONTACT"), Kit.lp(this, 22))
        val opt = Kit.card(this)
        if (sims.size >= 2) {
            val box = Kit.rowText(this, "SIM for calls", "Which SIM to use when calling this contact.")
            val labels = ArrayList<String>()
            labels.add("Use setting")
            for (s in sims) labels.add(s.short)
            val cur = Prefs.getString(this, "sim_c_$contactId", "")
            var selected = 0
            for (i in sims.indices) if (cur == "sim:" + sims[i].handle.id) selected = i + 1
            val chips = Util.chipGroup(this, labels, selected) { index ->
                Prefs.putString(this, "sim_c_$contactId", if (index == 0) "" else "sim:" + sims[index - 1].handle.id)
            }
            box.addView(chips, Kit.lp(this, 10))
            Kit.addRow(this, opt, box)
        }
        val toneName = ringtoneTitle(ringtone)
        Kit.addRow(this, opt, Kit.rowText(this, "Ringtone", toneName) { pickRingtone(ringtone) })
        if (numbers.isNotEmpty() && Util.canBlock(this)) {
            Kit.addRow(
                this, opt,
                Kit.rowText(
                    this, if (blocked) "Unblock this contact" else "Block this contact",
                    if (blocked) "Calls are blocked." else "Stop calls from their numbers.",
                    if (blocked) AppTheme.text else AppTheme.red
                ) {
                    if (blocked) {
                        for (n in numbers) Util.unblock(this, n)
                        load()
                    } else {
                        AlertDialog.Builder(this, AppTheme.dialogTheme())
                            .setTitle("Block $name?")
                            .setMessage("Calls from all of their numbers will be stopped.")
                            .setPositiveButton("Block") { _, _ ->
                                for (n in numbers) Util.block(this, n)
                                load()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            )
        }
        body.addView(opt, Kit.lp(this, 8))

        // History
        body.addView(Kit.sectionLabel(this, "CALL HISTORY (TAP TO ADD A NOTE)"), Kit.lp(this, 22))
        val histCard = Kit.card(this)
        if (hist.isEmpty()) {
            histCard.addView(Kit.rowText(this, "No calls yet", ""))
        }
        for (h in hist.take(60)) {
            val kind = when (h.type) {
                CallLog.Calls.MISSED_TYPE -> "Missed"
                CallLog.Calls.INCOMING_TYPE -> "Received"
                CallLog.Calls.OUTGOING_TYPE -> "Dialed"
                CallLog.Calls.REJECTED_TYPE -> "Declined"
                6 -> "Blocked"
                else -> "Call"
            }
            val parts = ArrayList<String>()
            parts.add(dateFormat.format(Date(h.date)))
            if (h.duration > 0) parts.add(Kit.duration(h.duration))
            val sim = Util.simName(sims, h.accountId)
            if (sim != null) parts.add(sim)
            var sub = parts.joinToString(" · ")
            val note = Notes.get(this, h.callId)
            if (note.isNotEmpty()) sub += "\n✎ " + note
            val color = if (h.type == CallLog.Calls.MISSED_TYPE) AppTheme.red else AppTheme.text
            Kit.addRow(this, histCard, Kit.rowText(this, kind, sub, color) {
                Notes.edit(this, h.callId, name) { load() }
            })
        }
        body.addView(histCard, Kit.lp(this, 8))
    }

    private fun ringtoneTitle(uri: String?): String {
        if (uri.isNullOrEmpty()) return "Default"
        return try {
            RingtoneManager.getRingtone(this, Uri.parse(uri))?.getTitle(this) ?: "Custom"
        } catch (e: Exception) {
            "Custom"
        }
    }

    @Suppress("DEPRECATION")
    private fun pickRingtone(current: String?) {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ringtone for this contact")
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        if (!current.isNullOrEmpty()) {
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
        }
        try {
            startActivityForResult(i, 31)
        } catch (e: Exception) {
            Toast.makeText(this, "No ringtone picker found", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 31 || resultCode != RESULT_OK) return
        val picked = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val value: String? = if (picked == null || picked == Settings.System.DEFAULT_RINGTONE_URI) null else picked.toString()
        try {
            val values = ContentValues()
            values.put(ContactsContract.Contacts.CUSTOM_RINGTONE, value)
            contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), values, null, null
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save the ringtone", Toast.LENGTH_SHORT).show()
        }
        load()
    }
}
