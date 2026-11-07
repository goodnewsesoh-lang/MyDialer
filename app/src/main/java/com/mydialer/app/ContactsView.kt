package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import android.widget.TextView

/** All your contacts, with photos, search and an Add button. */
class ContactsView(private val act: Activity) : LinearLayout(act) {

    class Contact(val id: Long, val name: String, val thumbUri: String?)

    private val search = EditText(act)
    private val list = ListView(act)
    private val message = Util.text(act, "", 16f, Util.GREY)
    private var all: List<Contact> = emptyList()
    private var shown: List<Contact> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = shown[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? RowView) ?: RowView(act)
            val c = shown[position]
            row.bind(Util.avatar(act, c.name, c.thumbUri, Util.dp(act, 48)), c.name, "", 0xFF212121.toInt())
            return row
        }
    }

    init {
        orientation = LinearLayout.VERTICAL

        val top = LinearLayout(act)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(Util.dp(act, 12), Util.dp(act, 8), Util.dp(act, 12), Util.dp(act, 8))

        search.hint = "Search contacts"
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })
        top.addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val add = Util.text(act, "+ Add", 15f, 0xFFFFFFFF.toInt(), true)
        add.gravity = Gravity.CENTER
        add.setPadding(Util.dp(act, 16), Util.dp(act, 10), Util.dp(act, 16), Util.dp(act, 10))
        add.background = Util.roundBg(act, Util.BLUE, 20)
        add.setOnClickListener {
            act.startActivity(Intent(act, AddContactActivity::class.java))
        }
        val addLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        addLp.leftMargin = Util.dp(act, 8)
        top.addView(add, addLp)
        addView(top)

        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24))
        message.visibility = View.GONE
        addView(message)

        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> openContact(shown[position]) }
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
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                )
                act.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI, cols,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1", null,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE NOCASE ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        items.add(Contact(c.getLong(0), c.getString(1) ?: "(No name)", c.getString(2)))
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

    private fun openContact(contact: Contact) {
        val numbers = ArrayList<String>()
        try {
            act.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                arrayOf(contact.id.toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank() && !numbers.contains(n)) numbers.add(n)
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        val options = ArrayList<String>()
        for (n in numbers) options.add("Call $n")
        options.add("Edit contact")

        AlertDialog.Builder(act)
            .setTitle(contact.name)
            .setItems(options.toTypedArray()) { _, which ->
                if (which < numbers.size) {
                    Util.call(act, numbers[which])
                } else {
                    val i = Intent(act, AddContactActivity::class.java)
                    i.putExtra("contactId", contact.id)
                    act.startActivity(i)
                }
            }
            .show()
    }
}
