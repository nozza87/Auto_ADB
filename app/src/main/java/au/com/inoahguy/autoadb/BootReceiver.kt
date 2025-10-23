package au.com.inoahguy.autoadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val TEST_ACTION = "au.com.inoahguy.autoadb.TEST_BOOT_RECEIVER"
        private const val PREF_AUTO_START = "ChkAutoStartEnabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received broadcast: ${intent.action}")

        val action = intent.action ?: run {
            Log.w(TAG, "Received intent with null action")
            return
        }

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_REBOOT,
            TEST_ACTION -> handleBootCompleted(context)
            else -> Log.d(TAG, "Ignoring action: $action")
        }
    }

    private fun handleBootCompleted(context: Context) {
        val prefs = getSharedPreferences(context)
        val autoStartEnabled = prefs.getBoolean(PREF_AUTO_START, false)

        if (!autoStartEnabled) {
            Log.d(TAG, "Boot completed. Auto-start disabled.")
            context.showToast(
                "Boot completed. [Start 'Auto ADB' on system boot] disabled",
                Toast.LENGTH_LONG
            )
            return
        }

        Log.d(TAG, "Boot completed. Auto-start enabled, starting Auto ADB Service.")
        context.showToast("Boot completed. Starting Auto ADB")

        try {
            val serviceIntent = Intent(context, BootForegroundService::class.java)
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "Successfully started BootForegroundService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BootForegroundService", e)
            context.showToast(
                "Failed to start Auto ADB Service",
                Toast.LENGTH_LONG
            )
        }
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            context.applicationContext.packageName,
            Context.MODE_PRIVATE
        )
    }
}