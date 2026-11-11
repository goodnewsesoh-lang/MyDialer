package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.content.ContentProviderOperation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.util.Base64
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Save all contacts (with photos) to one .vcf file, or bring them back from one. */
class BackupActivity : Activity() {

    private class Card {
        var name = ""
        var given = ""
        var family = ""
        val numbers = ArrayList<String>()
        val emails = ArrayList<String>()
        var photo: ByteArray? = null
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(AppTheme.bg)
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val pad = Util.dp(this, 16)
        body.setPadding(pad, pad, pad, pad)
        scroll.addView(body)

        body.addView(Kit.title(this, "Backup and restore"))
        val hint = Util.text(
            this,
            "A backup is one file with all your contacts and their photos. Keep it in Google Drive or send it to yourself. Restoring skips contacts you already have.",
            13f, AppTheme.muted
        )
        hint.setPadding(Util.dp(this, 4), 0, Util.dp(this, 4), 0)
        body.addView(hint, Kit.lp(this, 4))

        body.addView(Kit.pill(this, "Back up contacts", AppTheme.brand) { startBackup() }, Kit.lp(this, 20))
        body.addView(Kit.pill(this, "Restore from a backup file", AppTheme.green) { startRestore() }, Kit.lp(this, 12))

        status = Util.text(this, "", 14f, AppTheme.text)
        status.setPadding(Util.dp(this, 4), Util.dp(this, 4), Util.dp(this, 4), Util.dp(this, 4))
        body.addView(status, Kit.lp(this, 20))

        setContentView(scroll)
        AppTheme.style(this)
    }

    private fun say(text: String) {
        runOnUiThread { status.text = text }
    }

    @Suppress("DEPRECATION")
    private fun startBackup() {
        if (!Util.has(this, Manifest.permission.READ_CONTACTS)) {
            status.text = "Allow the Contacts permission first."
            return
        }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "text/x-vcard"
        i.putExtra(Intent.EXTRA_TITLE, "MyDialer-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".vcf")
        startActivityForResult(i, 1)
    }

    @Suppress("DEPRECATION")
    private fun startRestore() {
        if (!Util.has(this, Manifest.permission.WRITE_CONTACTS)) {
            status.text = "Allow the Contacts permission first."
            return
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 2)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == 1) export(uri) else if (requestCode == 2) restore(uri)
    }

    // ---------- Backup ----------

