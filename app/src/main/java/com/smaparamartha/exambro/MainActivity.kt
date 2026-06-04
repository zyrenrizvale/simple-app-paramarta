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

    // Overlay Elements
    private lateinit var tokenOverlay: RelativeLayout
    private lateinit var splashScreen: RelativeLayout
    private lateinit var cardToken: LinearLayout
    private lateinit var tvOverlayTitle: TextView
    private lateinit var etOverlayToken: EditText
    private lateinit var btnOverlaySubmit: Button
    private lateinit var btnOverlayCancel: Button

    private val REQUEST_MEDIA_PROJECTION = 1001

    private var targetUrl = "https://paramartaapp.vercel.app/"
    
    private fun getTargetUrlWithCacheBuster(): String {
        return targetUrl + "?v=" + System.currentTimeMillis()
    }
    private var tokenSecretSeed = "PARAMARTHA_SECRET"
    private var tokenIntervalMinutes = 3

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

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        setupWebView()
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

    private fun fetchDynamicConfig() {
        thread {
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
                }
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
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
