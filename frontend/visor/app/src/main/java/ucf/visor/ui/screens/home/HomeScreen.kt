package ucf.visor.ui.screens.home

import androidx.annotation.StringRes
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.data.VoiceCategory
import ucf.visor.tts.SpokenPhrases
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.iconSizeFor
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.feedback.animationsEnabled
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.CaptureStatus
import ucf.visor.ui.viewmodel.LastAnswer
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.icon
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.viewmodel.labelRes
import java.util.Date

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    // Starts or ends the session, and says so (MainActivity).
    onToggleSession: () -> Unit = {},
    // Takes a picture with the glasses and answers it for the session's mode (MainActivity).
    onCapture: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val lastAnswer by viewModel.lastAnswer.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // The capture button and the modes only work during a session, but they are
    // on screen before one too, locked. That shows early what a session offers,
    // and gives the screen the same layout either way, so the session button
    // never jumps when a session starts. Locked controls stay full contrast
    // (dashed, not faded) and still answer a tap, saying what to do first.
    val locked = !uiState.isSessionActive

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            ScreenTitle(
                stringResource(R.string.home_screen_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            SessionToggleButton(
                isSessionActive = uiState.isSessionActive,
                onToggle = onToggleSession,
            )

            // Both labels are laid out and only one is shown: at large text sizes
            // "Start Session" can wrap where "End Session" does not, and a label
            // that changed height would move the button above it.
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.start_session),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shownOnlyIf(!uiState.isSessionActive),
                )
                Text(
                    text = stringResource(R.string.end_session),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.shownOnlyIf(uiState.isSessionActive),
                )
            }

            // The capture button, what it last found, then the mode that
            // decides what it does.
            CaptureControl(
                label = stringResource(captureLabel(session.mode)),
                status = capture,
                locked = locked,
                onClick = {
                    if (locked) {
                        talk(SpokenPhrases.startSessionToCapture(session.mode), VoiceCategory.SESSION_UPDATES)
                    } else {
                        onCapture()
                    }
                },
            )

            LastAnswerCard(
                answer = lastAnswer,
                onReadAgain = { talk(it, VoiceCategory.ANSWERS) },
            )

            ModePicker(
                selected = session.mode,
                locked = locked,
                onSelect = { mode ->
                    viewModel.setMode(mode)
                    talk(SpokenPhrases.modeActivated(mode), VoiceCategory.SESSION_UPDATES)
                },
                onLockedClick = {
                    talk(SpokenPhrases.START_SESSION_TO_SWITCH_MODES, VoiceCategory.SESSION_UPDATES)
                },
            )

            // What the locked controls are waiting for. Its space is kept during
            // a session too, for the same reason as the label's above.
            Row(
                modifier = Modifier.shownOnlyIf(locked),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(iconSizeFor(MaterialTheme.typography.bodyLarge)),
                )
                Text(
                    text = stringResource(R.string.home_locked_hint),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** Keeps an element's space while hiding it from sight and from screen readers. */
private fun Modifier.shownOnlyIf(shown: Boolean): Modifier =
    if (shown) this else alpha(0f).clearAndSetSemantics {}

/**
 * The capture button, which turns into a panel saying what VISOR is doing
 * while it takes a picture and works out what is in it: "Taking picture…",
 * then "Thinking…". A user can see VISOR is working with its beeps turned off.
 *
 * The panel has a ring sweeping around its edge, once every two seconds; in
 * VISOR, that ring means it is working. With the system's animations off, the
 * ring holds still around the whole edge. Each step is a polite live region,
 * so a screen reader says it as it changes.
 *
 * The button and the panel take the same room, so nothing below moves as they
 * swap.
 */
@Composable
private fun CaptureControl(
    label: String,
    status: CaptureStatus,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val working = status != CaptureStatus.IDLE
    Box(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        SwapInPlace(
            showSecond = working,
            first = {
                VisorButton(
                    text = label,
                    width = 1f,
                    locked = locked,
                    onClick = onClick,
                    modifier = if (working) Modifier.clearAndSetSemantics {} else Modifier,
                )
            },
            second = {
                // A new node for each step, so a screen reader hears each one.
                key(status) { CaptureStatusPanel(status) }
            },
        )
    }
}

@Composable
private fun CaptureStatusPanel(status: CaptureStatus) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val sweep: State<Float>? = if (animationsEnabled()) {
        rememberInfiniteTransition(label = "working").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SweepMillis, easing = LinearEasing)),
            label = "sweep",
        )
    } else {
        null
    }
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .background(colors.surface, VisorShapes.Control)
            .sweepingRing(sweep, track = colors.outline, ring = colors.primary)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (status == CaptureStatus.TAKING_PICTURE) Icons.Filled.PhotoCamera else Icons.Filled.HourglassTop,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(iconSizeFor(style)),
        )
        Text(
            stringResource(
                if (status == CaptureStatus.TAKING_PICTURE) R.string.home_capture_taking_picture
                else R.string.home_capture_thinking
            ),
            style = style,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The panel's edge: a thin outline all the way round, and over it a thicker
 * stretch of [ring] that runs around it as [sweep] goes from 0 to 1. With no
 * [sweep] (animations off), the thick ring goes all the way round instead.
 */
private fun Modifier.sweepingRing(sweep: State<Float>?, track: Color, ring: Color): Modifier =
    drawWithCache {
        val thick = SweepWidth.toPx()
        val outline = VisorShapes.Control.createOutline(
            Size(size.width - thick, size.height - thick),
            layoutDirection,
            this,
        )
        val path = Path().apply { addOutline(outline) }
        val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
        val length = measure.length
        val stretch = Path()
        onDrawWithContent {
            drawContent()
            translate(thick / 2, thick / 2) {
                if (sweep == null) {
                    drawPath(path, ring, style = Stroke(width = thick))
                    return@translate
                }
                drawPath(path, track, style = Stroke(width = 2.dp.toPx()))
                // A quarter of the way round, starting wherever the sweep has got to.
                val start = sweep.value * length
                val end = start + length * SweepFraction
                stretch.reset()
                measure.getSegment(start, minOf(end, length), stretch, startWithMoveTo = true)
                if (end > length) measure.getSegment(0f, end - length, stretch, startWithMoveTo = true)
                drawPath(stretch, ring, style = Stroke(width = thick, cap = StrokeCap.Round))
            }
        }
    }

/**
 * Shows [first], or [second] in its place when [showSecond], at the size of
 * the taller of the two, so swapping them never moves what is below. The one
 * not shown is not placed: it can't be seen or touched.
 */
@Composable
private fun SwapInPlace(
    showSecond: Boolean,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Layout(contents = listOf(first, second)) { (firsts, seconds), constraints ->
        val width = constraints.maxWidth
        val height = maxOf(
            firsts.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0,
            seconds.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0,
        )
        val shown = (if (showSecond) seconds else firsts).map {
            it.measure(Constraints(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height))
        }
        layout(width, height) { shown.forEach { it.place(0, 0) } }
    }
}

/**
 * What VISOR last answered, in large text that stays on screen, with the time
 * and a Read again button that says it again at the Answers volume. It is
 * there for whatever an answer said that a user didn't hear: a noisy street,
 * hearing loss, or Answers turned down in Settings. A new answer is a polite
 * live region, and the card's border flashes once as it arrives (not with
 * the system's animations off; the new text and time show it then).
 *
 * The card is on Home from the start, saying what it is for, so the first
 * answer doesn't push the modes below it down the screen.
 */
@Composable
private fun LastAnswerCard(answer: LastAnswer?, onReadAgain: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val flash = remember { Animatable(0f) }
    LaunchedEffect(answer?.atMillis) {
        // Only for an answer that has just arrived, not one from before Home was opened.
        val fresh = answer != null && System.currentTimeMillis() - answer.atMillis < FreshAnswerMillis
        if (fresh && animationsEnabled()) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FlashMillis))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, VisorShapes.Control)
            .drawWithContent {
                drawContent()
                val width = (2.dp + 4.dp * flash.value).toPx()
                val outline = VisorShapes.Control.createOutline(
                    Size(size.width - width, size.height - width),
                    layoutDirection,
                    this,
                )
                translate(width / 2, width / 2) {
                    drawOutline(outline, lerp(colors.outline, colors.primary, flash.value), style = Stroke(width = width))
                }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_last_answer_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Box(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            // A new node for each answer, so a screen reader hears each one.
            key(answer?.atMillis) {
                if (answer == null) {
                    Text(
                        stringResource(R.string.home_last_answer_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            DateFormat.getTimeFormat(context).format(Date(answer.atMillis)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                        Text(answer.text, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                    }
                }
            }
        }
        if (answer != null) {
            SwitchButton(
                label = stringResource(R.string.home_read_again),
                onClick = { onReadAgain(answer.text) },
            )
        }
    }
}

private const val SweepMillis = 2_000
private const val SweepFraction = 0.25f
private val SweepWidth = 5.dp
private const val FlashMillis = 900
private const val FreshAnswerMillis = 3_000L

@Composable
private fun ModePicker(
    selected: SessionMode,
    locked: Boolean,
    onSelect: (SessionMode) -> Unit,
    onLockedClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.home_mode_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        SessionMode.entries.forEach { mode ->
            SelectableChip(
                label = stringResource(mode.labelRes),
                selected = mode == selected,
                locked = locked,
                icon = mode.icon,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (locked) onLockedClick()
                    else if (mode != selected) onSelect(mode)
                },
            )
        }
    }
}

@StringRes
private fun captureLabel(mode: SessionMode): Int = when (mode) {
    SessionMode.HAZARD -> R.string.home_capture_hazard
    SessionMode.SCENE -> R.string.home_capture_scene
    SessionMode.READER -> R.string.home_capture_reader
}
