package ucf.visor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.tts.ReadingState
import ucf.visor.ui.theme.VisorTheme

/**
 * Pause (or Resume) and Stop for a long answer VISOR is reading aloud, along
 * the bottom of the screen, just above the navigation bar, from the moment a
 * reading starts until it ends or is stopped. Help's Read aloud, an answer
 * to a wake phrase and the like can start one from any screen.
 *
 * The answer on Home's Last answer card has these same buttons in the card
 * itself, under its text, so this bar isn't shown on Home for that one
 * (VisorLayout): it is for a reading with no card of its own on screen.
 *
 * No line saying "Paused" or "Reading aloud": Pause or Resume on the first
 * button already says which, in the same place, and a second way of saying
 * it would only cost room above the screen.
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

    Surface(color = colors.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            // Sets the controls apart from the screen above them.
            HorizontalDivider(thickness = 2.dp, color = colors.outline)
            ReadingButtons(
                paused = state == ReadingState.PAUSED,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Pause (or Resume, when [paused]) and Stop, side by side: the controls for a
 * reading wherever they are shown, at the bottom of the screen
 * ([ReadingControlsBar]) or in Home's Last answer card.
 *
 * Pause and Resume are one button, so a second tap where the first one landed
 * undoes it. Each is labeled in words beside its symbol, unless the longest
 * of the words (any of Pause, Resume or Stop, so the buttons never change as
 * a reading is paused) would wrap at the text size in use: then both show
 * their symbols alone, larger, which stay readable at any size, rather than
 * a word broken over two lines in a taller button. Either way, a screen
 * reader and voice access name each one in words ("Pause reading").
 */
@Composable
fun ReadingButtons(
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.titleMedium
    val pauseResumeName = stringResource(if (paused) R.string.reading_resume_named else R.string.reading_pause_named)
    val stopName = stringResource(R.string.reading_stop_named)
    val labels = listOf(
        stringResource(R.string.reading_pause),
        stringResource(R.string.reading_resume),
        stringResource(R.string.reading_stop),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val iconRoom = iconSizeFor(style) + SwitchButtonIconGap

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // What a button has for its word: its half of the row, less its
        // padding and the symbol before the word.
        val padding = ButtonDefaults.ContentPadding
        val buttonWidth = (maxWidth - ReadingButtonGap) / 2
        val forWord = buttonWidth - padding.calculateLeftPadding(LayoutDirection.Ltr) -
            padding.calculateRightPadding(LayoutDirection.Ltr) - iconRoom
        val widest = labels.maxOf { measurer.measure(it, style).size.width }
        // A little to spare, so a word that only just fits isn't wrapped after all.
        val showWords = with(density) { widest.toDp() } + 2.dp <= forWord

        Row(horizontalArrangement = Arrangement.spacedBy(ReadingButtonGap), modifier = Modifier.fillMaxWidth()) {
            SwitchButton(
                label = stringResource(if (paused) R.string.reading_resume else R.string.reading_pause),
                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                onClick = if (paused) onResume else onPause,
                textStyle = style,
                minHeight = ReadingControlHeight,
                showLabel = showWords,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = pauseResumeName },
            )
            SwitchButton(
                label = stringResource(R.string.reading_stop),
                icon = Icons.Filled.Stop,
                outlined = true,
                onClick = onStop,
                textStyle = style,
                minHeight = ReadingControlHeight,
                showLabel = showWords,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = stopName },
            )
        }
    }
}

/** Taller than the usual 56dp: these are pressed in a hurry, often without looking closely. */
val ReadingControlHeight = 64.dp

private val ReadingButtonGap = 12.dp

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

@Preview(fontScale = 2f)
@Composable
private fun ReadingControlsBarLargeTextPreview() {
    VisorTheme {
        ReadingControlsBar(ReadingState.PAUSED, onPause = {}, onResume = {}, onStop = {})
    }
}
