package ucf.visor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import ucf.visor.R
import ucf.visor.wearables.GlassesConnectionNotice as Notice

/**
 * What VISOR is doing about a lost link to the glasses, at the top of every
 * screen, under the offline notice: "Reconnecting to your glasses.",
 * "VISOR is restarting.", "Glasses reconnected." (for a few seconds) or
 * "Glasses not reachable. VISOR keeps trying…". Nothing while all is well, and
 * it takes no room then.
 *
 * Laid out like [ConnectivityNotice]: a bold word first, then the rest, at
 * full contrast inside an outline, never resting on its icon or color. Unlike
 * it, not a live region: VISOR already says each change aloud itself, screen
 * or not, so a screen reader would say it twice. It is one item to a screen
 * reader that moves onto it.
 */
@Composable
fun GlassesConnectionNotice(notice: Notice, modifier: Modifier = Modifier) {
    val shown = when (notice) {
        Notice.NONE -> return
        Notice.RECONNECTING -> Triple(
            Icons.Filled.BluetoothSearching,
            R.string.glasses_notice_reconnecting_label,
            R.string.glasses_notice_reconnecting_body,
        )
        Notice.RESTARTING -> Triple(
            Icons.Filled.RestartAlt,
            R.string.glasses_notice_restarting_label,
            R.string.glasses_notice_restarting_body,
        )
        Notice.RECONNECTED -> Triple(
            Icons.Filled.BluetoothConnected,
            R.string.glasses_notice_reconnected_label,
            R.string.glasses_notice_reconnected_body,
        )
        Notice.LOST -> Triple(
            Icons.Filled.BluetoothDisabled,
            R.string.glasses_notice_lost_label,
            R.string.glasses_notice_lost_body,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        NoticeRow(
            icon = shown.first,
            label = stringResource(shown.second),
            body = stringResource(shown.third),
            modifier = modifier,
        )
    }
}
