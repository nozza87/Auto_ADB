package au.com.inoahguy.autoadb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.text.isDigitsOnly
import kotlinx.coroutines.*

@SuppressLint("AccessibilityPolicy")
class AdbPairingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdbPairingService"
        const val PAIRING_DIALOG_TITLE_TEXT = "Wi‑Fi pairing code"
        const val PAIRING_CODE_PATTERN = "\\d{6}"
        const val IP_AND_PORT_PATTERN =
            """^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])(?::([0-9]{1,5}))?$"""
        private const val SERVICE_TIMEOUT_MS = 60_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    @Volatile
    private var pairingCodeSent = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")

        pairingCodeSent = false

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        serviceInfo = info

        Log.d(TAG, "Service info configured")
        applicationContext.showToast("Accessibility service is enabled (60 seconds).")
        applicationContext.showToast("Please return to \"Auto ADB\" and\npress [PAIR VIA ACCESSIBILITY SERVICE] again.", Toast.LENGTH_LONG)

        // Auto-disable after timeout
        handler.postDelayed({
            Log.d(TAG, "Disabling service due to timeout.")
            applicationContext.showToast(
                "Accessibility service disabled due to timeout.",
                Toast.LENGTH_LONG
            )
            disableSelf()
        }, SERVICE_TIMEOUT_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || pairingCodeSent) return

        rootInActiveWindow?.let { root ->
            findAndProcessPairingCode(root)
        }
    }

    private fun findAndProcessPairingCode(rootNode: AccessibilityNodeInfo) {
        val regexCode = Regex(PAIRING_CODE_PATTERN)
        val regexIpPort = Regex(IP_AND_PORT_PATTERN)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        var dialogTitleFound = false
        var pairingCode: String? = null
        var ipAndPort: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()

            if (text.isNullOrBlank()) {
                // Add children to queue
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }

            // First, find the dialog title
            if (!dialogTitleFound) {
                if (text.contains(PAIRING_DIALOG_TITLE_TEXT, ignoreCase = true)) {
                    Log.d(TAG, "Dialog title found: $text")
                    dialogTitleFound = true
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
                continue
            }

            // After title is found, search for code and IP:port
            Log.v(TAG, "Scanning text: $text")

            if (pairingCode == null) {
                regexCode.find(text)?.let {
                    pairingCode = it.value
                    Log.i(TAG, "Pairing code found: $pairingCode")
                }
            }

            if (ipAndPort == null) {
                regexIpPort.find(text)?.let {
                    ipAndPort = it.value
                    Log.i(TAG, "IP:Port found: $ipAndPort")
                }
            }

            // If we have both, process pairing
            if (pairingCode != null && ipAndPort != null) {
                sendPairingCodeToApp(pairingCode, ipAndPort)
                return
            }

            // Add children to queue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    private fun sendPairingCodeToApp(pairingCode: String, ipAndPort: String) {
        if (pairingCodeSent) {
            Log.w(TAG, "Pairing code already sent.")
            return
        }
        pairingCodeSent = true

        val parts = ipAndPort.split(":")
        if (parts.size != 2 || !parts[1].isDigitsOnly()) {
            applicationContext.showToast("Error parsing port from pairing data, try again")
            Log.e(TAG, "Could not parse port from $ipAndPort")
            pairingCodeSent = false // Allow retry
            return
        }

        val port = parts[1].toIntOrNull() ?: run {
            applicationContext.showToast("Invalid port number")
            Log.e(TAG, "Invalid port number: ${parts[1]}")
            pairingCodeSent = false // Allow retry
            return
        }

        serviceScope.launch {
            try {
                withContext(Dispatchers.Main) { // Ensure this toast is shown on the main thread before switching context
                    applicationContext.showToast("Pairing code found. Attempting to pair in background...")
                }

                val isPaired = withContext(Dispatchers.IO) {
                    AdbRepository.pair(port, pairingCode)
                }

                withContext(Dispatchers.Main) { // Switch back to the Main thread to show the result toasts
                    if (isPaired) {
                        Log.i(TAG, "Pairing successful!")
                        applicationContext.showToast("Pairing successful")
                        applicationContext.showToast(
                            "Please return to \"Auto ADB\" and\npress [ENABLE LEGACY MODE].",
                            Toast.LENGTH_LONG
                        )
                    } else {
                        Log.w(TAG, "Pairing failed!")
                        applicationContext.showToast("Pairing failed. Check logs.")
                    }
                }

                handler.postDelayed({ // Post a delayed action to disable the service to give the Toasts enough time to be displayed.
                    Log.d(TAG, "Stopping service after pairing attempt.")
                    disableSelf()
                }, 8_000L) // Wait for Toast before disabling.
            }
            catch (e: Exception) {
                Log.e(TAG, "Exception during pairing", e)
                applicationContext.showToast("Pairing error: ${e.message}")
                handler.postDelayed({ // Also ensure service is disabled on error after a delay for Toast
                    disableSelf()
                }, 5_000L)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        cleanup()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility Service Unbound")
        applicationContext.showToast("Accessibility service is disabled.")
        cleanup()
        return super.onUnbind(intent)
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        serviceJob.cancel()
    }
}