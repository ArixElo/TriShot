package pl.arixelo.trishot.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.arixelo.trishot.R
import pl.arixelo.trishot.data.Shot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    shots: List<Shot>,
    takenTotal: Int,
    serviceOn: Boolean,
    shizuku: Boolean,
    onShizukuChange: (Boolean) -> Unit,
    gridState: LazyGridState,
    canReadMedia: Boolean,
    onRequestMediaAccess: () -> Unit,
    onOpen: (Int) -> Unit,
    onDelete: (List<Shot>) -> Unit,
    onCapture: () -> Unit,
    onSetup: () -> Unit,
) {
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val selected = shots.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    fun toggle(shot: Shot) {
        selectedIds = if (shot.id in selectedIds) selectedIds - shot.id else selectedIds + shot.id
    }

    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (selecting) {
                        Text(pluralStringResource(R.plurals.selected_count, selected.size, selected.size))
                    } else {
                        TriShotLogo()
                    }
                },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_selection))
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { shareShots(context, selected) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = {
                            onDelete(selected)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !selecting, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = onCapture,
                    icon = { Icon(painterResource(R.drawable.ic_capture), contentDescription = null) },
                    text = { Text(stringResource(R.string.action_capture)) },
                    expanded = gridState.firstVisibleItemIndex == 0,
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "stats", span = { GridItemSpan(maxLineSpan) }) {
                StatsRow(
                    saved = shots.size,
                    totalBytes = shots.sumOf { it.sizeBytes },
                    takenTotal = takenTotal,
                )
            }
            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                StatusCard(
                    serviceOn = serviceOn,
                    shizuku = shizuku,
                    onShizukuChange = onShizukuChange,
                    onSetup = onSetup,
                )
            }
            if (!canReadMedia) {
                item(key = "media", span = { GridItemSpan(maxLineSpan) }) {
                    MediaAccessCard(onAllow = onRequestMediaAccess)
                }
            }
            if (shots.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyState() }
            } else {
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.section_screenshots),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(shots, key = { _, shot -> shot.id }) { index, shot ->
                    ShotTile(
                        shot = shot,
                        selected = shot.id in selectedIds,
                        onClick = { if (selecting) toggle(shot) else onOpen(index) },
                        onLongClick = { toggle(shot) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

}

@Composable
private fun MediaAccessCard(onAllow: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.media_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.media_body), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAllow) { Text(stringResource(R.string.action_allow)) }
        }
    }
}

@Composable
private fun StatsRow(saved: Int, totalBytes: Long, takenTotal: Int) {
    val context = LocalContext.current
    // Intrinsic height keeps the three cards equal when a translated label wraps.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        StatCard(
            value = saved.toString(),
            label = stringResource(R.string.stat_saved),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = Formatter.formatShortFileSize(context, totalBytes),
            label = stringResource(R.string.stat_total_size),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = takenTotal.toString(),
            label = stringResource(R.string.stat_taken),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, container: Color, content: Color, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StatusCard(
    serviceOn: Boolean,
    shizuku: Boolean,
    onShizukuChange: (Boolean) -> Unit,
    onSetup: () -> Unit,
) {
    if (serviceOn) {
        OutlinedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (shizuku) R.string.ready_shizuku else R.string.ready_in_app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SwitchRow(
                title = stringResource(R.string.shizuku_title),
                subtitle = stringResource(R.string.shizuku_subtitle),
                checked = shizuku,
                onCheckedChange = onShizukuChange,
            )
        }
    } else {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.capture_off_title), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.capture_off_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onSetup) { Text(stringResource(R.string.action_turn_on)) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_capture),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShotTile(
    shot: Shot,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by rememberShotBitmap(shot, maxDim = 480)
    val scale by animateFloatAsState(if (selected) 0.9f else 1f, label = "tileScale")
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier
            .aspectRatio(9f / 16f)
            .scale(scale)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxSize()) {
            bitmap?.let {
                Image(it, contentDescription = stringResource(R.string.cd_screenshot), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary, shape),
            )
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .size(24.dp),
            )
        }
    }
}
