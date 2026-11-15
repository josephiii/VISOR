package ucf.visor.ui.screens.profile

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ucf.visor.R
import ucf.visor.data.VisionPlan
import ucf.visor.data.VisionType
import ucf.visor.data.label
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.labelRes

/**
 * What VISOR does differently for this user (data/VisionPlan), in the words
 * setup's last step says aloud and Settings > About you shows: one "Because
 * you…" line for each way it is tailored, then their first Home button when
 * it isn't the usual one. Empty when there is nothing to tailor to.
 */
fun visionFitLines(context: Context, plan: VisionPlan): List<String> = buildList {
    plan.tailorings.forEach { add(context.getString(it.textRes)) }
    val first = plan.featureOrder.first()
    if (first != CameraCommand.entries.first()) {
        add(context.getString(R.string.tailoring_first_button, context.getString(first.labelRes)))
    }
}

@get:StringRes
private val VisionPlan.Tailoring.textRes: Int
    get() = when (this) {
        VisionPlan.Tailoring.AHEAD_FIRST -> R.string.tailoring_ahead_first
        VisionPlan.Tailoring.SIDES_FIRST -> R.string.tailoring_sides_first
        VisionPlan.Tailoring.READ_DETAILS -> R.string.tailoring_read_details
        VisionPlan.Tailoring.CONTRAST_AND_LIGHT -> R.string.tailoring_contrast_and_light
        VisionPlan.Tailoring.SEVERE_MORE_SAFETY -> R.string.tailoring_severe_more_safety
        VisionPlan.Tailoring.MILD_SKIP_OBVIOUS -> R.string.tailoring_mild_skip_obvious
        VisionPlan.Tailoring.OWN_WORDS -> R.string.tailoring_own_words
    }

/**
 * A kind of vision loss as VISOR says it in a sentence: "trouble seeing the
 * sides", and "contrast and light sensitivity" rather than reading out a slash.
 */
fun VisionType.spokenLabel(): String = label().lowercase().replace(" / ", " and ")

/**
 * [visionFitLines], one to a row with a bullet, or [R.string.tailoring_none]
 * when there are none. A screen reader reads each line as one stop, without
 * the bullet.
 */
@Composable
fun VisionFitSummary(plan: VisionPlan, modifier: Modifier = Modifier) {
    val lines = visionFitLines(LocalContext.current, plan)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (lines.isEmpty()) {
            Text(
                stringResource(R.string.tailoring_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        lines.forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { contentDescription = line },
            ) {
                Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
                Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
