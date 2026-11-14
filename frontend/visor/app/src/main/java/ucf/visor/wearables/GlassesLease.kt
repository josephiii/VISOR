package ucf.visor.wearables

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ucf.visor.wearables.GlassesLease.holder

/**
 * Who may hold a DAT `DeviceSession` on the glasses right now.
 *
 * MWDAT allows one session per device (a second `createSession` fails with
 * `SESSION_ALREADY_EXISTS`), and several VISOR features want one: the glasses
 * camera (a session held while VISOR is in use, see HeldGlassesSession), the
 * always-on glasses tap navigation (a Display session) and the head-motion lab
 * (a camera + motion session for the length of a trial). The navigation
 * controller watches [holder], steps aside while anyone else holds it, and
 * reconnects when it is released.
 *
 * A holder can take the lease *yielding* ([acquireYielding]): it holds the
 * glasses only until someone else asks, as the camera's held session does, so
 * the lab can still run a trial. A plain [acquire] then asks it to let go and
 * takes over; the yielding holder's session stops asynchronously, which the
 * new holder's createSession retries wait out (SESSION_ALREADY_EXISTS).
 *
 * A process-wide object rather than something injected, because both sides
 * already live for the process and the only state is who holds the lease.
 */
object GlassesLease {

    private val _holder = MutableStateFlow<String?>(null)

    /** The current exclusive holder, or null when the glasses are free. */
    val holder: StateFlow<String?> = _holder.asStateFlow()

    /** The yielding holder, and how to ask it to let go. */
    private var yielding: Pair<String, () -> Unit>? = null

    /**
     * Takes the lease for [owner]. Returns false if someone else holds it and
     * will not give way. A yielding holder is asked to let go first.
     */
    @Synchronized
    fun acquire(owner: String): Boolean {
        if (_holder.value == owner) return true
        val giving = yielding
        if (giving != null && _holder.value == giving.first) {
            yielding = null
            // The new owner holds the glasses before the old one hears it must
            // let go: its letting go can run at once (on the main thread) and
            // would otherwise find the lease free and take it straight back.
            _holder.value = owner
            giving.second()
            return true
        }
        return _holder.compareAndSet(null, owner)
    }

    /**
     * Takes the lease for [owner] until another feature asks for it, when
     * [onYield] is called and the lease passes on.
     */
    @Synchronized
    fun acquireYielding(owner: String, onYield: () -> Unit): Boolean {
        if (_holder.value != owner && !_holder.compareAndSet(null, owner)) return false
        yielding = owner to onYield
        return true
    }

    /** Gives the lease back — only if [owner] is the one holding it. */
    @Synchronized
    fun release(owner: String) {
        if (yielding?.first == owner) yielding = null
        _holder.compareAndSet(owner, null)
    }
}
