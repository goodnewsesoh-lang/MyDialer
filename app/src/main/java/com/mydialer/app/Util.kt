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
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
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

    class Caller(val name: String?, val photoUri: String?, val thumbUri: String?)

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
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    return Caller(c.getString(0), c.getString(1), c.getString(2))
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

    /** The menu shown when you tap a contact: call, edit, add to favorites. */
    fun contactMenu(act: Activity, id: Long, name: String, starred: Boolean, changed: () -> Unit) {
        val numbers = numbersOf(act, id)
        val options = ArrayList<String>()
        for (n in numbers) options.add("Call $n")
        options.add("Edit contact")
        options.add(if (starred) "Remove from favorites" else "Add to favorites")

        AlertDialog.Builder(act, AppTheme.dialogTheme())
            .setTitle(name)
            .setItems(options.toTypedArray()) { _, which ->
                if (which < numbers.size) {
                    callOrAsk(act, name, numbers[which])
                } else if (which == numbers.size) {
                    val i = Intent(act, AddContactActivity::class.java)
                    i.putExtra("contactId", id)
                    act.startActivity(i)
                } else {
                    setStarred(act, id, !starred)
                    changed()
                }
            }
            .show()
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

    // ---------- Calling ----------

    fun call(ctx: Context, number: String) {
        if (number.isBlank()) return
        if (!has(ctx, Manifest.permission.CALL_PHONE)) {
            Toast.makeText(ctx, "Please allow the Phone permission first", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not place call: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    /** Calls the number, or asks first if "Ask before calling" is turned on in Settings. */
    fun callOrAsk(act: Activity, name: String?, number: String) {
        if (number.isBlank()) return
        if (Prefs.getBool(act, Prefs.CONFIRM, false)) {
            AlertDialog.Builder(act, AppTheme.dialogTheme())
                .setTitle("Call " + (name ?: number) + "?")
                .setMessage(if (name != null) number else "")
                .setPositiveButton("Call") { _, _ -> call(act, number) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            call(act, number)
        }
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
        texts.addView(titleView)

        subtitleView.textSize = 12f
        subtitleView.maxLines = 1
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
