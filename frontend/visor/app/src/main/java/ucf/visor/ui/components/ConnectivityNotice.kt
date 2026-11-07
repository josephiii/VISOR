package ucf.visor.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.theme.VisorShapes

/**
 * Whether VISOR is offline, at the top of every screen, for as long as it is:
 * "Offline. Features that need the internet are locked until you reconnect."
 * Each locked feature's own button says the same where it is (see
 * capture/Availability).
 *
 * As the phone comes back online, "Back online. Every feature is available
 * again." takes its place for a few seconds, so the change can be seen, then
 * the notice goes and takes no room.
 *
 * Like [ErrorMessage], it never rests on color or its icon alone: it starts
 * with a word in bold, in text at full contrast inside an outline. It is a
 * polite live region, kept in place while empty, so a screen reader says each
 * change as it happens; with [talk], VISOR says it as well, for a user who
 * isn't looking at the screen, except while a screen reader is on to read it.
 * Only changes are said: a phone that is offline at launch shows the notice
 * without saying it.
 *
 * Not an error, and not dismissible: being offline is how things are, not
 * something that went wrong once.
 */
@Composable
fun ConnectivityNotice(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    talk: ((String, VoiceCategory) -> Unit)? = null,
) {
    val context = LocalContext.current
    val offlineSpoken = stringResource(R.string.offline_notice_spoken)
    val onlineSpoken = stringResource(R.string.online_again_spoken)

    // What the notice last showed, so only a real change is said.
    var shownOnline by remember { mutableStateOf(isOnline) }
    var showOnlineAgain by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (isOnline == shownOnline) return@LaunchedEffect
        shownOnline = isOnline
        if (talk != null && !context.isScreenReaderOn()) {
            talk(if (isOnline) onlineSpoken else offlineSpoken, VoiceCategory.STATUS_UPDATES)
        }
        if (isOnline) {
            showOnlineAgain = true
            delay(OnlineAgainMillis)
        }
        showOnlineAgain = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when {
            !isOnline -> NoticeRow(
                icon = Icons.Filled.CloudOff,
                label = stringResource(R.string.offline_notice_label),
                body = stringResource(R.string.offline_notice_body),
                modifier = modifier,
            )

            showOnlineAgain -> NoticeRow(
                icon = Icons.Filled.Cloud,
                label = stringResource(R.string.online_again_label),
                body = stringResource(R.string.online_again_body),
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun NoticeRow(icon: ImageVector, label: String, body: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, colors.outline, VisorShapes.Control)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurface,
            modifier = Modifier.size(iconSizeFor(style)),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
                append(" ")
                append(body)
            },
            style = style,
            color = colors.onSurface,
        )
    }
}

/** How long "Back online" stays up: long enough to read under a magnifier. */
private const val OnlineAgainMillis = 6_000L
