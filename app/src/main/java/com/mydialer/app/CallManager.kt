package com.mydialer.app

import android.telecom.Call

/** Keeps track of the calls that are happening right now. */
object CallManager {

    private val calls = ArrayList<Call>()

    /** Called whenever a call is added, removed or changes state. */
    var onChange: (() -> Unit)? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            onChange?.invoke()
        }
    }

    val current: Call?
        get() = calls.lastOrNull()

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

    fun isEmpty(): Boolean = calls.isEmpty()
}
