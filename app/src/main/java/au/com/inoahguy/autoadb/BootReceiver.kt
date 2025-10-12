package au.com.inoahguy.autoadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    @Suppress("PropertyName")
    val TAG = "<BootReceiver>"

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive")

        val mSharedPreferences = context.getSharedPreferences(
            "${context.applicationContext.packageName}",
            Context.MODE_PRIVATE
        )

        if (Intent.ACTION_BOOT_COMPLETED == intent.action || "au.com.inoahguy.autoadb.TEST_BOOT_RECEIVER" == intent.action.toString()) {
            // Start 'Auto ADB' on system boot
            if (!mSharedPreferences.getBoolean("ChkAutoStartEnabled", false)) {
                Log.d(TAG, "Boot completed. [Start 'Auto ADB' on system boot] disabled.")
                context.showToast("Boot completed. [Start 'Auto ADB' on system boot] disabled", Toast.LENGTH_LONG)
                return
            } else {
                Log.d(
                    TAG,
                    "Boot completed. [Start 'Auto ADB' on system boot] enabled, Starting Auto ADB Service."
                )
                context.showToast(
                    "Boot completed. Starting Auto ADB"
                )
                // Start the foreground service
                val serviceIntent = Intent(context, BootForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
        else {
            Log.d(TAG, intent.action.toString())
        }
    }
}