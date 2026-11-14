package ucf.visor.background

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ucf.visor.MainActivity
import ucf.visor.R

/**
 * "VISOR is restarting": shown while VISOR restarts itself to reconnect to
 * the glasses, and through the restart, since an ordinary notification
 * outlives VISOR's process (the listening notification goes with it). The
 * relaunched VISOR takes it away once it knows whether the glasses are back.
 *
 * Accessible the same way as the listening notification: the standard
 * template, words rather than an icon alone, and silent, because VISOR has
 * already said it aloud. Nothing private, so it shows on the lock screen. It
 * takes itself away after [TIMEOUT_MS] if a restart never finishes.
 */
object GlassesRestartNotification {

    private const val ID = 4101
    private const val CHANNEL = "glasses_connection"
    private const val TIMEOUT_MS = 90_000L

    fun show(context: Context) {
        if (ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_glasses_connection))
                .setDescription(context.getString(R.string.notification_channel_glasses_connection_description))
                .setShowBadge(false)
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_listening)
            .setContentTitle(context.getString(R.string.notification_restarting_title))
            .setContentText(context.getString(R.string.notification_restarting_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_restarting_text)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setTimeoutAfter(TIMEOUT_MS)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        manager.notify(ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }
}
