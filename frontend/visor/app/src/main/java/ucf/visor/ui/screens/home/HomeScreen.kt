package ucf.visor.ui.screens.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import ucf.visor.R
import ucf.visor.capture.Availability
import ucf.visor.capture.availability
import ucf.visor.data.CameraChoice
import ucf.visor.data.VoiceCategory
import ucf.visor.stt.CameraCommand
import ucf.visor.tts.ReadingControls
import ucf.visor.tts.ReadingState
import ucf.visor.ui.components.ReadingButtons
import ucf.visor.ui.components.ReadingControlHeight
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.VisorButton
import ucf.visor.ui.components.iconSizeFor
import ucf.visor.ui.components.isScreenReaderOn
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.feedback.animationsEnabled
import ucf.visor.ui.icon
import ucf.visor.ui.labelRes
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.CaptureStatus
import ucf.visor.ui.viewmodel.LastAnswer
import ucf.visor.ui.viewmodel.VisorViewModel
import java.util.Date
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorTheme

@Composable
fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    // Takes a picture with the glasses and answers it for one camera feature (MainActivity).
    onCapture: (CameraCommand) -> Unit = {},
    // Pause, resume and stop for the last answer, while VISOR reads it (Speaker).
    reading: ReadingControls? = null,
    modifier: Modifier = Modifier,
) {
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    val lastAnswer by viewModel.lastAnswer.collectAsStateWithLifecycle()
    val readingState by (reading?.reading ?: remember { MutableStateFlow(ReadingState.NONE) })
        .collectAsStateWithLifecycle()
    val readingText by (reading?.readingText ?: remember { MutableStateFlow<String?>(null) })
        .collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
            // Close under the title, so it costs the buttons below little room.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ScreenTitle(
                    stringResource(R.string.home_screen_title),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (profile.camera == CameraChoice.PHONE) PhoneCameraNotice()
            }

            // What Home is for, first: a button for each camera feature, each
            // always in the same place and always doing the same thing, hazards
            // at the top. Full width, one to a row, so a long name wraps at a
            // large text size rather than shrinking. Then what VISOR last found.
            //
            // Offline, each stays in its place, so nothing moves under a finger
            // or a magnifier: the ones that need the internet are locked, and
            // say so (see capture/Availability).
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CaptureGap),
            ) {
                CameraCommand.entries.forEach { command ->
                    val label = stringResource(command.labelRes)
                    val availability = command.availability(uiState.isOnline)
                    CaptureControl(
                        label = label,
                        icon = command.icon,
                        status = captures[command] ?: CaptureStatus.IDLE,
                        availability = availability,
                        onClick = {
                            if (availability != Availability.LOCKED) {
                                onCapture(command)
                            } else if (!context.isScreenReaderOn()) {
                                // The button has buzzed; this says why, for a
                                // user who isn't looking. A screen reader has
                                // already read "Needs the internet, locked".
                                talk(context.getString(R.string.offline_feature_locked, label), VoiceCategory.ANSWERS)
                            }
                        },
                    )
                }
            }

            LastAnswerCard(
                answer = lastAnswer,
                // Only a reading of this answer: anything else VISOR reads has
                // its controls at the bottom of the screen (VisorLayout).
                playback = if (readingText != null && readingText == lastAnswer?.text) readingState else ReadingState.NONE,
                onReadAgain = { talk(it, VoiceCategory.ANSWERS) },
                onPause = { reading?.pause() },
                onResume = { reading?.resume() },
                onStop = { reading?.stop() },
            )
        }
    }
}

/**
 * Shown on Home while the phone's camera is chosen (on the Pair screen): the
 * buttons below take their pictures with the phone, not the glasses. Only
 * then, as the glasses are what VISOR uses unless told otherwise.
 */
