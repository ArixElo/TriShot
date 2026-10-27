package pl.arixelo.trishot.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import pl.arixelo.trishot.R
import pl.arixelo.trishot.data.Shot
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    shots: List<Shot>,
    startIndex: Int,
    onClose: () -> Unit,
    onDelete: (List<Shot>) -> Unit,
) {
    if (shots.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, shots.lastIndex)) { shots.size }
    val current = shots[pagerState.currentPage.coerceIn(0, shots.lastIndex)]
    var showInfo by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.viewer_position, pagerState.currentPage + 1, shots.size)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.details_title))
                    }
                    IconButton(onClick = { shareShots(context, listOf(current)) }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = { onDelete(listOf(current)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            )
        },
        bottomBar = {
            Text(
                "${formatDate(current.takenAt)}  ·  ${Formatter.formatShortFileSize(context, current.sizeBytes)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            key = { shots[it].id },
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            val bitmap by rememberShotBitmap(shots[page], maxDim = 2048)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image == null) {
                    CircularProgressIndicator()
                } else {
                    Image(
                        image,
                        contentDescription = stringResource(R.string.cd_screenshot),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.details_title)) },
            text = {
                Column {
                    DetailLine(stringResource(R.string.detail_name), current.name)
                    DetailLine(stringResource(R.string.detail_taken), formatDate(current.takenAt))
                    DetailLine(stringResource(R.string.detail_size), Formatter.formatFileSize(context, current.sizeBytes))
                    DetailLine(stringResource(R.string.detail_resolution), "${current.width} × ${current.height}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
