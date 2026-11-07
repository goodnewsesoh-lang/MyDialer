package com.mydialer.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream

/** Add a new contact, or edit an existing one: any length of name, plus a photo. */
class AddContactActivity : Activity() {

    private var contactId = -1L
    private var rawId = -1L
    private var photoBytes: ByteArray? = null
    private var photoChanged = false

    private lateinit var photoView: ImageView
    private lateinit var nameField: EditText
    private lateinit var phoneField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactId = intent.getLongExtra("contactId", -1L)
        val editing = contactId >= 0

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFFFFFFFF.toInt())
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(Util.dp(this, 24), Util.dp(this, 32), Util.dp(this, 24), Util.dp(this, 32))
        scroll.addView(root)

        val title = Util.text(this, if (editing) "Edit contact" else "New contact", 22f, 0xFF212121.toInt(), true)
        root.addView(title)

        photoView = ImageView(this)
        photoView.setImageBitmap(Util.drawAvatar(null, null, Util.dp(this, 120)))
        photoView.setOnClickListener { pickPhoto() }
        val photoLp = LinearLayout.LayoutParams(Util.dp(this, 120), Util.dp(this, 120))
        photoLp.topMargin = Util.dp(this, 24)
        root.addView(photoView, photoLp)

        val pickLabel = Util.text(this, "Tap the picture to choose a photo", 13f, Util.GREY)
        pickLabel.gravity = Gravity.CENTER
        root.addView(pickLabel)

        nameField = EditText(this)
        nameField.hint = "Full name (any length)"
        nameField.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        nameField.setLines(2)
        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = Util.dp(this, 24)
        root.addView(nameField, nameLp)

        phoneField = EditText(this)
        phoneField.hint = "Phone number"
        phoneField.inputType = InputType.TYPE_CLASS_PHONE
        root.addView(
            phoneField,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val save = Util.text(this, "Save", 17f, 0xFFFFFFFF.toInt(), true)
        save.gravity = Gravity.CENTER
        save.background = Util.roundBg(this, Util.BLUE, 28)
        save.setOnClickListener { save() }
        val saveLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(this, 56))
        saveLp.topMargin = Util.dp(this, 32)
        root.addView(save, saveLp)

        val cancel = Util.text(this, "Cancel", 16f, Util.GREY, true)
        cancel.gravity = Gravity.CENTER
        cancel.setOnClickListener { finish() }
        val cancelLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(this, 48))
        cancelLp.topMargin = Util.dp(this, 8)
        root.addView(cancel, cancelLp)

        if (editing) {
            val delete = Util.text(this, "Delete contact", 16f, Util.RED, true)
            delete.gravity = Gravity.CENTER
            delete.setOnClickListener { confirmDelete() }
            root.addView(
                delete,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(this, 48))
            )
            loadExisting()
        }

        setContentView(scroll)
    }

    // ---------- Loading an existing contact ----------

    private fun loadExisting() {
        var name: String? = null
        var photoUri: String? = null
        try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_URI),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    photoUri = c.getString(1)
                }
            }
            contentResolver.query(
                Phone.CONTENT_URI, arrayOf(Phone.NUMBER),
                Phone.CONTACT_ID + "=?", arrayOf(contactId.toString()), null
            )?.use { c ->
                if (c.moveToFirst()) phoneField.setText(c.getString(0))
            }
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                ContactsContract.RawContacts.CONTACT_ID + "=? AND " + ContactsContract.RawContacts.DELETED + "=0",
                arrayOf(contactId.toString()), null
            )?.use { c ->
                if (c.moveToFirst()) rawId = c.getLong(0)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load contact: " + e.message, Toast.LENGTH_LONG).show()
        }
        nameField.setText(name ?: "")
        photoView.setImageBitmap(Util.avatar(this, name, photoUri, Util.dp(this, 120)))
    }

    // ---------- Photo ----------

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, 2001)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK && data?.data != null) {
            val bmp = Util.loadPicked(this, data.data!!, 720)
            if (bmp == null) {
                Toast.makeText(this, "Could not open that picture", Toast.LENGTH_LONG).show()
                return
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            photoBytes = out.toByteArray()
            photoChanged = true
            photoView.setImageBitmap(Util.drawAvatar(bmp, null, Util.dp(this, 120)))
        }
    }

    // ---------- Saving ----------

    private fun save() {
        val name = nameField.text.toString().trim()
        val phone = phoneField.text.toString().trim()
        if (name.isEmpty() && phone.isEmpty()) {
            Toast.makeText(this, "Please type a name or a number", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (contactId < 0) insertContact(name, phone) else updateContact(name, phone)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not save: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun insertContact(name: String, phone: String) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        if (name.isNotEmpty()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, name)
                    .build()
            )
        }
        if (phone.isNotEmpty()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )
        }
        val bytes = photoBytes
        if (bytes != null) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, bytes)
                    .build()
            )
        }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun findDataId(mimeType: String): Long {
        var id = -1L
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
            arrayOf(rawId.toString(), mimeType), null
        )?.use { c ->
            if (c.moveToFirst()) id = c.getLong(0)
        }
        return id
    }

    private fun updateContact(name: String, phone: String) {
        if (rawId < 0) throw IllegalStateException("contact not found")
        val ops = ArrayList<ContentProviderOperation>()
        val data = ContactsContract.Data.CONTENT_URI
        val idSelection = ContactsContract.Data._ID + "=?"

        if (name.isNotEmpty()) {
            val id = findDataId(StructuredName.CONTENT_ITEM_TYPE)
            if (id >= 0) {
                ops.add(
                    ContentProviderOperation.newUpdate(data)
                        .withSelection(idSelection, arrayOf(id.toString()))
                        .withValue(StructuredName.DISPLAY_NAME, name)
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newInsert(data)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, name)
                        .build()
                )
            }
        }

        if (phone.isNotEmpty()) {
            val id = findDataId(Phone.CONTENT_ITEM_TYPE)
            if (id >= 0) {
                ops.add(
                    ContentProviderOperation.newUpdate(data)
                        .withSelection(idSelection, arrayOf(id.toString()))
                        .withValue(Phone.NUMBER, phone)
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newInsert(data)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, phone)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .build()
                )
            }
        }

        val bytes = photoBytes
        if (photoChanged && bytes != null) {
            ops.add(
                ContentProviderOperation.newDelete(data)
                    .withSelection(
                        ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                        arrayOf(rawId.toString(), Photo.CONTENT_ITEM_TYPE)
                    )
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(data)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, bytes)
                    .build()
            )
        }

        if (ops.isNotEmpty()) contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete this contact?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    contentResolver.delete(
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                        null, null
                    )
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not delete: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
