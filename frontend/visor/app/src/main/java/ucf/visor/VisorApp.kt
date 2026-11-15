package ucf.visor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import ucf.visor.engine.VisorEngine
import ucf.visor.engine.VisorState
import ucf.visor.wearables.GlassesLinkNudge

/**
 * VISOR's process. Holds the two things that must outlive any screen: what
 * VISOR knows ([state]) and what listens and speaks ([engine]), so VISOR can
 * keep answering wake phrases with the screen off (background listening),
 * and a screen that is closed and opened again finds them as they were.
 */
class VisorApp : Application() {

    val state: VisorState by lazy { VisorState(this) }

    /** Built the first time a screen or the listening service needs it: it loads the wake-phrase model. */
    val engine: VisorEngine by lazy { VisorEngine(this, state) }

    // The Meta Wearables SDK registers its Bluetooth listener through this
    // context. VISOR notes it, to make the SDK reconnect to glasses it has
    // written off (GlassesLinkNudge). Every receiver is still registered as asked.

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? =
        super.registerReceiver(receiver, filter).also { GlassesLinkNudge.onRegistered(receiver) }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter, flags: Int): Intent? =
        super.registerReceiver(receiver, filter, flags).also { GlassesLinkNudge.onRegistered(receiver) }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? = super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        .also { GlassesLinkNudge.onRegistered(receiver) }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int,
    ): Intent? = super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
        .also { GlassesLinkNudge.onRegistered(receiver) }

    override fun unregisterReceiver(receiver: BroadcastReceiver) {
        GlassesLinkNudge.onUnregistered(receiver)
        super.unregisterReceiver(receiver)
    }

    companion object {
        fun from(context: Context): VisorApp = context.applicationContext as VisorApp
    }
}
