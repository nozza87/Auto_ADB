package au.com.inoahguy.autoadb

import android.app.Application
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Singleton object to manage the ADB connection state for the entire app.
object AdbRepository {
    private const val TAG = "AdbRepository"
    private lateinit var manager: AbsAdbConnectionManager
    private lateinit var application: Application

    // This MUST be called from the Application's onCreate method.
    fun initialize(application: Application) {
        this.application = application
        manager = AdbConnectionManager.getInstance(application)
        Log.d(TAG, "AdbRepository initialized.")
    }

    // The core pairing logic
    // It's a suspend function to ensure it runs on a background thread.
    suspend fun pair(port: Int, pairingCode: String): Boolean {
        if (!::manager.isInitialized) {
            Log.e(TAG, "Repository not initialized!")
            return false
        }
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to pair with port=$port, code=$pairingCode")
                val result = manager.pair(port, pairingCode)
                Log.d(TAG, "Pairing result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception during pairing", e)
                false
            }
        }
    }

    @Suppress("unused")
    suspend fun pair(ip: String, port: Int, pairingCode: String): Boolean {
        if (!::manager.isInitialized) {
            Log.e(TAG, "Repository not initialized!")
            return false
        }
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to pair with $ip:$port, code=$pairingCode")
                val result = manager.pair(ip, port, pairingCode)
                Log.d(TAG, "Pairing result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception during pairing", e)
                false
            }
        }
    }
}