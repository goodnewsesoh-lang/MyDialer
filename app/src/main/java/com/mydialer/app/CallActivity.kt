package com.mydialer.app

import android.app.Activity
import android.app.NotificationManager
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
import android.widget.TextView

/** The screen you see during a call: caller photo, name, number and buttons. */
@Suppress("DEPRECATION")
class CallActivity : Activity() {

    private lateinit var photoView: ImageView
    private lateinit var nameView: TextView
    private lateinit var numberView: TextView
    private lateinit var stateView: TextView
    private lateinit var ringingRow: LinearLayout
    private lateinit var activeRow: LinearLayout
    private lateinit var endButton: TextView
    private lateinit var muteButton: TextView
    private lateinit var speakerButton: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var muted = false
    private var speaker = false
    private var shownNumber: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    private val listener: () -> Unit = { runOnUiThread { update() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
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
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(0xFF121212.toInt())
        root.setPadding(Util.dp(this, 24), Util.dp(this, 64), Util.dp(this, 24), Util.dp(this, 48))

        photoView = ImageView(this)
        root.addView(photoView, LinearLayout.LayoutParams(Util.dp(this, 160), Util.dp(this, 160)))

        nameView = Util.text(this, "", 28f, 0xFFFFFFFF.toInt(), true)
        nameView.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = Util.dp(this, 24)
        root.addView(nameView, nameLp)

        numberView = Util.text(this, "", 16f, 0xFFBDBDBD.toInt())
        numberView.gravity = Gravity.CENTER
        root.addView(numberView)

        stateView = Util.text(this, "", 16f, 0xFF90CAF9.toInt())
        stateView.gravity = Gravity.CENTER
        val stateLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stateLp.topMargin = Util.dp(this, 8)
        root.addView(stateView, stateLp)

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        // Buttons shown while the phone is ringing
        ringingRow = LinearLayout(this)
        ringingRow.orientation = LinearLayout.HORIZONTAL
        ringingRow.gravity = Gravity.CENTER
        val decline = button("Decline", Util.RED) { CallManager.current?.reject(false, null) }
        val answer = button("Answer", Util.GREEN) { CallManager.current?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        ringingRow.addView(decline, buttonParams())
        ringingRow.addView(answer, buttonParams())
        root.addView(ringingRow)

        // Buttons shown during a call
        activeRow = LinearLayout(this)
        activeRow.orientation = LinearLayout.HORIZONTAL
        activeRow.gravity = Gravity.CENTER
        muteButton = button("Mute", 0xFF424242.toInt()) { toggleMute() }
        speakerButton = button("Speaker", 0xFF424242.toInt()) { toggleSpeaker() }
        activeRow.addView(muteButton, buttonParams())
        activeRow.addView(speakerButton, buttonParams())
        root.addView(activeRow)

        endButton = button("End call", Util.RED) { CallManager.current?.disconnect() }
        val endLp = LinearLayout.LayoutParams(Util.dp(this, 200), Util.dp(this, 64))
        endLp.topMargin = Util.dp(this, 16)
        endLp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(endButton, endLp)

        setContentView(root)
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(Util.dp(this, 120), Util.dp(this, 64))
        lp.leftMargin = Util.dp(this, 8)
        lp.rightMargin = Util.dp(this, 8)
        return lp
    }

    private fun button(label: String, color: Int, action: () -> Unit): TextView {
        val b = Util.text(this, label, 16f, 0xFFFFFFFF.toInt(), true)
        b.gravity = Gravity.CENTER
        b.background = Util.roundBg(this, color, 32)
        b.setOnClickListener { action() }
        return b
    }

    private fun toggleMute() {
        muted = !muted
        DialerCallService.instance?.setMuted(muted)
        muteButton.text = if (muted) "Muted" else "Mute"
        muteButton.background = Util.roundBg(this, if (muted) Util.BLUE else 0xFF424242.toInt(), 32)
    }

    private fun toggleSpeaker() {
        speaker = !speaker
        DialerCallService.instance?.setAudioRoute(
            if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        )
        speakerButton.text = if (speaker) "Speaker on" else "Speaker"
        speakerButton.background = Util.roundBg(this, if (speaker) Util.BLUE else 0xFF424242.toInt(), 32)
    }

    private fun update() {
        val call = CallManager.current
        if (call == null) {
            finish()
            return
        }

        val number = call.details.handle?.schemeSpecificPart ?: ""
        if (number != shownNumber) {
            shownNumber = number
            val caller = Util.lookup(this, number)
            val title = caller.name ?: if (number.isBlank()) "Unknown caller" else number
            nameView.text = title
            numberView.text = if (caller.name != null) number else ""
            photoView.setImageBitmap(Util.avatar(this, title, caller.photoUri ?: caller.thumbUri, Util.dp(this, 160)))
        }

        val state = call.state
        stateView.text = when (state) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling..."
            Call.STATE_ACTIVE -> elapsed(call)
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_DISCONNECTING -> "Ending..."
            Call.STATE_DISCONNECTED -> "Call ended"
            else -> ""
        }

        val ringing = state == Call.STATE_RINGING
        ringingRow.visibility = if (ringing) View.VISIBLE else View.GONE
        activeRow.visibility = if (ringing) View.GONE else View.VISIBLE
        endButton.visibility = if (ringing) View.GONE else View.VISIBLE
    }

    private fun elapsed(call: Call): String {
        val start = call.details.connectTimeMillis
        if (start <= 0) return "In call"
        val seconds = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0)
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
