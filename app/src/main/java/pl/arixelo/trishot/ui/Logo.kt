package pl.arixelo.trishot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import pl.arixelo.trishot.R

// Same gradient as the launcher icon background.
private val LogoGradient = Brush.linearGradient(listOf(Color(0xFF7B61FF), Color(0xFF4527A0)))

// Google Sans Flex is a variable font on Pixels, so it has a real bold; elsewhere this falls
// back to the theme's font.
private fun flexWeight(weight: Int) = Font(
    DeviceFontFamilyName("google-sans-flex"),
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val WordmarkFont = FontFamily(flexWeight(400), flexWeight(750))

/** App mark plus the "TriShot" wordmark: "Tri" bold in the accent colour, "Shot" regular. */
@Composable
fun TriShotLogo(modifier: Modifier = Modifier) {
    val name = "TriShot"
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(LogoGradient, RoundedCornerShape(11.dp)),
        ) {
            Icon(
                painterResource(R.drawable.ic_capture),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight(750), color = MaterialTheme.colorScheme.primary)) {
                    append("Tri")
                }
                withStyle(SpanStyle(fontWeight = FontWeight(400))) { append("Shot") }
            },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = WordmarkFont, letterSpacing = (-0.02).em),
        )
    }
}
