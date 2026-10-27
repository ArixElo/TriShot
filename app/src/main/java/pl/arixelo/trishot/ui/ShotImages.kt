package pl.arixelo.trishot.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import pl.arixelo.trishot.data.Shot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ImageCache : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Loads [shot] scaled so its longest side is at most [maxDim]. */
@Composable
fun rememberShotBitmap(shot: Shot, maxDim: Int): State<ImageBitmap?> {
    val context = LocalContext.current.applicationContext
    val key = "${shot.id}#${shot.takenAt}#$maxDim"
    return produceState(initialValue = ImageCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decode(context, shot, maxDim) }
                ?.also { ImageCache.put(key, it) }
        }
    }
}

private fun decode(context: Context, shot: Shot, maxDim: Int): ImageBitmap? = runCatching {
    val resolver = context.contentResolver
    val bitmap = if (maxDim <= THUMBNAIL_MAX) {
        resolver.loadThumbnail(shot.uri, Size(maxDim, maxDim), null)
    } else {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, shot.uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxDim) {
                val scale = maxDim.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    }
    bitmap.asImageBitmap()
}.getOrNull()

private const val THUMBNAIL_MAX = 512

fun shareShots(context: Context, shots: List<Shot>) {
    if (shots.isEmpty()) return
    val uris = shots.map { it.uri }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = "image/*"
        // ClipData carries the read grant to every URI, not just the first.
        clipData = ClipData.newRawUri(null, uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
