package au.com.inoahguy.autoadb

import android.database.ContentObserver
import android.os.Handler
import android.util.Log

/**
 * Observer for global settings changes, specifically ADB-related settings.
 * Notifies the MainActivity when USB or Wireless debugging settings change.
 */
class GlobalSettingsObserver(
    handler: Handler,
    private val callback: SettingsChangeCallback
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "GlobalSettingsObserver"
    }

    interface SettingsChangeCallback {
        fun onSettingsChanged()
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d(TAG, "Settings changed (selfChange=$selfChange)")

        try {
            callback.onSettingsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error in settings change callback", e)
        }
    }

    override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "Settings changed (selfChange=$selfChange, uri=$uri)")

        try {
            callback.onSettingsChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Error in settings change callback", e)
        }
    }
}