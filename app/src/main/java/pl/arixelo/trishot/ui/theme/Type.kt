package pl.arixelo.trishot.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Typography built from the fonts the device itself uses for its system UI
 * (e.g. Google Sans on Pixels). Missing fonts fall back to the default sans-serif.
 */
fun systemTypography(context: Context): Typography {
    val headline = deviceFamily(
        regular = systemFontName(context, "config_headlineFontFamily"),
        medium = systemFontName(context, "config_headlineFontFamilyMedium"),
    )
    val body = deviceFamily(
        regular = systemFontName(context, "config_bodyFontFamily"),
        medium = systemFontName(context, "config_bodyFontFamilyMedium"),
    )
    fun TextStyle.headline() = copy(fontFamily = headline)
    fun TextStyle.body() = copy(fontFamily = body)

    // Same split as Material 3: "brand" font for big text, "plain" font for reading text.
    return Typography().run {
        copy(
            displayLarge = displayLarge.headline(),
            displayMedium = displayMedium.headline(),
            displaySmall = displaySmall.headline(),
            headlineLarge = headlineLarge.headline(),
            headlineMedium = headlineMedium.headline(),
            headlineSmall = headlineSmall.headline(),
            titleLarge = titleLarge.headline(),
            titleMedium = titleMedium.body(),
            titleSmall = titleSmall.body(),
            bodyLarge = bodyLarge.body(),
            bodyMedium = bodyMedium.body(),
            bodySmall = bodySmall.body(),
            labelLarge = labelLarge.body(),
            labelMedium = labelMedium.body(),
            labelSmall = labelSmall.body(),
        )
    }
}

private fun deviceFamily(regular: String?, medium: String?): FontFamily {
    if (regular == null) return FontFamily.Default
    val mediumName = medium ?: regular
    return FontFamily(
        Font(DeviceFontFamilyName(regular), FontWeight.Normal),
        Font(DeviceFontFamilyName(mediumName), FontWeight.Medium),
        Font(DeviceFontFamilyName(mediumName), FontWeight.SemiBold),
        Font(DeviceFontFamilyName(regular), FontWeight.Bold),
    )
}

/** Reads the font family name the OEM configured for system UI text, if any. */
@SuppressLint("DiscouragedApi")
private fun systemFontName(context: Context, resName: String): String? {
    val id = context.resources.getIdentifier(resName, "string", "android")
    if (id == 0) return null
    return runCatching { context.resources.getString(id) }.getOrNull()?.takeIf { it.isNotBlank() }
}
