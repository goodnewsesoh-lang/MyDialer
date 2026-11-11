package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView

/** Pick a contact or type a number to add another call during a call. */
class AddCallActivity : Activity() {

    private class Entry(val title: String, val subtitle: String, val number: String, val thumb: String?)

    private var all: List<Entry> = emptyList()
    private var shown: List<Entry> = emptyList()
    private lateinit var input: EditText
    private var pickMode = false

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? RowView) ?: RowView(this@AddCallActivity)
            val e = shown[position]
            row.bind(
                Util.avatar(this@AddCallActivity, e.title, e.thumb, Util.dp(this@AddCallActivity, 48)),
                e.title, e.subtitle
            )
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)
        pickMode = intent.getBooleanExtra("pick", false)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppTheme.bg)

        val title = Util.text(this, if (pickMode) "Choose a contact" else "Add call", 26f, AppTheme.text, true)
        title.setPadding(Util.dp(this, 20), Util.dp(this, 20), Util.dp(this, 20), Util.dp(this, 4))
        root.addView(title)

        input = EditText(this)
        input.hint = "Type a name or number"
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.isSingleLine = true
        Util.styleField(this, input)
        input.background = Util.roundBg(this, AppTheme.card, 24)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(Util.dp(this, 16), Util.dp(this, 8), Util.dp(this, 16), Util.dp(this, 8))
        root.addView(input, lp)

        val list = ListView(this)
        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(0))
        list.setOnItemClickListener { _, _, position, _ ->
            val e = shown[position]
            if (pickMode) {
                val data = Intent()
                data.putExtra("number", e.number)
                data.putExtra("name", if (e.title.startsWith("Call ")) e.number else e.title)
                setResult(RESULT_OK, data)
                finish()
            } else {
                Util.startCall(this, e.title, e.number)
                finish()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        AppTheme.style(this)
        loadContacts()
    }

    override fun finish() {
        super.finish()
        if (!pickMode && CallManager.visible().isNotEmpty()) {
            val back = Intent(this, CallActivity::class.java)
            back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(back)
        }
    }

    private fun loadContacts() {
        if (!Util.has(this, Manifest.permission.READ_CONTACTS)) return
        Thread {
            val items = ArrayList<Entry>()
            try {
                contentResolver.query(
                    Phone.CONTENT_URI,
                    arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI),
                    null, null, Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val number = c.getString(1) ?: continue
                        items.add(Entry(c.getString(0) ?: number, number, number, c.getString(2)))
                    }
                }
            } catch (e: Exception) {
                // empty list
            }
            runOnUiThread {
                all = items
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = input.text.toString().trim()
        val digits = q.filter { it.isDigit() }
        val out = ArrayList<Entry>()
        if (digits.length >= 3 && q.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            out.add(Entry("Call " + q, "Dial this number", q, null))
        }
        for (e in all) {
            val match = q.isEmpty() ||
                e.title.contains(q, ignoreCase = true) ||
                (digits.isNotEmpty() && e.number.filter { it.isDigit() }.contains(digits))
            if (match) out.add(e)
        }
        shown = out
        adapter.notifyDataSetChanged()
    }
}
