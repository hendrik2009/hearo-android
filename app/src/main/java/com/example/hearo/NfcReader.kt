package com.example.hearo

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "HearoNfc"
private const val NFC_POLL_INTERVAL_MS = 400L

class NfcReader(private val activity: Activity) {

    private var nfcAdapter: NfcAdapter? = null
    private var readerCallback: NfcAdapter.ReaderCallback? = null
    private val presenceCheckCancelled = AtomicBoolean(false)
    private val presenceCheckGeneration = AtomicInteger(0)

    fun init(onTagDetected: (String) -> Unit, onTagRemoved: () -> Unit) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        readerCallback = NfcAdapter.ReaderCallback { tag ->
            handleTag(tag, onTagDetected, onTagRemoved)
        }
    }

    fun enableTagDetection() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        readerCallback?.let { callback ->
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            adapter.enableReaderMode(activity, callback, flags, null)
            Log.d(TAG, "enableTagDetection: reader mode enabled")
        }
    }

    fun disableTagDetection() {
        presenceCheckCancelled.set(true)
        nfcAdapter?.disableReaderMode(activity)
        Log.d(TAG, "disableTagDetection: reader mode disabled")
    }

    private fun handleTag(tag: Tag, onTagDetected: (String) -> Unit, onTagRemoved: () -> Unit) {
        val id = tag.id.toHex()
        Log.d(TAG, "onTagDiscovered id=$id")
        presenceCheckCancelled.set(true)
        activity.runOnUiThread { onTagDetected(id) }
        startTagPresenceCheck(tag, onTagRemoved)
    }

    /**
     * Runs on a background thread: polls the tag (NfcA) until it is removed or cancelled,
     * then calls onTagRemoved on the main thread to clear the UI.
     */
    private fun startTagPresenceCheck(tag: Tag, onTagRemoved: () -> Unit) {
        presenceCheckCancelled.set(false)
        val myGeneration = presenceCheckGeneration.incrementAndGet()
        Thread {
            try {
                Thread.sleep(NFC_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (presenceCheckCancelled.get()) return@Thread
            val nfcA = NfcA.get(tag) ?: return@Thread
            try {
                while (!presenceCheckCancelled.get()) {
                    try {
                        nfcA.connect()
                        nfcA.close()
                    } catch (_: IOException) {
                        break
                    } catch (_: SecurityException) {
                        break
                    }
                    try {
                        Thread.sleep(NFC_POLL_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                if (myGeneration == presenceCheckGeneration.get() && !presenceCheckCancelled.get()) {
                    Log.d(TAG, "tag removed, clearing UI")
                    activity.runOnUiThread(onTagRemoved)
                }
            } catch (_: Exception) {
                if (myGeneration == presenceCheckGeneration.get() && !presenceCheckCancelled.get()) {
                    activity.runOnUiThread(onTagRemoved)
                }
            }
        }.start()
    }

    private fun ByteArray.toHex(): String = joinToString("") { b -> "%02x".format(b) }
}
