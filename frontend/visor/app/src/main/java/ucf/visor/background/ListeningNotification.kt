package ucf.visor.background

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
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
 * Background listening's notification, which Android requires while VISOR
 * listens with the screen off. Its words come from [ListeningNotificationText];
 * this shows them the accessible way:
 *
 * - The **standard template** only, no custom layout, so it follows the phone's
 *   font size, display size, contrast and dark theme, and TalkBack reads it in
 *   a predictable order: title, text, actions.
 * - **Silent, low importance, alerting once.** It never makes a sound, buzzes
 *   or pops up, and TalkBack does not read each update aloud over VISOR's own
 *   voice, which already said the answer.
 * - **Private on the lock screen** ([NotificationCompat.VISIBILITY_PRIVATE] with
 *   a public version that holds no answer).
 * - **Actions with text labels**, "Repeat answer" and "Stop listening", large
 *   targets TalkBack names in full. Tapping the notification opens VISOR.
 */
object ListeningNotification {

    const val ID = 4100
    private const val CHANNEL = "background_listening"

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.notification_channel_listening))
            .setDescription(context.getString(R.string.notification_channel_listening_description))
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setSound(null, null)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context, content: ListeningNotificationText.Content): Notification {
        val public = base(context)
            .setContentTitle(content.publicTitle)
            .setContentText(content.publicText)
            .build()

        val builder = base(context)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setSubText(content.subText)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)

        // A time stamp only for an answer: "2 min ago" says how old it is.
        val answeredAt = content.answeredAtMillis
        if (answeredAt != null) {
            builder.setWhen(answeredAt).setShowWhen(true)
            builder.addAction(
                0,
                context.getString(R.string.notification_action_repeat_answer),
                serviceAction(context, ListeningService.ACTION_REPEAT_ANSWER, REQUEST_REPEAT),
            )
        } else {
            builder.setShowWhen(false)
        }
        builder.addAction(
            0,
            context.getString(R.string.notification_action_stop_listening),
            serviceAction(context, ListeningService.ACTION_STOP_LISTENING, REQUEST_STOP),
        )
        return builder.build()
    }

    /** Shows [content] in place of what the notification said before. */
    fun update(context: Context, content: ListeningNotificationText.Content) {
        // Without the permission (Android 13+), Android keeps the notification
        // out of the shade; VISOR still listens, and says how to stop it.
        if (ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(ID, build(context, content))
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_listening)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openVisor(context))

    private fun openVisor(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceAction(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            context,
            request,
            Intent(context, ListeningService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val REQUEST_OPEN = 0
    private const val REQUEST_REPEAT = 1
    private const val REQUEST_STOP = 2
}
