package pl.arixelo.trishot.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Look-and-feel preferences. */
object Appearance {
    private const val KEY_AMOLED = "amoled"

    private lateinit var appContext: Context
    private val prefs by lazy { appContext.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    /** Pure black backgrounds in dark theme; accents and cards keep the system colours. */
    private val _amoled = MutableStateFlow(false)
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _amoled.value = prefs.getBoolean(KEY_AMOLED, false)
    }

    fun setAmoled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED, enabled) }
        _amoled.value = enabled
    }
}
