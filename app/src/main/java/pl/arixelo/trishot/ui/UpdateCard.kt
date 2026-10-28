package pl.arixelo.trishot.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.arixelo.trishot.R
import pl.arixelo.trishot.update.Release
import pl.arixelo.trishot.update.UpdateState

/** Shown on the home screen while an update is available, downloading, installing or failed. */
@Composable
fun UpdateCard(
    state: UpdateState,
    currentVersion: String,
    onUpdate: (Release) -> Unit,
    onDismiss: () -> Unit,
) {
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    } ?: return

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_available_title, release.version),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.update_current, currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val notes = remember(release.notes) { plainNotes(release.notes) }
            if (notes.isNotBlank() && state is UpdateState.Available) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = TextButtonPadding,
                    colors = onCardButtonColors(),
                ) {
                    Text(stringResource(if (expanded) R.string.show_less else R.string.show_more))
                }
            }

            Spacer(Modifier.height(8.dp))
            when (state) {
                is UpdateState.Downloading -> {
                    Text(
                        stringResource(R.string.update_downloading, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.update_installing), style = MaterialTheme.typography.labelLarge)
                }
                else -> {
                    if (state is UpdateState.Failed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(failureText(state.reason)), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Retrying can't fix a wrong signature, an older build or a release without an APK.
                    val canRetry = state !is UpdateState.Failed ||
                        state.reason == UpdateState.Reason.Network ||
                        state.reason == UpdateState.Reason.InstallFailed
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canRetry) {
                            Button(onClick = { onUpdate(release) }) {
                                Text(
                                    stringResource(
                                        if (state is UpdateState.Failed) R.string.action_retry else R.string.action_update,
                                    ),
                                )
                            }
                        }
                        TextButton(onClick = onDismiss, colors = onCardButtonColors()) {
                            Text(stringResource(if (canRetry) R.string.action_later else R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}

private val TextButtonPadding = PaddingValues(horizontal = 0.dp)

// The default text-button colour (primary) almost disappears on a primaryContainer card.
@Composable
private fun onCardButtonColors() =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)

fun failureText(reason: UpdateState.Reason): Int = when (reason) {
    UpdateState.Reason.Network -> R.string.update_failed_network
    UpdateState.Reason.NoApk -> R.string.update_failed_no_apk
    UpdateState.Reason.BadSignature -> R.string.update_failed_signature
    UpdateState.Reason.NotNewer -> R.string.update_failed_not_newer
    UpdateState.Reason.InstallFailed -> R.string.update_failed_install
}

/** GitHub release notes are Markdown; show them as plain text. */
private fun plainNotes(markdown: String): String =
    markdown
        .replace(Regex("\\*\\*|__|`"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("(?m)^- "), "• ")
        .trim()
