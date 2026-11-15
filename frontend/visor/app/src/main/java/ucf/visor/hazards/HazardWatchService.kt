package ucf.visor.hazards

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.os.IBinder
import android.util.Log
import ucf.visor.MainActivity
import ucf.visor.R

/**
 * Keeps VISOR running while the hazard watch is on, screen off included, with
 * an ongoing notification ("VISOR hazard watch is on") that opens VISOR, and a
 * Stop button that ends the watch from the lock screen.
 *
 * The watch itself runs in MainActivity (HazardWatch); this only keeps the
 * process in the foreground, and passes a Stop tap back through [onStopRequested].
 */
class HazardWatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        val type =
            if (intent?.getBooleanExtra(EXTRA_PHONE_CAMERA, false) == true) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        try {
            startForeground(NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            // Not allowed right now (started from the background, or a permission
            // missing): the watch still runs while VISOR is on screen.
            Log.w(TAG, "Could not keep the hazard watch in the foreground", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hazard_watch_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HazardWatchService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.hazard_watch_notification_title))
            .setContentText(getString(R.string.hazard_watch_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.hazard_watch_notification_stop), stop).build())
            .build()
    }

    companion object {
        private const val TAG = "VisorHazardWatch"
        private const val CHANNEL_ID = "hazard_watch"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "ucf.visor.hazards.STOP"
        private const val EXTRA_PHONE_CAMERA = "phoneCamera"

        /** The notification's Stop button was tapped. Set by MainActivity. */
        var onStopRequested: (() -> Unit)? = null

        fun start(context: Context, phoneCamera: Boolean) {
            // Android refuses a camera or connected-device foreground service
            // without its permission, and a service started but never put in the
            // foreground crashes VISOR. Without it, the watch runs while VISOR is on screen.
            val needed = if (phoneCamera) Manifest.permission.CAMERA else Manifest.permission.BLUETOOTH_CONNECT
            if (context.checkSelfPermission(needed) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "No $needed: the hazard watch runs only while VISOR is on screen")
                return
            }
            try {
                context.startForegroundService(
                    Intent(context, HazardWatchService::class.java).putExtra(EXTRA_PHONE_CAMERA, phoneCamera),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the hazard watch service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HazardWatchService::class.java))
        }
    }
}