@Composable
private fun PhoneCameraNotice() {
    val style = MaterialTheme.typography.bodyLarge
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Icon(
            imageVector = Icons.Filled.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSizeFor(style)),
        )
        Text(
            stringResource(R.string.home_using_phone_camera),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A camera feature's button, which turns into a panel saying what VISOR is doing
 * while it takes a picture and works out what is in it: "Taking picture…",
 * then "Thinking…". A user can see VISOR is working with its beeps turned off.
 *
 * The panel has a ring sweeping around its edge, once every two seconds; in
 * VISOR, that ring means it is working. With the system's animations off, the
 * ring holds still around the whole edge. Each step is a polite live region,
 * so a screen reader says it as it changes.
 *
 * The button and the panel take the same room, so nothing below moves as they
 * swap. Both are taller than any other button, with larger text: what Home
 * is for should be the easiest thing on it to find and press.
 *
 * Offline ([availability]), a feature that needs the internet is a locked
 * button, with a lock, a dashed outline and "Needs the internet" under its
 * name; pressed, it buzzes three times. One that does less offline (Describe
 * the scene) says what it does instead, under its name.
 */
@Composable
private fun CaptureControl(
    label: String,
    icon: ImageVector,
    status: CaptureStatus,
    availability: Availability,
    onClick: () -> Unit,
) {
    val working = status != CaptureStatus.IDLE
    val style = MaterialTheme.typography.headlineSmall
    Box(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        SwapInPlace(
            showSecond = working,
            first = {
                VisorButton(
                    text = label,
                    width = 1f,
                    onClick = onClick,
                    textStyle = style,
                    icon = icon,
                    locked = availability == Availability.LOCKED,
                    supportingText = when (availability) {
                        Availability.AVAILABLE -> null
                        Availability.LIMITED -> stringResource(R.string.offline_describe_limited)
                        Availability.LOCKED -> stringResource(R.string.offline_locked_reason)
                    },
                    modifier = Modifier
                        .heightIn(min = CaptureMinHeight)
                        .then(if (working) Modifier.clearAndSetSemantics {} else Modifier),
                )
            },
            second = {
                // A new node for each step, so a screen reader hears each one.
                key(status) { CaptureStatusPanel(status, style) }
            },
        )
    }
}

@Composable
private fun CaptureStatusPanel(status: CaptureStatus, style: TextStyle) {
    val colors = MaterialTheme.colorScheme
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
            .heightIn(min = CaptureMinHeight)
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
 * and the controls for hearing it. It is there for whatever an answer said
 * that a user didn't hear: a noisy street, hearing loss, or Answers turned
 * down in Settings. A new answer is a polite live region, and the card's
 * border flashes once as it arrives (not with the system's animations off;
 * the new text and time show it then).
 *
 * Above the answer, which feature gave it ("Read text · 1:09 PM"): five
 * features feed the card, wake phrases among them, and "No text found" means
 * more beside "Read text".
 *
 * A long answer shows its first lines, and scrolls in place for the rest
 * ([AnswerText]), so the controls under it stay in the same place, close to
 * the camera buttons, however long the answer is.
 *
 * Under the answer, its controls ([playback]): Read again, which says it again
 * at the Answers volume; while VISOR reads it, Pause (or Resume) and Stop in
 * its place, Pause exactly where Read again was. All are the height of the
 * controls at the bottom of other screens, which this card's take the place
 * of on Home.
 *
 * The card is on Home from the start, saying what it is for.
 */
@Composable
private fun LastAnswerCard(
    answer: LastAnswer?,
    playback: ReadingState,
    onReadAgain: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
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
                        val time = DateFormat.getTimeFormat(context).format(Date(answer.atMillis))
                        val source = stringResource(answer.command.labelRes)
                        // Read without the "·", which a screen reader would say aloud.
                        val spoken = stringResource(R.string.home_last_answer_from_spoken, source, time)
                        Text(
                            stringResource(R.string.home_last_answer_from, source, time),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = spoken },
                        )
                        AnswerText(answer.text)
                    }
                }
            }
        }
        if (answer != null) {
            if (playback == ReadingState.NONE) {
                SwitchButton(
                    label = stringResource(R.string.home_read_again),
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onReadAgain(answer.text) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    minHeight = ReadingControlHeight,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ReadingButtons(
                    paused = playback == ReadingState.PAUSED,
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                )
            }
        }
    }
}

