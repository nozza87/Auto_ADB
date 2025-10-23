package au.com.inoahguy.autoadb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress

class PairWebService : Service() {

    private val TAG = "PairWebService"
    private lateinit var webServer: WebServer
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var ipAddress = "127.0.0.1"
    private var adbPort = -1
    private var adbMdns: AdbMdns? = null

    private val handler = Handler(Looper.getMainLooper())
    private val SERVICE_TIMEOUT_MS = 60_000L

    companion object {
        @Volatile
        private var isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            Log.d(TAG, "Service is already running.")
            applicationContext.showToast("Pairing web service is already running.")
            return START_NOT_STICKY
        }
        isRunning = true

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "pair_web_service_channel")
            .setContentTitle("Pairing Web Service")
            .setContentText("Service is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)

        // Discover the ADB pairing port
        adbMdns = AdbMdns(applicationContext, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { host: InetAddress?, port: Int ->
            if (port != -1) {
                adbPort = port
                Log.d(TAG, "Discovered ADB pairing port: $port")
                applicationContext.showToast(
                    "Open http://$ipAddress:${webServer.listeningPort} on another device and enter pairing code for $ipAddress:$adbPort",
                    Toast.LENGTH_LONG
                )
            }
        }
        adbMdns?.start()

        webServer = WebServer()
        try {
            ipAddress = getDeviceIP(applicationContext)
            webServer.start()
            applicationContext.showToast(
                "Web server started @ http://$ipAddress:${webServer.listeningPort} for 60 seconds",
                Toast.LENGTH_LONG
            )
            // Auto-disable after timeout
            handler.postDelayed({
                Log.d(TAG, "Disabling server due to timeout.")
                applicationContext.showToast(
                    "Web server disabled due to timeout.",
                    Toast.LENGTH_LONG
                )
                stopSelf()
            }, SERVICE_TIMEOUT_MS)

            // Open Debug Menu Screen
            val devSettingsIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            devSettingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            devSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            devSettingsIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            try {
                startActivity(devSettingsIntent)
                showToast("Navigate to 'Developer options -> Wireless debugging\n-> Pair device with pairing code'.", Toast.LENGTH_LONG)
            } catch (ex: Exception) {
                ex.printStackTrace()
                showToast("Could not open developer settings", Toast.LENGTH_LONG)
            }


        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        isRunning = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun stopServer() {
        Log.d(TAG, "Stopping server")
        handler.removeCallbacksAndMessages(null)
        if (::webServer.isInitialized && webServer.isAlive) {
            webServer.stop()
        }
        adbMdns?.stop()
        applicationContext.showToast(
            "Web server stopped.",
            Toast.LENGTH_LONG
        )
    }

    private fun createNotificationChannel() {
        val name = "Pair Web Service Channel"
        val descriptionText = "Channel for pairing web service"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("pair_web_service_channel", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private inner class WebServer : NanoHTTPD(8080) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            Log.i(TAG, "uri: $uri, method: ${session.method}")

            if (uri == "/stop" && session.method == Method.GET) {
                Handler(Looper.getMainLooper()).postDelayed({
                    stopSelf()
                }, 1000)

                val json = """{"success": true, "message": "Server Stopped"}"""
                return newFixedLengthResponse(Response.Status.OK, "application/json", json)
            }

            return when {
                uri == "/" -> serveIndexPage()
                uri == "/pair" && session.method == Method.POST -> handlePairRequest(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Not Found"
                )
            }
        }

        private fun handlePairRequest(session: IHTTPSession): Response {
            try {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)

                val params = session.parameters
                Log.i(TAG, "Request parameters: $params")

                val pairingCode = params["code"]?.firstOrNull() ?: ""

                Log.i(TAG, "Pairing code: $pairingCode")

                if (pairingCode.isEmpty()) {
                    val json = """{"success": false, "message": "No Pairing Code"}"""
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", json)
                }

                // Call the pairing method in a coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val isPaired = withContext(Dispatchers.IO) {
                            AdbRepository.pair(adbPort, pairingCode)
                        }

                        withContext(Dispatchers.Main) { // Switch back to the Main thread to show the result toasts
                            if (isPaired) {
                                Log.i(TAG, "Pairing successful!")
                                applicationContext.showToast("Pairing successful")
                                applicationContext.showToast(
                                    "Please return to \"Auto ADB\" and\npress [ENABLE LEGACY MODE].",
                                    Toast.LENGTH_LONG
                                )
                                stopSelf()
                            } else {
                                Log.w(TAG, "Pairing failed!")
                                applicationContext.showToast("Pairing failed. Check logs.")
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val json = """{"success": true, "message": "Check device for status"}"""
                return newFixedLengthResponse(Response.Status.OK, "application/json", json)

            } catch (e: Exception) {
                val json = """{"success": false, "message": "${e.message}"}"""
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", json)
            }
        }

        private fun serveIndexPage(): Response {
            ipAddress = getDeviceIP(applicationContext)
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Auto ADB Pairing</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            max-width: 500px;
                            margin: 50px auto;
                            padding: 20px;
                            background: #1e1f22;
                        }
                        .container {
                            background: #373537;
                            padding: 30px;
                            border-radius: 10px;
                            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        }
                        h1 {
                            color: #fff;
                            text-align: center;
                        }
                        .info {
                            background: #e3f2fd;
                            padding: 15px;
                            border-radius: 5px;
                            margin: 20px 0;
                        }
                        .info-label {
                            font-weight: bold;
                            color: #1976d2;
                        }
                        .info-value {
                            font-size: 18px;
                            color: #333;
                            margin: 5px 0;
                        }
                        input {
                            width: 100%;
                            padding: 12px;
                            margin: 10px 0;
                            border: 2px solid #ddd;
                            border-radius: 5px;
                            box-sizing: border-box;
                            font-size: 16px;
                        }
                        button {
                            width: 100%;
                            padding: 12px;
                            background: #4CAF50;
                            color: white;
                            border: none;
                            border-radius: 5px;
                            font-size: 16px;
                            cursor: pointer;
                            margin-top: 10px;
                        }
                        button:hover {
                            background: #45a049;
                        }
                        #stopBtn {
                             background: #f75a4a !important;
                        }
                        #stopBtn:hover {
                             background: #ed4c34 !important;
                        }
                        button:disabled {
                            background: #ccc;
                            cursor: not-allowed;
                        }
                        #message {
                            margin-top: 15px;
                            padding: 10px;
                            border-radius: 5px;
                            display: none;
                        }
                        .success {
                            background: #d4edda;
                            color: #155724;
                            border: 1px solid #c3e6cb;
                        }
                        .error {
                            background: #f8d7da;
                            color: #721c24;
                            border: 1px solid #f5c6cb;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>ADB Pairing</h1>
                        <!--
                        <div class="info">
                            <div class="info-label">Device IP:</div>
                            <div class="info-value" id="deviceIp">$ipAddress</div>
                        </div>
                        
                        <div class="info">
                            <div class="info-label">ADB WiFi Port:</div>
                            <div class="info-value" id="adbPort">$adbPort</div>
                        </div>
                        -->
                        <form id="pairForm">
                            <input 
                                type="number" 
                                id="pairingCode" 
                                placeholder="Enter 6 digit pairing code" 
                                pattern="[0-9]{6}"
                                maxlength="6"
                                max="999999"
                                required
                            />
                            <button type="submit" id="pairBtn">Pair</button>
                        </form>
                        
                        <div id="message"></div>
                        
                        <button type="button" id="stopBtn">Stop Server</button>
                    </div>
                    <script>
                        document.getElementById('stopBtn').addEventListener('click', async (e) => {
                            const btn = document.getElementById('pairBtn');
                            const btn2 = document.getElementById('stopBtn');
                            const msg = document.getElementById('message');

                            btn.disabled = true;
                            btn2.disabled = true;
                            btn.textContent = 'Stopping...';
                            msg.style.display = 'none';

                            try {
                                console.log('Stopping Server');

                                const response = await fetch('/stop', {
                                    method: 'GET',
                                });
                                
                                const result = await response.json();
                                
                                msg.style.display = 'block';
                                if (result.success) {
                                    msg.className = 'success';
                                    msg.textContent = result.message || 'Server Stopped!';
                                    btn2.textContent = 'Stopped';
                                } else {
                                    msg.className = 'error';
                                    msg.textContent = result.message || 'Stopping failed!';
                                }

                            } catch (error) {
                                msg.style.display = 'block';
                                msg.className = 'error';
                                msg.textContent = 'Error: ' + error.message;
                                btn.disabled = false;
                                btn2.disabled = false;
                            } finally {
                                // pass
                            }
                        });
                        
                        document.getElementById('pairForm').addEventListener('submit', async (e) => {
                            e.preventDefault();

                            const btn = document.getElementById('pairBtn');
                            const btn2 = document.getElementById('stopBtn');
                            const msg = document.getElementById('message');
                            const code = document.getElementById('pairingCode').value;

                            btn.disabled = true;
                            btn.textContent = 'Pairing...';
                            btn2.disabled = true;
                            msg.style.display = 'none';

                            try {
                                console.log('Pairing code:', code);
                                if (code.length !== 6) {
                                    throw new Error('Invalid pairing code');
                                }

                                const response = await fetch('/pair', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'application/x-www-form-urlencoded',
                                    },
                                    body: 'code=' + encodeURIComponent(code)
                                });

                                const result = await response.json();

                                msg.style.display = 'block';
                                if (result.success) {
                                    msg.className = 'success';
                                    msg.textContent = result.message || 'Pairing successful!';
                                    document.getElementById('pairingCode').value = '';
                                } else {
                                    msg.className = 'error';
                                    msg.textContent = result.message || 'Pairing failed!';
                                }
                            } catch (error) {
                                msg.style.display = 'block';
                                msg.className = 'error';
                                msg.textContent = 'Error: ' + error.message;
                            } finally {
                                btn.disabled = false;
                                btn.textContent = 'Pair';
                                btn2.disabled = false;
                            }
                        });
                    </script>
                </body>
                </html>
            """.trimIndent()

            return newFixedLengthResponse(html)
        }
    }
}
