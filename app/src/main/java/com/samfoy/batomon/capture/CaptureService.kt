package com.samfoy.batomon.capture

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.media.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.samfoy.batomon.R
import com.samfoy.batomon.recognition.SceneRecognizer
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : LifecycleService() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val running = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private val recognizer = SceneRecognizer()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Batomon Companion")
            .setContentText("Capturing the default display · diagnostic mode")
            .setOngoing(true).build())
        if (!running.get()) startCapture(intent)
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: return stopSelf()
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return stopSelf()
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(code, data) ?: return stopSelf()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(320)
        val height = metrics.heightPixels.coerceAtLeast(240)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        thread = HandlerThread("batomon-capture").also { it.start(); handler = Handler(it.looper) }
        reader?.setOnImageAvailableListener({ source ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return@setOnImageAvailableListener
            lastFrameAt = now
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Keep frames ephemeral. Recognition implementations may copy only the needed crop.
                // The MVP intentionally returns UNKNOWN until calibrated Thor fixtures are supplied.
            } finally { image.close() }
        }, handler)
        virtualDisplay = projection?.createVirtualDisplay("BatomonDefaultDisplay", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopCapture() } }, handler)
        running.set(true)
    }

    private fun stopCapture() {
        if (!running.getAndSet(false)) return
        virtualDisplay?.release(); virtualDisplay = null
        reader?.setOnImageAvailableListener(null, null); reader?.close(); reader = null
        projection?.stop(); projection = null
        thread?.quitSafely(); thread = null; handler = null
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Capture", NotificationManager.IMPORTANCE_LOW)) }
    companion object { const val EXTRA_RESULT_CODE = "resultCode"; const val EXTRA_RESULT_DATA = "resultData"; private const val CHANNEL = "capture"; private const val NOTIFICATION_ID = 41; private const val FRAME_INTERVAL_MS = 700L }
}
