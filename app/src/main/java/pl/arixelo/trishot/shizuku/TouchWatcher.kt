package pl.arixelo.trishot.shizuku

import android.content.Context
import android.os.RemoteException
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku user service, i.e. as the shell user, which may read raw touchscreen
 * events from /dev/input. It only listens: every touch still reaches apps unchanged.
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
        val screen = findTouchscreen() ?: return "error: no touchscreen found"
        if (started) return "already running on ${screen.path}"
        started = true
        Thread({ watch(screen, listener) }, "TriShotTouchWatcher").apply {
            isDaemon = true
            start()
        }
        return "watching ${screen.name} (${screen.path}, maxY=${screen.maxY})"
    }

    private fun watch(screen: Touchscreen, listener: ISwipeListener) {
        val detector = SwipeDetector(threshold = (screen.maxY * SWIPE_FRACTION).toInt()) {
            try {
                listener.onSwipeDown()
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
            Log.w(TAG, "touch watcher stopped", e)
        }
        started = false
    }

    private class Touchscreen(val path: String, val name: String, val maxY: Int)

    /** Asks getevent for the direct-touch device and its Y range. */
    private fun findTouchscreen(): Touchscreen? {
        val output = runCatching {
            val process = ProcessBuilder("getevent", "-pl").redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText().also { process.waitFor() }
        }.getOrNull() ?: return null

        var path: String? = null
        var name = ""
        var maxY = 0
        var direct = false
        fun candidate() = path?.takeIf { direct && maxY > 0 }?.let { Touchscreen(it, name, maxY) }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("add device") -> {
                    candidate()?.let { return it }
                    path = trimmed.substringAfter(": ").trim()
                    name = ""
                    maxY = 0
                    direct = false
                }
                trimmed.startsWith("name:") -> name = trimmed.substringAfter("name:").trim().trim('"')
                "ABS_MT_POSITION_Y" in trimmed ->
                    maxY = Regex("max (\\d+)").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                "INPUT_PROP_DIRECT" in trimmed -> direct = true
            }
        }
        return candidate()
    }

    private companion object {
        const val TAG = "TriShotTouchWatcher"
        const val EVENT_SIZE = 24
        const val SWIPE_FRACTION = 0.09f
    }
}

/**
 * Multi-touch protocol B parser: tracks contacts per slot and fires once when exactly three
 * fingers move down together by [threshold] raw units.
 */
internal class SwipeDetector(private val threshold: Int, private val onSwipe: () -> Unit) {
    private val active = BooleanArray(MAX_SLOTS)
    private val ys = IntArray(MAX_SLOTS)
    private var slot = 0
    private var lastCount = 0
    private var armed = false
    private var blocked = false
    private var startY = 0

    fun feed(type: Int, code: Int, value: Int) {
        when {
            type == EV_ABS && code == ABS_MT_SLOT -> slot = value.coerceIn(0, MAX_SLOTS - 1)
            type == EV_ABS && code == ABS_MT_TRACKING_ID -> active[slot] = value >= 0
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
                startY = averageY()
            }
            count == 3 && armed && averageY() - startY > threshold -> {
                armed = false
                blocked = true
                onSwipe()
            }
        }
        lastCount = count
    }

    private fun averageY(): Int {
        var sum = 0L
        var n = 0
        for (i in 0 until MAX_SLOTS) if (active[i]) {
            sum += ys[i]
            n++
        }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    private companion object {
        const val MAX_SLOTS = 16
        const val EV_SYN = 0x00
        const val EV_ABS = 0x03
        const val SYN_REPORT = 0x00
        const val ABS_MT_SLOT = 0x2f
        const val ABS_MT_POSITION_Y = 0x36
        const val ABS_MT_TRACKING_ID = 0x39
    }
}
