package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Pick a folder of pictures named after your contacts (for example Mum.jpg).
 * The app matches each file name to a contact and sets the photo.
 */
class ImportPhotosActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var chooseButton: TextView
    private val log = StringBuilder()

    private fun dp(value: Int): Int = Util.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppTheme.bg)
        root.setPadding(dp(24), dp(32), dp(24), dp(24))

        root.addView(Util.text(this, "Import photos", 26f, AppTheme.text, true))

        val help = Util.text(
            this,
            "Put your pictures in one folder and name each file after the contact, for example Mum.jpg or John Peter.png. " +
                "Then choose that folder. The app matches the file names to your contacts and adds the photos. " +
                "Capital letters, spaces and dashes do not matter.",
            14f, AppTheme.muted
        )
        val helpLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        helpLp.topMargin = dp(10)
        root.addView(help, helpLp)

        chooseButton = Util.text(this, "Choose folder", 17f, 0xFFFFFFFF.toInt(), true)
        chooseButton.gravity = Gravity.CENTER
        chooseButton.background = Util.roundBg(this, AppTheme.brand, 28)
        chooseButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 3001)
        }
        val btnLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        btnLp.topMargin = dp(20)
        root.addView(chooseButton, btnLp)

        status = Util.text(this, "", 14f, AppTheme.text)
        val scroll = ScrollView(this)
        scroll.addView(status)
        val scrollLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        scrollLp.topMargin = dp(16)
        root.addView(scroll, scrollLp)

        setContentView(root)
        AppTheme.style(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 3001 && resultCode == RESULT_OK && data?.data != null) {
            if (!Util.has(this, Manifest.permission.READ_CONTACTS) || !Util.has(this, Manifest.permission.WRITE_CONTACTS)) {
                status.text = "Allow the Contacts permission first."
                return
            }
            chooseButton.isEnabled = false
            chooseButton.alpha = 0.5f
            log.setLength(0)
            status.text = "Working..."
            val tree = data.data!!
            Thread { importFrom(tree) }.start()
        }
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        for (c in text.lowercase()) {
            if (Character.isLetterOrDigit(c)) sb.append(c)
        }
        return sb.toString()
    }

    private fun addLine(line: String) {
        log.append(line).append('\n')
        runOnUiThread { status.text = log.toString() }
    }

    private fun importFrom(tree: Uri) {
        // Contacts: name -> ids
        val byName = HashMap<String, ArrayList<Long>>()
        try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    for (i in 1..2) {
                        val n = normalize(c.getString(i) ?: "")
                        if (n.isNotEmpty()) {
                            val list = byName.getOrPut(n) { ArrayList() }
                            if (!list.contains(id)) list.add(id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addLine("Could not read your contacts.")
        }

        // Files in the folder
        class Pic(val docUri: Uri, val fileName: String)
        val pics = ArrayList<Pic>()
        try {
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
            contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: ""
                    if (mime.startsWith("image/")) {
                        pics.add(Pic(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getString(1) ?: ""))
                    }
                }
            }
        } catch (e: Exception) {
            addLine("Could not open that folder: " + e.message)
        }

        if (pics.isEmpty()) {
            addLine("No pictures found in that folder.")
        }

        var added = 0
        var unmatched = 0
        var failed = 0
        for (pic in pics) {
            val base = pic.fileName.substringBeforeLast('.')
            val ids = byName[normalize(base)]
            if (ids == null) {
                unmatched++
                addLine("No contact named \"" + base + "\"")
                continue
            }
            val bmp = Util.loadPicked(this, pic.docUri, 720)
            if (bmp == null) {
                failed++
                addLine("Could not open " + pic.fileName)
                continue
            }
            val bytes = Util.toJpeg(bmp)
            var ok = false
            for (id in ids) {
                if (Util.setContactPhoto(this, id, bytes)) ok = true
            }
            if (ok) {
                added++
                addLine("Added photo: " + base)
            } else {
                failed++
                addLine("Could not save photo for " + base)
            }
        }

        addLine("")
        addLine("Done. Photos added: " + added + ". No matching contact: " + unmatched + ". Problems: " + failed + ".")
        runOnUiThread {
            chooseButton.isEnabled = true
            chooseButton.alpha = 1f
        }
    }
}
