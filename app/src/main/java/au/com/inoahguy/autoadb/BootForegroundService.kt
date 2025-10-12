package au.com.inoahguy.autoadb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.Int
import kotlin.toString
class BootForegroundService : Service() {
    @Suppress("PropertyName")
    val TAG = "<ForegroundService>"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // Build the notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto ADB")
            .setContentText("Starting...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .build()

        // Start the service as a foreground service
        startForeground(NOTIFICATION_ID, notification)

        Log.d(TAG, "Auto ADB Service started")
        //applicationContext.showToast("Auto ADB Service started")

        val pm: PackageManager = applicationContext.packageManager
        val packageLeanbackLaunchActivity: String = pm.getLeanbackLaunchIntentForPackage("${applicationContext.packageName}").toString()
        Log.v(TAG, "packageLeanbackLaunchActivity: '$packageLeanbackLaunchActivity'")

        try {
            val launchIntent = Intent(applicationContext, MainActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(launchIntent)

            val startIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            assert(startIntent != null)
            startIntent!!.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            applicationContext.startActivity(startIntent)

            Log.d(TAG, "Launched Auto ADB")
            //applicationContext.showToast("Launched Auto ADB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Auto ADB", e)
            applicationContext.showToast("Failed to launch Auto ADB.", Toast.LENGTH_LONG)
        }

        // Stop the foreground service once the activity has been started
        stopSelf()

        Log.d(TAG, "Auto ADB Service complete.")
        //applicationContext.showToast("Auto ADB Service complete")

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Auto ADB Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager: NotificationManager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AutoADBForegroundServiceChannel"
    }
}