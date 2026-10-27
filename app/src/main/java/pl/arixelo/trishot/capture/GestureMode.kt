package pl.arixelo.trishot.capture

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the three-finger swipe is detected outside TriShot (via Shizuku). */
object GestureMode {
    private const val KEY_SHIZUKU = "shizuku_gesture"

    private lateinit var appContext: Context
    private val prefs by lazy { appContext.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    /** Shizuku reads the touchscreen passively; touch works normally everywhere. */
    private val _shizuku = MutableStateFlow(false)
    val shizuku: StateFlow<Boolean> = _shizuku.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _shizuku.value = prefs.getBoolean(KEY_SHIZUKU, false)
    }

    fun setShizuku(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHIZUKU, enabled) }
        _shizuku.value = enabled
    }
}
