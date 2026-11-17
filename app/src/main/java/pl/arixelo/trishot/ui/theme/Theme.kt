package pl.arixelo.trishot.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    val amoled by Appearance.amoled.collectAsState()
    val system = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val black = darkTheme && amoled
    val colors = if (black) system.amoled() else system
    val typography = remember(context) { systemTypography(context) }
    CompositionLocalProvider(LocalAmoled provides black) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** True while AMOLED mode is actually showing (enabled and the system is in dark theme). */
val LocalAmoled = staticCompositionLocalOf { false }

/**
 * AMOLED: backgrounds, menus and dialogs go pure black. Cards and tiles keep the system's
 * accent and highest surface colours, so they still stand out.
 */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    // Menus use surfaceContainer, dialogs surfaceContainerHigh.
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
)

/** Black menus would melt into a black screen; in AMOLED mode they get a thin outline. */
@Composable
fun amoledMenuBorder(): BorderStroke? =
    if (LocalAmoled.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

/** Same for dialogs: pass as an AlertDialog modifier. */
@Composable
fun amoledDialogModifier(): Modifier =
    if (LocalAmoled.current) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, AlertDialogDefaults.shape)
    } else {
        Modifier
    }
