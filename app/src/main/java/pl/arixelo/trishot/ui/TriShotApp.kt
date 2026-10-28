package pl.arixelo.trishot.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import pl.arixelo.trishot.update.AppUpdater
import pl.arixelo.trishot.update.Release
import pl.arixelo.trishot.update.UpdateState
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import pl.arixelo.trishot.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.arixelo.trishot.R
import pl.arixelo.trishot.capture.CaptureService
import pl.arixelo.trishot.capture.GestureMode
import pl.arixelo.trishot.data.Shot
import pl.arixelo.trishot.data.ShotRepository

private const val SHIZUKU_REQUEST = 7

@Composable
fun TriShotApp(
    showSetup: Boolean,
    onShowSetup: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onCaptureOutside: () -> Unit,
) {
    val shots by ShotRepository.shots.collectAsStateWithLifecycle()
    val takenTotal by ShotRepository.takenTotal.collectAsStateWithLifecycle()
    val serviceOn by CaptureService.running.collectAsStateWithLifecycle()
    val shizuku by GestureMode.shizuku.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Turn the Shizuku mode on once Shizuku's own permission dialog is accepted.
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == SHIZUKU_REQUEST && result == PackageManager.PERMISSION_GRANTED) GestureMode.setShizuku(true)
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }
    val onShizukuChange: (Boolean) -> Unit = { on ->
        when {
            !on -> GestureMode.setShizuku(false)
            !ShizukuAccess.isInstalled(context) -> {
                Toast.makeText(context, R.string.toast_install_shizuku, Toast.LENGTH_LONG).show()
                ShizukuAccess.openPlayStore(context)
            }
            !ShizukuAccess.isRunning() -> {
                Toast.makeText(context, R.string.toast_start_shizuku, Toast.LENGTH_LONG).show()
                ShizukuAccess.openApp(context)
            }
            ShizukuAccess.hasPermission() -> GestureMode.setShizuku(true)
            else -> Shizuku.requestPermission(SHIZUKU_REQUEST)
        }
    }
    val canReadMedia by ShotRepository.canReadMedia.collectAsStateWithLifecycle()
    val updateState by AppUpdater.state.collectAsStateWithLifecycle()

    // Results of a manual check that don't need a card get a short message instead.
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            UpdateState.Checking ->
                Toast.makeText(context, R.string.update_checking, Toast.LENGTH_SHORT).show()
            UpdateState.UpToDate -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_up_to_date, AppUpdater.currentVersion),
                    Toast.LENGTH_SHORT,
                ).show()
                AppUpdater.dismiss()
            }
            is UpdateState.Failed -> if (state.release == null) {
                Toast.makeText(context, failureText(state.reason), Toast.LENGTH_LONG).show()
                AppUpdater.dismiss()
            }
            else -> Unit
        }
    }
    val startUpdate: (Release) -> Unit = { release ->
        if (AppUpdater.canInstall()) {
            AppUpdater.downloadAndInstall(release)
        } else {
            // One-time "Install unknown apps" switch for TriShot.
            Toast.makeText(context, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }
    }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
    val gridState = rememberLazyGridState()

    val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ShotRepository.refresh()
        // After repeated denials Android stops showing the dialog; send the user to App info instead.
        val activity = context as? Activity
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, ShotRepository.mediaPermission)
        ) {
            Toast.makeText(context, R.string.toast_allow_photos_in_settings, Toast.LENGTH_LONG).show()
            onOpenAppInfo()
        }
    }
    // Android shows its own "Delete?" confirmation for files other apps (the system UI) created.
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        ShotRepository.refresh()
    }
    val delete: (List<Shot>) -> Unit = { targets ->
        if (targets.isNotEmpty()) {
            deleteRequest.launch(IntentSenderRequest.Builder(ShotRepository.deleteRequest(targets)).build())
        }
    }

    AnimatedContent(
        targetState = viewerIndex >= 0,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
        label = "screen",
    ) { viewing ->
        if (viewing) {
            BackHandler { viewerIndex = -1 }
            ViewerScreen(
                shots = shots,
                startIndex = viewerIndex,
                onClose = { viewerIndex = -1 },
                onDelete = delete,
            )
        } else {
            HomeScreen(
                shots = shots,
                takenTotal = takenTotal,
                serviceOn = serviceOn,
                shizuku = shizuku,
                onShizukuChange = onShizukuChange,
                gridState = gridState,
                canReadMedia = canReadMedia,
                onRequestMediaAccess = { mediaPermission.launch(ShotRepository.mediaPermission) },
                onOpen = { viewerIndex = it },
                onDelete = delete,
                onCapture = onCaptureOutside,
                onSetup = { onShowSetup(true) },
                updateState = updateState,
                appVersion = AppUpdater.currentVersion,
                onCheckUpdates = { AppUpdater.check() },
                onUpdate = startUpdate,
                onDismissUpdate = { AppUpdater.dismiss() },
            )
        }
    }

    if (showSetup && !serviceOn) {
        AlertDialog(
            onDismissRequest = { onShowSetup(false) },
            icon = { Icon(painterResource(R.drawable.ic_capture), contentDescription = null) },
            title = { Text(stringResource(R.string.setup_title)) },
            text = { Text(stringResource(R.string.setup_body)) },
            confirmButton = {
                TextButton(onClick = onOpenAccessibilitySettings) { Text(stringResource(R.string.action_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.action_app_info)) }
            },
        )
    }
}
