package pl.arixelo.trishot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette for Android 11, which has no Material You dynamic color.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4F5BD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF000C62),
    secondaryContainer = Color(0xFFE1E0F9),
    tertiaryContainer = Color(0xFFFFD8EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF1A2799),
    primaryContainer = Color(0xFF3540B0),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondaryContainer = Color(0xFF444559),
    tertiaryContainer = Color(0xFF5E3A50),
)

@Composable
fun TriShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val typography = remember(context) { systemTypography(context) }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
