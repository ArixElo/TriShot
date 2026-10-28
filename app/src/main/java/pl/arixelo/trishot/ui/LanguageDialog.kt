package pl.arixelo.trishot.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.arixelo.trishot.R

/**
 * Per-app language (Android 13+). An empty tag means "follow the system". The choice is the
 * same one Android shows in Settings → System → Languages → App languages.
 */
object AppLanguage {
    /** Language tags TriShot has translations for; keep in sync with res/xml/locales_config.xml. */
    val available = listOf("en", "pl")

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun current(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else locales[0].language
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun set(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    /** Each language is shown in its own language, so it's recognisable whatever is active. */
    fun nativeName(tag: String): String = when (tag) {
        "en" -> "English"
        "pl" -> "Polski"
        else -> tag
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val options = listOf("" to stringResource(R.string.language_system)) +
        AppLanguage.available.map { it to AppLanguage.nativeName(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                for ((tag, label) in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = tag == current,
                                role = Role.RadioButton,
                                onClick = {
                                    onDismiss()
                                    // Android restarts the screen in the new language right away.
                                    if (tag != current) AppLanguage.set(context, tag)
                                },
                            )
                            .padding(vertical = 12.dp),
                    ) {
                        RadioButton(selected = tag == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
