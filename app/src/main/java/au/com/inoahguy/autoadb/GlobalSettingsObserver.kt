package au.com.inoahguy.autoadb

import android.database.ContentObserver
import android.os.Handler
import android.util.Log

class GlobalSettingsObserver(handler: Handler?, private val activity: MainActivity) : ContentObserver(handler) {
    @Suppress("PropertyName")
    val TAG = "<GlobalSettingsObserver>"

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d(TAG, "GlobalSettingsObserver: onChange($selfChange)")
        activity.updateToggles()
    }
}