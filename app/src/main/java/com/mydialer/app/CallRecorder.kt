package com.mydialer.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records during a call using the microphone.
 * Android does not let apps record the other person's voice directly, so with Speaker on
 * the microphone hears both sides.
 */
class CallRecorder(private val ctx: Context) {

    private var recorder: MediaRecorder? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var uri: Uri? = null

    var isRecording = false
        private set

    @Suppress("DEPRECATION")
    fun start(label: String): Boolean {
        if (isRecording) return true
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = label.replace(Regex("[^A-Za-z0-9+]"), "_")
        val fileName = "Call_" + safe + "_" + stamp + ".m4a"
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)

            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues()
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MyDialer")
                values.put(MediaStore.Audio.Media.IS_PENDING, 1)
                val u = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                val d = ctx.contentResolver.openFileDescriptor(u, "w") ?: return false
                uri = u
                descriptor = d
                r.setOutputFile(d.fileDescriptor)
            } else {
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return false
                dir.mkdirs()
                r.setOutputFile(File(dir, fileName).absolutePath)
            }

            r.prepare()
            r.start()
            recorder = r
            isRecording = true
            return true
        } catch (e: Exception) {
            discard()
            return false
        }
    }

    /** Stops recording. Returns true if a recording was saved. */
    fun stop(): Boolean {
        if (!isRecording) return false
        isRecording = false
        var saved = true
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            saved = false
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
        try {
            descriptor?.close()
        } catch (e: Exception) {
            // ignore
        }
        descriptor = null

        val u = uri
        uri = null
        if (u != null) {
            try {
                if (saved) {
                    val values = ContentValues()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    ctx.contentResolver.update(u, values, null, null)
                } else {
                    ctx.contentResolver.delete(u, null, null)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return saved
    }

    private fun discard() {
        isRecording = false
        try {
            recorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        recorder = null
        try {
            descriptor?.close()
        } catch (e: Exception) {
            // ignore
        }
        descriptor = null
        val u = uri
        uri = null
        if (u != null) {
            try {
                ctx.contentResolver.delete(u, null, null)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
