package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView

/** All your contacts, with photos, search and an Add button. */
class ContactsView(private val act: Activity) : LinearLayout(act) {

    class Contact(val id: Long, val name: String, val thumbUri: String?, val starred: Boolean)

    private val search = EditText(act)
    private val list = ListView(act)
    private val message = Util.text(act, "", 16f, AppTheme.muted)
    private var all: List<Contact> = emptyList()
    private var shown: List<Contact> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = shown[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? RowView) ?: RowView(act)
            val c = shown[position]
            row.bind(
                Util.avatar(act, c.name, c.thumbUri, Util.dp(act, 48)),
                c.name, "", AppTheme.text,
                if (c.starred) R.drawable.ic_star else 0, AppTheme.amber, ""
            )
            return row
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AppTheme.bg)

        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(Util.dp(act, 20), Util.dp(act, 16), Util.dp(act, 16), Util.dp(act, 4))
        val header = Util.text(act, "Contacts", 26f, AppTheme.text, true)
        top.addView(header, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val add = Util.icon(act, R.drawable.ic_add, 0xFFFFFFFF.toInt())
        val pad = Util.dp(act, 10)
        add.setPadding(pad, pad, pad, pad)
        add.background = Util.roundBg(act, AppTheme.brand, 22)
        add.setOnClickListener {
            act.startActivity(Intent(act, AddContactActivity::class.java))
        }
        top.addView(add, LinearLayout.LayoutParams(Util.dp(act, 44), Util.dp(act, 44)))
        addView(top)

        search.hint = "Search contacts"
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        Util.styleField(act, search)
        search.background = Util.roundBg(act, AppTheme.card, 24)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })
        val searchLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        searchLp.setMargins(Util.dp(act, 16), Util.dp(act, 8), Util.dp(act, 16), Util.dp(act, 8))
        addView(search, searchLp)

        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24))
        message.visibility = View.GONE
        addView(message)

        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(0))
        list.clipToPadding = false
        list.setPadding(0, Util.dp(act, 4), 0, Util.dp(act, 8))
        list.setOnItemClickListener { _, _, position, _ ->
            val c = shown[position]
            val i = Intent(act, ContactActivity::class.java)
            i.putExtra("contactId", c.id)
            act.startActivity(i)
        }
        addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (!Util.has(act, Manifest.permission.READ_CONTACTS)) {
            message.text = "Allow the Contacts permission to see your contacts."
            message.visibility = View.VISIBLE
            return
        }
        Thread {
            val items = ArrayList<Contact>()
            try {
                val cols = arrayOf(
                    ContactsContract.Contacts._ID,
                    Util.nameColumn(act),
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.STARRED
                )
                act.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI, cols,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1", null,
                    Util.sortColumn(act)
                )?.use { c ->
                    while (c.moveToNext()) {
                        items.add(
                            Contact(c.getLong(0), c.getString(1) ?: "(No name)", c.getString(2), c.getInt(3) == 1)
                        )
                    }
                }
            } catch (e: Exception) {
                // show an empty list
            }
            post {
                all = items
                message.visibility = View.GONE
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        shown = if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
        if (shown.isEmpty()) {
            message.text = "No contacts found."
            message.visibility = View.VISIBLE
        } else {
            message.visibility = View.GONE
        }
    }
}
