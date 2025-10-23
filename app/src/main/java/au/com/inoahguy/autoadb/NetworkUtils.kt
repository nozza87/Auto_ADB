package au.com.inoahguy.autoadb

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun getDeviceIP(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?

        if (wifiManager == null) {
            return "127.0.0.1"
        }

        val ipAddress = wifiManager.connectionInfo.ipAddress

        // Convert int to byte array in little-endian order
        val ipBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(ipAddress)
            .array()

        // Convert byte array to IP address string
        val inetAddress = InetAddress.getByAddress(ipBytes)
        val result = inetAddress.hostAddress
        return result ?: "127.0.0.1"
    } catch (e: java.lang.Exception) {
        Log.e("getDeviceIP()", "Failed to get device IP", e)
        return "127.0.0.1"
    }
}