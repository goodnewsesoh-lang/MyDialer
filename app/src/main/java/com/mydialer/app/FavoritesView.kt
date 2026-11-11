package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout

/** Your starred contacts, shown as big photos for quick calling. */
class FavoritesView(private val act: Activity) : LinearLayout(act) {

    class Fav(val id: Long, val name: String, val thumbUri: String?)

    private val grid = GridView(act)
    private val message = Util.text(act, "", 16f, AppTheme.muted)
    private var favs: List<Fav> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = favs.size
        override fun getItem(position: Int) = favs[position]
        override fun getItemId(position: Int) = favs[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val fav = favs[position]
            val cell = LinearLayout(act)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(Util.dp(act, 8), Util.dp(act, 14), Util.dp(act, 8), Util.dp(act, 12))
            cell.background = Util.roundBg(act, AppTheme.card, 18)

            val size = Util.dp(act, 68)
            val img = ImageView(act)
            img.setImageBitmap(Util.avatar(act, fav.name, fav.thumbUri, size))
            cell.addView(img, LinearLayout.LayoutParams(size, size))

            val name = Util.text(act, fav.name, 13f, AppTheme.text, true)
            name.gravity = Gravity.CENTER
            name.maxLines = 2
            name.ellipsize = TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Util.dp(act, 8)
            cell.addView(name, lp)
            return cell
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AppTheme.bg)

        val header = Util.text(act, "Favorites", 26f, AppTheme.text, true)
        header.setPadding(Util.dp(act, 20), Util.dp(act, 16), Util.dp(act, 20), Util.dp(act, 8))
        addView(header)

        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(act, 32), Util.dp(act, 48), Util.dp(act, 32), Util.dp(act, 24))
        message.visibility = View.GONE
        addView(message)

        grid.numColumns = 3
        grid.horizontalSpacing = Util.dp(act, 10)
        grid.verticalSpacing = Util.dp(act, 10)
        grid.setPadding(Util.dp(act, 14), Util.dp(act, 8), Util.dp(act, 14), Util.dp(act, 12))
        grid.clipToPadding = false
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val fav = favs[position]
            val numbers = Util.numbersOf(act, fav.id)
            if (numbers.isNotEmpty()) Util.callOrAsk(act, fav.name, numbers[0], fav.id)
        }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            val fav = favs[position]
            Util.contactMenu(act, fav.id, fav.name, true) { refresh() }
            true
        }
        addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        if (!Util.has(act, Manifest.permission.READ_CONTACTS)) {
            message.text = "Allow the Contacts permission to see your favorites."
            message.visibility = View.VISIBLE
            return
        }
        Thread {
            val items = ArrayList<Fav>()
            try {
                val nameCol = Util.nameColumn(act)
                val cols = arrayOf(
                    ContactsContract.Contacts._ID,
                    nameCol,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                )
                act.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI, cols,
                    ContactsContract.Contacts.STARRED + "=1 AND " + ContactsContract.Contacts.HAS_PHONE_NUMBER + "=1",
                    null, Util.sortColumn(act)
                )?.use { c ->
                    while (c.moveToNext()) {
                        items.add(Fav(c.getLong(0), c.getString(1) ?: "(No name)", c.getString(2)))
                    }
                }
            } catch (e: Exception) {
                // show an empty page
            }
            post {
                favs = items
                adapter.notifyDataSetChanged()
                if (items.isEmpty()) {
                    message.text = "No favorites yet.\n\nOpen Contacts, tap a name, and choose Add to favorites."
                    message.visibility = View.VISIBLE
                } else {
                    message.visibility = View.GONE
                }
            }
        }.start()
    }
}
