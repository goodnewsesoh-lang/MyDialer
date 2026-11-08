package com.mydialer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService

/** Android connects to this service when a call comes in or goes out. */
class DialerCallService : InCallService() {

    companion object {
        const val CHANNEL_ID = "incoming_calls"
        const val MISSED_CHANNEL_ID = "missed_calls"
        const val NOTIFICATION_ID = 1
        var instance: DialerCallService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)

        val intent = Intent(this, CallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (call.state == Call.STATE_RINGING) {
            showIncomingNotification(call, intent)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // The full-screen notification will open the call screen instead.
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        var missed = false
        var number = ""
        try {
            missed = call.details.disconnectCause?.code == DisconnectCause.MISSED
            number = call.details.handle?.schemeSpecificPart ?: ""
        } catch (e: Exception) {
            // ignore
        }
        CallManager.remove(call)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        if (missed) showMissedNotification(number)
    }

    private fun showIncomingNotification(call: Call, intent: Intent) {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        channel.enableVibration(false)
        nm.createNotificationChannel(channel)

        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val number = call.details.handle?.schemeSpecificPart ?: ""
        val caller = Util.lookup(this, number)
        val title = caller.name ?: if (number.isBlank()) "Unknown caller" else number

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showMissedNotification(number: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(MISSED_CHANNEL_ID, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)

        val caller = Util.lookup(this, number)
        val who = caller.name ?: if (number.isBlank()) "Unknown caller" else number
        val id = 100 + (number.hashCode() and 0xFFF)

        val open = Intent(this, MainActivity::class.java)
        open.putExtra("tab", 0)
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            this, id, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, MISSED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Missed call")
            .setContentText(who)
            .setCategory(Notification.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openPending)

        if (number.isNotBlank()) {
            val call = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val callPending = PendingIntent.getActivity(
                this, id + 5000, call,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.sym_action_call),
                "Call back",
                callPending
            ).build()
            builder.addAction(action)
        }
        nm.notify(id, builder.build())
    }
}
