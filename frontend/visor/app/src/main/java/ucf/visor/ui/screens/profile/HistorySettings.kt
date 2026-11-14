package ucf.visor.ui.screens.profile

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.HistoryEntry
import ucf.visor.data.ResponseHistory
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.components.ReadingControlHeight
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.VisorDialog
import ucf.visor.ui.components.iconSizeFor
import ucf.visor.ui.icon
import ucf.visor.ui.labelRes
import ucf.visor.ui.theme.VisorShapes
import java.time.ZoneId
import java.util.Date

/**
 * "History": what VISOR has answered today, newest first, each to read or hear
 * again. Home's Last answer card keeps only the latest; this is for the one
 * before it, missed on a noisy street, or the bus stop's sign read an hour ago.
 *
 * Each answer is a card of its own, with the feature that gave it and when
 * as its heading, so a screen reader can jump from answer to answer by
 * heading. The whole answer is shown, not cut short: on a page that already
 * scrolls, a box that scrolls inside it is hard to use with a magnifier.
 *
 * Read aloud says it at the Answers volume, as Home's Read again does; a long
 * one can then be paused and stopped from the controls at the bottom of the
 * screen. Clear history asks first.
 *
 * Only today's, as [ResponseHistory] keeps them: if VISOR is left open past
 * midnight, yesterday's answers leave this page as it is next drawn.
 */
@Composable
internal fun HistorySettings(
    entries: List<HistoryEntry>,
    talk: (String, VoiceCategory) -> Unit,
    onClear: () -> Unit,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    // At most 50 to look through, so worked out afresh each time it is drawn.
    val today = ResponseHistory.today(entries, System.currentTimeMillis(), ZoneId.systemDefault())

    Text(stringResource(R.string.history_description), style = MaterialTheme.typography.bodyMedium)

    if (today.isEmpty()) {
        // A polite live region, so a screen reader says it as History is cleared.
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else {
        Text(
            pluralStringResource(R.plurals.history_count, today.size, today.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (today.size >= ResponseHistory.MaxEntries) {
            Text(
                stringResource(R.string.history_count_limit, ResponseHistory.MaxEntries),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        today.forEach { entry ->
            key(entry.atMillis, entry.command) {
                HistoryCard(entry, onReadAloud = { talk(it, VoiceCategory.ANSWERS) })
            }
        }
        SwitchButton(
            label = stringResource(R.string.history_clear),
            isDestructive = true,
            icon = Icons.Filled.DeleteSweep,
            onClick = { confirmingClear = true },
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (confirmingClear) {
        VisorDialog(
            title = stringResource(R.string.history_clear_confirm_title),
            message = stringResource(R.string.history_clear_confirm_message),
            confirmLabel = stringResource(R.string.history_clear_confirm_button),
            confirmIcon = Icons.Filled.DeleteSweep,
            destructive = true,
            onConfirm = { confirmingClear = false; onClear() },
            onDismiss = { confirmingClear = false },
            talk = talk,
        )
    }
}

/**
 * One answer: which feature gave it and when (with the feature's icon, so it
 * never rests on the words alone), the question for "Ask a question", the
 * answer, and Read aloud under it.
 */
@Composable
private fun HistoryCard(entry: HistoryEntry, onReadAloud: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(entry.atMillis))
    val source = stringResource(entry.command.labelRes)
    // Read without the "·", which a screen reader would say aloud.
    val spokenSource = stringResource(R.string.home_last_answer_from_spoken, source, time)
    val headerStyle = MaterialTheme.typography.titleMedium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, VisorShapes.Control)
            .border(2.dp, colors.outline, VisorShapes.Control)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                heading()
                contentDescription = spokenSource
            },
        ) {
            Icon(
                imageVector = entry.command.icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(iconSizeFor(headerStyle)),
            )
            Text(
                stringResource(R.string.home_last_answer_from, source, time),
                style = headerStyle,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
        }
        // The question and its answer, one stop for a screen reader.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            entry.question?.let { question ->
                Text(
                    stringResource(R.string.history_question, question),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
            }
            Text(entry.text, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        }
        val readAloudName = stringResource(R.string.history_read_aloud_named, source, time)
        SwitchButton(
            label = stringResource(R.string.history_read_aloud),
            outlined = true,
            icon = Icons.Filled.PlayArrow,
            onClick = { onReadAloud(entry.text) },
            textStyle = MaterialTheme.typography.titleMedium,
            minHeight = ReadingControlHeight,
            // Every card has one, so a screen reader names whose it is.
            modifier = Modifier.semantics { contentDescription = readAloudName },
        )
    }
}
