package au.com.inoahguy.autoadb

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class AdbViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AdbViewModel"
        private const val AUTO_CONNECT_TIMEOUT_MS = 5_000L
        private const val DEFAULT_ADB_PORT = 5555
    }

    // LiveData for backward compatibility
    private val _connectAdb = MutableLiveData<Boolean>()
    val connectAdb: LiveData<Boolean> = _connectAdb

    private val _pairAdb = MutableLiveData<Boolean>()
    val pairAdb: LiveData<Boolean> = _pairAdb

    private val _askPairAdb = MutableLiveData<Boolean>()
    val askPairAdb: LiveData<Boolean> = _askPairAdb

    private val _commandOutput = MutableLiveData<CharSequence>()
    val commandOutput: LiveData<CharSequence> = _commandOutput

    private val _pairingPort = MutableLiveData<Int>()
    val pairingPort: LiveData<Int> = _pairingPort

    // StateFlow alternatives (recommended for new code)
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // Shell stream management
    private var adbShellStream: AdbStream? = null
    private var outputReaderJob: Job? = null
    private val commandChannel = Channel<String>(Channel.BUFFERED)

    @Volatile
    private var clearEnabled = false

    sealed class ConnectionStatus {
        object Disconnected : ConnectionStatus()
        object Connecting : ConnectionStatus()
        object Connected : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
        object PairingRequired : ConnectionStatus()
    }

    init {
        // Start command processor
        startCommandProcessor()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            cleanup()
        }
    }

    private suspend fun cleanup() = withContext(Dispatchers.IO) {
        try {
            outputReaderJob?.cancelAndJoin()
            commandChannel.close()
            adbShellStream?.close()
            adbShellStream = null

            AdbConnectionManager.getInstance(getApplication()).close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Auto-connect to ADB using mDNS discovery
     */
    fun autoConnect() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting

            try {
                val connected = withContext(Dispatchers.IO) {
                    autoConnectInternal()
                }

                _connectAdb.postValue(connected)
                _isConnected.value = connected

                if (connected) {
                    _connectionStatus.value = ConnectionStatus.Connected
                } else {
                    _connectionStatus.value = ConnectionStatus.Error("Failed to connect")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-connect", e)
                _connectAdb.postValue(false)
                _isConnected.value = false
                _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun autoConnectInternal(): Boolean {
        val manager = AdbConnectionManager.getInstance(getApplication())

        return try {
            // Try auto-connect first
            val autoConnected = manager.autoConnect(getApplication(), AUTO_CONNECT_TIMEOUT_MS)
            if (autoConnected) {
                Log.d(TAG, "Auto-connect successful")
                return true
            }

            // Fall back to default port
            Log.d(TAG, "Auto-connect failed, trying default port $DEFAULT_ADB_PORT")
            manager.connect(DEFAULT_ADB_PORT)
        } catch (e: AdbPairingRequiredException) {
            Log.w(TAG, "Pairing required", e)
            withContext(Dispatchers.Main) {
                _askPairAdb.value = true
                _connectionStatus.value = ConnectionStatus.PairingRequired
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            false
        }
    }

    /**
     * Connect to ADB on a specific port
     */
    fun connect(port: Int) {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting

            val connected = withContext(Dispatchers.IO) {
                try {
                    val manager = AdbConnectionManager.getInstance(getApplication())
                    manager.connect(port)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect on port $port", e)
                    false
                }
            }

            _connectAdb.postValue(connected)
            _isConnected.value = connected

            _connectionStatus.value = if (connected) {
                ConnectionStatus.Connected
            } else {
                ConnectionStatus.Error("Failed to connect on port $port")
            }
        }
    }

    /**
     * Disconnect from ADB
     */
    fun disconnect() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    adbShellStream?.close()
                    adbShellStream = null

                    val manager = AdbConnectionManager.getInstance(getApplication())
                    manager.disconnect()

                    withContext(Dispatchers.Main) {
                        _connectAdb.value = false
                        _isConnected.value = false
                        _connectionStatus.value = ConnectionStatus.Disconnected
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during disconnect", e)
                }
            }
        }
    }

    /**
     * Execute a shell command
     */
    fun execute(command: String) {
        if (command.isBlank()) {
            Log.w(TAG, "Attempted to execute blank command")
            return
        }

        viewModelScope.launch {
            try {
                commandChannel.send(command)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command: $command", e)
            }
        }
    }

    /**
     * Command processor that handles commands sequentially
     */
    private fun startCommandProcessor() {
        viewModelScope.launch(Dispatchers.IO) {
            for (command in commandChannel) {
                try {
                    executeCommandInternal(command)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing command: $command", e)
                }
            }
        }
    }

    private fun executeCommandInternal(command: String) {
        try {
            // Ensure shell stream is open
            if (adbShellStream == null || adbShellStream?.isClosed == true) {
                val manager = AdbConnectionManager.getInstance(getApplication())
                adbShellStream = manager.openStream(LocalServices.SHELL)
                startOutputReader()
            }

            // Handle clear command
            if (command == "clear") {
                clearEnabled = true
            }

            // Send command
            adbShellStream?.openOutputStream()?.use { os ->
                os.write("$command\n".toByteArray(StandardCharsets.UTF_8))
                os.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command internally", e)
            throw e
        }
    }

    /**
     * Start reading output from the shell stream
     */
    private fun startOutputReader() {
        outputReaderJob?.cancel()

        outputReaderJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = adbShellStream ?: return@launch

                BufferedReader(InputStreamReader(stream.openInputStream())).use { reader ->
                    val sb = StringBuilder()
                    var line: String? = ""

                    while (isActive && reader.readLine().also { line = it } != null) {
                        if (clearEnabled) {
                            sb.clear()
                            clearEnabled = false
                        }

                        sb.append(line).append("\n")
                        _commandOutput.postValue(sb.toString())
                    }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Error reading output", e)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Unexpected error reading output", e)
                }
            }
        }
    }

    /**
     * Enable TCP/IP mode on specified port
     */
    fun tcpip(port: Int) {
        if (port !in 1024..65535) {
            Log.e(TAG, "Invalid port number: $port")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Enabling TCP/IP mode on port $port")
                val manager = AdbConnectionManager.getInstance(getApplication())
                manager.adbConnection?.open("tcpip:$port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable TCP/IP mode", e)
            }
        }
    }

    /**
     * Disable TCP/IP mode and switch to USB
     */
    fun usb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Switching to USB mode")
                val manager = AdbConnectionManager.getInstance(getApplication())
                manager.adbConnection?.open("usb:")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch to USB mode", e)
            }
        }
    }

    /**
     * Pair with ADB (requires Android 11+)
     */
    fun pair(port: Int, pairingCode: String) {
        viewModelScope.launch {
            val pairingStatus = withContext(Dispatchers.IO) {
                try {
                    AdbRepository.pair(port, pairingCode)
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing failed", e)
                    false
                }
            }

            _pairAdb.postValue(pairingStatus)

            if (pairingStatus) {
                // Auto-connect after successful pairing
                delay(500) // Brief delay to let pairing complete
                autoConnect()
            }
        }
    }

    /**
     * Get the pairing port via mDNS discovery
     */
    fun getPairingPort() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // This would need to be implemented using AdbMdns
                // Left as placeholder for now
                Log.d(TAG, "getPairingPort called - needs implementation")
                // val port = discoverPairingPort()
                // _pairingPort.postValue(port)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get pairing port", e)
            }
        }
    }

    // Legacy method names for backward compatibility
    fun watchConnectAdb(): LiveData<Boolean> = connectAdb
    fun watchPairAdb(): LiveData<Boolean> = pairAdb
    fun watchAskPairAdb(): LiveData<Boolean> = askPairAdb
    fun watchCommandOutput(): LiveData<CharSequence> = commandOutput
    fun watchPairingPort(): LiveData<Int> = pairingPort
}