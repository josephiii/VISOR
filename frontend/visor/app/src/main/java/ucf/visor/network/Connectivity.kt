package ucf.visor.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow

/**
 * Whether the phone is online: on a network that reaches the internet.
 * VISOR's server features (describing a scene, answering a question, checking
 * for hazards, and logging in) need it; everything else works without it.
 *
 * "Online" means Android has checked the network really reaches the internet
 * (NET_CAPABILITY_VALIDATED), not only that one is connected. A clinic or
 * hotel Wi-Fi that wants a sign-in page first, or that drops outside traffic,
 * shows as connected, and each server feature would wait out its 10-second
 * timeout there before apologizing. Here it counts as offline, and VISOR says
 * so before anyone presses anything.
 *
 * A backend on the same Wi-Fi as the phone, with no internet behind it, shows
 * as offline too. For developing against such a backend, use the emulator,
 * whose network always reaches the internet.
 */
class Connectivity(context: Context) {

    private val manager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    /** Whether the phone is online right now, without waiting for a callback. */
    fun isOnlineNow(): Boolean {
        val manager = manager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.reachesInternet() == true
    }

    /**
     * Each time the phone goes online or offline, once it has stayed that way
     * for a moment (see [settled]). Starts with the state at the time it is
     * collected.
     */
    fun changes(): Flow<Boolean> {
        val manager = manager ?: return emptyFlow()
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(capabilities.reachesInternet())
                }

                // A new default network, if there is one, follows with its own capabilities.
                override fun onLost(network: Network) {
                    trySend(false)
                }
            }
            try {
                manager.registerDefaultNetworkCallback(callback)
            } catch (e: RuntimeException) {
                // Only without ACCESS_NETWORK_STATE, which the manifest asks for.
                Log.w("VISOR", "Can't watch the network connection", e)
                close()
                return@callbackFlow
            }
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.settled()
    }
}

private fun NetworkCapabilities.reachesInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

/** How long the phone must stay offline before VISOR says it is. */
const val OFFLINE_GRACE_MS = 3_000L

/** How long the phone must stay online again before VISOR says it is. */
const val ONLINE_SETTLE_MS = 1_000L

/**
 * Online and offline as they settle, not as they flicker. Moving from Wi-Fi to
 * mobile data, or walking past the edge of a Wi-Fi network, can drop the
 * connection for a second or two; announcing each drop, and locking buttons
 * under a finger, would be a nuisance for no reason. So a change counts once
 * it has lasted [OFFLINE_GRACE_MS] (going offline) or [ONLINE_SETTLE_MS]
 * (coming back), and the same state twice in a row is passed on only once.
 */
@OptIn(FlowPreview::class)
fun Flow<Boolean>.settled(): Flow<Boolean> =
    distinctUntilChanged()
        .debounce { online -> if (online) ONLINE_SETTLE_MS else OFFLINE_GRACE_MS }
        .distinctUntilChanged()
