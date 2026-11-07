package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.provider.CallLog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Call history with All / Missed / Received / Dialed / Most called. */
class RecentsView(private val act: Activity) : LinearLayout(act) {

    class Entry(
        val number: String,
        val name: String?,
        val type: Int,
        val date: Long,
        val duration: Long,
        val thumbUri: String?
    )

    class Line(
        val number: String,
        val title: String,
        val subtitle: String,
        val thumbUri: String?,
        val titleColor: Int,
        val iconRes: Int,
        val iconColor: Int,
        val trailing: String
    )

    private class Agg(var count: Int, var last: Long, var name: String?, var thumb: String?)

    private val filterNames = listOf("All", "Missed", "Received", "Dialed", "Most called")
    private val chips = ArrayList<TextView>()
    private val list = ListView(act)
    private val message = Util.text(act, "", 16f, AppTheme.muted)
    private var filter = 0
    private var all: List<Entry> = emptyList()
    private var shown: List<Line> = emptyList()
    private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? RowView) ?: RowView(act)
            val line = shown[position]
            row.bind(
                Util.avatar(act, line.title, line.thumbUri, Util.dp(act, 48)),
                line.title, line.subtitle, line.titleColor,
                line.iconRes, line.iconColor, line.trailing
            )
            return row
        }
    }

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AppTheme.bg)

        val header = Util.text(act, "Recents", 26f, AppTheme.text, true)
        header.setPadding(Util.dp(act, 20), Util.dp(act, 16), Util.dp(act, 20), Util.dp(act, 4))
        addView(header)

        val scroll = HorizontalScrollView(act)
        scroll.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(act)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(Util.dp(act, 16), Util.dp(act, 8), Util.dp(act, 16), Util.dp(act, 8))
        for (i in filterNames.indices) {
            val chip = Util.text(act, filterNames[i], 14f, AppTheme.text, true)
            chip.gravity = Gravity.CENTER
            chip.setPadding(Util.dp(act, 16), Util.dp(act, 8), Util.dp(act, 16), Util.dp(act, 8))
            chip.setOnClickListener {
                filter = i
                applyFilter()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = Util.dp(act, 8)
            chipRow.addView(chip, lp)
            chips.add(chip)
        }
        scroll.addView(chipRow)
        addView(scroll)

        message.gravity = Gravity.CENTER
        message.setPadding(Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24), Util.dp(act, 24))
        message.visibility = View.GONE
        addView(message)

        list.adapter = adapter
        list.divider = null
        list.dividerHeight = 0
        list.setSelector(ColorDrawable(0))
        list.clipToPadding = false
        list.setPadding(0, Util.dp(act, 4), 0, Util.dp(act, 8))
        list.setOnItemClickListener { _, _, position, _ ->
            val line = shown[position]
            val name = if (line.title == line.number) null else line.title
            Util.callOrAsk(act, name, line.number)
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
            val items = ArrayList<Entry>()
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
                    while (c.moveToNext() && items.size < 1000) {
                        val number = c.getString(0) ?: ""
                        val cached = c.getString(1)
                        val caller = if (number.isBlank()) Util.Caller(null, null, null)
                        else lookups.getOrPut(number) { Util.lookup(act, number) }
                        val name = caller.name ?: cached
                        items.add(Entry(number, name, c.getInt(2), c.getLong(3), c.getLong(4), caller.thumbUri))
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
        val lines = ArrayList<Line>()
        if (filter == 4) {
            val map = LinkedHashMap<String, Agg>()
            for (e in all) {
                if (e.number.isBlank()) continue
                val a = map.getOrPut(e.number) { Agg(0, 0L, e.name, e.thumbUri) }
                a.count++
                if (e.date > a.last) a.last = e.date
                if (a.name == null) a.name = e.name
            }
            val sorted = map.entries.sortedWith(
                compareByDescending<Map.Entry<String, Agg>> { it.value.count }
                    .thenByDescending { it.value.last }
            )
            for (entry in sorted.take(50)) {
                val a = entry.value
                val title = a.name ?: entry.key
                val sub = (if (a.name != null) entry.key + " · " else "") +
                    "Last " + dateFormat.format(Date(a.last))
                val count = if (a.count == 1) "1 call" else "${a.count} calls"
                lines.add(Line(entry.key, title, sub, a.thumb, AppTheme.text, 0, 0, count))
            }
        } else {
            for (e in all) {
                val keep = when (filter) {
                    1 -> e.type == CallLog.Calls.MISSED_TYPE
                    2 -> e.type == CallLog.Calls.INCOMING_TYPE
                    3 -> e.type == CallLog.Calls.OUTGOING_TYPE
                    else -> true
                }
                if (keep) lines.add(toLine(e))
            }
        }
        shown = lines
        adapter.notifyDataSetChanged()
        styleChips()
        if (shown.isEmpty()) {
            message.text = if (filter == 4) "No calls yet." else "No calls here yet."
            message.visibility = View.VISIBLE
        } else {
            message.visibility = View.GONE
        }
    }

    private fun toLine(e: Entry): Line {
        val title = e.name ?: if (e.number.isBlank()) "Unknown" else e.number
        val kind: String
        val iconRes: Int
        val iconColor: Int
        when (e.type) {
            CallLog.Calls.MISSED_TYPE -> {
                kind = "Missed"; iconRes = R.drawable.ic_call_missed; iconColor = AppTheme.red
            }
            CallLog.Calls.INCOMING_TYPE -> {
                kind = "Received"; iconRes = R.drawable.ic_call_received; iconColor = AppTheme.green
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                kind = "Dialed"; iconRes = R.drawable.ic_call_made; iconColor = AppTheme.accent
            }
            CallLog.Calls.REJECTED_TYPE -> {
                kind = "Declined"; iconRes = R.drawable.ic_call_missed; iconColor = AppTheme.amber
            }
            CallLog.Calls.VOICEMAIL_TYPE -> {
                kind = "Voicemail"; iconRes = 0; iconColor = 0
            }
            else -> {
                kind = "Call"; iconRes = 0; iconColor = 0
            }
        }
        val parts = ArrayList<String>()
        parts.add(kind)
        if (e.name != null && e.number.isNotBlank()) parts.add(e.number)
        parts.add(dateFormat.format(Date(e.date)))
        if (e.duration > 0) parts.add(formatDuration(e.duration))
        val titleColor = if (e.type == CallLog.Calls.MISSED_TYPE) AppTheme.red else AppTheme.text
        return Line(e.number, title, parts.joinToString(" · "), e.thumbUri, titleColor, iconRes, iconColor, "")
    }

    private fun styleChips() {
        for (i in chips.indices) {
            val selected = i == filter
            chips[i].setTextColor(if (selected) 0xFFFFFFFF.toInt() else AppTheme.text)
            chips[i].background = Util.roundBg(act, if (selected) AppTheme.brand else AppTheme.chip, 20)
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
