package au.com.inoahguy.autoadb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.text.isDigitsOnly
import kotlinx.coroutines.delay

class AdbPairingAccessibilityService : AccessibilityService() {
    @Suppress("PropertyName")
    val TAG = "AdbPairingService"
    private val handler = Handler(Looper.getMainLooper())

    // Create a CoroutineScope for the service
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var pairingCodeSent = false
    companion object {
        const val PAIRING_DIALOG_TITLE_TEXT_PART = "Wi‑Fi pairing code"
        const val PAIRING_CODE_REGEX_PATTERN = "\\d{6}" // 6-digit code, Eg: 123456
        const val IP_AND_PORT_REGEX_PATTERN = """^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]|[0-9])(?::([0-9]{1,5}))?$""" // IP:Port Eg: 192.168.1.10:12345


    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")

        pairingCodeSent = false

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        serviceInfo = info
        Log.d(TAG, "onServiceConnected called and serviceInfo set!")
        applicationContext.showToast("Accessibility service is enabled.")
        handler.postDelayed({
            Log.d(TAG, "Disabling service due to 60 second timeout.")
            applicationContext.showToast("Disabled service due to timeout.", Toast.LENGTH_LONG)
            disableSelf()
        }, 60000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || pairingCodeSent) return

        //Log.d(TAG, "onAccessibilityEvent: type=${AccessibilityEvent.eventTypeToString(event.eventType)}, pkg=${event.packageName}")

        val rootNode = rootInActiveWindow ?: return // Get the root node of the active window
        // Log.d(TAG, "Root node: $rootNode") // Verbose, use for debugging

        findPairingCode(rootNode)
    }

    private fun findPairingCode(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        val regex = Regex(PAIRING_CODE_REGEX_PATTERN)
        val ipAndPortRegex = Regex(IP_AND_PORT_REGEX_PATTERN)

        val queue: MutableList<AccessibilityNodeInfo> = mutableListOf()

        var dialogTitleFound = false
        var pairingCode = ""
        var pairingCodeFound = false
        var ipAndPort = ""
        var ipAndPortFound = false

        // Search for the pairing code
        queue.add(nodeInfo)
        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            if (node.text != null) {
                val text = node.text.toString()

                if (!dialogTitleFound) {
                    if (text.contains(PAIRING_DIALOG_TITLE_TEXT_PART, ignoreCase = true) ) {
                        Log.d(TAG, "Dialog title part found: $text")
                        dialogTitleFound = true
                        continue
                    }
                    continue
                }

                Log.d(TAG, "Scanning text: $text")
                val matchResult = regex.find(text)
                if (matchResult != null) {
                    pairingCode = matchResult.value
                    Log.i(TAG, "Pairing code found: $pairingCode")
                    pairingCodeFound = true
                }
                val ipAndPortMatchResult = ipAndPortRegex.find(text)
                if (ipAndPortMatchResult != null) {
                    ipAndPort = ipAndPortMatchResult.value
                    Log.i(TAG, "IP:Port found: $ipAndPort")
                    ipAndPortFound = true
                }
                if (pairingCodeFound && ipAndPortFound) {
                    queue.clear()
                    sendPairingCodeToApp(pairingCode, ipAndPort)
                    break
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }


    private fun sendPairingCodeToApp(pairingCode: String, ipAndPort: String) {
        if (pairingCodeSent) { // Single event allowed only!
            Log.w(TAG, "Pairing code already sent.")
            return
        }
        pairingCodeSent = true

        val parts = ipAndPort.split(":")
        if (parts.size == 2 && parts[1].isDigitsOnly()) {
            //val ip = parts[0]
            val port = parts[1].toInt()

            // Use the service's CoroutineScope to launch the pairing task
            serviceScope.launch {
                applicationContext.showToast("Pairing code found. Attempting to pair in background...", Toast.LENGTH_SHORT)

                // Call the repository directly
                //val isPaired = AdbRepository.pair(ip, port, pairingCode)
                val isPaired = AdbRepository.pair(port, pairingCode) // don't need IP, use default local host 127.0.0.1

                // Show the result
                if (isPaired) {
                    Log.v(TAG, "Pairing successful!")
                    applicationContext.showToast("Pairing successful!", Toast.LENGTH_LONG)
                } else {
                    Log.v(TAG, "Pairing failed!")
                    applicationContext.showToast("Pairing failed. Check logs.", Toast.LENGTH_SHORT)
                }

                // Stop the service after the attempt, delay so toast can show
                delay(2000L)
                Log.d(TAG, "Stopping service after pairing attempt.")
                disableSelf()
            }
        } else {
            applicationContext.showToast("Error parsing port from pairing data, try again", Toast.LENGTH_SHORT)
            Log.e("AdbPairingService", "Could not parse port from $ipAndPort")
            // Don't disable if we fail so we can try again
            //handler.postDelayed({ disableSelf() }, 2000) // Also disable on error, delay so toast can show
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        serviceJob.cancel() // Cancel coroutines
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility Service Unbound")
        handler.removeCallbacksAndMessages(null) // Remove any pending timeouts
        serviceJob.cancel() // Cancel coroutines
        return super.onUnbind(intent)
    }
}
