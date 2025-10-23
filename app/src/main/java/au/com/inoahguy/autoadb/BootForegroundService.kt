package au.com.inoahguy.autoadb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

class BootForegroundService : Service() {

    companion object {
        private const val TAG = "BootForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AutoADBForegroundServiceChannel"
        private const val CHANNEL_NAME = "Auto ADB Foreground Service Channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        try {
            createNotificationChannel()

            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification)

            Log.d(TAG, "Foreground service started")

            launchMainActivity()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            applicationContext.showToast(
                "Failed to start Auto ADB Service",
                Toast.LENGTH_LONG
            )
        } finally {
            // Always stop the service
            stopSelf()
            Log.d(TAG, "Service stopped")
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound/vibration
        ).apply {
            description = "Used to launch Auto ADB on boot"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto ADB")
            .setContentText("Starting...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun launchMainActivity() {
        try {
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            applicationContext.startActivity(launchIntent)
            Log.d(TAG, "MainActivity launched successfully")

            // Also bring existing instance to front if already running
            val bringToFrontIntent = packageManager.getLaunchIntentForPackage(packageName)
            bringToFrontIntent?.apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                applicationContext.startActivity(this)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity", e)
            applicationContext.showToast(
                "Failed to launch Auto ADB",
                Toast.LENGTH_LONG
            )
        }
    }
}