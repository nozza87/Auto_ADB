package au.com.inoahguy.autoadb

import android.app.Application
import android.content.Context

//import org.lsposed.hiddenapibypass.HiddenApiBypass

class MainApplication : Application() {
    @Suppress("PropertyName")
    val TAG = "<MainApplication>"

    override fun onCreate() {
        super.onCreate()
        // Initialise the repository as soon as the app starts.
        AdbRepository.initialize(this)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        //HiddenApiBypass.addHiddenApiExemptions("L")
    }
}