package ucf.visor.ui.components

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.theme.VisorShapes

/**
 * Where something that went wrong is shown: [message], or nothing while it is
 * null. It is said in a way that never rests on color alone (Section 508:
 * "don't just make them red"): an error icon, the word "Error:", and an
 * outline in the error color around text at full contrast.
 *
 * It stays in place, empty and taking no room, while there is no message, so
 * a screen reader announces a message as it appears in it: it is a polite live
 * region, and a live region that only appeared with its message could go
 * unannounced. With [talk], VISOR also says each message, for a user who isn't
 * looking at the screen, except while a screen reader is on to read it.
 *
 * @param modifier applies to the message as shown, such as the space above it.
 */
@Composable
fun ErrorMessage(
    message: String?,
    modifier: Modifier = Modifier,
    talk: ((String, VoiceCategory) -> Unit)? = null,
) {
    val prefix = stringResource(R.string.error_prefix)
    val context = LocalContext.current
    if (talk != null && message != null) {
        LaunchedEffect(message) {
            if (!context.isScreenReaderOn()) talk("$prefix $message", VoiceCategory.ANSWERS)
        }
    }

    val style = MaterialTheme.typography.bodyLarge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (message != null) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.error, VisorShapes.Control)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(iconSizeFor(style)),
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(prefix) }
                        append(" ")
                        append(message)
                    },
                    style = style,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Whether a screen reader such as TalkBack is reading the screen, so VISOR's
 * own voice need not say again what the screen reader will.
 */
fun Context.isScreenReaderOn(): Boolean =
    getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
