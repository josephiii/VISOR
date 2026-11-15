package ucf.visor.wearables

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.meta.wearable.acdc.sdk.state.ConnectivityIndicator
import com.meta.wearable.acdc.sdk.state.TransportEvent
import com.meta.wearable.acdc.sdk.state.broadcast.BluetoothConnectivityIndicatorBroadcastReceiver

/**
 * Gets the Meta Wearables SDK to reconnect to glasses it has written off,
 * without restarting VISOR, so it works with the screen off too.
 *
 * **Why it is needed.** On MWDAT 1.0.0, when a camera stream stops, the
 * Meta AI app can take the glasses' Bluetooth data channel from under the
 * SDK. The SDK then marks the glasses disconnected and never tries again by
 * itself (see GlassesRecovery). It only reconnects when Android reports a
 * Bluetooth change, and none comes, because the glasses never left the phone.
 *
 * **What this does.** The SDK hears Android's Bluetooth changes through its
 * `BluetoothConnectivityIndicatorBroadcastReceiver`, which passes each one to a
 * `ConnectivityIndicator`. Every event the indicator passes on for a device
 * (mwdat-core 1.0.0 bytecode: `DeviceConnectivityStateMachine.updateBLE`,
 * then `MetaWearable.bleConnectionJob`, then `LinkConnectionJob`) makes the SDK
 * try the glasses' Bluetooth LE link again, whatever the event says. So
 * VISOR tells the indicator "connected" for each device it tracks, the same
 * call a real Android broadcast would lead to, and the SDK reconnects.
 *
 * Checked again on MWDAT 1.0.1: its `mwdat-core` has the same classes, and
 * these two classes the same fields, as 1.0.0's.
 *
 * VISOR finds the receiver as the SDK registers it (`VisorApp.registerReceiver`
 * passes every receiver to [onRegistered]). It reads two private fields, the
 * indicator and its device map, by their types. If a later SDK changes them,
 * [nudge] finds nothing, logs it, and VISOR falls back to restarting itself.
 *
 * Only call it while the SDK has lost the glasses: when the link is up, a
 * fresh connection attempt is not wanted.
 */
object GlassesLinkNudge {

    private const val TAG = "VisorGlassesNudge"

    @Volatile
    private var receiver: BroadcastReceiver? = null

    /** Every receiver VISOR's process registers; keeps the SDK's Bluetooth one. */
    fun onRegistered(registered: BroadcastReceiver?) {
        if (registered is BluetoothConnectivityIndicatorBroadcastReceiver) {
            receiver = registered
            Log.i(TAG, "Found the SDK's Bluetooth listener")
        }
    }

    fun onUnregistered(unregistered: BroadcastReceiver?) {
        if (unregistered != null && unregistered === receiver) receiver = null
    }

    /** Whether VISOR can nudge the SDK: it has registered its Bluetooth listener and its fields can be read. */
    val isAvailable: Boolean get() = indicator() != null

    /**
     * Tells the SDK each glasses it tracks is connected, so it reconnects to
     * them. Returns how many it told, 0 when it couldn't reach the SDK.
     */
    fun nudge(context: Context): Int {
        val indicator = indicator() ?: return 0.also {
            Log.w(TAG, "Can't reach the SDK's Bluetooth listener to reconnect the glasses")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return 0
        var told = 0
        for (address in trackedAddresses(indicator)) {
            runCatching {
                indicator.onDeviceBLEChange(adapter.getRemoteDevice(address), TransportEvent.CONNECTED)
                told++
            }.onFailure { Log.w(TAG, "Couldn't nudge the SDK about one device", it) }
        }
        Log.i(TAG, "Asked the SDK to reconnect to $told device(s)")
        return told
    }

    private fun indicator(): ConnectivityIndicator? {
        val found = receiver ?: return null
        return runCatching {
            BluetoothConnectivityIndicatorBroadcastReceiver::class.java.declaredFields
                .firstOrNull { ConnectivityIndicator::class.java.isAssignableFrom(it.type) }
                ?.apply { isAccessible = true }
                ?.get(found) as? ConnectivityIndicator
        }.getOrNull()
    }

    /** The Bluetooth LE addresses of the devices the SDK tracks: its device map's keys. */
    private fun trackedAddresses(indicator: ConnectivityIndicator): List<String> = runCatching {
        val fields = ConnectivityIndicator::class.java.declaredFields
        val map = fields.firstOrNull { Map::class.java.isAssignableFrom(it.type) }
            ?.apply { isAccessible = true }
            ?.get(indicator) as? Map<*, *>
            ?: return@runCatching emptyList()
        // The indicator guards the map with its own lock (a plain Object field).
        val lock = fields.firstOrNull { it.type == Any::class.java }?.apply { isAccessible = true }?.get(indicator)
        val keys = if (lock != null) synchronized(lock) { map.keys.toList() } else map.keys.toList()
        keys.filterIsInstance<String>()
    }.getOrElse {
        Log.w(TAG, "Couldn't read the SDK's devices", it)
        emptyList()
    }
}