/**
 * The text of an answer, at most [AnswerVisibleLines] lines tall: a longer one
 * scrolls in place, with a scrollbar along its edge. While there is more
 * below, its last line fades into the card under an ellipsis button, so it
 * reads as cut short rather than finished; scrolled to the end, both go.
 *
 * Pressed, the ellipsis shows the whole answer at once, the card growing to
 * fit it and Home scrolling as it always does, with Show less under it to go
 * back. Each new answer starts short again.
 *
 * Its height is in lines of its own text, so it grows with Settings' text
 * size and the system's font size, and always shows the same amount.
 *
 * A screen reader reads the whole answer, whatever has been scrolled to, and
 * names the ellipsis "Show the whole answer".
 */
@Composable
private fun AnswerText(text: String) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleLarge
    val lineHeight = if (style.lineHeight.isSp) style.lineHeight else style.fontSize * 1.5f
    val maxHeight = with(LocalDensity.current) { (lineHeight * AnswerVisibleLines).toDp() }
    val scroll = rememberScrollState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (expanded) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, style = style, color = colors.onSurface)
            AnswerSizeButton(
                text = stringResource(R.string.home_answer_show_less),
                name = null,
                onClick = { expanded = false },
                modifier = Modifier.align(Alignment.End),
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Text(
            text,
            style = style,
            color = colors.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .scrollIndicator(scroll, colors.outline)
                // Room for the scrollbar, so it never covers a letter.
                .padding(end = AnswerScrollbarRoom)
                .verticalScroll(scroll),
        )
        if (scroll.canScrollForward) {
            val fadeHeight = with(LocalDensity.current) { lineHeight.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = AnswerScrollbarRoom)
                    .height(fadeHeight)
                    .background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = 0f), colors.surface)))
                    .clearAndSetSemantics {},
            )
            AnswerSizeButton(
                text = "…",
                name = stringResource(R.string.home_answer_show_all_named),
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = AnswerScrollbarRoom),
            )
        }
    }
}

/**
 * The ellipsis that shows a whole answer, and Show less that shortens it
 * again: small outlined buttons, at least 48dp each way, pressed and focused
 * like every other VISOR control. [name] is what a screen reader says in
 * place of [text], for the ellipsis.
 */
@Composable
private fun AnswerSizeButton(
    text: String,
    name: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val fill = if (state.pressed) colors.onSurface else colors.surface
    val ink = if (state.pressed) colors.surface else colors.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .background(fill, VisorShapes.Control)
            .border(
                if (state.pressed) PressedBorderWidth else 2.dp,
                colors.outline,
                VisorShapes.Control,
            )
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button) {
                haptics?.tick()
                onClick()
            }
            .then(if (name != null) Modifier.semantics { contentDescription = name } else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}

// Half again VisorButton's 56dp, and at 200% text the top three still fit on
// a phone's screen without scrolling.
private val CaptureMinHeight = 88.dp

// Between the camera buttons: room enough that a tap meant for one doesn't land on the next.
private val CaptureGap = 16.dp
private const val SweepMillis = 2_000
private const val SweepFraction = 0.25f
private val SweepWidth = 5.dp
private const val FlashMillis = 900

// Enough of an answer to take in at a glance, and short enough that the
// card's controls stay near the camera buttons, at any text size.
private const val AnswerVisibleLines = 6

// The scrollbar's 6dp, its 6dp inset from the edge, and a little air.
private val AnswerScrollbarRoom = 16.dp
private const val FreshAnswerMillis = 3_000L

