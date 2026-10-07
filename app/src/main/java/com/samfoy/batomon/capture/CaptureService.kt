package com.samfoy.batomon.capture

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.net.Uri
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
    private var projectionCallback: MediaProjection.Callback? = null
    @Volatile private var pendingExportUri: Uri? = null
    private val running = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private val recognizer = SceneRecognizer()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        val stopIntent = PendingIntent.getService(this, 42, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Batomon Companion")
            .setContentText("Capturing the default display · diagnostic mode")
            .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent).build())
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SAVE_FRAME) {
            if (running.get()) {
                pendingExportUri = intent.getParcelableExtra(EXTRA_EXPORT_URI)
                sendState(STATE_EXPORT_ARMED)
            } else {
                sendState(STATE_ERROR, "Start capture before exporting a diagnostic frame")
            }
            return START_NOT_STICKY
        }
        if (!running.get()) startCapture(intent)
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent?) {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: return stopSelf()
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return stopSelf()
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            projection = manager.getMediaProjection(code, data) ?: error("MediaProjection unavailable")
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
                    pendingExportUri?.let { uri ->
                        pendingExportUri = null
                        exportRedactedFrame(image, uri)
                    }
                    // Keep frames ephemeral. Recognition implementations may copy only the needed crop.
                    // The MVP intentionally returns UNKNOWN until calibrated Thor fixtures are supplied.
                } finally { image.close() }
            }, handler)

            // Android 14+ requires the callback before createVirtualDisplay. Keep the reference so
            // cleanup can unregister it even if virtual-display creation fails halfway through.
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() { stopCapture(projectionAlreadyStopped = true) }
            }
            projection?.registerCallback(projectionCallback!!, handler)
            virtualDisplay = projection?.createVirtualDisplay("BatomonDefaultDisplay", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
                ?: error("Virtual display creation failed")
            running.set(true)
            sendState(STATE_CAPTURING)
        } catch (failure: Throwable) {
            Log.e(TAG, "Capture startup failed", failure)
            sendState(STATE_ERROR, failure.message ?: "Capture startup failed")
            stopCapture()
            stopSelf()
        }
    }

    private fun stopCapture(projectionAlreadyStopped: Boolean = false) {
        // Do not gate cleanup on `running`: startup can fail after allocating any one of these.
        val resourcesAllocated = projection != null || reader != null || virtualDisplay != null || thread != null
        val hadResources = CaptureCleanupPolicy.needsCleanup(running.getAndSet(false), resourcesAllocated)
        if (!hadResources) return
        virtualDisplay?.release(); virtualDisplay = null
        reader?.setOnImageAvailableListener(null, null); reader?.close(); reader = null
        val currentProjection = projection; projection = null
        val callback = projectionCallback; projectionCallback = null
        if (currentProjection != null) {
            if (callback != null) runCatching { currentProjection.unregisterCallback(callback) }
            if (!projectionAlreadyStopped) runCatching { currentProjection.stop() }
        }
        thread?.quitSafely(); thread = null; handler = null
        sendState(STATE_IDLE)
    }

    private fun exportRedactedFrame(image: Image, uri: Uri) {
        val plane = image.planes.firstOrNull() ?: return
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        // Explicit diagnostic exports redact the common top/bottom system strips and carry a visible marker.
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = 220 }
        val strip = (bitmap.height * 0.08f).toInt().coerceAtLeast(24)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), strip.toFloat(), paint)
        canvas.drawRect(0f, (bitmap.height - strip).toFloat(), bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.color = Color.WHITE; paint.alpha = 255; paint.textSize = (bitmap.width * 0.018f).coerceAtLeast(18f)
        canvas.drawText("BATOMON DIAGNOSTIC · REDACTED", 18f, strip * .65f, paint)
        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        bitmap.recycle()
        sendState(STATE_EXPORT_SAVED)
    }

    private fun sendState(state: String, error: String? = null) {
        sendBroadcast(Intent(ACTION_CAPTURE_STATE).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, state); if (error != null) putExtra(EXTRA_ERROR, error)
        })
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Capture", NotificationManager.IMPORTANCE_LOW)) }
    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"; const val EXTRA_RESULT_DATA = "resultData"; const val EXTRA_EXPORT_URI = "exportUri"
        const val ACTION_SAVE_FRAME = "com.samfoy.batomon.SAVE_FRAME"; const val ACTION_STOP = "com.samfoy.batomon.STOP"; const val ACTION_CAPTURE_STATE = "com.samfoy.batomon.CAPTURE_STATE"
        const val EXTRA_STATE = "state"; const val EXTRA_ERROR = "error"
        const val STATE_CAPTURING = "capturing"; const val STATE_IDLE = "idle"; const val STATE_ERROR = "error"; const val STATE_EXPORT_ARMED = "export_armed"; const val STATE_EXPORT_SAVED = "export_saved"
        private const val CHANNEL = "capture"; private const val NOTIFICATION_ID = 41; private const val FRAME_INTERVAL_MS = 700L; private const val TAG = "CaptureService"
    }
}
