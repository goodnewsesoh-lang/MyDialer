package com.mydialer.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** The screen you see during a call. */
@Suppress("DEPRECATION")
class CallActivity : Activity() {

    private class ActionButton(val box: LinearLayout, val circle: ImageView, val label: TextView)

    private val darkBg = 0xFF12121C.toInt()
    private val buttonColor = 0xFF2A2A3A.toInt()

    private lateinit var photoView: ImageView
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var stateView: TextView
    private lateinit var heldCard: TextView
    private lateinit var incomingPanel: LinearLayout
    private lateinit var incomingText: TextView
    private lateinit var ringingRow: LinearLayout
    private lateinit var slideBox: LinearLayout
    private val simList by lazy { Util.sims(this) }
    private lateinit var gridPanel: LinearLayout
    private lateinit var padPanel: LinearLayout
    private lateinit var padDisplay: TextView
    private lateinit var endBox: LinearLayout

    private lateinit var muteBtn: ActionButton
    private lateinit var padBtn: ActionButton
    private lateinit var speakerBtn: ActionButton
    private lateinit var addBtn: ActionButton
    private lateinit var holdBtn: ActionButton
    private lateinit var recordBtn: ActionButton

    private val handler = Handler(Looper.getMainLooper())
    private val recorder by lazy { CallRecorder(this) }
    private val callerCache = HashMap<String, Util.Caller>()
    private var muted = false
    private var speaker = false
    private var padOpen = false
    private var shownKey: String? = null
    private var finishing = false

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    private val listener: () -> Unit = { runOnUiThread { update() } }

    private fun dp(value: Int): Int = Util.dp(this, value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.apply(this)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.statusBarColor = darkBg
        window.navigationBarColor = darkBg
        buildScreen()
        CallManager.onChange = listener
    }

    override fun onResume() {
        super.onResume()
        getSystemService(NotificationManager::class.java).cancel(DialerCallService.NOTIFICATION_ID)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        if (CallManager.onChange === listener) CallManager.onChange = null
        if (recorder.isRecording) recorder.stop()
        super.onDestroy()
    }

    // ---------- Building the screen ----------

    private fun circleBg(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun actionButton(iconRes: Int, label: String, onClick: () -> Unit): ActionButton {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL

        val circle = ImageView(this)
        circle.setImageResource(iconRes)
        circle.setColorFilter(0xFFFFFFFF.toInt())
        circle.setPadding(dp(17), dp(17), dp(17), dp(17))
        circle.background = circleBg(buttonColor)
        box.addView(circle, LinearLayout.LayoutParams(dp(60), dp(60)))

        val text = Util.text(this, label, 12f, 0xFFBDBDBD.toInt())
        text.gravity = Gravity.CENTER
        text.maxLines = 1
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4)
        box.addView(text, lp)

        box.setOnClickListener { onClick() }
        return ActionButton(box, circle, text)
    }

    private fun setButton(b: ActionButton, iconRes: Int, label: String, active: Boolean, enabled: Boolean = true) {
        b.circle.setImageResource(iconRes)
        b.circle.background = circleBg(if (active) AppTheme.brand else buttonColor)
        b.label.text = label
        b.box.alpha = if (enabled) 1f else 0.4f
    }

