package ucf.visor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.tts.ReadingState
import ucf.visor.ui.theme.VisorTheme

/**
 * Pause (or Resume) and Stop for a long answer VISOR is reading aloud, along
 * the bottom of every screen, just above the navigation bar, from the moment
 * a reading starts until it ends or is stopped. Help's Read aloud, Home's Read
 * again and an answer to a wake phrase can all start one from any screen, so
 * the controls are always in the same place, within reach of a thumb.
 *
 * Pause and Resume are one button, so a second tap where the first one landed
 * undoes it. Both buttons are as large as Home's other buttons, labeled in
 * words as well as icons, and say what they act on to a screen reader
 * ("Pause reading"). The line above them says whether VISOR is reading or
 * paused.
 *
 * Nothing here is a live region: VISOR is talking whenever this is shown, and
 * a screen reader talking over it would make both hard to follow.
 */
@Composable
fun ReadingControlsBar(
    state: ReadingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == ReadingState.NONE) return
    val colors = MaterialTheme.colorScheme
    val paused = state == ReadingState.PAUSED
    val statusStyle = MaterialTheme.typography.titleMedium
    val buttonStyle = MaterialTheme.typography.titleMedium

    val pauseResumeName = stringResource(if (paused) R.string.reading_resume_named else R.string.reading_pause_named)
    val stopName = stringResource(R.string.reading_stop_named)

    Surface(color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            // Sets the controls apart from the screen above them.
            HorizontalDivider(thickness = 2.dp, color = colors.outline)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.Pause else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(iconSizeFor(statusStyle)),
                    )
                    Text(
                        stringResource(if (paused) R.string.reading_status_paused else R.string.reading_status_reading),
                        style = statusStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchButton(
                        label = stringResource(if (paused) R.string.reading_resume else R.string.reading_pause),
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        onClick = if (paused) onResume else onPause,
                        textStyle = buttonStyle,
                        minHeight = ControlHeight,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = pauseResumeName },
                    )
                    SwitchButton(
                        label = stringResource(R.string.reading_stop),
                        icon = Icons.Filled.Stop,
                        outlined = true,
                        onClick = onStop,
                        textStyle = buttonStyle,
                        minHeight = ControlHeight,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = stopName },
                    )
                }
            }
        }
    }
}

// Taller than the usual 56dp: these are pressed in a hurry, often without looking closely.
private val ControlHeight = 64.dp

@Preview
@Composable
private fun ReadingControlsBarPreview() {
    VisorTheme {
        Column {
            ReadingControlsBar(ReadingState.READING, onPause = {}, onResume = {}, onStop = {})
            Spacer(modifier = Modifier.height(16.dp))
            ReadingControlsBar(ReadingState.PAUSED, onPause = {}, onResume = {}, onStop = {})
        }
    }
}
