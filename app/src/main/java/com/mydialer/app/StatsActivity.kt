package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How much you talked: a chart of the last 7 or 30 days, totals and top callers. */
class StatsActivity : Activity() {

    private class Row(val number: String, val name: String?, val type: Int, val date: Long, val duration: Long)
    private class Person(var name: String?, var calls: Int, var seconds: Long)

    private var days = 7
    private lateinit var chart: BarChartView
    private lateinit var tiles: LinearLayout
    private lateinit var people: LinearLayout
    private lateinit var rangeText: TextView

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

        body.addView(Kit.title(this, "Call stats"))

        val chips = Util.chipGroup(this, listOf("Last 7 days", "Last 30 days"), 0) { index ->
            days = if (index == 0) 7 else 30
            load()
        }
        body.addView(chips, Kit.lp(this, 8))

        rangeText = Util.text(this, "Talk time per day (minutes)", 13f, AppTheme.muted)
        rangeText.setPadding(Util.dp(this, 4), 0, 0, 0)
        body.addView(rangeText, Kit.lp(this, 16))

        val chartCard = Kit.card(this)
        chartCard.setPadding(Util.dp(this, 12), Util.dp(this, 12), Util.dp(this, 12), Util.dp(this, 8))
        chart = BarChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(this, 200)))
        body.addView(chartCard, Kit.lp(this, 8))

        tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        body.addView(tiles, Kit.lp(this, 12))

        body.addView(Kit.sectionLabel(this, "TOP PEOPLE BY TALK TIME"), Kit.lp(this, 22))
        people = Kit.card(this)
        body.addView(people, Kit.lp(this, 8))

        setContentView(scroll)
        AppTheme.style(this)
        load()
    }

    private fun startOfDay(time: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun load() {
        if (!Util.has(this, Manifest.permission.READ_CALL_LOG)) {
            people.removeAllViews()
            people.addView(Kit.rowText(this, "Allow the Call log permission", "Open Settings > App permissions."))
            return
        }
        val range = days
        Thread {
            val today = startOfDay(System.currentTimeMillis())
            val cal = Calendar.getInstance()
            cal.timeInMillis = today
            cal.add(Calendar.DAY_OF_YEAR, -(range - 1))
            val since = cal.timeInMillis

            val rows = ArrayList<Row>()
            try {
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                        CallLog.Calls.DATE, CallLog.Calls.DURATION
                    ),
                    CallLog.Calls.DATE + ">=?", arrayOf(since.toString()), CallLog.Calls.DATE + " DESC"
                )?.use { c ->
                    while (c.moveToNext() && rows.size < 5000) {
                        rows.add(Row(c.getString(0) ?: "", c.getString(1), c.getInt(2), c.getLong(3), c.getLong(4)))
                    }
                }
            } catch (e: Exception) {
                // empty stats
            }

            val seconds = FloatArray(range)
            var talk = 0L
            var missed = 0
            val map = LinkedHashMap<String, Person>()
            for (r in rows) {
                val ago = Math.round((today - startOfDay(r.date)) / 86400000.0).toInt()
                val idx = range - 1 - ago
                if (idx in 0 until range) seconds[idx] += r.duration.toFloat()
                talk += r.duration
                if (r.type == CallLog.Calls.MISSED_TYPE) missed++
                if (r.number.isNotBlank()) {
                    val key = Kit.numberKey(r.number)
                    val p = map.getOrPut(key) { Person(r.name ?: r.number, 0, 0L) }
                    p.calls++
                    p.seconds += r.duration
                    if (p.name == r.number && r.name != null) p.name = r.name
                }
            }
            val top = map.values.filter { it.seconds > 0 }.sortedByDescending { it.seconds }.take(5)

            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val values = ArrayList<Float>()
            val labels = ArrayList<String>()
            for (i in 0 until range) {
                values.add(seconds[i] / 60f)
                val d = Calendar.getInstance()
                d.timeInMillis = since
                d.add(Calendar.DAY_OF_YEAR, i)
                if (range == 7) {
                    labels.add(dayFmt.format(Date(d.timeInMillis)))
                } else {
                    val dom = d.get(Calendar.DAY_OF_MONTH)
                    labels.add(if (i % 5 == 0 || i == range - 1) dom.toString() else "")
                }
            }

            val callCount = rows.size
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                chart.setData(values, labels, range == 7)
                tiles.removeAllViews()
                addTile("Calls", callCount.toString(), true)
                addTile("Talk time", if (talk == 0L) "0m" else Kit.duration(talk), true)
                addTile("Missed", missed.toString(), false)

                people.removeAllViews()
                if (top.isEmpty()) {
                    people.addView(Kit.rowText(this, "No talk time yet", "Make or receive a call and it will show here."))
                }
                for (p in top) {
                    val line = Kit.rowText(
                        this, p.name ?: "Unknown",
                        Kit.duration(p.seconds) + " · " + (if (p.calls == 1) "1 call" else "${p.calls} calls")
                    )
                    Kit.addRow(this, people, line)
                }
            }
        }.start()
    }

    private fun addTile(label: String, value: String, gap: Boolean) {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.setPadding(Util.dp(this, 8), Util.dp(this, 14), Util.dp(this, 8), Util.dp(this, 14))
        tile.background = Util.roundBg(this, AppTheme.card, 16)
        val v = Util.text(this, value, 20f, AppTheme.text, true)
        v.gravity = Gravity.CENTER
        v.maxLines = 1
        tile.addView(v)
        val l = Util.text(this, label, 12f, AppTheme.muted)
        l.gravity = Gravity.CENTER
        tile.addView(l)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        if (gap) lp.rightMargin = Util.dp(this, 8)
        tiles.addView(tile, lp)
    }
}