    private fun export(target: Uri) {
        status.text = "Backing up..."
        Thread {
            var count = 0
            var failed = 0
            try {
                val keys = ArrayList<String>()
                contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val k = c.getString(0)
                        if (!k.isNullOrEmpty()) keys.add(k)
                    }
                }
                contentResolver.openOutputStream(target)?.use { out ->
                    for (k in keys) {
                        try {
                            val vu = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, k)
                            val bytes = contentResolver.openInputStream(vu)?.use { it.readBytes() }
                            if (bytes != null && bytes.isNotEmpty()) {
                                out.write(bytes)
                                if (bytes[bytes.size - 1] != '\n'.code.toByte()) out.write('\n'.code)
                                count++
                            }
                        } catch (e: Exception) {
                            failed++
                        }
                        if (count % 20 == 0) say("Backing up... $count of ${keys.size}")
                    }
                }
            } catch (e: Exception) {
                say("The backup failed: " + e.message)
                return@Thread
            }
            say("Done. Saved $count contacts" + (if (failed > 0) " ($failed could not be read)." else "."))
        }.start()
    }

    // ---------- Restore ----------

    private fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (line in raw) {
            if (line.isNotEmpty() && (line[0] == ' ' || line[0] == '\t') && out.isNotEmpty()) {
                out[out.size - 1] = out[out.size - 1] + line.substring(1)
            } else if (out.isNotEmpty() && out[out.size - 1].endsWith("=") &&
                out[out.size - 1].uppercase().contains("QUOTED-PRINTABLE")
            ) {
                out[out.size - 1] = out[out.size - 1].dropLast(1) + line
            } else {
                out.add(line)
            }
        }
        return out
    }

    private fun decodeQp(value: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '=' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    bytes.write(b)
                    i += 3
                    continue
                }
            }
            bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun unescape(v: String): String =
        v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\;", ";").replace("\\\\", "\\")

    private fun parse(text: String): List<Card> {
        val cards = ArrayList<Card>()
        var cur: Card? = null
        for (line in unfold(text)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val headParts = head.split(';')
            var prop = headParts[0].uppercase()
            val dot = prop.indexOf('.')
            if (dot >= 0) prop = prop.substring(dot + 1)
            val params = head.uppercase()

            if (prop == "BEGIN" && value.trim().uppercase() == "VCARD") {
                cur = Card()
                continue
            }
            if (prop == "END" && value.trim().uppercase() == "VCARD") {
                cur?.let { cards.add(it) }
                cur = null
                continue
            }
            val card = cur ?: continue
            val qp = params.contains("QUOTED-PRINTABLE")
            when (prop) {
                "FN" -> card.name = (if (qp) decodeQp(value) else unescape(value)).trim()
                "N" -> {
                    val v = if (qp) decodeQp(value) else value
                    val p = v.split(';')
                    card.family = unescape(p.getOrElse(0) { "" }).trim()
                    card.given = unescape(p.getOrElse(1) { "" }).trim()
                }
                "TEL" -> {
                    val n = value.trim()
                    if (n.isNotEmpty() && !card.numbers.contains(n)) card.numbers.add(n)
                }
                "EMAIL" -> {
                    val e = value.trim()
                    if (e.isNotEmpty()) card.emails.add(e)
                }
                "PHOTO" -> {
                    if (params.contains("ENCODING=B") || params.contains("BASE64")) {
                        try {
                            val b = Base64.decode(value.trim(), Base64.DEFAULT)
                            if (b.isNotEmpty()) card.photo = b
                        } catch (e: Exception) {
                            // skip the photo
                        }
                    }
                }
            }
        }
        for (c in cards) {
            if (c.name.isBlank()) c.name = (c.given + " " + c.family).trim()
            if (c.name.isBlank() && c.numbers.isNotEmpty()) c.name = c.numbers[0]
        }
        return cards
    }

    private fun insert(card: Card, accountType: String?, accountName: String?) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, card.name)
                .build()
        )
        for (n in card.numbers) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, n)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )
        }
        for (e in card.emails) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, e)
                    .build()
            )
        }
        val photo = card.photo
        if (photo != null) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, photo)
                    .build()
            )
        }
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun restore(source: Uri) {
        status.text = "Reading the backup..."
        Thread {
            val cards: List<Card>
            try {
                val text = contentResolver.openInputStream(source)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                cards = parse(text)
            } catch (e: Exception) {
                say("Could not read that file: " + e.message)
                return@Thread
            }
            if (cards.isEmpty()) {
                say("No contacts were found in that file.")
                return@Thread
            }

            // What you already have, so nothing is added twice
            val names = HashSet<String>()
            val pairs = HashSet<String>()
            try {
                contentResolver.query(
                    Phone.CONTENT_URI, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val n = (c.getString(0) ?: "").lowercase()
                        names.add(n)
                        pairs.add(n + "|" + Kit.numberKey(c.getString(1) ?: ""))
                    }
                }
            } catch (e: Exception) {
                // treat as empty
            }

            val saveTo = Prefs.getString(this, Prefs.SAVE_TO, "")
            val google = Util.googleAccounts(this)
            val useGoogle = saveTo.isNotEmpty() && saveTo != "phone" && google.contains(saveTo)
            val type: String? = if (useGoogle) "com.google" else null
            val account: String? = if (useGoogle) saveTo else null

            var added = 0
            var skipped = 0
            var failed = 0
            for ((index, card) in cards.withIndex()) {
                val lower = card.name.lowercase()
                var duplicate = false
                if (card.numbers.isEmpty()) {
                    duplicate = names.contains(lower)
                } else {
                    for (n in card.numbers) if (pairs.contains(lower + "|" + Kit.numberKey(n))) duplicate = true
                }
                if (card.name.isBlank() || duplicate) {
                    skipped++
                } else {
                    try {
                        insert(card, type, account)
                        added++
                        names.add(lower)
                        for (n in card.numbers) pairs.add(lower + "|" + Kit.numberKey(n))
                    } catch (e: Exception) {
                        failed++
                    }
                }
                if (index % 5 == 0) say("Restoring... ${index + 1} of ${cards.size}")
            }
            say(
                "Done. Added $added contacts, skipped $skipped you already have" +
                    (if (failed > 0) ", $failed could not be added." else ".")
            )
        }.start()
    }
}
