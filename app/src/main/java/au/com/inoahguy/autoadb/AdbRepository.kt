package au.com.inoahguy.autoadb

import android.app.Application
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton repository to manage ADB connection state for the entire application.
 * Must be initialized from Application.onCreate() before use.
 */
object AdbRepository {
    private const val TAG = "AdbRepository"
    private const val LOCALHOST = "127.0.0.1"

    private lateinit var manager: AbsAdbConnectionManager
    private lateinit var application: Application

    val isInitialized: Boolean
        get() = ::manager.isInitialized

    /**
     * Initialize the repository. Must be called from Application.onCreate().
     */
    fun initialize(application: Application) {
        if (isInitialized) {
            Log.w(TAG, "AdbRepository already initialized")
            return
        }

        this.application = application
        manager = AdbConnectionManager.getInstance(application)
        Log.d(TAG, "AdbRepository initialized successfully")
    }

    /**
     * Pair with ADB using localhost and the specified port and pairing code.
     * This is a suspend function that runs on IO dispatcher.
     *
     * @param port The pairing port
     * @param pairingCode The 6-digit pairing code
     * @return true if pairing succeeded, false otherwise
     */
    suspend fun pair(port: Int, pairingCode: String): Boolean {
        return pair(LOCALHOST, port, pairingCode)
    }

    /**
     * Pair with ADB using the specified IP, port and pairing code.
     * This is a suspend function that runs on IO dispatcher.
     *
     * @param ip The IP address to connect to
     * @param port The pairing port
     * @param pairingCode The 6-digit pairing code
     * @return true if pairing succeeded, false otherwise
     */
    suspend fun pair(ip: String, port: Int, pairingCode: String): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Repository not initialized! Call initialize() first.")
            return false
        }

        if (pairingCode.length != 6 || !pairingCode.all { it.isDigit() }) {
            Log.e(TAG, "Invalid pairing code format: $pairingCode")
            return false
        }

        if (port !in 1..65535) {
            Log.e(TAG, "Invalid port number: $port")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to pair with $ip:$port, code=$pairingCode")
                val result = manager.pair(ip, port, pairingCode)
                Log.d(TAG, "Pairing result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Exception during pairing with $ip:$port", e)
                false
            }
        }
    }

    /**
     * Get the current ADB connection manager instance.
     * @throws IllegalStateException if not initialized
     */
    fun getManager(): AbsAdbConnectionManager {
        if (!isInitialized) {
            throw IllegalStateException("AdbRepository not initialized")
        }
        return manager
    }
}