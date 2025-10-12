package au.com.inoahguy.autoadb

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.Settings
import android.text.Html
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Thread.sleep
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    @Suppress("PropertyName")
    val TAG = "<MainActivity>"

    private var viewModel: AdbViewModel? = null

    private var uiTxtCredits: TextView? = null

    internal var uiTxtStatus: TextView? = null

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var uiSwiUsbDebugEnabled: Switch? = null
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private var uiSwiWirelessDebugEnabled: Switch? = null
    private var txtWriteSecure: TextView? = null
    private var txtDisplayOver: TextView? = null
    private var txtAccessibilityService: TextView? = null
    private var txtAutoRevoke: TextView? = null

    private var uiBtnEnableLegacyADB: Button? = null
    private var uiBtnDevOptions: Button? = null
    private var uiChkAutoRunEnabled: CheckBox? = null
    private var uiChkAutoStartEnabled: CheckBox? = null
    private var uiChkAutoDisableWireless: CheckBox? = null

    private var uiChkAutoRefresh: CheckBox? = null
    private var uiChkAutoClose: CheckBox? = null

    private var uiTxtInfo: TextView? = null

    private var mFail = ""
    private var mAppHasFocus: Boolean = true
    private var mWaitForPopup: Boolean = false
    private var isAccessibilityServiceEnabled = false 

    private fun enableStrictMode() {
        Log.w("enableStrictMode()", "** BEGIN **")
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // For network calls on the main thread
                .detectAll() // For all detectable problems
                .penaltyLog() // Log detected violations to LogCat
                // .penaltyDialog() // Show a dialog an ANR is detected on the main thread.
                // .penaltyDeath() // Crash the app on violation (good for strict testing)
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects() // Detect unclosed closable objects
                .penaltyLog()
                // .penaltyDeath()
                .build()
        )
        Log.w("enableStrictMode()", "** END **")
    }

    @SuppressLint("UseKtx")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) { // Only enable in debug builds
            enableStrictMode()
        }

        Log.d("onCreate()", "** BEGIN **")

        setContentView(R.layout.activity_main)
        viewModel = ViewModelProvider(this)[AdbViewModel::class.java]

        val mSharedPreferences = getSharedPreferences("${applicationContext.packageName}" , MODE_PRIVATE)

        supportActionBar?.hide()

        txtWriteSecure = findViewById(R.id.txtWriteSecure)
        txtDisplayOver = findViewById(R.id.txtDisplayOver)
        txtAccessibilityService = findViewById(R.id.txtAccessibilityService)
        txtAutoRevoke = findViewById(R.id.txtAutoRevoke)

        uiTxtStatus = findViewById(R.id.txtStatus)

        uiSwiUsbDebugEnabled = findViewById(R.id.swiUsbDebugEnabled)
        uiSwiUsbDebugEnabled?.isChecked = (Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1)
        uiSwiWirelessDebugEnabled = findViewById(R.id.swiWirelessDebugEnabled)
        uiSwiWirelessDebugEnabled?.isChecked = (Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0) == 1)

        uiTxtInfo = findViewById(R.id.txtInfo)

        uiChkAutoRunEnabled = findViewById(R.id.chkAutoRunEnabled)
        uiChkAutoRunEnabled?.isChecked = mSharedPreferences.getBoolean("ChkAutoRunEnabled" , false)
        uiChkAutoStartEnabled = findViewById(R.id.chkAutoStartEnabled)
        uiChkAutoStartEnabled?.isChecked = mSharedPreferences.getBoolean("ChkAutoStartEnabled" , false)
        uiChkAutoDisableWireless = findViewById(R.id.chkAutoDisableWireless)
        uiChkAutoDisableWireless?.isChecked = mSharedPreferences.getBoolean("chkAutoDisableWireless" , false)

        uiChkAutoRefresh = findViewById(R.id.chkAutoRefresh)
        uiChkAutoRefresh?.isChecked = mSharedPreferences.getBoolean("chkAutoRefresh" , false)
        uiChkAutoClose = findViewById(R.id.chkAutoClose)
        uiChkAutoClose?.isChecked = mSharedPreferences.getBoolean("chkAutoClose" , false)

        uiBtnDevOptions = findViewById(R.id.btnDevOptions)
        uiBtnDevOptions?.text = Html.fromHtml("<b>" + "DEVELOPER OPTIONS" + "</b><br/><small><small>" + "hold for app settings" + "</small></small>", Html.FROM_HTML_MODE_LEGACY)
        uiBtnEnableLegacyADB = findViewById(R.id.btnEnableLegacyADB)
        uiBtnEnableLegacyADB?.text = Html.fromHtml("<b>" + "ENABLE LEGACY ADB" + "</b><br/><small><small>" + "hold to refresh info" + "</small></small>", Html.FROM_HTML_MODE_LEGACY)

        uiTxtCredits = findViewById(R.id.txtCredits)
        uiTxtCredits?.movementMethod = LinkMovementMethod.getInstance()
        uiTxtCredits?.isEnabled = false
        uiTxtCredits?.text = getString(R.string.credits, BuildConfig.VERSION_NAME)

        enableBtns(false)

        if (uiChkAutoRunEnabled!!.isChecked) { // Enable ADB if autorun turned on so [ENABLE LEGACY ADB] will work
            showToast("AUTO Enable ADB")
            enableUsbDebugging(1)
            enableWirelessDebugging(1)
            sleep(100) // 500 works
        }

        if (mFail == "") { // No Error
            Log.d("onCreate()", "Set contentResolvers")
            val contentResolver: ContentResolver = contentResolver
            val observerAdbUSB = GlobalSettingsObserver(Handler(Looper.getMainLooper()), this)
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                true,
                observerAdbUSB
            )
            val observerAdbWifi = GlobalSettingsObserver(Handler(Looper.getMainLooper()), this)
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor("adb_wifi_enabled"),
                true,
                observerAdbWifi
            )

            Log.d("onCreate()", "Add Listeners")
            uiSwiUsbDebugEnabled!!.setOnClickListener {
                if (uiSwiUsbDebugEnabled!!.isChecked) {
                    enableUsbDebugging(1)
                } else {
                    enableUsbDebugging(0)
                }
            }

            uiSwiWirelessDebugEnabled!!.setOnClickListener {
                if (uiSwiWirelessDebugEnabled!!.isChecked) {
                    enableWirelessDebugging(1)
                } else {
                    enableWirelessDebugging(0)
                }
            }

            uiBtnEnableLegacyADB!!.setOnClickListener {
                enableLegacyADB()
            }
            uiBtnEnableLegacyADB!!.setOnLongClickListener {
                Log.d("uiBtnConnectLocalADB()","OnLongClick")
                uiRefreshInfo()
                return@setOnLongClickListener true
            }

            uiBtnDevOptions!!.setOnClickListener {
                Log.d("uiBtnDevOptions(OnClickListener)", "Open Developer Settings Screen")
                // Open Debug Menu Screen
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                try {
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    showToast("Could not open developer settings", Toast.LENGTH_LONG)
                }
            }
            uiBtnDevOptions!!.setOnLongClickListener {
                Log.d("uiBtnDevOptions()","OnLongClick")
                // Open App Settings Screen
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    showToast("Could not open app settings", Toast.LENGTH_LONG)
                }
                return@setOnLongClickListener true
            }

            uiChkAutoStartEnabled!!.setOnClickListener {
                Log.d("onCreate()", "uiChkAutoStartEnabled = ${uiChkAutoStartEnabled!!.isChecked}")
                mSharedPreferences.edit()
                    .putBoolean("ChkAutoStartEnabled", uiChkAutoStartEnabled!!.isChecked).apply()
            }
            uiChkAutoRunEnabled!!.setOnClickListener {
                Log.d("onCreate()", "uiChkAutoRunEnabled = ${uiChkAutoRunEnabled!!.isChecked}")
                mSharedPreferences.edit()
                    .putBoolean("ChkAutoRunEnabled", uiChkAutoRunEnabled!!.isChecked).apply()
            }
            uiChkAutoDisableWireless!!.setOnClickListener {
                Log.d("onCreate()", "uiChkAutoDisableWireless = ${uiChkAutoDisableWireless!!.isChecked}")
                mSharedPreferences.edit()
                    .putBoolean("chkAutoDisableWireless", uiChkAutoDisableWireless!!.isChecked).apply()
            }
            uiChkAutoRefresh!!.setOnClickListener {
                Log.d("onCreate()", "uiChkAutoRefresh = ${uiChkAutoRefresh!!.isChecked}")
                mSharedPreferences.edit()
                    .putBoolean("chkAutoRefresh", uiChkAutoRefresh!!.isChecked).apply()
            }
            uiChkAutoClose!!.setOnClickListener {
                Log.d("onCreate()", "uiChkAutoClose = ${uiChkAutoClose!!.isChecked}")
                mSharedPreferences.edit()
                    .putBoolean("chkAutoClose", uiChkAutoClose!!.isChecked).apply()
            }
        }

        init()

        Log.d("onCreate()", "** END **")
    }

    private fun init() {
        // Observers
        viewModel?.watchConnectAdb()?.observe(this) { isConnected ->
            if (isConnected) {
                Log.d("init()","Connected to ADB")
                showToast("Connected to ADB", Toast.LENGTH_SHORT)
                uiSetStatusText("Connected to ADB")

                Log.d("init()", "WRITE_SECURE_SETTINGS")
                viewModel?.execute("pm grant au.com.inoahguy.autoadb android.permission.WRITE_SECURE_SETTINGS")
                // Default: "pm revoke au.com.inoahguy.autoadb android.permission.WRITE_SECURE_SETTINGS"

                sleep(50L)
                Log.d("init()", "SYSTEM_ALERT_WINDOW")
                viewModel?.execute("pm grant au.com.inoahguy.autoadb android.permission.SYSTEM_ALERT_WINDOW")
                // Default: "pm revoke au.com.inoahguy.autoadb android.permission.SYSTEM_ALERT_WINDOW"

                sleep(50L)
                Log.d("init()", "AUTO_REVOKE_PERMISSIONS_IF_UNUSED")
                viewModel?.execute("cmd appops set au.com.inoahguy.autoadb AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore")
                // Default: "cmd appops set au.com.inoahguy.autoadb AUTO_REVOKE_PERMISSIONS_IF_UNUSED allow"

                sleep(50L)
                Log.d("init()", "Disabling ADB Auto Revoke")
                viewModel?.execute("settings put global adb_allowed_connection_time 0")
                // Default: "settings put global adb_allowed_connection_time 604800000" // 7 Days

                sleep(50L)
                Log.d("init()", "ENABLE_LEGACY")
                viewModel?.tcpip(5555)
                // Default: viewModel?.usb()

                sleep(500L)
                Log.d("init()", "DISCONNECT")
                //viewModel?.disconnect()

                sleep(500L)
                uiRefreshInfo()
            } else {
                Log.d("init()","Disconnected from ADB")
                //showToast("ADB Disconnected", Toast.LENGTH_SHORT)
            }
        }

        viewModel?.watchPairAdb()?.observe(this) { isPaired ->
            if (isPaired) {
                showToast("Pairing successful", Toast.LENGTH_SHORT)
            } else {
                showToast("Pairing failed", Toast.LENGTH_SHORT)
            }
        }

        viewModel?.watchAskPairAdb()?.observe(this) { displayDialog ->
            if (displayDialog) {
                //pairAdb()
                initiateAdbPairingWithAccessibilityService()
            }
        }

        viewModel?.watchCommandOutput()?.observe(this) { output ->
            Log.v("init()", "Command Output: $output".replace("\n", " ").replace("\r"," ").replace("  ", " ").trim() )
        }

        viewModel?.watchPairingPort()?.observe(this) { output ->
            Log.d("init()", "Pairing Port: $output")
        }
    }

    private fun initiateAdbPairingWithAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            showToast("Please enable the \"Auto ADB Pairing Code Reader\" Accessibility Service.", Toast.LENGTH_LONG)
            // Guide user to enable it
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Could not open Accessibility Settings.", Toast.LENGTH_SHORT)
            }
            return
        }

        // The service is enabled.
        // At this point, the user needs to manually navigate to the pairing screen.
        // The accessibility service will be watching for the pairing code.
        uiBtnDevOptions?.performClick() // This opens Developer Options

        showToast("Navigate to 'Developer options -> Wireless debugging -> Pair device with pairing code'.", Toast.LENGTH_LONG)
    }

    // Function to check if Accessibility Service is enabled
    private fun isAccessibilityServiceEnabled(): Boolean {
        var accessibilityEnabled: Int
        val serviceId = "$packageName/${AdbPairingAccessibilityService::class.java.canonicalName}"
        Log.d("MainActivity", "Checking for service: $serviceId")
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("MainActivity", "Error finding ACCESSIBILITY_ENABLED: " + e.message)

            return false // Assume not enabled if setting not found
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                colonSplitter.setString(settingValue)
                while (colonSplitter.hasNext()) {
                    val accessibilityService = colonSplitter.next()
                    if (accessibilityService.equals(serviceId, ignoreCase = true)) {
                        Log.i("MainActivity", "Accessibility service $serviceId is enabled.")
                        // We have returned to the app here enabled go to next step.
                        //showToast("We have returned to app")
                        return true
                    }
                }
            }
        }
        Log.d("MainActivity", "Accessibility service $serviceId is NOT enabled.")
        return false
    }

    override fun onResume() {
        super.onResume()

        if (!mAppHasFocus) {
            mAppHasFocus = true
            Log.d("FocusChange", "App regained focus")

            if (mWaitForPopup) {
                //mWaitForPopup = false
                Log.d("FocusChange", "was waiting for popup")
                //enableBtns(true)
                //return
            }
        }
        mWaitForPopup = false // Clear flag

        Log.d("onResume()", "** BEGIN **")

        enableBtns(false)

        uiSwiUsbDebugEnabled?.isChecked = (Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1)
        uiSwiWirelessDebugEnabled?.isChecked = (Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0) == 1)

        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        if (isAccessibilityServiceEnabled) {
            Log.d("MainActivity", "Accessibility Service is enabled.")
        } else {
            Log.d("MainActivity", "Accessibility Service is NOT enabled.")
        }

        if (mFail == "PERMISSION") {
            uiSetStatusText("Required Permissions missing!")
            if (uiChkAutoRefresh!!.isChecked) {
                uiRefreshInfo()
            }
            else {
                enableBtns(true)
            }
        }
        else {
            // Auto Run logic?
            if (uiChkAutoRunEnabled!!.isChecked) {
                showToast("[ENABLE LEGACY ADB]")
                Handler(Looper.getMainLooper()).postDelayed({
                    // [ENABLE LEGACY ADB] when opening the app
                    enableLegacyADB()
                }, 500)
            } else {
                if (uiChkAutoRefresh!!.isChecked) {
                    uiRefreshInfo()
                }
                else {
                    enableBtns(true)
                }
            }
        }

        updatePermissionColors()

        Log.d("onResume()", "** END **")
    }

    private fun updatePermissionColors() {
        val enabled = ContextCompat.getColor(this, R.color.green_600)
        val disabled = ContextCompat.getColor(this, R.color.red_600)
        val disabledNotRequired = ContextCompat.getColor(this, R.color.yellow_600)

        txtWriteSecure?.setTextColor(
            if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) enabled else disabled
        )
        txtDisplayOver?.setTextColor(
            if (checkSelfPermission(Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED) enabled else disabled
        )
        txtAccessibilityService?.setTextColor(
            if (isAccessibilityServiceEnabled) enabled else disabledNotRequired
        )
        txtAutoRevoke?.setTextColor(
            if (packageManager.isAutoRevokeWhitelisted) enabled else disabled
        )
        uiSwiUsbDebugEnabled?.setTextColor(
            if (uiSwiUsbDebugEnabled!!.isChecked) {
                enabled
            }
            else {
                if (uiSwiWirelessDebugEnabled!!.isChecked) disabledNotRequired else disabled
            }
        )
        uiSwiWirelessDebugEnabled?.setTextColor(
            if (uiSwiWirelessDebugEnabled!!.isChecked) {
                enabled
            }
            else {
                if (uiSwiUsbDebugEnabled!!.isChecked) disabledNotRequired else disabled
            }
        )
    }

    override fun onPause() {
        super.onPause()
        mAppHasFocus = false
        Log.d("FocusChange", "App lost focus")
        if (mWaitForPopup) {
            showToast("Please check 'Always allow...' then press 'Allow'", Toast.LENGTH_LONG)
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundJob?.cancel()
    }

    private val enableUsbDebuggingLock: Lock = ReentrantLock()

    private fun enableUsbDebugging(state: Int) = enableUsbDebuggingLock.withLock {
        Log.d("enableUsbDebugging()", "state = $state")
        if (Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.ADB_ENABLED, 0) != state) {
            enableBtns(false)
            uiSetStatusText("Toggling USB debugging ${if (state == 1) "on" else "off"}...")
            try {
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    Settings.Global.ADB_ENABLED,
                    state
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                uiSetStatusText("Failed to toggle USB debugging, check permissions!")
            } finally {
                uiSetInfoText("waiting...")
                //enableBtns(true)
                updateToggles()
            }
        }
    }

    private val enableWirelessDebuggingLock: Lock = ReentrantLock()
    private fun enableWirelessDebugging(state: Int) = enableWirelessDebuggingLock.withLock {
        Log.d("enableWirelessDebugging()", "state = $state")
        if (Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0) != state) {
            enableBtns(false)
            uiSetStatusText("Toggling Wireless debugging ${if (state == 1) "on" else "off"}...")
            try {
                lastWirelessState = -1
                Settings.Global.putInt(
                    applicationContext.contentResolver,
                    "adb_wifi_enabled",
                    state
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                uiSetStatusText("Failed to toggle wireless debugging, check permissions!")
            } finally {
                uiSetInfoText("waiting...")
                //enableBtns(true)
                updateToggles()
            }
        }
    }

    private var backgroundJob: Job? = null
    private val enabeLegacyAdbLock: Lock = ReentrantLock()
    internal fun enableLegacyADB() = enabeLegacyAdbLock.withLock {
        Log.d("connectLocalADB()", "** BEGIN **")
        enableBtns(false)
        uiSetStatusText("Connecting to local legacy ADB...")
        uiSetInfoText("waiting...")

        try {
            // Try auto-connecting
            viewModel?.autoConnect()
        }
        catch (ex: Exception) {
            ex.printStackTrace()
            uiSetStatusText("Connecting failed!")
        }
        finally {
            enableBtns(true)
        }

        Log.d("connectLocalADB()", "** END **")
    }

    fun uiRefreshInfo(silent: Boolean = false) {
        enableBtns(false)
        updatePermissionColors()
        lifecycleScope.launch {
            val result: RefreshInfoResult = withContext(Dispatchers.IO) {
                return@withContext refreshInfo(silent)
            }
            Log.d(
                "uiRefreshInfo()",
                "RefreshInfoCaller:\n - State: ${result.blOK}"
            )
            Log.d(
                "uiRefreshInfo()",
                " - Legacy ADB OK: ${result.blLegacyOK}: ${result.strLegacyIPAddress}:${result.intLegacyPort}"
            )
            Log.d(
                "uiRefreshInfo()",
                " - Wireless ADB OK: ${result.blWirelessOK}: ${result.strWirelessIPAddress}:${result.intWirelessPort}\""
            )
            enableBtns(true)
        }
    }
    data class RefreshInfoResult(val blOK: Boolean? = false, val blLegacyOK: Boolean? = false, val strLegacyIPAddress: String? = "", val intLegacyPort: Int? = -1, val blWirelessOK: Boolean? = false, val strWirelessIPAddress: String? = "", val intWirelessPort: Int? = -1)
    private val refreshInfoLock: Lock = ReentrantLock()
    private fun refreshInfo(silent: Boolean = false, timeoutMillis: Long = 3000L): RefreshInfoResult {
        Log.d("refreshInfo()", "** BEGIN ** silent= $silent")

        refreshInfoLock.lock() // Acquire lock

        try {
            if (!silent) {
                uiSetInfoText("waiting...")
            }

            // Check ADB
            if (Settings.Global.getInt(applicationContext.contentResolver,"adb_wifi_enabled",0) == 0 && Settings.Global.getInt(applicationContext.contentResolver,Settings.Global.ADB_ENABLED,0) == 0) {
                Log.d("refreshInfo()", "debugging not enabled!")
                if (!silent) {
                    uiSetStatusText("You must enable debugging first!")
                    uiSetInfoText("ADB not running")
                }
                return RefreshInfoResult(blOK = false)
            }

            // Get Details from adbMdns
            val atomicLegacyHostAddress = AtomicReference<String>(null)
            val atomicLegacyPort = AtomicInteger(-1)
            val resolveLegacyHostAndPort = CountDownLatch(1)
            val atomicWirelessHostAddress = AtomicReference<String>(null)
            val atomicWirelessPort = AtomicInteger(-1)
            val resolveWirelessHostAndPort = CountDownLatch(1)

            val adbMdnsTcp = AdbMdns(applicationContext,AdbMdns.SERVICE_TYPE_ADB) {
                legacyHostAddress: InetAddress?, legacyPort: Int ->
                if (legacyHostAddress != null) {
                    atomicLegacyHostAddress.set(legacyHostAddress.hostAddress)
                    atomicLegacyPort.set(legacyPort)
                }
                resolveLegacyHostAndPort.countDown()
            }
            adbMdnsTcp.start()

            val adbMdnsTls = AdbMdns(applicationContext,AdbMdns.SERVICE_TYPE_TLS_CONNECT) {
                wirelessHostAddress: InetAddress?, wirelessPort: Int ->
                if (wirelessHostAddress != null) {
                    atomicWirelessHostAddress.set(wirelessHostAddress.hostAddress)
                    atomicWirelessPort.set(wirelessPort)
                }
                resolveWirelessHostAndPort.countDown()
            }
            adbMdnsTls.start()

            try {
                if (!resolveLegacyHostAndPort.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    Log.w("adbMdnsDiscover()", "Timed out while trying to find a valid legacy host address and port")
                    //return RefreshInfoResult(blOK = false)
                }
                if (!resolveWirelessHostAndPort.await(timeoutMillis, TimeUnit.MILLISECONDS) ) {
                    Log.w("adbMdnsDiscover()", "Timed out while trying to find a valid wireless host address and port")
                    //return RefreshInfoResult(blOK = false)
                }
            }
            finally {
                adbMdnsTcp.stop()
                adbMdnsTls.stop()
            }

            val legacyHost = atomicLegacyHostAddress.get()
            val legacyPort = atomicLegacyPort.get()
            var legacyOK = true
            if (legacyHost == null || legacyPort == -1) {
                legacyOK = false
                Log.w("adbMdnsDiscover()", "Could not find any valid legacy host address or port")
            }
            val wirelessHost = atomicWirelessHostAddress.get()
            val wirelessPort = atomicWirelessPort.get()
            var wirelessOK = true
            if (wirelessHost == null || wirelessPort == -1) {
                wirelessOK = false
                Log.e("adbMdnsDiscover()", "Could not find any valid wireless host address or port")
            }

            var blOK = true
            if (!legacyOK && !wirelessOK) {
                blOK = false
                Log.e("adbMdnsDiscover()", "Could not find any valid host address or port")
            }

            if (!silent) {
                if (wirelessOK && legacyOK) {
                    uiSetInfoText("Local wireless ADB running @ ${wirelessHost}:${wirelessPort}   |   Local legacy ADB running @ ${legacyHost}:${legacyPort}")
                } else if (wirelessOK) {
                    uiSetInfoText("Local wireless ADB running @ ${wirelessHost}:${wirelessPort}")
                } else if (legacyOK) {
                    uiSetInfoText("Local legacy ADB running @ ${legacyHost}:${legacyPort}")
                } else {
                    uiSetInfoText("ADB not running")
                }
            }

            if (legacyOK && uiChkAutoDisableWireless!!.isChecked) {
                Log.e("refreshInfo()", "Disabling wireless debugging")
                enableUsbDebugging(1)
                enableWirelessDebugging(0)
            }

            if (legacyOK && uiChkAutoClose!!.isChecked) {
                Log.e("refreshInfo()", "Returning to Launcher")
                finishAffinity()
            }


            return RefreshInfoResult(blOK = blOK, blLegacyOK = legacyOK, strLegacyIPAddress = legacyHost, intLegacyPort = legacyPort, blWirelessOK = wirelessOK, strWirelessIPAddress = wirelessHost, intWirelessPort = wirelessPort)
        }
        finally {
            refreshInfoLock.unlock() // Release lock
            Log.d("refreshInfo()", "** END **")
        }
    }

    private var lastFocusedView: View? = null
    private fun enableBtns(state: Boolean) {
        Log.d("enableBtns()", "** BEGIN ** state= $state")
        runOnUiThread {
            //if (lastFocusedView != null) {
                if (!state) {
                    // Store the currently focused view before disabling elements
                    lastFocusedView = currentFocus
                }
            //}

            uiSwiUsbDebugEnabled?.isEnabled = state
            uiSwiWirelessDebugEnabled?.isEnabled = state

            uiBtnEnableLegacyADB?.isEnabled = state
            uiBtnDevOptions?.isEnabled = state

            uiChkAutoRunEnabled?.isEnabled = state
            uiChkAutoStartEnabled?.isEnabled = state
            uiChkAutoDisableWireless?.isEnabled = state

            uiChkAutoRefresh?.isEnabled = state
            uiChkAutoClose?.isEnabled = state

            //uiTxtCredits?.isEnabled = state

            if (state) {
                /*
                if (lastFocusedView == null) {
                    // Default focus
                    uiBtnEnableLegacyADB?.requestFocus()
                }
                else {
                */
                // If we are enabling buttons, try to restore focus to the last focused view
                lastFocusedView?.requestFocus()
                //}
            }
        }
        Log.d("enableBtns()", "** END **")
    }

    private var lastUsbState: Int? = -1
    private var lastWirelessState: Int? = -1
    internal fun updateToggles() {
        Log.d("updateToggles()", "** BEGIN ** USB debugging: ${Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.ADB_ENABLED, 0)} - Wireless debugging: ${Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0)}")
        runOnUiThread {
            var state = Settings.Global.getInt(applicationContext.contentResolver, Settings.Global.ADB_ENABLED, 0)
            if (lastUsbState != state) {
                uiSwiUsbDebugEnabled?.isChecked = (state == 1)
                lastUsbState = state
                if (state == 1) {
                    uiSetStatusText("USB debugging Enabled")
                } else {
                    uiSetStatusText("USB debugging Disabled")
                }
            }

            state = Settings.Global.getInt(applicationContext.contentResolver, "adb_wifi_enabled", 0)
            if (lastWirelessState != state) {
                uiSwiWirelessDebugEnabled?.isChecked = (state == 1)
                lastWirelessState = state
                if (state == 1) {
                    uiSetStatusText("Wireless debugging Enabled")
                } else {
                    uiSetStatusText("Wireless debugging Disabled")
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                uiRefreshInfo()
            }, 100)
        }
        Log.d("updateToggles()", "** END **")
    }
    fun isOnMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
    private fun uiSetStatusText(text: String = "") {
        if (isOnMainThread()) {
            uiTxtStatus?.text = text
        }
        else {
            runOnUiThread {
                uiTxtStatus?.text = text
            }
        }
    }
    private fun uiSetInfoText(text: String = "") {
        if (isOnMainThread()) {
            uiTxtInfo?.text = text
        }
        else {
            runOnUiThread {
                uiTxtInfo?.text = text
            }
        }
    }
}
