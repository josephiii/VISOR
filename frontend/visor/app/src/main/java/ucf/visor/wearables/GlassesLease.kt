package ucf.visor.wearables

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ucf.visor.wearables.GlassesLease.holder

/**
 * Who may hold a DAT `DeviceSession` on the glasses right now.
 *
 * MWDAT allows one session per device (a second `createSession` fails with
 * `SESSION_ALREADY_EXISTS`), and VISOR has two features that want one: the
 * always-on glasses tap navigation (a Display session) and the head-motion
 * lab (a camera + motion session for the length of a trial). The lab takes the
 * lease for a trial; the navigation controller watches [holder], steps aside
 * while anyone else holds it, and reconnects when it is released.
 *
 * A process-wide object rather than something injected, because both sides
 * already live for the process and the only state is who holds the lease.
 */
object GlassesLease {

    private val _holder = MutableStateFlow<String?>(null)

    /** The current exclusive holder, or null when the glasses are free. */
    val holder: StateFlow<String?> = _holder.asStateFlow()

    /** Takes the lease for [owner]. Returns false if someone else holds it. */
    fun acquire(owner: String): Boolean =
        _holder.compareAndSet(null, owner) || _holder.value == owner

    /** Gives the lease back — only if [owner] is the one holding it. */
    fun release(owner: String) {
        _holder.compareAndSet(owner, null)
    }
}
