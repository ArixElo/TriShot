package pl.arixelo.trishot.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class Shot(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val takenAt: Long,
    val width: Int,
    val height: Int,
) {
    val id: String get() = uri.toString()
}

/**
 * Screenshots are regular MediaStore images (Pictures/Screenshots), mostly written by the
 * system screenshot UI. TriShot remembers the IDs of the ones it triggered.
 */
object ShotRepository {
    private const val KEY_TAKEN = "taken_total"
    private const val KEY_IDS = "tracked_ids"
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private lateinit var appContext: Context
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _shots = MutableStateFlow<List<Shot>>(emptyList())
    val shots: StateFlow<List<Shot>> = _shots.asStateFlow()

    /** Every screenshot ever taken through TriShot, including ones deleted since. */
    private val _takenTotal = MutableStateFlow(0)
    val takenTotal: StateFlow<Int> = _takenTotal.asStateFlow()

    /** Without photo access only screenshots TriShot saved itself are visible. */
    private val _canReadMedia = MutableStateFlow(true)
    val canReadMedia: StateFlow<Boolean> = _canReadMedia.asStateFlow()

    private val prefs by lazy { appContext.getSharedPreferences("stats", Context.MODE_PRIVATE) }

    val mediaPermission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _takenTotal.value = prefs.getInt(KEY_TAKEN, 0)
        refresh()
    }

    fun refresh() {
        io.launch { load() }
    }

    @Synchronized
    fun recordCapture() {
        val total = prefs.getInt(KEY_TAKEN, 0) + 1
        prefs.edit { putInt(KEY_TAKEN, total) }
        _takenTotal.value = total
    }

    /** Looks for a screenshot saved at or after [sinceMillis] and starts tracking it. */
    suspend fun trackNewScreenshot(sinceMillis: Long): Boolean = withContext(Dispatchers.IO) {
        val tracked = trackedIds()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf((sinceMillis / 1000 - 2).toString(), "%Screenshots%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val id = appContext.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            generateSequence { if (c.moveToNext()) c.getLong(0) else null }.firstOrNull { it !in tracked }
        } ?: return@withContext false
        addTracked(id)
        load()
        true
    }

    /** Fallback when the system screenshot UI isn't available: save the bitmap ourselves. */
    suspend fun saveBitmap(bitmap: Bitmap): Shot? = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_${stamp}_TriShot.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return@withContext null
        }
        recordCapture()
        addTracked(ContentUris.parseId(uri))
        load()
        _shots.value.firstOrNull { it.uri == uri }
    }

    /** System dialog that asks the user to confirm deleting [targets]. */
    fun deleteRequest(targets: Collection<Shot>): IntentSender =
        MediaStore.createDeleteRequest(appContext.contentResolver, targets.map { it.uri }).intentSender

    private fun hasMediaAccess(): Boolean =
        ContextCompat.checkSelfPermission(appContext, mediaPermission) == PackageManager.PERMISSION_GRANTED

    private fun trackedIds(): Set<Long> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    @Synchronized
    private fun addTracked(id: Long) {
        prefs.edit { putStringSet(KEY_IDS, trackedIds().map { it.toString() }.toSet() + id.toString()) }
    }

    @Synchronized
    private fun load() {
        val access = hasMediaAccess()
        _canReadMedia.value = access
        val ids = trackedIds()
        if (ids.isEmpty()) {
            _shots.value = emptyList()
            return
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val selection = "${MediaStore.Images.Media._ID} IN (${ids.joinToString(",")})"
        val found = appContext.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val taken = if (c.isNull(3)) c.getLong(4) * 1000 else c.getLong(3)
                    add(
                        Shot(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(1).orEmpty(),
                            sizeBytes = c.getLong(2),
                            takenAt = taken,
                            width = c.getInt(5),
                            height = c.getInt(6),
                        ),
                    )
                }
            }
        }.orEmpty()

        // Forget screenshots deleted elsewhere, but only when we can see everything.
        if (access) {
            val present = found.map { ContentUris.parseId(it.uri).toString() }.toSet()
            prefs.edit { putStringSet(KEY_IDS, present) }
        }
        _shots.value = found.sortedByDescending { it.takenAt }
    }
}
