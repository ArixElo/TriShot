package pl.arixelo.trishot.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Immutable
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val reason: Reason, val release: Release? = null) : UpdateState

    enum class Reason { Network, NoApk, BadSignature, NotNewer, InstallFailed }
}

/**
 * Over-the-air updates from the GitHub releases of ArixElo/TriShot. The APK is only handed to
 * the system installer if it is newer and signed with the same key as the installed app.
 */
object AppUpdater {
    private const val LATEST_RELEASE = "https://api.github.com/repos/ArixElo/TriShot/releases/latest"
    private const val KEY_LAST_CHECK = "last_update_check"
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private lateinit var appContext: Context
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val prefs by lazy { appContext.getSharedPreferences("updates", Context.MODE_PRIVATE) }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String
        get() = installedPackage().versionName.orEmpty()

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
    }

    /** Checks quietly on app start, at most every few hours. */
    fun autoCheck() {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_CHECK_INTERVAL_MS) return
        check(quiet = true)
    }

    /** [quiet] keeps the UI idle when nothing is found or the network fails. */
    fun check(quiet: Boolean = false) {
        if (job?.isActive == true) return
        job = io.launch {
            if (!quiet) _state.value = UpdateState.Checking
            val release = runCatching { fetchLatest() }.getOrElse {
                _state.value = if (quiet) UpdateState.Idle else UpdateState.Failed(UpdateState.Reason.Network)
                return@launch
            }
            prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
            _state.value = when {
                release == null -> if (quiet) UpdateState.Idle else UpdateState.Failed(UpdateState.Reason.NoApk)
                isNewer(release.version, currentVersion) -> UpdateState.Available(release)
                else -> if (quiet) UpdateState.Idle else UpdateState.UpToDate
            }
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    fun downloadAndInstall(release: Release) {
        if (job?.isActive == true) return
        job = io.launch {
            val apk = runCatching { download(release) }.getOrElse {
                _state.value = UpdateState.Failed(UpdateState.Reason.Network, release)
                return@launch
            }
            val problem = verify(apk)
            if (problem != null) {
                apk.delete()
                _state.value = UpdateState.Failed(problem, release)
                return@launch
            }
            _state.value = UpdateState.Installing(release)
            runCatching { install(apk) }.onFailure {
                _state.value = UpdateState.Failed(UpdateState.Reason.InstallFailed, release)
            }
        }
    }

    /** Called by [InstallResultReceiver] when the installer reports an error or a cancel. */
    internal fun onInstallFailed() {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = UpdateState.Failed(UpdateState.Reason.InstallFailed, release)
    }

    /** Whether Android lets TriShot install APKs ("Install unknown apps"). */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    private fun fetchLatest(): Release? {
        val json = JSONObject(httpGet(LATEST_RELEASE).bufferedReader().use { it.readText() })
        val asset = (0 until json.getJSONArray("assets").length())
            .map { json.getJSONArray("assets").getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
            ?: return null
        return Release(
            version = json.getString("tag_name").removePrefix("v"),
            notes = json.optString("body"),
            apkUrl = asset.getString("browser_download_url"),
            apkSize = asset.optLong("size"),
            pageUrl = json.optString("html_url"),
        )
    }

    private fun download(release: Release): File {
        val dir = File(appContext.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "TriShot-${release.version}.apk")
        _state.value = UpdateState.Downloading(release, 0f)
        val connection = open(release.apkUrl)
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        return file
    }

    private fun verify(apk: File): UpdateState.Reason? {
        val pm = appContext.packageManager
        val downloaded = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return UpdateState.Reason.NoApk
        val installed = installedPackage(PackageManager.GET_SIGNING_CERTIFICATES)
        if (downloaded.packageName != installed.packageName) return UpdateState.Reason.BadSignature
        if (signatures(downloaded) != signatures(installed)) return UpdateState.Reason.BadSignature
        if (downloaded.longVersionCode <= installed.longVersionCode) return UpdateState.Reason.NotNewer
        return null
    }

    private fun install(apk: File) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Lets Android skip the confirm step when TriShot itself installed the current version.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                Intent(appContext, InstallResultReceiver::class.java),
                // The installer fills in the status extras, so this one has to be mutable.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun installedPackage(flags: Int = 0): PackageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, flags)

    private fun signatures(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()

    private fun httpGet(url: String) = open(url).inputStream

    private fun open(url: String): HttpURLConnection {
        // GitHub redirects asset downloads to its CDN; follow those by hand to stay on HTTPS.
        var target = URL(url)
        repeat(5) {
            val connection = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
                setRequestProperty("User-Agent", "TriShot-Updater")
            }
            when (connection.responseCode) {
                in 200..299 -> return connection
                in 300..399 -> {
                    val next = URL(target, connection.getHeaderField("Location"))
                    connection.disconnect()
                    require(next.protocol == "https") { "insecure redirect" }
                    target = next
                }
                else -> error("HTTP ${connection.responseCode}")
            }
        }
        error("too many redirects")
    }

    /** Compares dotted versions numerically: 1.10 is newer than 1.9. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
