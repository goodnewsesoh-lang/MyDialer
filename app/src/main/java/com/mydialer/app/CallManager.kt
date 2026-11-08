package com.mydialer.app

import android.telecom.Call

/** Keeps track of all the calls that are happening right now. */
object CallManager {

    val calls = ArrayList<Call>()

    /** Called whenever a call is added, removed or changes. */
    var onChange: (() -> Unit)? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            onChange?.invoke()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            onChange?.invoke()
        }
    }

    fun add(call: Call) {
        if (!calls.contains(call)) calls.add(call)
        call.registerCallback(callback)
        onChange?.invoke()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        onChange?.invoke()
    }

    /** Calls that are shown on screen. Parts of a conference call are hidden. */
    fun visible(): List<Call> = calls.filter { it.parent == null }

    /** The main call: the one you are talking on, otherwise the one dialing, ringing or on hold. */
    @Suppress("DEPRECATION")
    fun primary(): Call? {
        val list = visible()
        return list.lastOrNull { it.state == Call.STATE_ACTIVE }
            ?: list.lastOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: list.lastOrNull { it.state == Call.STATE_RINGING }
            ?: list.lastOrNull { it.state == Call.STATE_HOLDING }
            ?: list.lastOrNull()
    }
}
