package pl.arixelo.trishot.shizuku

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuAccess {
    private const val PACKAGE = "moe.shizuku.privileged.api"

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess

    /** Opens Shizuku's Google Play page (or the web page if Play isn't available). */
    fun openPlayStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
            .setPackage("com.android.vending")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(web)
        }
    }

    /** Opens the Shizuku app so the user can start its service. */
    fun openApp(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let(context::startActivity)
    }

    /** Shizuku is running and reachable. */
    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = isRunning() && !Shizuku.isPreV11() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
}

/**
 * Keeps a [TouchWatcher] running in Shizuku's shell process while started, and calls
 * [onSwipe] on the main thread for every three-finger swipe down. Reconnects whenever
 * Shizuku (re)starts.
 */
class ShizukuTouchSession(context: Context, private val onSwipe: () -> Unit) {

    private val main = Handler(Looper.getMainLooper())

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, TouchWatcher::class.java.name))
        .daemon(false)
        .processNameSuffix("touch")
        // A new APK must replace the old watcher process, so tie the version to the install.
        .version((context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime / 1000).toInt())

    private val listener = object : ISwipeListener.Stub() {
        override fun onSwipeDown() {
            main.post(onSwipe)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val status = runCatching { ITouchWatcher.Stub.asInterface(binder).start(listener) }
            Log.i(TAG, "touch watcher: ${status.getOrElse { "failed: $it" }}")
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { bind() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        if (ShizukuAccess.isRunning()) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }

    private fun bind() {
        if (!ShizukuAccess.hasPermission()) {
            Log.w(TAG, "Shizuku permission missing")
            return
        }
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { Log.w(TAG, "bindUserService failed", it) }
    }

    private companion object {
        const val TAG = "TriShotShizuku"
    }
}
