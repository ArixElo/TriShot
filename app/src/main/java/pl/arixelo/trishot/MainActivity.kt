package pl.arixelo.trishot

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pl.arixelo.trishot.capture.CaptureService
import pl.arixelo.trishot.data.ShotRepository
import pl.arixelo.trishot.ui.TriShotApp
import pl.arixelo.trishot.ui.theme.TriShotTheme

class MainActivity : ComponentActivity() {

    private var showSetup by mutableStateOf(false)

    private val threeFingerSwipe by lazy {
        ThreeFingerSwipeDetector(minDistancePx = 100 * resources.displayMetrics.density) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            val service = CaptureService.instance
            if (service != null) service.capture(delayMs = 150) else showSetup = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ShotRepository.init(this)
        handleIntent(intent)

        setContent {
            TriShotTheme {
                TriShotApp(
                    showSetup = showSetup,
                    onShowSetup = { showSetup = it },
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onOpenAppInfo = ::openAppInfo,
                    onCaptureOutside = ::captureOutsideApp,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ShotRepository.refresh()
        if (CaptureService.instance != null) showSetup = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        threeFingerSwipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SETUP, false) == true && CaptureService.instance == null) {
            showSetup = true
        }
    }

    /** Sends the app to the background, then captures whatever is underneath. */
    private fun captureOutsideApp() {
        val service = CaptureService.instance ?: run { showSetup = true; return }
        moveTaskToBack(true)
        service.capture(delayMs = 700)
    }

    private fun openAccessibilitySettings() {
        val component = ComponentName(this, CaptureService::class.java).flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        try {
            startActivity(details)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: SecurityException) {
            // Newer Android versions reserve the per-service page for system apps.
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openAppInfo() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_app_info, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_SETUP = "pl.arixelo.trishot.SETUP"
    }
}

/** Fires once when three fingers move down together by [minDistancePx]. */
class ThreeFingerSwipeDetector(
    private val minDistancePx: Float,
    private val onSwipe: () -> Unit,
) {
    private var tracking = false
    private var startY = 0f

    fun onTouchEvent(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 3) {
                tracking = true
                startY = ev.averageY()
            } else if (ev.pointerCount > 3) {
                tracking = false
            }
            MotionEvent.ACTION_MOVE -> if (tracking && ev.pointerCount == 3 && ev.averageY() - startY > minDistancePx) {
                tracking = false
                onSwipe()
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
    }

    private fun MotionEvent.averageY(): Float = (0 until pointerCount).sumOf { getY(it).toDouble() }.toFloat() / pointerCount
}
