package pl.arixelo.trishot.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import pl.arixelo.trishot.MainActivity
import pl.arixelo.trishot.R

/** Special values of the "taken ever" counter, each with its own message. */
object Milestones {
    private const val CHANNEL = "milestones"

    private val messages = mapOf(
        69 to R.string.milestone_69,
        420 to R.string.milestone_420,
        1337 to R.string.milestone_1337,
        2137 to R.string.milestone_2137,
    )

    @StringRes
    fun messageFor(total: Int): Int? = messages[total]

    /** Posts the milestone notification, or shows it as a toast when notifications are off. */
    fun announce(context: Context, total: Int, @StringRes message: Int) {
        val title = context.resources.getQuantityString(R.plurals.milestone_title, total, total)
        val text = context.getString(message)
        if (!canNotify(context)) {
            Toast.makeText(context, "$title $text", Toast.LENGTH_LONG).show()
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_milestones), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(total, notification)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
