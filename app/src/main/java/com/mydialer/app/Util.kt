package com.mydialer.app

import android.Manifest
import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min

object Util {

    const val BLUE = 0xFF1565C0.toInt()
    const val RED = 0xFFD32F2F.toInt()
    const val GREEN = 0xFF2E7D32.toInt()
    const val GREY = 0xFF757575.toInt()

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
}

/** One row used in the contact list and call history list. */
class RowView(ctx: Context) : LinearLayout(ctx) {
    private val avatarView = ImageView(ctx)
    private val titleView = TextView(ctx)
    private val subtitleView = TextView(ctx)

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Util.dp(ctx, 16), Util.dp(ctx, 8), Util.dp(ctx, 16), Util.dp(ctx, 8))

        val size = Util.dp(ctx, 48)
        addView(avatarView, LinearLayout.LayoutParams(size, size))

        val texts = LinearLayout(ctx)
        texts.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.leftMargin = Util.dp(ctx, 16)
        addView(texts, lp)

        titleView.textSize = 17f
        titleView.setTextColor(0xFF212121.toInt())
        titleView.maxLines = 2
        texts.addView(titleView)

        subtitleView.textSize = 13f
        subtitleView.setTextColor(Util.GREY)
        subtitleView.maxLines = 1
        texts.addView(subtitleView)
    }

    fun bind(avatar: Bitmap, title: String, subtitle: String, titleColor: Int) {
        avatarView.setImageBitmap(avatar)
        titleView.text = title
        titleView.setTextColor(titleColor)
        subtitleView.text = subtitle
    }
}
