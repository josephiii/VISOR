package ucf.visor.background

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ucf.visor.VisorApp

/**
 * Background listening: keeps VISOR in the foreground, as far as Android is
 * concerned, so it can hear wake phrases with the screen off or another app
 * open, and shows the notification Android requires while it does.
 *
 * It does no listening itself. VisorEngine listens, here or not; this only
 * tells it that it may go on with no screen showing. VisorEngine starts and
 * stops it to match Settings > Commands > "Keep listening with the screen off".
 *
 * Android only lets a microphone service start while VISOR is on screen
 * (Android 11+ "while-in-use" rules; Android 14 refuses outright), so it is
 * never restarted by the system ([START_NOT_STICKY]): VISOR starts it again the
 * next time it is opened.
 */
class ListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var inForeground = false

    private val engine get() = VisorApp.from(this).engine

    override fun onCreate() {
        super.onCreate()
        ListeningNotification.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // From the notification. Turning the switch off stops this service (VisorEngine).
            ACTION_STOP_LISTENING -> engine.stopBackgroundListening()
            ACTION_REPEAT_ANSWER -> engine.repeatLastAnswer()
            else -> if (!inForeground) startInForeground()
        }
        // An action for a notification left over from a service that has gone.
        if (!inForeground) stopSelf()
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val first = ListeningNotificationText.build(throughGlasses = false, answer = null, glasses = null)
        try {
            ServiceCompat.startForeground(
                this,
                ListeningNotification.ID,
                ListeningNotification.build(this, first),
                foregroundTypes(),
            )
        } catch (e: Exception) {
            // Not allowed from the background, or a permission is missing:
            // VISOR listens only while on screen, as with the switch off.
            Log.w(TAG, "Couldn't start background listening", e)
            stopSelf()
            return
        }
        inForeground = true
        engine.onServiceStarted()
        scope.launch {
            engine.notificationContent.collect { ListeningNotification.update(this@ListeningService, it) }
        }
    }

    /**
     * The microphone, and the glasses as a connected device when VISOR may
     * reach them (Bluetooth permission): the glasses' camera and microphone
     * are used from here too.
     */
    private fun foregroundTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) == PERMISSION_GRANTED) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return types
    }

    override fun onDestroy() {
        scope.cancel()
        if (inForeground) {
            inForeground = false
            engine.onServiceStopped()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VisorListeningService"
        const val ACTION_STOP_LISTENING = "ucf.visor.action.STOP_LISTENING"
        const val ACTION_REPEAT_ANSWER = "ucf.visor.action.REPEAT_ANSWER"

        /** Starts background listening. Only works while VISOR is on screen. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: VISOR was not on screen after all.
                Log.w(TAG, "Background listening can only start with VISOR on screen", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
