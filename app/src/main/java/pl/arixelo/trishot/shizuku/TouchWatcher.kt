package pl.arixelo.trishot.shizuku

import android.content.Context
import android.os.RemoteException
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku user service, i.e. as the shell user, which may read raw touchscreen
 * events from /dev/input. It only listens: every touch still reaches apps unchanged.
 *
 * Coordinates are in the panel's raw axes, which don't follow screen rotation, so swipes are
 * reported as a raw direction and the app decides which one is "down" right now.
 */
class TouchWatcher() : ITouchWatcher.Stub() {

    // Shizuku looks for a (Context) constructor first.
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    @Volatile
    private var started = false

    override fun destroy() {
        exitProcess(0)
    }

    override fun start(listener: ISwipeListener): String {
        val screens = findTouchscreens()
        if (screens.isEmpty()) return "error: no touchscreen found"
        val description = screens.joinToString { "${it.name} (${it.path}, ${it.maxX}x${it.maxY})" }
        if (started) return "already watching $description"
        started = true
        // Some devices (tablets with a pen or keyboard cover) expose more than one touch device.
        for (screen in screens) {
            Thread({ watch(screen, listener) }, "TriShotTouch-${screen.path.substringAfterLast('/')}").apply {
                isDaemon = true
                start()
            }
        }
        return "watching $description"
    }

    private fun watch(screen: Touchscreen, listener: ISwipeListener) {
        val detector = SwipeDetector(screen.maxX, screen.maxY) { direction ->
            try {
                listener.onSwipe(direction)
            } catch (e: RemoteException) {
                // TriShot's process is gone; nothing to tell.
            }
        }
        try {
            FileInputStream(screen.path).use { input ->
                val bytes = ByteArray(EVENT_SIZE * 64)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                while (true) {
                    val read = input.read(bytes)
                    if (read <= 0) break
                    for (offset in 0 until read - EVENT_SIZE + 1 step EVENT_SIZE) {
                        // struct input_event on 64-bit: timeval (16 bytes), type, code, value.
                        val type = buffer.getShort(offset + 16).toInt() and 0xffff
                        val code = buffer.getShort(offset + 18).toInt() and 0xffff
                        val value = buffer.getInt(offset + 20)
                        detector.feed(type, code, value)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "touch watcher stopped for ${screen.path}", e)
        }
    }

    private class Touchscreen(val path: String, val name: String, val maxX: Int, val maxY: Int)

    /** Asks getevent for every direct-touch, multi-touch device and its X/Y range. */
    private fun findTouchscreens(): List<Touchscreen> {
        val output = runCatching {
            val process = ProcessBuilder("getevent", "-pl").redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText().also { process.waitFor() }
        }.getOrNull() ?: return emptyList()

        val found = mutableListOf<Touchscreen>()
        var path: String? = null
        var name = ""
        var maxX = 0
        var maxY = 0
        var direct = false
        fun flush() {
            val p = path ?: return
            if (direct && maxX > 0 && maxY > 0) found += Touchscreen(p, name, maxX, maxY)
        }
        fun maxOf(line: String) = Regex("max (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("add device") -> {
                    flush()
                    path = trimmed.substringAfter(": ").trim()
                    name = ""
                    maxX = 0
                    maxY = 0
                    direct = false
                }
                trimmed.startsWith("name:") -> name = trimmed.substringAfter("name:").trim().trim('"')
                "ABS_MT_POSITION_X" in trimmed -> maxX = maxOf(trimmed)
                "ABS_MT_POSITION_Y" in trimmed -> maxY = maxOf(trimmed)
                "INPUT_PROP_DIRECT" in trimmed -> direct = true
            }
        }
        flush()
        return found
    }

    private companion object {
        const val TAG = "TriShotTouchWatcher"
        const val EVENT_SIZE = 24
    }
}

/**
 * Multi-touch protocol B parser: tracks contacts per slot and reports once when exactly three
 * fingers move together far enough along one raw axis.
 */
internal class SwipeDetector(
    private val maxX: Int,
    private val maxY: Int,
    private val onSwipe: (direction: Int) -> Unit,
) {
    private val active = BooleanArray(MAX_SLOTS)
    private val xs = IntArray(MAX_SLOTS)
    private val ys = IntArray(MAX_SLOTS)
    private var slot = 0
    private var lastCount = 0
    private var armed = false
    private var blocked = false
    private var startX = 0f
    private var startY = 0f

    fun feed(type: Int, code: Int, value: Int) {
        when {
            type == EV_ABS && code == ABS_MT_SLOT -> slot = value.coerceIn(0, MAX_SLOTS - 1)
            type == EV_ABS && code == ABS_MT_TRACKING_ID -> active[slot] = value >= 0
            type == EV_ABS && code == ABS_MT_POSITION_X -> xs[slot] = value
            type == EV_ABS && code == ABS_MT_POSITION_Y -> ys[slot] = value
            type == EV_SYN && code == SYN_REPORT -> frame()
        }
    }

    private fun frame() {
        val count = active.count { it }
        when {
            count == 0 -> {
                armed = false
                blocked = false
            }
            count > 3 -> {
                armed = false
                blocked = true
            }
            count == 3 && lastCount < 3 && !blocked -> {
                armed = true
                startX = average(xs)
                startY = average(ys)
            }
            count == 3 && armed -> {
                // Compare in fractions of each axis so the threshold is the same share of the
                // screen whichever way the panel is mounted.
                val dx = (average(xs) - startX) / maxX
                val dy = (average(ys) - startY) / maxY
                if (maxOf(abs(dx), abs(dy)) > SWIPE_FRACTION) {
                    armed = false
                    blocked = true
                    onSwipe(
                        if (abs(dy) >= abs(dx)) {
                            if (dy > 0) DIRECTION_PLUS_Y else DIRECTION_MINUS_Y
                        } else {
                            if (dx > 0) DIRECTION_PLUS_X else DIRECTION_MINUS_X
                        },
                    )
                }
            }
        }
        lastCount = count
    }

    private fun average(values: IntArray): Float {
        var sum = 0L
        var n = 0
        for (i in 0 until MAX_SLOTS) if (active[i]) {
            sum += values[i]
            n++
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    companion object {
        const val DIRECTION_PLUS_Y = 0
        const val DIRECTION_MINUS_Y = 1
        const val DIRECTION_PLUS_X = 2
        const val DIRECTION_MINUS_X = 3

        private const val SWIPE_FRACTION = 0.09f
        private const val MAX_SLOTS = 16
        private const val EV_SYN = 0x00
        private const val EV_ABS = 0x03
        private const val SYN_REPORT = 0x00
        private const val ABS_MT_SLOT = 0x2f
        private const val ABS_MT_POSITION_X = 0x35
        private const val ABS_MT_POSITION_Y = 0x36
        private const val ABS_MT_TRACKING_ID = 0x39
    }
}