    private fun bigButton(iconRes: Int, color: Int, label: String, onClick: () -> Unit): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val circle = ImageView(this)
        circle.setImageResource(iconRes)
        circle.setColorFilter(0xFFFFFFFF.toInt())
        circle.setPadding(dp(20), dp(20), dp(20), dp(20))
        circle.background = circleBg(color)
        box.addView(circle, LinearLayout.LayoutParams(dp(72), dp(72)))
        val text = Util.text(this, label, 13f, 0xFFDDDDDD.toInt())
        text.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4)
        box.addView(text, lp)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun pill(label: String, color: Int, onClick: () -> Unit): TextView {
        val t = Util.text(this, label, 13f, 0xFFFFFFFF.toInt(), true)
        t.gravity = Gravity.CENTER
        t.maxLines = 2
        t.setPadding(dp(6), dp(10), dp(6), dp(10))
        t.background = Util.roundBg(this, color, 18)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(darkBg)
        scroll.isFillViewport = true

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))
        scroll.addView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        photoView = ImageView(this)
        root.addView(photoView, LinearLayout.LayoutParams(dp(110), dp(110)))

        nameView = Util.text(this, "", 26f, 0xFFFFFFFF.toInt(), true)
        nameView.gravity = Gravity.CENTER
        nameView.maxLines = 2
        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = dp(14)
        root.addView(nameView, nameLp)

        numberView = Util.text(this, "", 15f, 0xFFBDBDBD.toInt())
        numberView.gravity = Gravity.CENTER
        root.addView(numberView)

        stateView = Util.text(this, "", 15f, 0xFF90CAF9.toInt())
        stateView.gravity = Gravity.CENTER
        val stateLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stateLp.topMargin = dp(6)
        root.addView(stateView, stateLp)

        // The other call that is on hold
        heldCard = Util.text(this, "", 14f, 0xFFFFFFFF.toInt(), true)
        heldCard.gravity = Gravity.CENTER
        heldCard.setPadding(dp(14), dp(10), dp(14), dp(10))
        heldCard.background = Util.roundBg(this, buttonColor, 16)
        heldCard.visibility = View.GONE
        val heldLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        heldLp.topMargin = dp(12)
        root.addView(heldCard, heldLp)

        // Someone calls while you are on a call
        incomingPanel = LinearLayout(this)
        incomingPanel.orientation = LinearLayout.VERTICAL
        incomingPanel.setPadding(dp(12), dp(12), dp(12), dp(12))
        incomingPanel.background = Util.roundBg(this, 0xFF1F2A44.toInt(), 16)
        incomingPanel.visibility = View.GONE
        incomingText = Util.text(this, "", 14f, 0xFFFFFFFF.toInt(), true)
        incomingText.gravity = Gravity.CENTER
        incomingPanel.addView(incomingText)
        val incomingButtons = LinearLayout(this)
        incomingButtons.orientation = LinearLayout.HORIZONTAL
        val holdAnswer = pill("Hold & answer", AppTheme.green) { answerSecond(false) }
        val endAnswer = pill("End & answer", AppTheme.brand) { answerSecond(true) }
        val declineSecond = pill("Decline", AppTheme.red) { findRinging()?.reject(false, null) }
        for (b in listOf(holdAnswer, endAnswer, declineSecond)) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = dp(3)
            lp.rightMargin = dp(3)
            incomingButtons.addView(b, lp)
        }
        val ibLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ibLp.topMargin = dp(8)
        incomingPanel.addView(incomingButtons, ibLp)
        val ipLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        ipLp.topMargin = dp(12)
        root.addView(incomingPanel, ipLp)

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // Buttons shown while the phone is ringing
        ringingRow = LinearLayout(this)
        ringingRow.orientation = LinearLayout.HORIZONTAL
        ringingRow.gravity = Gravity.CENTER
        val decline = bigButton(R.drawable.ic_call_end, AppTheme.red, "Decline") {
            CallManager.primary()?.reject(false, null)
        }
        val answer = bigButton(R.drawable.ic_call, AppTheme.green, "Answer") {
            CallManager.primary()?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        val rrLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        ringingRow.addView(decline, rrLp)
        ringingRow.addView(answer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(ringingRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Slide to answer (used when it is turned on in Settings)
        slideBox = LinearLayout(this)
        slideBox.orientation = LinearLayout.VERTICAL
        slideBox.gravity = Gravity.CENTER_HORIZONTAL
        val slider = SlideToAnswerView(this) {
            CallManager.primary()?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        val sliderLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72))
        sliderLp.setMargins(dp(24), 0, dp(24), dp(20))
        slideBox.addView(slider, sliderLp)
        slideBox.addView(bigButton(R.drawable.ic_call_end, AppTheme.red, "Decline") {
            CallManager.primary()?.reject(false, null)
        })
        root.addView(slideBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // The grid of call buttons
        muteBtn = actionButton(R.drawable.ic_mic, "Mute") { toggleMute() }
        padBtn = actionButton(R.drawable.ic_dialpad, "Keypad") {
            padOpen = true
            update()
        }
        speakerBtn = actionButton(R.drawable.ic_volume, "Speaker") { toggleSpeaker() }
        addBtn = actionButton(R.drawable.ic_add, "Add call") { addOrSwap() }
        holdBtn = actionButton(R.drawable.ic_pause, "Hold") { holdOrMerge() }
        recordBtn = actionButton(R.drawable.ic_record, "Record") { toggleRecord() }

        gridPanel = LinearLayout(this)
        gridPanel.orientation = LinearLayout.VERTICAL
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        for (b in listOf(muteBtn, padBtn, speakerBtn)) {
            row1.addView(b.box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        for (b in listOf(addBtn, holdBtn, recordBtn)) {
            row2.addView(b.box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        gridPanel.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val row2Lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row2Lp.topMargin = dp(14)
        gridPanel.addView(row2, row2Lp)
        root.addView(gridPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // The keypad used during a call (for menus such as "press 1 for...")
        padPanel = LinearLayout(this)
        padPanel.orientation = LinearLayout.VERTICAL
        padPanel.visibility = View.GONE
        padDisplay = Util.text(this, "", 26f, 0xFFFFFFFF.toInt(), true)
        padDisplay.gravity = Gravity.CENTER
        padDisplay.setSingleLine(true)
        padPanel.addView(padDisplay, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        val keys = listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        )
        for (rowKeys in keys) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in rowKeys) {
                val key = Util.text(this, k, 22f, 0xFFFFFFFF.toInt(), true)
                key.gravity = Gravity.CENTER
                key.background = Util.roundBg(this, buttonColor, 14)
                key.setOnClickListener { sendDtmf(k) }
                val lp = LinearLayout.LayoutParams(0, dp(50), 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                row.addView(key, lp)
            }
            padPanel.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val hide = Util.text(this, "Hide keypad", 14f, 0xFF90CAF9.toInt(), true)
        hide.gravity = Gravity.CENTER
        hide.setOnClickListener {
            padOpen = false
            update()
        }
        padPanel.addView(hide, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        root.addView(padPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // End call
        endBox = bigButton(R.drawable.ic_call_end, AppTheme.red, "End call") {
            CallManager.primary()?.disconnect()
        }
        val endLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        endLp.topMargin = dp(16)
        root.addView(endBox, endLp)

        setContentView(scroll)
    }

    // ---------- Actions ----------

    private fun findRinging(): Call? {
        val primary = CallManager.primary()
        return CallManager.visible().firstOrNull { it !== primary && it.state == Call.STATE_RINGING }
    }

    private fun findHeld(): Call? {
        val primary = CallManager.primary()
        return CallManager.visible().firstOrNull { it !== primary && it.state == Call.STATE_HOLDING }
    }

    private fun answerSecond(endFirst: Boolean) {
        val ringing = findRinging() ?: return
        if (endFirst) CallManager.primary()?.disconnect()
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    private fun toggleMute() {
        muted = !muted
        DialerCallService.instance?.setMuted(muted)
        update()
    }

    private fun toggleSpeaker() {
        speaker = !speaker
        DialerCallService.instance?.setAudioRoute(
            if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
        update()
    }

    private fun addOrSwap() {
        val held = findHeld()
        if (held != null) {
            held.unhold()
        } else {
            startActivity(Intent(this, AddCallActivity::class.java))
        }
    }

    private fun holdOrMerge() {
        val primary = CallManager.primary() ?: return
        val held = findHeld()
        if (held != null) {
            val canMerge = (primary.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                (held.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
            if (canMerge) {
                primary.conference(held)
            } else {
                Toast.makeText(this, "Your network does not allow merging these calls", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (primary.state == Call.STATE_HOLDING) {
            primary.unhold()
        } else if ((primary.details.callCapabilities and Call.Details.CAPABILITY_HOLD) != 0) {
            primary.hold()
        } else {
            Toast.makeText(this, "This call cannot be put on hold", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendDtmf(key: String) {
        val primary = CallManager.primary() ?: return
        primary.playDtmfTone(key[0])
        handler.postDelayed({ primary.stopDtmfTone() }, 150)
        padDisplay.text = padDisplay.text.toString() + key
    }

    private fun toggleRecord() {
        if (recorder.isRecording) {
            stopRecording()
            update()
            return
        }
        if (!Util.has(this, Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 300)
            return
        }
        startRecording()
    }

    private fun startRecording() {
        val primary = CallManager.primary()
        val label = if (primary == null) "call" else describe(primary).first
        if (recorder.start(label)) {
            val hint = if (speaker) "Recording started" else "Recording started. Turn Speaker on to record both sides."
            Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Could not start recording on this phone", Toast.LENGTH_LONG).show()
        }
        update()
    }

    private fun stopRecording() {
        if (recorder.stop()) {
            Toast.makeText(this, "Recording saved in Music > MyDialer", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 300) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "Allow the Microphone permission to record calls", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- Showing what is happening ----------

    private fun describe(call: Call): Triple<String, String, String?> {
        val conference = (call.details.callProperties and Call.Details.PROPERTY_CONFERENCE) != 0
        if (conference) {
            return Triple("Conference call", call.children.size.toString() + " people", null)
        }
        val number = call.details.handle?.schemeSpecificPart ?: ""
        val caller = callerCache.getOrPut(number) { Util.lookup(this, number) }
        val title = caller.name ?: if (number.isBlank()) "Unknown caller" else number
        return Triple(title, if (caller.name != null) number else "", caller.photoUri ?: caller.thumbUri)
    }

    private fun update() {
        if (finishing) return
        val primary = CallManager.primary()
        if (primary == null) {
            finishing = true
            if (recorder.isRecording) stopRecording()
            finish()
            return
        }

        val info = describe(primary)
        val key = info.first + "|" + info.second
        if (key != shownKey) {
            shownKey = key
            nameView.text = info.first
            numberView.text = info.second
            photoView.setImageBitmap(Util.avatar(this, info.first, info.third, dp(110)))
        }

        val held = findHeld()
        val ringing2 = findRinging()
        val state = primary.state
        val ringingAlone = state == Call.STATE_RINGING

        var stateText = when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling..."
            Call.STATE_ACTIVE -> elapsed(primary)
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTING -> "Ending..."
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> ""
        }
        val simLabel = Util.simName(simList, primary.details.accountHandle?.id)
        if (simLabel != null) stateText += "  ·  $simLabel"
        if (recorder.isRecording) stateText += "  ● REC"
        stateView.text = stateText

        // The other call on hold
        if (held != null) {
            heldCard.text = "On hold: " + describe(held).first
            heldCard.visibility = View.VISIBLE
        } else {
            heldCard.visibility = View.GONE
        }

        // A second person calling
        if (ringing2 != null) {
            incomingText.text = describe(ringing2).first + " is calling"
            incomingPanel.visibility = View.VISIBLE
        } else {
            incomingPanel.visibility = View.GONE
        }

        // Which buttons to show
        val slide = Prefs.getBool(this, Prefs.SLIDE, true)
        ringingRow.visibility = if (ringingAlone && !slide) View.VISIBLE else View.GONE
        slideBox.visibility = if (ringingAlone && slide) View.VISIBLE else View.GONE
        endBox.visibility = if (ringingAlone) View.GONE else View.VISIBLE
        gridPanel.visibility = if (!ringingAlone && !padOpen) View.VISIBLE else View.GONE
        padPanel.visibility = if (!ringingAlone && padOpen) View.VISIBLE else View.GONE
        photoView.visibility = if (padOpen && !ringingAlone) View.GONE else View.VISIBLE
        if (padOpen && !ringingAlone) {
            // keep what was typed
        } else {
            padDisplay.text = ""
        }

        // Button looks
        setButton(muteBtn, if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic, if (muted) "Muted" else "Mute", muted)
        setButton(padBtn, R.drawable.ic_dialpad, "Keypad", false)
        setButton(speakerBtn, R.drawable.ic_volume, if (speaker) "Speaker on" else "Speaker", speaker)
        setButton(recordBtn, R.drawable.ic_record, if (recorder.isRecording) "Stop" else "Record", recorder.isRecording)
        if (recorder.isRecording) recordBtn.circle.background = circleBg(AppTheme.red)

        if (held != null) {
            setButton(addBtn, R.drawable.ic_swap, "Swap", false)
            val canMerge = (primary.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                (held.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
            setButton(holdBtn, R.drawable.ic_merge, "Merge", false, canMerge)
        } else {
            setButton(addBtn, R.drawable.ic_add, "Add call", false)
            if (state == Call.STATE_HOLDING) {
                setButton(holdBtn, R.drawable.ic_play, "Resume", true)
            } else {
                val canHold = (primary.details.callCapabilities and Call.Details.CAPABILITY_HOLD) != 0
                setButton(holdBtn, R.drawable.ic_pause, "Hold", false, canHold)
            }
        }
    }

    private fun elapsed(call: Call): String {
        val start = call.details.connectTimeMillis
        if (start <= 0) return "In call"
        val seconds = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}
