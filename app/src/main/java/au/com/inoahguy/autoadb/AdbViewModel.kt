package au.com.inoahguy.autoadb

import android.app.Application
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class AdbViewModel(application: Application) : AndroidViewModel(application) {
    @Suppress("PropertyName")
    val TAG = "AdbViewModel"
    private val executor = Executors.newFixedThreadPool(3)
    private val connectAdb = MutableLiveData<Boolean>()
    private val pairAdb = MutableLiveData<Boolean>()
    private val askPairAdb = MutableLiveData<Boolean>()
    private val commandOutput = MutableLiveData<CharSequence>()
    private val pairingPort = MutableLiveData<Int>()

    private var adbShellStream: AdbStream? = null

    fun watchConnectAdb(): LiveData<Boolean> {
        return connectAdb
    }

    fun watchPairAdb(): LiveData<Boolean> {
        return pairAdb
    }

    fun watchAskPairAdb(): LiveData<Boolean> {
        return askPairAdb
    }

    fun watchCommandOutput(): LiveData<CharSequence> {
        return commandOutput
    }

    fun watchPairingPort(): LiveData<Int> {
        return pairingPort
    }

    override fun onCleared() {
        super.onCleared()
        executor.submit {
            try {
                adbShellStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                AdbConnectionManager.getInstance(getApplication()).close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        executor.shutdown()
    }

    /*
    fun connect(port: Int) {
        executor.submit {
            try {
                val manager = AdbConnectionManager.getInstance(getApplication())
                var connectionStatus: Boolean
                try {
                    connectionStatus = manager.connect(AndroidUtils.getHostIpAddress(getApplication()), port)
                } catch (th: Throwable) {
                    th.printStackTrace()
                    connectionStatus = false
                }
                connectAdb.postValue(connectionStatus)
            } catch (th: Throwable) {
                th.printStackTrace()
                connectAdb.postValue(false)
            }
        }
    }
    */

    fun autoConnect() {
        executor.submit(this::autoConnectInternal)
    }

    /*
    fun disconnect() {
        executor.submit {
            try {
                val manager = AdbConnectionManager.getInstance(getApplication())
                manager.disconnect()
                connectAdb.postValue(false)
            } catch (th: Throwable) {
                th.printStackTrace()
                connectAdb.postValue(true)
            }
        }
    }
    */

    /*
    fun getPairingPort() {
        executor.submit {
            val atomicPort = AtomicInteger(-1)
            val resolveHostAndPort = CountDownLatch(1)

            val adbMdns = AdbMdns(getApplication(), AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                atomicPort.set(port)
                resolveHostAndPort.countDown()
            }
            adbMdns.start()

            try {
                if (!resolveHostAndPort.await(1, TimeUnit.MINUTES)) {
                    return@submit
                }
            } catch (ignore: InterruptedException) {
            } finally {
                adbMdns.stop()
            }

            pairingPort.postValue(atomicPort.get())
        }
    }
    */

    /*
    fun pair(port: Int, pairingCode: String) {
        executor.submit {
            try {
                val pairingStatus: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val manager = AdbConnectionManager.getInstance(getApplication())
                    manager.pair(AndroidUtils.getHostIpAddress(getApplication()), port, pairingCode)
                } else false
                pairAdb.postValue(pairingStatus)
                autoConnectInternal()
            } catch (th: Throwable) {
                th.printStackTrace()
                pairAdb.postValue(false)
            }
        }
    }
    */

    @WorkerThread
    private fun autoConnectInternal() {
        try {
            val manager = AdbConnectionManager.getInstance(getApplication())
            var connected = false
            try {
                connected = manager.autoConnect(getApplication(), 5000)
            } catch (e: AdbPairingRequiredException) {
                Log.w(TAG, "Pairing required! ${e.toString()}")
                askPairAdb.postValue(true)
                return
            } catch (th: Throwable) {
                th.printStackTrace()
            }
            if (!connected) {
                connected = manager.connect(5555)
            }
            if (connected) {
                connectAdb.postValue(true)
            }
        } catch (th: Throwable) {
            th.printStackTrace()
        }
    }

    @Volatile
    private var clearEnabled = false
    private val outputGenerator = Runnable {
        try {
            BufferedReader(InputStreamReader(adbShellStream!!.openInputStream())).use { reader ->
                val sb = StringBuilder()
                var s: String?
                while (reader.readLine().also { s = it } != null) {
                    if (clearEnabled) {
                        sb.delete(0, sb.length)
                        clearEnabled = false
                    }
                    sb.append(s).append("\n")
                    commandOutput.postValue(sb)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun execute(command: String) {
        executor.submit {
            try {
                if (adbShellStream == null || adbShellStream!!.isClosed) {
                    val manager = AdbConnectionManager.getInstance(getApplication())
                    adbShellStream = manager.openStream(LocalServices.SHELL)
                    Thread(outputGenerator).start()
                }
                if (command == "clear") {
                    clearEnabled = true
                }
                adbShellStream!!.openOutputStream().use { os ->
                    os.write(String.format("%1\$s\n", command).toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                    os.write("\n".toByteArray(StandardCharsets.UTF_8))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun tcpip(port: Int) {
        executor.submit {
            try {
                Log.v("tcpip()", "open")
                val manager = AdbConnectionManager.getInstance(getApplication())
                manager.adbConnection?.open("tcpip:${port}")
                Thread(outputGenerator).start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /*
    fun usb() { // Disables tcpip
        executor.submit {
            try {
                Log.v("usb()", "open")
                val manager = AdbConnectionManager.getInstance(getApplication())
                manager.adbConnection?.open("usb")
                Thread(outputGenerator).start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    */
}
