package com.smaparamartha.exambro

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.provider.Settings
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.app.ProgressDialog
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.webkit.PermissionRequest
import android.webkit.JsResult
import java.security.MessageDigest
import android.content.pm.Signature
import java.io.OutputStreamWriter
import android.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tvBattery: TextView
    private lateinit var tvNetworkSpeed: TextView
    private lateinit var ivBattery: ImageView
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var ivSignal: ImageView
    private lateinit var btnRotate: ImageButton
    private lateinit var btnExit: ImageButton

    // Network Speed Monitor
    private var lastTotalRxBytes: Long = 0
    private var lastTotalTxBytes: Long = 0
    private val speedHandler = Handler(Looper.getMainLooper())
    private val speedRunnable = object : Runnable {
        override fun run() {
            updateNetworkSpeedAndType()
            speedHandler.postDelayed(this, 1000)
        }
    }

    // Rotation Lock State
    private var isRotationLocked = false

    // Battery Warning State
    private var isBatteryWarningShown = false

    // Overlay Elements
    private lateinit var tokenOverlay: RelativeLayout
    private lateinit var splashScreen: RelativeLayout
    private lateinit var cardToken: LinearLayout
    private lateinit var tvOverlayTitle: TextView
    private lateinit var etOverlayToken: EditText
    private lateinit var btnOverlaySubmit: Button
    private lateinit var btnOverlayCancel: Button

    // Anti-Cheat Elements
    private lateinit var antiCheatOverlay: RelativeLayout
    private lateinit var tvAntiCheatReason: TextView
    private lateinit var btnExitCheat: Button

    private val REQUEST_MEDIA_PROJECTION = 1001

    private var targetUrl = "https://paramartaapp.vercel.app/"
    
    private fun getTargetUrlWithCacheBuster(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        return targetUrl + "?v=" + System.currentTimeMillis() + "&device_id=" + androidId
    }
    private var tokenSecretSeed = "PARAMARTHA_SECRET"
    private var tokenIntervalMinutes = 3

    private var isUpdateDialogShowing = false
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isUpdateDialogShowing) {
                fetchDynamicConfig(isPeriodic = true)
            }
            updateHandler.postDelayed(this, 60000) // Cek setiap 60 detik
        }
    }

    private fun isValidToken(inputToken: String, isExit: Boolean): Boolean {
        val type = if (isExit) "KELUAR" else "MASUK"
        val timeNow = System.currentTimeMillis()
        val intervalMillis = tokenIntervalMinutes * 60 * 1000L
        val currentWindowIndex = timeNow / intervalMillis
        
        // Cek current, previous, dan next window untuk mentoleransi jam HP yang tidak sinkron
        for (offset in -1..1) {
            val windowIndex = currentWindowIndex + offset
            val seedString = "${tokenSecretSeed}_${windowIndex}_${type}"
            
            var hash: Int = 0
            for (i in seedString.indices) {
                hash = (hash * 31) + seedString[i].code
            }
            
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val result = StringBuilder()
            var randomSeed: Long = hash.toLong() and 0xFFFFFFFFL
            
            for (i in 0 until 5) {
                randomSeed = (randomSeed * 1103515245L + 12345L) and 0xFFFFFFFFL
                result.append(chars[(randomSeed % chars.length).toInt()])
            }
            
            if (inputToken == result.toString()) {
                return true
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        verifyAppSignature()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1002)
        }
        
        hideSystemUI()
        setContentView(R.layout.activity_main)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        tvBattery = findViewById(R.id.tvBattery)
        tvNetworkSpeed = findViewById(R.id.tvNetworkSpeed)
        ivBattery = findViewById(R.id.ivBattery)
        ivSignal = findViewById(R.id.ivSignal)
        btnRotate = findViewById(R.id.btnRotate)
        btnExit = findViewById(R.id.btnExit)

        tokenOverlay = findViewById(R.id.tokenOverlay)
        splashScreen = findViewById(R.id.splashScreen)
        cardToken = findViewById(R.id.cardToken)
        tvOverlayTitle = findViewById(R.id.tvOverlayTitle)
        etOverlayToken = findViewById(R.id.etOverlayToken)
        btnOverlaySubmit = findViewById(R.id.btnOverlaySubmit)
        btnOverlayCancel = findViewById(R.id.btnOverlayCancel)

        // Anti-Cheat Bindings
        antiCheatOverlay = findViewById(R.id.antiCheatOverlay)
        tvAntiCheatReason = findViewById(R.id.tvAntiCheatReason)
        btnExitCheat = findViewById(R.id.btnExitCheat)
        
        btnExitCheat.setOnClickListener {
            finishAffinity() // Force exit
        }

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        setupWebView()
        
        // Clear previous session data (Auto-Clear Cache & Cookies)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)

        checkAntiCheat()
        fetchDynamicConfig()
        
        btnRotate.setOnClickListener {
            toggleRotationLock()
        }

        btnExit.setOnClickListener {
            showTokenOverlay(isExit = true)
        }

        // Delay splash screen (increased to 4 seconds)
        Handler(Looper.getMainLooper()).postDelayed({
            splashScreen.animate()
                .alpha(0f)
                .setDuration(500)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreen.visibility = View.GONE
                        showTokenOverlay(isExit = false)
                    }
                })
        }, 4000)
    }

    override fun onResume() {
        super.onResume()
        checkAntiCheat()
        updateHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun fetchDynamicConfig(isPeriodic: Boolean = false) {
        Thread {
            try {
                val url = URL("https://paramartaapp.vercel.app/exam.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    
                    var newTargetUrl = ""
                    if (json.has("targetUrl")) {
                        newTargetUrl = decrypt(json.getString("targetUrl"))
                    }

                    if (json.has("latest_version_code")) {
                        val latestVersion = json.getInt("latest_version_code")
                        val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                        if (latestVersion > currentVersionCode) {
                            val changelog = if (json.has("changelog")) json.getString("changelog") else "Pembaruan penting tersedia."
                            val apkUrl = if (json.has("apk_url")) decrypt(json.getString("apk_url")) else ""
                            
                            if (apkUrl.isNotEmpty()) {
                                val safeUrl = apkUrl.trim().replace(" ", "%20")
                                runOnUiThread { showForceUpdateDialog(changelog, safeUrl) }
                            }
                        }
                    }

                    if (json.has("secret_seed")) {
                        tokenSecretSeed = json.getString("secret_seed")
                    }
                    if (json.has("interval_minutes")) {
                        tokenIntervalMinutes = json.getInt("interval_minutes")
                    }

                    if (json.has("ui_config")) {
                        val uiConfig = json.getJSONObject("ui_config")
                        val btnColor = if(uiConfig.has("btn_color")) uiConfig.getString("btn_color") else null
                        val titleText = if(uiConfig.has("title")) uiConfig.getString("title") else null
                        
                        runOnUiThread {
                            try {
                                if (btnColor != null) {
                                    btnOverlaySubmit.setBackgroundColor(Color.parseColor(btnColor))
                                }
                                if (titleText != null) {
                                    tvOverlayTitle.text = titleText
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.userAgentString = webSettings.userAgentString + " ExambroParamartha"

        // Anti-Copas: Disable Text Selection & Long Click
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Pemberitahuan")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .create()
                    .show()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.addJavascriptInterface(object : Any() {
            @android.webkit.JavascriptInterface
            fun stopScreenRecord() {
                val serviceIntent = Intent(this@MainActivity, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_STOP
                }
                startService(serviceIntent)
            }
        }, "AndroidExambro")
    }

    private fun toggleRotationLock() {
        isRotationLocked = !isRotationLocked
        if (isRotationLocked) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
            btnRotate.setColorFilter(Color.parseColor("#EF4444")) // Red icon
            Toast.makeText(this, "Rotasi Layar Dikunci", Toast.LENGTH_SHORT).show()
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            btnRotate.setColorFilter(Color.WHITE)
            Toast.makeText(this, "Rotasi Layar Otomatis", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTokenOverlay(isExit: Boolean) {
        // Fade-in animation
        tokenOverlay.alpha = 0f
        tokenOverlay.visibility = View.VISIBLE
        tokenOverlay.animate().alpha(1f).setDuration(300).setListener(null).start()
        
        etOverlayToken.text.clear()

        if (isExit) {
            tvOverlayTitle.text = "TOKEN KELUAR"
            btnOverlaySubmit.text = "KELUAR"
            btnOverlayCancel.visibility = View.VISIBLE
        } else {
            tvOverlayTitle.text = "TOKEN MASUK"
            btnOverlaySubmit.text = "MASUK"
            btnOverlayCancel.visibility = View.GONE
        }

        btnOverlaySubmit.setOnClickListener {
            val token = etOverlayToken.text.toString().trim().uppercase()
            if (isValidToken(token, isExit)) {
                if (isExit) {
                    // Fade-out animation on exit success
                    tokenOverlay.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            tokenOverlay.visibility = View.GONE
                            hideSystemUI()
                            exitApp()
                        }
                    }).start()
                } else {
                    // MASUK: Request Screen Capture FIRST before hiding overlay
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                    startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
                }
            } else {
                Toast.makeText(this, "Token salah, coba lagi!", Toast.LENGTH_SHORT).show()
            }
        }

        btnOverlayCancel.setOnClickListener {
            // Fade-out animation on cancel
            tokenOverlay.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    tokenOverlay.visibility = View.GONE
                    hideSystemUI()
                }
            }).start()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                // Screen Cast Approved!
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
                }
                
                // Start Foreground Service
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                // Setup callback to inject frame to JS
                ScreenCaptureService.frameCallback = { args ->
                    runOnUiThread {
                        webView.evaluateJavascript("if(window.updateAndroidFrame) { window.updateAndroidFrame($args); }", null)
                    }
                }

                // Hide overlay and enter exam mode
                tokenOverlay.animate().alpha(0f).setDuration(300).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        tokenOverlay.visibility = View.GONE
                        hideSystemUI()
                        startExamMode()
                    }
                }).start()

            } else {
                // Screen Cast Denied
                Toast.makeText(this, "AKSES DITOLAK: Anda wajib mengizinkan rekam layar untuk ujian!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startExamMode() {
        try {
            startLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webView.loadUrl(getTargetUrlWithCacheBuster())
        registerBatteryReceiver()
        registerSignalListener()
    }

    private fun exitApp() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Hentikan Screen Capture Service secara eksplisit agar rekaman tidak berjalan di background
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(stopIntent)

        finishAffinity()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun reportTamperingAndClose() {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val deviceModel = android.os.Build.MODEL ?: "Unknown Device"
        
        thread {
            try {
                val url = URL("https://sma-paramartha-default-rtdb.firebaseio.com/mod_detections/\$androidId.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                
                val jsonBody = JSONObject()
                jsonBody.put("deviceModel", deviceModel)
                jsonBody.put("timestamp", System.currentTimeMillis())
                jsonBody.put("status", "DETECTED")
                
                val out = OutputStreamWriter(conn.outputStream)
                out.write(jsonBody.toString())
                out.flush()
                out.close()
                conn.responseCode // execute
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Aplikasi Ilegal")
                .setMessage("Aplikasi ini telah dimodifikasi (di-mod) secara ilegal. Perangkat Anda (ID: \$androidId) telah dilaporkan ke sistem pusat SMA Paramartha dan tidak dapat mengikuti ujian.")
                .setCancelable(false)
                .setPositiveButton("TUTUP") { _, _ ->
                    finishAffinity()
                }
                .show()
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (splashScreen.visibility == View.VISIBLE || tokenOverlay.visibility == View.VISIBLE) {
                return true // block back button
            }
            if (webView.canGoBack()) {
                webView.goBack()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            
            if (level != -1 && scale != -1) {
                val batteryPct = level * 100 / scale
                tvBattery.text = "$batteryPct%"
                
                if (isCharging) {
                    ivBattery.setImageResource(R.drawable.ic_battery_charging)
                    ivBattery.setColorFilter(Color.parseColor("#10B981")) // Green for charging
                    tvBattery.setTextColor(Color.parseColor("#10B981"))
                } else {
                    ivBattery.setImageResource(R.drawable.ic_battery_modern)
                    // Real-time battery logic
                    if (batteryPct <= 20) {
                        ivBattery.setColorFilter(Color.parseColor("#EF4444")) // Red
                        tvBattery.setTextColor(Color.parseColor("#EF4444"))
                    } else if (batteryPct <= 50) {
                        ivBattery.setColorFilter(Color.parseColor("#FBBF24")) // Yellow
                        tvBattery.setTextColor(Color.parseColor("#FBBF24"))
                    } else {
                        ivBattery.setColorFilter(Color.WHITE)
                        tvBattery.setTextColor(Color.WHITE)
                    }
                    
                    // Low Battery Warning
                    if (batteryPct <= 15) {
                        if (!isBatteryWarningShown) {
                            isBatteryWarningShown = true
                            showLowBatteryWarning(batteryPct)
                        }
                    } else {
                        isBatteryWarningShown = false
                    }
                }
            }
        }
    }

    private fun showLowBatteryWarning(batteryPct: Int) {
        android.app.AlertDialog.Builder(this@MainActivity)
            .setTitle("⚠️ BATERAI LEMAH!")
            .setMessage("Sisa baterai Anda hanya $batteryPct%.\nHarap segera mencari charger atau lapor ke pengawas sebelum HP Anda mati!")
            .setCancelable(false)
            .setPositiveButton("Mengerti") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkDeveloperOptions() {
        val devOptions = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        val adbEnabled = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        if (devOptions || adbEnabled) {
            showDeveloperOptionsWarning()
        }
    }

    private fun showDeveloperOptionsWarning() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⛔ PERINGATAN KEAMANAN")
            .setMessage("Sistem mendeteksi Mode Pengembang (Developer Options) atau USB Debugging aktif di HP ini. Ujian tidak bisa dilanjutkan.\n\nHarap matikan fitur tersebut di Pengaturan HP Anda, lalu buka kembali aplikasi.")
            .setCancelable(false)
            .setPositiveButton("Keluar Aplikasi") { _, _ ->
                exitApp()
            }
            .show()
    }

    private fun showForceUpdateDialog(changelog: String, apkUrl: String) {
        if (isUpdateDialogShowing) return
        isUpdateDialogShowing = true
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Pembaruan Diperlukan")
            .setMessage("Ada versi baru yang wajib diinstal.\n\nApa yang baru:\n$changelog")
            .setCancelable(false)
            .setPositiveButton("Update Sekarang") { _, _ ->
                downloadAndInstallUpdate(apkUrl)
            }
            .show()
    }

    private fun checkAntiCheat() {
        Thread {
            // 1. Check Root
            if (isRooted()) {
                showAntiCheatWarning("Perangkat ini terdeteksi telah di-Root (Jailbreak). Aplikasi tidak dapat berjalan di perangkat Root demi keamanan.")
                return@Thread
            }

            // 2. Check Developer Options / USB Debugging
            if (Settings.Secure.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1) {
                showAntiCheatWarning("USB Debugging / Opsi Pengembang sedang aktif. Harap matikan fitur ini di Pengaturan HP Anda untuk melanjutkan ujian.")
                return@Thread
            }

            // 3. Check Dual App / Clone App
            val dataDir = applicationInfo.dataDir
            if (dataDir != null && (dataDir.contains("999") || dataDir.contains("dual") || dataDir.contains("clone") || dataDir.contains("parallel"))) {
                showAntiCheatWarning("Aplikasi terdeteksi dijalankan di dalam Aplikasi Ganda (Dual/Clone App). Hal ini tidak diizinkan.")
                return@Thread
            }

            // 4. Check Blacklisted Apps (Auto Clicker, Macro, Cheats)
            val blacklistedApps = listOf(
                "com.chelpus.lackypatch",
                "com.dimonvideo.luckypatcher",
                "com.forpda.lp",
                "catch_.me_.if_.you_.can_",
                "com.truemacro.auto",
                "com.geone.autoclicker",
                "com.phonephreak.screenrecorder",
                "com.topjohnwu.magisk"
            )
            val pm = packageManager
            for (pkg in blacklistedApps) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    showAntiCheatWarning("Terdeteksi Aplikasi Curang / Auto-Clicker di HP Anda. Harap HAPUS aplikasi tersebut sebelum ujian!")
                    return@Thread
                } catch (e: PackageManager.NameNotFoundException) {
                    // Not installed, safe
                }
            }
            // 5. Check App Signature (Anti-Mod / Anti-Tamper)
            if (!verifyAppSignature()) {
                reportTamperingAndClose()
                return@Thread
            }
        }.start()
    }

    private fun verifyAppSignature(): Boolean {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            // SHA-256 Hash of exambro.keystore
            val expectedHash = "84A9E23C6576E39257ED605698BE0D3F3A9D1228460483EDA6DBF0B12D909450"

            signatures?.forEach { signature ->
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val currentHash = md.digest().joinToString("") { "%02X".format(it) }
                if (currentHash == expectedHash) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    private fun showAntiCheatWarning(reason: String) {
        runOnUiThread {
            tvAntiCheatReason.text = reason
            antiCheatOverlay.visibility = View.VISIBLE
            webView.visibility = View.GONE
        }
    }

    private var downloadId: Long = -1
    private var progressDialog: ProgressDialog? = null

    private fun downloadAndInstallUpdate(apkUrl: String) {
        progressDialog = ProgressDialog(this).apply {
            setTitle("Mengunduh Pembaruan")
            setMessage("Mohon tunggu, jangan tutup aplikasi...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            max = 100
            show()
        }

        val fileName = "Exambro_Update.apk"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) file.delete() // Hapus APK lama jika ada

        val request = DownloadManager.Request(Uri.parse(apkUrl))
        request.setTitle("Mengunduh Exambro")
        request.setDescription("Sedang mengunduh pembaruan terbaru...")
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        Thread {
            var downloading = true
            while (downloading) {
                val q = DownloadManager.Query()
                q.setFilterById(downloadId)
                val cursor = downloadManager.query(q)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                            runOnUiThread { progressDialog?.progress = progress }
                        }
                    }
                    
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex >= 0) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                    cursor.close()
                }
                Thread.sleep(500)
            }
        }.start()

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    runOnUiThread { progressDialog?.dismiss() }
                    Toast.makeText(this@MainActivity, "Unduhan selesai. Memulai instalasi...", Toast.LENGTH_SHORT).show()
                    installApk(fileName)
                    unregisterReceiver(this)
                }
            }
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(fileName: String) {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file),
                "application/vnd.android.package-archive"
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Buka gembok LockTask secara sementara supaya layar instalasi bisa muncul (karena OS Android memaksa layar penuh)
            try {
                stopLockTask()
            } catch (e: Exception) {}

            startActivity(intent)
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun decrypt(input: String): String {
        if (input.startsWith("http")) return input
        try {
            val decoded = android.util.Base64.decode(input, android.util.Base64.DEFAULT)
            val result = ByteArray(decoded.size)
            val key = "PARAMARTHA"
            for (i in decoded.indices) {
                result[i] = (decoded[i].toInt() xor key[i % key.length].toInt()).toByte()
            }
            return String(result)
        } catch (e: Exception) {
            return input
        }
    }

    private fun registerSignalListener() {
        // Start network speed monitoring
        lastTotalRxBytes = TrafficStats.getTotalRxBytes()
        lastTotalTxBytes = TrafficStats.getTotalTxBytes()
        speedHandler.post(speedRunnable)

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                super.onSignalStrengthsChanged(signalStrength)
                // We update alpha dynamically in updateNetworkSpeedAndType instead
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    private fun updateNetworkSpeedAndType() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()

        var rxSpeed = 0L
        var txSpeed = 0L

        if (lastTotalRxBytes != TrafficStats.UNSUPPORTED.toLong() && currentRxBytes != TrafficStats.UNSUPPORTED.toLong()) {
            rxSpeed = (currentRxBytes - lastTotalRxBytes) / 1024
        }
        if (lastTotalTxBytes != TrafficStats.UNSUPPORTED.toLong() && currentTxBytes != TrafficStats.UNSUPPORTED.toLong()) {
            txSpeed = (currentTxBytes - lastTotalTxBytes) / 1024
        }

        lastTotalRxBytes = currentRxBytes
        lastTotalTxBytes = currentTxBytes

        val totalSpeed = rxSpeed + txSpeed
        tvNetworkSpeed.text = "$totalSpeed KB/s"

        // Determine network type
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val networkCapabilities = cm.getNetworkCapabilities(activeNetwork)

        if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            ivSignal.setImageResource(R.drawable.ic_signal_wifi)
        } else {
            ivSignal.setImageResource(R.drawable.ic_signal_cellular)
        }

        // Realtime icon strength based on network speed
        when {
            totalSpeed > 100 -> ivSignal.alpha = 1.0f
            totalSpeed > 20 -> ivSignal.alpha = 0.7f
            totalSpeed > 0 -> ivSignal.alpha = 0.4f
            else -> ivSignal.alpha = 0.2f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speedHandler.removeCallbacks(speedRunnable)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
        }
    }
}
