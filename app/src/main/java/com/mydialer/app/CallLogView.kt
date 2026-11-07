package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Call history with All / Missed / Received / Dialed filters. */
class CallLogView(private val act: Activity) : LinearLayout(act) {

    class Item(
        val number: String,
        val name: String?,
        val type: Int,
        val date: Long,
        val duration: Long,
        val thumbUri: String?
    )

    private val filterNames = listOf("All", "Missed", "Received", "Dialed")
    private val chips = ArrayList<TextView>()
    private val list = ListView(act)
    private val message = Util.text(act, "", 16f, Util.GREY)
    private var filter = 0
    private var all: List<Item> = emptyList()
    private var shown: List<Item> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? RowView) ?: RowView(act)
            val item = shown[position]
            val title = item.name ?: if (item.number.isBlank()) "Unknown" else item.number
            val kind = when (item.type) {
                CallLog.Calls.MISSED_TYPE -> "Missed"
                CallLog.Calls.INCOMING_TYPE -> "Received"
                CallLog.Calls.OUTGOING_TYPE -> "Dialed"
                CallLog.Calls.REJECTED_TYPE -> "Declined"
                CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                else -> "Call"
            }
            val parts = ArrayList<String>()
            parts.add(kind)
            if (item.name != null && item.number.isNotBlank()) parts.add(item.number)
            parts.add(dateFormat.format(Date(item.date)))
            if (item.duration > 0) parts.add(formatDuration(item.duration))
            val color = if (item.type == CallLog.Calls.MISSED_TYPE) Util.RED else 0xFF212121.toInt()
            val avatar = Util.avatar(act, title, item.thumbUri, Util.dp(act, 48))
            row.bind(avatar, title, parts.joinToString(" · "), color)
            return row
        }
    }

    init {
        orientation = LinearLayout.VERTICAL

        val chipRow = LinearLayout(act)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(Util.dp(act, 8), Util.dp(act, 8), Util.dp(act, 8), Util.dp(act, 8))
        for (i in filterNames.indices) {
            val chip = Util.text(act, filterNames[i], 14f, 0xFF212121.toInt(), true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(Util.dp(act, 14), Util.dp(act, 8), Util.dp(act, 14), Util.dp(act, 8))
            chip.setOnClickListener {
                filter = i
                applyFilter()
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = Util.dp(act, 4)
            lp.rightMargin = Util.dp(act, 4)
            chipRow.addView(chip, lp)
            chips.add(chip)
        }
        addView(chipRow)

        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24))
        message.visibility = View.GONE
        addView(message)

        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            Util.call(act, shown[position].number)
        }
        addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        styleChips()
    }

    fun refresh() {
        if (!Util.has(act, Manifest.permission.READ_CALL_LOG)) {
            message.text = "Allow the Call log permission to see your calls."
            message.visibility = View.VISIBLE
            return
        }
        Thread {
            val items = ArrayList<Item>()
            val lookups = HashMap<String, Util.Caller>()
            try {
                val cols = arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                )
                act.contentResolver.query(
                    CallLog.Calls.CONTENT_URI, cols, null, null, CallLog.Calls.DATE + " DESC"
                )?.use { c ->
                    while (c.moveToNext() && items.size < 500) {
                        val number = c.getString(0) ?: ""
                        val cached = c.getString(1)
                        val caller = if (number.isBlank()) Util.Caller(null, null, null)
                        else lookups.getOrPut(number) { Util.lookup(act, number) }
                        val name = caller.name ?: cached
                        items.add(Item(number, name, c.getInt(2), c.getLong(3), c.getLong(4), caller.thumbUri))
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
        shown = when (filter) {
            1 -> all.filter { it.type == CallLog.Calls.MISSED_TYPE }
            2 -> all.filter { it.type == CallLog.Calls.INCOMING_TYPE }
            3 -> all.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
            else -> all
        }
        adapter.notifyDataSetChanged()
        styleChips()
        if (shown.isEmpty()) {
            message.text = "No calls here yet."
            message.visibility = View.VISIBLE
        } else {
            message.visibility = View.GONE
        }
    }

    private fun styleChips() {
        for (i in chips.indices) {
            val selected = i == filter
            chips[i].setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF212121.toInt())
            chips[i].background = Util.roundBg(act, if (selected) Util.BLUE else 0xFFE0E0E0.toInt(), 20)
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
