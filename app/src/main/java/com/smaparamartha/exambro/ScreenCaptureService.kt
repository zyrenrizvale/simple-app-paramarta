package com.smaparamartha.exambro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        var frameCallback: ((String) -> Unit)? = null
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    // Target resolution (very low to save Firebase bandwidth)
    private val captureWidth = 640
    private val captureHeight = 360

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            
            if (resultData != null) {
                createNotificationChannel()
                val notification: Notification = NotificationCompat.Builder(this, "ScreenCaptureChannel")
                    .setContentTitle("Exambro Active")
                    .setContentText("Ujian sedang berlangsung dan layar direkam.")
                    .setSmallIcon(R.drawable.logo)
                    .build()

                startForeground(1, notification)
                startScreenCapture(resultCode, resultData)
            }
        } else if (intent?.action == ACTION_STOP) {
            stopScreenCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "ScreenCaptureChannel",
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            captureWidth, captureHeight, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        isCapturing = true
        captureLoop()
    }

    private fun captureLoop() {
        if (!isCapturing) return
        
        try {
            val image: Image? = imageReader?.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer: ByteBuffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * captureWidth

                // Create bitmap
                val bitmap = Bitmap.createBitmap(captureWidth + rowPadding / pixelStride, captureHeight, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop to actual width
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, captureWidth, captureHeight)

                // Compress to Base64 (JPEG 40% quality to keep size < 20KB)
                val outputStream = ByteArrayOutputStream()
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64Str = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
                val deviceModel = Build.MODEL

                frameCallback?.invoke("'$base64Str', '$deviceModel'")

                croppedBitmap.recycle()
                bitmap.recycle()
                image.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Capture every 5 seconds (5000ms) to match web polling rate and save bandwidth
        handler.postDelayed({ captureLoop() }, 5000)
    }

    private fun stopScreenCapture() {
        isCapturing = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        stopScreenCapture()
        super.onDestroy()
    }
}
