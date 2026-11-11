package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.app.NotificationManager
import android.content.ContentProviderOperation
import android.os.Bundle
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.widget.HorizontalScrollView
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.provider.BlockedNumberContract
import android.util.LruCache
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

object Util {

    private val cache = LruCache<String, Bitmap>(150)

    fun has(ctx: Context, permission: String): Boolean =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun roundBg(ctx: Context, color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radiusDp * ctx.resources.displayMetrics.density
        return d
    }

    fun text(ctx: Context, value: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = value
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun icon(ctx: Context, res: Int, color: Int): ImageView {
        val v = ImageView(ctx)
        v.setImageResource(res)
        v.setColorFilter(color)
        return v
    }

    /** Style for text boxes: a soft rounded box. */
    fun styleField(ctx: Context, field: EditText) {
        field.background = roundBg(ctx, AppTheme.card, 14)
        field.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        field.setTextColor(AppTheme.text)
        field.setHintTextColor(AppTheme.muted)
        field.textSize = 16f
    }

    // ---------- Caller lookup ----------

    class Caller(val name: String?, val photoUri: String?, val thumbUri: String?, val id: Long = -1L)

    fun lookup(ctx: Context, number: String): Caller {
        if (number.isBlank() || !has(ctx, Manifest.permission.READ_CONTACTS)) {
            return Caller(null, null, null)
        }
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            val cols = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ContactsContract.PhoneLookup._ID
            )
            ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    return Caller(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))
                }
            }
        } catch (e: Exception) {
            // ignore, treat as unknown caller
        }
        return Caller(null, null, null)
    }

    // ---------- Contacts helpers ----------

    fun nameColumn(ctx: Context): String =
        if (Prefs.getBool(ctx, Prefs.LAST_NAME_FIRST, false))
            ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE
        else ContactsContract.Contacts.DISPLAY_NAME_PRIMARY

    fun sortColumn(ctx: Context): String =
        if (Prefs.getBool(ctx, Prefs.LAST_NAME_FIRST, false))
            ContactsContract.Contacts.SORT_KEY_ALTERNATIVE
        else ContactsContract.Contacts.SORT_KEY_PRIMARY

    fun numbersOf(ctx: Context, contactId: Long): List<String> {
        val numbers = ArrayList<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                arrayOf(contactId.toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank() && !numbers.contains(n)) numbers.add(n)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return numbers
    }

    fun setStarred(ctx: Context, contactId: Long, starred: Boolean): Boolean {
        return try {
            val values = ContentValues()
            values.put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
            ctx.contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                values, null, null
            ) > 0
        } catch (e: Exception) {
            false
        }
    }

    /** The menu shown when you tap a contact: call, message, edit, add to favorites. */
    fun contactMenu(act: Activity, id: Long, name: String, starred: Boolean, changed: () -> Unit) {
        val numbers = numbersOf(act, id)
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        for (n in numbers) {
            labels.add("Call $n")
            actions.add { callOrAsk(act, name, n, id) }
        }
        labels.add("Open contact page")
        actions.add {
            val i = Intent(act, ContactActivity::class.java)
            i.putExtra("contactId", id)
            act.startActivity(i)
        }
        if (numbers.isNotEmpty()) {
            labels.add("Send SMS")
            actions.add { openSms(act, numbers[0]) }
            labels.add("WhatsApp")
            actions.add { openWhatsApp(act, numbers[0]) }
        }
        labels.add("Edit contact")
        actions.add {
            val i = Intent(act, AddContactActivity::class.java)
            i.putExtra("contactId", id)
            act.startActivity(i)
        }
        labels.add(if (starred) "Remove from favorites" else "Add to favorites")
        actions.add {
            setStarred(act, id, !starred)
            changed()
        }

        AlertDialog.Builder(act, AppTheme.dialogTheme())
            .setTitle(name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    /** The menu shown when you long-press a call in Recents. */
    fun numberMenu(
        act: Activity, name: String?, number: String,
        callId: Long = -1L, contactId: Long = -1L, changed: () -> Unit = {}
    ) {
        if (number.isBlank()) return
        val cid = if (contactId >= 0) contactId else lookup(act, number).id
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add("Call")
        actions.add { callOrAsk(act, name, number, cid) }
        labels.add("Send SMS")
        actions.add { openSms(act, number) }
        labels.add("WhatsApp")
        actions.add { openWhatsApp(act, number) }
        if (cid >= 0) {
            labels.add("Open contact page")
            actions.add {
                val i = Intent(act, ContactActivity::class.java)
                i.putExtra("contactId", cid)
                act.startActivity(i)
            }
        }
        if (callId >= 0) {
            val has = Notes.get(act, callId).isNotEmpty()
            labels.add(if (has) "Edit note" else "Add note")
            actions.add { Notes.edit(act, callId, name ?: number, changed) }
        }
        if (canBlock(act)) {
            if (isBlocked(act, number)) {
                labels.add("Unblock this number")
                actions.add { unblock(act, number); changed() }
            } else {
                labels.add("Block this number")
                actions.add {
                    AlertDialog.Builder(act, AppTheme.dialogTheme())
                        .setTitle("Block " + (name ?: number) + "?")
                        .setMessage("Calls from this number will be stopped.")
                        .setPositiveButton("Block") { _, _ ->
                            if (!block(act, number)) {
                                Toast.makeText(act, "Could not block this number", Toast.LENGTH_SHORT).show()
                            }
                            changed()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
        if (name == null) {
            labels.add("Save to contacts")
            actions.add {
                val i = Intent(act, AddContactActivity::class.java)
                i.putExtra("number", number)
                act.startActivity(i)
            }
        }
        AlertDialog.Builder(act, AppTheme.dialogTheme())
            .setTitle(name ?: number)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    // ---------- Blocked numbers ----------

    fun canBlock(ctx: Context): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(ctx)
    } catch (e: Exception) {
        false
    }

    fun isBlocked(ctx: Context, number: String): Boolean = try {
        BlockedNumberContract.isBlocked(ctx, number)
    } catch (e: Exception) {
        false
    }

    fun block(ctx: Context, number: String): Boolean = try {
        val values = ContentValues()
        values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        ctx.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
    } catch (e: Exception) {
        false
    }

    fun unblock(ctx: Context, number: String) {
        try {
            BlockedNumberContract.unblock(ctx, number)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun blockedNumbers(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            ctx.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) out.add(n)
                }
            }
        } catch (e: Exception) {
            // empty list
        }
        return out
    }

    fun openSms(act: Activity, number: String) {
        try {
            act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))))
        } catch (e: Exception) {
            Toast.makeText(act, "No messaging app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun whatsAppDigits(ctx: Context, number: String): String {
        val digits = number.filter { it.isDigit() }
        if (number.trim().startsWith("+")) return digits
        if (digits.startsWith("00")) return digits.substring(2)
        if (digits.startsWith("0")) {
            var iso = ""
            try {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                iso = (tm?.simCountryIso ?: "").ifEmpty { tm?.networkCountryIso ?: "" }.uppercase()
            } catch (e: Exception) {
                // use the default below
            }
            val codes = mapOf(
                "NG" to "234", "GH" to "233", "KE" to "254", "ZA" to "27", "GB" to "44",
                "US" to "1", "CA" to "1", "IN" to "91", "CM" to "237", "TG" to "228", "BJ" to "229"
            )
            return (codes[iso] ?: "234") + digits.substring(1)
        }
        return digits
    }

    fun openWhatsApp(act: Activity, number: String) {
        try {
            val link = "https://wa.me/" + whatsAppDigits(act, number)
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: Exception) {
            Toast.makeText(act, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Missed calls ----------

    fun unseenMissedCount(ctx: Context): Int {
        if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return 0
        return try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
                CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE + " AND " + CallLog.Calls.NEW + "=1",
                null, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** Called when you open Recents: the red dot goes away and the missed-call notifications are cleared. */
    fun markMissedSeen(ctx: Context) {
        if (has(ctx, Manifest.permission.WRITE_CALL_LOG)) {
            try {
                val values = ContentValues()
                values.put(CallLog.Calls.NEW, 0)
                values.put(CallLog.Calls.IS_READ, 1)
                ctx.contentResolver.update(
                    CallLog.Calls.CONTENT_URI, values,
                    CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE + " AND " + CallLog.Calls.NEW + "=1",
                    null
                )
            } catch (e: Exception) {
                // ignore
            }
        }
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            for (n in nm.activeNotifications) {
                if (n.id >= 100) nm.cancel(n.id)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // ---------- Accounts and photos ----------

    /** Google accounts that store contacts on this phone, so new contacts can be saved (and synced) there. */
    fun googleAccounts(ctx: Context): List<String> {
        val names = LinkedHashSet<String>()
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        try {
            ctx.contentResolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if (c.getString(1) == "com.google" && !name.isNullOrBlank()) names.add(name)
                }
            }
        } catch (e: Exception) {
            // try the next way
        }
        if (names.isEmpty()) {
            try {
                ctx.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
                    ContactsContract.RawContacts.ACCOUNT_TYPE + "=?", arrayOf("com.google"), null
                )?.use { c ->
                    var rows = 0
                    while (c.moveToNext() && rows < 3000 && names.size < 5) {
                        rows++
                        val name = c.getString(0)
                        if (!name.isNullOrBlank()) names.add(name)
                    }
                }
            } catch (e: Exception) {
                // no accounts found
            }
        }
        return names.toList()
    }

    fun toJpeg(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    /** Sets the photo of an existing contact. Tries the contact parts that are allowed to be edited. */
    fun setContactPhoto(ctx: Context, contactId: Long, bytes: ByteArray): Boolean {
        val raws = ArrayList<Pair<Long, String?>>()
        try {
            ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                ContactsContract.RawContacts.CONTACT_ID + "=? AND " + ContactsContract.RawContacts.DELETED + "=0",
                arrayOf(contactId.toString()), null
            )?.use { c ->
                while (c.moveToNext()) raws.add(Pair(c.getLong(0), c.getString(1)))
            }
        } catch (e: Exception) {
            return false
        }
        val sorted = raws.sortedBy {
            when (it.second) {
                null -> 0
                "com.google" -> 1
                else -> 2
            }
        }
        val mime = ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
        for (raw in sorted) {
            val rawId = raw.first
            try {
                val ops = ArrayList<ContentProviderOperation>()
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                            arrayOf(rawId.toString(), mime)
                        )
                        .build()
                )
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(ContactsContract.Data.MIMETYPE, mime)
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                        .build()
                )
                ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                return true
            } catch (e: Exception) {
                // try the next part of the contact
            }
        }
        return false
    }

    /** A row of tappable choices. */
    fun chipGroup(ctx: Context, options: List<String>, selected: Int, onPick: (Int) -> Unit): HorizontalScrollView {
        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val chips = ArrayList<TextView>()

        fun style(sel: Int) {
            for (i in chips.indices) {
                val on = i == sel
                chips[i].setTextColor(if (on) 0xFFFFFFFF.toInt() else AppTheme.text)
                chips[i].background = roundBg(ctx, if (on) AppTheme.brand else AppTheme.chip, 18)
            }
        }

        for (i in options.indices) {
            val chip = text(ctx, options[i], 14f, AppTheme.text, true)
            chip.gravity = Gravity.CENTER
            chip.maxLines = 1
            chip.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
            chip.setOnClickListener {
                style(i)
                onPick(i)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(ctx, 8)
            row.addView(chip, lp)
            chips.add(chip)
        }
        style(selected)
        scroll.addView(row)
        return scroll
    }

    // ---------- Pictures ----------

    fun loadBitmap(ctx: Context, uri: String?, maxPx: Int): Bitmap? {
        if (uri.isNullOrEmpty()) return null
        return try {
            val u = Uri.parse(uri)
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    /** Loads a picture chosen from the gallery and fixes its rotation. */
    fun loadPicked(ctx: Context, uri: Uri, maxPx: Int): Bitmap? {
        val bmp = loadBitmap(ctx, uri.toString(), maxPx) ?: return null
        return try {
            var degrees = 0
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                degrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
            if (degrees == 0) bmp else {
                val m = Matrix()
                m.postRotate(degrees.toFloat())
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
        } catch (e: Exception) {
            bmp
        }
    }

    private val palette = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(),
        0xFF8E24AA.toInt(), 0xFF00897B.toInt(), 0xFFC2185B.toInt(),
        0xFF3949AB.toInt(), 0xFF6D4C41.toInt()
    )

    /** A round picture: the photo if there is one, otherwise a coloured circle with a letter. */
    fun drawAvatar(photo: Bitmap?, name: String?, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val half = sizePx / 2f
        if (photo != null) {
            val side = min(photo.width, photo.height)
            val sx = (photo.width - side) / 2
            val sy = (photo.height - side) / 2
            canvas.drawCircle(half, half, half, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(photo, Rect(sx, sy, sx + side, sy + side), Rect(0, 0, sizePx, sizePx), paint)
        } else {
            val key = name ?: "#"
            paint.color = palette[Math.abs(key.hashCode()) % palette.size]
            canvas.drawCircle(half, half, half, paint)
            paint.color = Color.WHITE
            paint.textSize = sizePx * 0.45f
            paint.textAlign = Paint.Align.CENTER
            val letter = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
            val y = half - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letter, half, y, paint)
        }
        return out
    }

    fun avatar(ctx: Context, name: String?, photoUri: String?, sizePx: Int): Bitmap {
        val key = "$photoUri|$name|$sizePx"
        cache.get(key)?.let { return it }
        val bmp = drawAvatar(loadBitmap(ctx, photoUri, sizePx), name, sizePx)
        cache.put(key, bmp)
        return bmp
    }

    // ---------- Calling and SIM cards ----------

    class Sim(val handle: PhoneAccountHandle, val label: String, val short: String)

    /** The SIM cards that can make calls. Empty if the phone cannot tell us. */
    fun sims(ctx: Context): List<Sim> {
        val out = ArrayList<Sim>()
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            var n = 0
            for (h in tm.callCapablePhoneAccounts) {
                val acc = tm.getPhoneAccount(h) ?: continue
                if ((acc.capabilities and PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) == 0) continue
                n++
                val carrier = acc.label?.toString() ?: ""
                val label = if (carrier.isNotBlank()) "SIM $n · $carrier" else "SIM $n"
                out.add(Sim(h, label, "SIM $n"))
            }
        } catch (e: Exception) {
            // no SIM list
        }
        return out
    }

    /** "SIM 1" or "SIM 2" for a call log entry, or null when the phone has only one SIM. */
    fun simName(sims: List<Sim>, accountId: String?): String? {
        if (sims.size < 2 || accountId.isNullOrEmpty()) return null
        for (s in sims) if (s.handle.id == accountId) return s.short
        return null
    }

    fun call(ctx: Context, number: String, handle: PhoneAccountHandle? = null) {
        if (number.isBlank()) return
        if (!has(ctx, Manifest.permission.CALL_PHONE)) {
            Toast.makeText(ctx, "Please allow the Phone permission first", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val extras = Bundle()
            if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            tm.placeCall(Uri.fromParts("tel", number, null), extras)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not place call: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun simFromPref(sims: List<Sim>, pref: String): Sim? {
        if (!pref.startsWith("sim:")) return null
        val id = pref.substring(4)
        for (s in sims) if (s.handle.id == id) return s
        return null
    }

    /** Calls with the right SIM: the contact's own SIM, then the SIM chosen in Settings, otherwise asks. */
    fun startCall(act: Activity, name: String?, number: String, contactId: Long = -1L) {
        if (number.isBlank()) return
        val sims = sims(act)
        if (sims.size < 2) {
            call(act, number)
            return
        }
        val cid = if (contactId >= 0) contactId else lookup(act, number).id
        if (cid >= 0) {
            val chosen = simFromPref(sims, Prefs.getString(act, "sim_c_$cid", ""))
            if (chosen != null) {
                call(act, number, chosen.handle)
                return
            }
        }
        val mode = Prefs.getString(act, Prefs.SIM_MODE, "ask")
        if (mode == "default") {
            call(act, number)
            return
        }
        val fixed = simFromPref(sims, mode)
        if (fixed != null) {
            call(act, number, fixed.handle)
            return
        }
        val labels = sims.map { it.label }.toTypedArray()
        AlertDialog.Builder(act, AppTheme.dialogTheme())
            .setTitle("Call " + (name ?: number) + " with")
            .setItems(labels) { _, which -> call(act, number, sims[which].handle) }
            .show()
    }

    /** Calls the number, or asks first if "Ask before calling" is turned on in Settings. */
    fun callOrAsk(act: Activity, name: String?, number: String, contactId: Long = -1L) {
        if (number.isBlank()) return
        if (Prefs.getBool(act, Prefs.CONFIRM, false)) {
            AlertDialog.Builder(act, AppTheme.dialogTheme())
                .setTitle("Call " + (name ?: number) + "?")
                .setMessage(if (name != null) number else "")
                .setPositiveButton("Call") { _, _ -> startCall(act, name, number, contactId) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            startCall(act, name, number, contactId)
        }
    }
}

/** Short notes attached to calls, kept on this phone. */
object Notes {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("notes", Context.MODE_PRIVATE)

    fun get(ctx: Context, callId: Long): String = sp(ctx).getString("c$callId", "") ?: ""

    fun put(ctx: Context, callId: Long, note: String) {
        val e = sp(ctx).edit()
        if (note.isBlank()) e.remove("c$callId") else e.putString("c$callId", note.trim())
        e.apply()
    }

    fun edit(act: Activity, callId: Long, who: String, done: () -> Unit) {
        val field = EditText(act)
        field.hint = "Write a short note"
        field.setText(get(act, callId))
        Util.styleField(act, field)
        val box = LinearLayout(act)
        val pad = Util.dp(act, 20)
        box.setPadding(pad, Util.dp(act, 8), pad, 0)
        box.addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(act, AppTheme.dialogTheme())
            .setTitle("Note for $who")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                put(act, callId, field.text.toString())
                done()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

/** Speed dial: keys 2 to 9 can each hold a favorite number. */
object SpeedDial {
    fun number(ctx: Context, digit: Int): String = Prefs.getString(ctx, "speed_${digit}_number", "")
    fun name(ctx: Context, digit: Int): String = Prefs.getString(ctx, "speed_${digit}_name", "")

    fun set(ctx: Context, digit: Int, name: String, number: String) {
        Prefs.putString(ctx, "speed_${digit}_number", number)
        Prefs.putString(ctx, "speed_${digit}_name", name)
    }

    fun clear(ctx: Context, digit: Int) {
        Prefs.putString(ctx, "speed_${digit}_number", "")
        Prefs.putString(ctx, "speed_${digit}_name", "")
    }
}

/** One row in a list: a rounded card with a picture, two lines of text and an optional icon or label on the right. */
class RowView(ctx: Context) : LinearLayout(ctx) {
    private val card = LinearLayout(ctx)
    private val avatarView = ImageView(ctx)
    private val titleView = TextView(ctx)
    private val subtitleView = TextView(ctx)
    private val trailingIcon = ImageView(ctx)
    private val trailingText = TextView(ctx)

    init {
        setPadding(Util.dp(ctx, 12), Util.dp(ctx, 4), Util.dp(ctx, 12), Util.dp(ctx, 4))

        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(Util.dp(ctx, 12), Util.dp(ctx, 10), Util.dp(ctx, 14), Util.dp(ctx, 10))
        card.background = Util.roundBg(ctx, AppTheme.card, 16)
        addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val size = Util.dp(ctx, 48)
        card.addView(avatarView, LinearLayout.LayoutParams(size, size))

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = Util.dp(ctx, 14)
        card.addView(texts, lp)

        titleView.textSize = 16f
        titleView.setTypeface(titleView.typeface, Typeface.BOLD)
        titleView.maxLines = 2
        titleView.ellipsize = TextUtils.TruncateAt.END
        texts.addView(titleView)

        subtitleView.textSize = 12f
        subtitleView.maxLines = 2
        subtitleView.ellipsize = TextUtils.TruncateAt.END
        texts.addView(subtitleView)

        val iconLp = LinearLayout.LayoutParams(Util.dp(ctx, 22), Util.dp(ctx, 22))
        iconLp.leftMargin = Util.dp(ctx, 8)
        card.addView(trailingIcon, iconLp)

        trailingText.textSize = 13f
        trailingText.setTypeface(trailingText.typeface, Typeface.BOLD)
        val textLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        textLp.leftMargin = Util.dp(ctx, 8)
        card.addView(trailingText, textLp)
    }

    fun bind(
        avatar: Bitmap,
        title: String,
        subtitle: String,
        titleColor: Int = AppTheme.text,
        iconRes: Int = 0,
        iconColor: Int = 0,
        trailing: String = ""
    ) {
        avatarView.setImageBitmap(avatar)
        titleView.text = title
        titleView.setTextColor(titleColor)
        subtitleView.text = subtitle
        subtitleView.setTextColor(AppTheme.muted)
        if (iconRes != 0) {
            trailingIcon.setImageResource(iconRes)
            trailingIcon.setColorFilter(iconColor)
            trailingIcon.visibility = android.view.View.VISIBLE
        } else {
            trailingIcon.visibility = android.view.View.GONE
        }
        if (trailing.isNotEmpty()) {
            trailingText.text = trailing
            trailingText.setTextColor(AppTheme.accent)
            trailingText.visibility = android.view.View.VISIBLE
        } else {
            trailingText.visibility = android.view.View.GONE
        }
    }
}
