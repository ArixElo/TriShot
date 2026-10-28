package pl.arixelo.trishot.capture

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import pl.arixelo.trishot.R
import pl.arixelo.trishot.data.ShotRepository
import pl.arixelo.trishot.shizuku.ShizukuTouchSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Capture engine. Asks the system for a normal screenshot (with Android's own animation and
 * preview); falls back to grabbing the screen itself via takeScreenshot (API 30+).
 */
class CaptureService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var busy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _running.value = true
        scope.launch { GestureMode.shizuku.collect(::applyShizukuMode) }
    }

    private var shizukuSession: ShizukuTouchSession? = null

    private fun applyShizukuMode(enabled: Boolean) {
        shizukuSession?.stop()
        shizukuSession = if (enabled) {
            ShizukuTouchSession(this) { capture(delayMs = 100) }.also { it.start() }
        } else {
            null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun disconnect() {
        shizukuSession?.stop()
        shizukuSession = null
        if (instance === this) instance = null
        _running.value = false
    }

    /** Closes the notification shade first so it isn't in the shot. */
    fun captureFromShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        capture(delayMs = 600)
    }

    fun capture(delayMs: Long = 0) {
        if (busy) return
        busy = true
        scope.launch {
            delay(delayMs)
            val since = System.currentTimeMillis()
            // Android's own screenshot: flash animation, preview with Share / Edit / Capture more.
            if (performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)) {
                val total = ShotRepository.recordCapture()
                watchForScreenshot(since)
                delay(SYSTEM_SHOT_COOLDOWN_MS)
                busy = false
                celebrateIfMilestone(total)
            } else {
                captureSilently()
            }
        }
    }

    /** At a milestone count: three more screenshots, then the milestone's notification. */
    private fun celebrateIfMilestone(total: Int) {
        val message = Milestones.messageFor(total) ?: return
        scope.launch {
            repeat(MILESTONE_BURST) {
                delay(MILESTONE_GAP_MS)
                capture()
            }
            delay(MILESTONE_GAP_MS)
            Milestones.announce(this@CaptureService, total, message)
        }
    }

    /** Waits for the system to write the screenshot file, then adds it to TriShot. */
    private fun watchForScreenshot(since: Long) = scope.launch {
        val changes = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        try {
            withTimeoutOrNull(WATCH_TIMEOUT_MS) {
                while (!ShotRepository.trackNewScreenshot(since)) changes.receive()
            }
        } finally {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun captureSilently() {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    // Hardware bitmaps can't be compressed directly; copy to a software bitmap.
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware?.recycle()
                    result.hardwareBuffer.close()
                    if (bitmap == null) {
                        finish(getString(R.string.toast_read_failed))
                        return
                    }
                    scope.launch {
                        val shot = runCatching { ShotRepository.saveBitmap(bitmap) }.getOrNull()
                        bitmap.recycle()
                        finish(
                            if (shot == null) {
                                getString(R.string.toast_save_failed)
                            } else {
                                getString(R.string.toast_saved, Formatter.formatShortFileSize(this@CaptureService, shot.sizeBytes))
                            },
                        )
                        if (shot != null) celebrateIfMilestone(ShotRepository.takenTotal.value)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val reason = when (errorCode) {
                        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> getString(R.string.toast_too_fast)
                        ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> getString(R.string.toast_protected)
                        else -> getString(R.string.toast_failed, errorCode)
                    }
                    finish(reason)
                }
            })
    }

    private fun finish(message: String) {
        busy = false
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val SYSTEM_SHOT_COOLDOWN_MS = 1_000L
        private const val WATCH_TIMEOUT_MS = 15_000L
        private const val MILESTONE_BURST = 3
        // Longer than SYSTEM_SHOT_COOLDOWN_MS so each burst shot gets through the busy guard.
        private const val MILESTONE_GAP_MS = 1_600L

        @Volatile
        var instance: CaptureService? = null
            private set

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
