package ucf.visor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ucf.visor.R
import ucf.visor.ui.theme.VisorShapes

// Tracking is expressed in em rather than sp so it stays proportional as the
// wordmark grows with the user's text-size preference.
private const val WordmarkTracking = 0.2f

// How much of the banner's width the rule under the wordmark spans.
private const val RuleWidthFraction = 0.4f

// The mark is drawn a little taller than the wordmark's cap height so it holds
// its own beside it rather than reading as a bullet above the name.
private const val MarkHeightToWordmark = 1.15f

/**
 * VISOR's banner: the brand mark, the wordmark, and the acronym the name stands
 * for, framed as one plate. Used across the title, onboarding, login and home
 * screens, so it is the thing that tells a user at a glance which app they are
 * in and which screen family they are on.
 *
 * Everything here is real text and real vector drawing rather than a bitmap
 * logo, which is what lets it hold up for the users VISOR is built for:
 *
 *  - It scales. The wordmark takes its size from `MaterialTheme.typography`, so
 *    it grows with the user's Settings > Text size preference, and the mark is
 *    sized from that same type scale so the lockup keeps its proportions all the
 *    way to 200% (Section 508). [AutoSizeText] catches the point where an
 *    extreme scale would otherwise push the wordmark off the edge.
 *  - It recolours. The plate uses the primaryContainer/onPrimaryContainer pair,
 *    which is contrast-checked in all four VISOR themes (see ui/theme/Color.kt),
 *    so High Contrast users get a solid plate rather than a tinted one.
 *  - It reads as one thing. The mark carries no separate description; the whole
 *    banner is a single heading node, so a screen reader announces "VISOR,
 *    Vision Intelligence System for Ocular Rehabilitation" and moves on instead
 *    of stopping on each fragment.
 *
 * @param slogan the line under the wordmark. Defaults to the VISOR acronym.
 */
@Composable
fun VisorHeader(
    modifier: Modifier = Modifier,
    slogan: String = stringResource(R.string.visor_acronym),
) {
    val wordmarkStyle = MaterialTheme.typography.displayMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = WordmarkTracking.em,
    )

    val density = LocalDensity.current
    // The mark is sized from the wordmark rather than from a fixed dp value, so
    // the lockup keeps its proportions at every text scale.
    val markHeight = with(density) { (wordmarkStyle.fontSize * MarkHeightToWordmark).toDp() }
    // Tracking also lands after the final R, so a centred wordmark sits half a
    // letter-space to the left. Matching padding on the other side undoes that.
    val trackingCompensation = with(density) {
        (wordmarkStyle.fontSize * WordmarkTracking).toDp()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = VisorShapes.Surface,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            VisorMark(modifier = Modifier.height(markHeight))

            AutoSizeText(
                text = stringResource(R.string.app_name),
                style = wordmarkStyle,
                modifier = Modifier.padding(start = trackingCompensation),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(RuleWidthFraction)
                    .height(2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(1.dp),
                    )
            )

            Text(
                text = slogan,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
