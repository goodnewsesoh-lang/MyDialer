package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Goes through the contacts that have no photo, one by one, so you can add pictures quickly. */
class PhotoHelperActivity : Activity() {

    private class Item(val id: Long, val name: String)

    private var items: List<Item> = emptyList()
    private var index = 0
    private var added = 0

    private lateinit var progressView: TextView
    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var infoView: TextView
    private lateinit var chooseButton: TextView
    private lateinit var skipButton: TextView
    private lateinit var closeButton: TextView

    private fun dp(value: Int): Int = Util.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(AppTheme.bg)
        root.setPadding(dp(24), dp(32), dp(24), dp(32))

        val title = Util.text(this, "Photo helper", 26f, AppTheme.text, true)
        root.addView(title)

        progressView = Util.text(this, "", 14f, AppTheme.muted)
        progressView.gravity = Gravity.CENTER
        root.addView(progressView)

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        avatarView = ImageView(this)
        root.addView(avatarView, LinearLayout.LayoutParams(dp(140), dp(140)))

        nameView = Util.text(this, "", 24f, AppTheme.text, true)
        nameView.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = dp(16)
        root.addView(nameView, nameLp)

        infoView = Util.text(this, "", 15f, AppTheme.muted)
        infoView.gravity = Gravity.CENTER
        val infoLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        infoLp.topMargin = dp(8)
        root.addView(infoView, infoLp)

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        chooseButton = Util.text(this, "Choose photo", 17f, 0xFFFFFFFF.toInt(), true)
        chooseButton.gravity = Gravity.CENTER
        chooseButton.background = Util.roundBg(this, AppTheme.brand, 28)
        chooseButton.setOnClickListener { pickPhoto() }
        root.addView(chooseButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        skipButton = Util.text(this, "Skip", 16f, AppTheme.muted, true)
        skipButton.gravity = Gravity.CENTER
        skipButton.setOnClickListener { next() }
        val skipLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
        skipLp.topMargin = dp(8)
        root.addView(skipButton, skipLp)

        closeButton = Util.text(this, "Close", 17f, 0xFFFFFFFF.toInt(), true)
        closeButton.gravity = Gravity.CENTER
        closeButton.background = Util.roundBg(this, AppTheme.brand, 28)
        closeButton.setOnClickListener { finish() }
        closeButton.visibility = View.GONE
        root.addView(closeButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))

        setContentView(root)
        AppTheme.style(this)
        load()
    }

    private fun load() {
        if (!Util.has(this, Manifest.permission.READ_CONTACTS)) {
            infoView.text = "Allow the Contacts permission first."
            chooseButton.visibility = View.GONE
            skipButton.visibility = View.GONE
            closeButton.visibility = View.VISIBLE
            return
        }
        Thread {
            val list = ArrayList<Item>()
            try {
                contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID, Util.nameColumn(this)),
                    ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1 AND " + ContactsContract.Contacts.PHOTO_ID + " IS NULL",
                    null, Util.sortColumn(this)
                )?.use { c ->
                    while (c.moveToNext()) {
                        list.add(Item(c.getLong(0), c.getString(1) ?: "(No name)"))
                    }
                }
            } catch (e: Exception) {
                // empty
            }
            runOnUiThread {
                items = list
                index = 0
                show()
            }
        }.start()
    }

    private fun show() {
        if (index >= items.size) {
            avatarView.setImageBitmap(Util.drawAvatar(null, "✓", dp(140)))
            nameView.text = if (items.isEmpty()) "Nothing to do" else "All done"
            progressView.text = ""
            infoView.text = if (items.isEmpty()) "Every contact already has a photo."
            else "You added " + added + (if (added == 1) " photo." else " photos.")
            chooseButton.visibility = View.GONE
            skipButton.visibility = View.GONE
            closeButton.visibility = View.VISIBLE
            return
        }
        val item = items[index]
        progressView.text = "" + (index + 1) + " of " + items.size + " contacts without a photo"
        avatarView.setImageBitmap(Util.drawAvatar(null, item.name, dp(140)))
        nameView.text = item.name
        infoView.text = "Tap the button to pick a picture from your gallery."
    }

    private fun next() {
        index++
        show()
    }

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(i, 4001)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4001 || resultCode != RESULT_OK || data?.data == null || index >= items.size) return
        val item = items[index]
        val uri = data.data!!
        Thread {
            var ok = false
            val bmp = Util.loadPicked(this, uri, 720)
            if (bmp != null) ok = Util.setContactPhoto(this, item.id, Util.toJpeg(bmp))
            runOnUiThread {
                if (ok) {
                    added++
                    next()
                } else {
                    Toast.makeText(this, "Could not save that photo for " + item.name, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
