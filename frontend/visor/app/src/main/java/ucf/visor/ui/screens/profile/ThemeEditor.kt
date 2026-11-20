package ucf.visor.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ucf.visor.R
import ucf.visor.data.CustomColors
import ucf.visor.data.ThemeBase
import ucf.visor.data.ThemeElement
import ucf.visor.data.UserProfile
import ucf.visor.data.VoiceCategory
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.VisorDialog
import ucf.visor.ui.components.VisorSlider
import ucf.visor.ui.components.iconSizeFor
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.ContrastAudit
import ucf.visor.ui.theme.ContrastCheck
import ucf.visor.ui.theme.VisorColors
import ucf.visor.ui.theme.VisorColorsProvider
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.theme.colorName
import ucf.visor.ui.theme.colorfulnessOf
import ucf.visor.ui.theme.customVisorColors
import ucf.visor.ui.theme.keyColors
import ucf.visor.ui.theme.makeReadable
import ucf.visor.ui.theme.oklchColorRelative
import ucf.visor.ui.theme.parseHexColor
import ucf.visor.ui.theme.readableOn
import ucf.visor.ui.theme.toArgbInt
import ucf.visor.ui.theme.toHex
import ucf.visor.ui.theme.toOklch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Settings > Text and display > My colors: the user picks their own color
 * for each of six parts of VISOR ([ThemeElement]), starting from one of
 * VISOR's four looks.
 *
 * Built so a low-vision user can't lock themself out of their own phone:
 *
 *  - This screen stays in the colors in use. Only the preview at the top is
 *    drawn in the colors being picked, so the controls to fix colors stay
 *    readable however bad the colors get.
 *  - Nothing changes until "Use these colors".
 *  - Every pair is checked as it is picked (ContrastAudit, the same check the
 *    build runs on VISOR's own looks). A part that is hard to read says so in
 *    words, with what it is hard to read with and its contrast, and "Make
 *    readable" fixes it, keeping its hue. Hard-to-read colors can still be
 *    used, since some light-sensitive users want dimmer pairs on purpose.
 *  - "VISOR GO, use VISOR colors" goes back at any time.
 *
 * It opens on the list of parts; a part opens the picker ([ColorPicker]), and
 * Back returns to the list.
 */
@Composable
internal fun ThemeEditor(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    scrollState: ScrollState,
    talk: (String, VoiceCategory) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val saved = profile.customColors
    // Never picked: start from the look in use now.
    val starting = if (saved.isUnchanged && !profile.useCustomColors) {
        CustomColors(AppTheme.forProfile(profile, systemDark).base)
    } else {
        saved
    }
    var draft by rememberSaveable(stateSaver = CustomColorsSaver) { mutableStateOf(starting) }
    var editing by rememberSaveable { mutableStateOf<ThemeElement?>(null) }
    val draftColors = remember(draft) { customVisorColors(draft) }

    // Each view opens at its top.
    LaunchedEffect(editing) { scrollState.scrollTo(0) }
    BackHandler(enabled = editing != null) { editing = null }

    val part = editing
    if (part == null) {
        PartsList(
            profile = profile,
            draft = draft,
            draftColors = draftColors,
            starting = starting,
            onDraftChange = { draft = it },
            onEdit = { editing = it },
            onProfileChange = onProfileChange,
            talk = talk,
        )
    } else {
        ColorPicker(
            part = part,
            draft = draft,
            draftColors = draftColors,
            onDraftChange = { draft = it },
            onDone = { editing = null },
        )
    }
}

@Composable
private fun PartsList(
    profile: UserProfile,
    draft: CustomColors,
    draftColors: VisorColors,
    starting: CustomColors,
    onDraftChange: (CustomColors) -> Unit,
    onEdit: (ThemeElement) -> Unit,
    onProfileChange: (UserProfile) -> Unit,
    talk: (String, VoiceCategory) -> Unit,
) {
    var pendingBase by rememberSaveable { mutableStateOf<ThemeBase?>(null) }
    val keys = remember(draft) { keyColors(draft) }
    val failures = remember(draftColors) { ContrastAudit.failures(draftColors) }

    Text(stringResource(R.string.colors_intro), style = MaterialTheme.typography.bodyLarge)

    SettingSection(stringResource(R.string.colors_preview_heading))
    ThemePreview(draftColors)

    SettingSection(stringResource(R.string.colors_start_from))
    Text(stringResource(R.string.colors_start_from_description), style = MaterialTheme.typography.bodyMedium)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        ThemeBase.entries.forEach { base ->
            SelectableChip(
                label = stringResource(base.nameRes),
                selected = draft.base == base,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    draft.base == base -> Unit
                    draft.isUnchanged -> onDraftChange(CustomColors(base))
                    else -> pendingBase = base
                }
            }
        }
    }

    SettingSection(stringResource(R.string.colors_parts))
    ThemeElement.entries.forEach { part ->
        PartTile(
            part = part,
            color = keys.getValue(part),
            picked = part in draft.chosen,
            failures = failures.filter { part in it.parts }.sortedBy { it.ratio / it.minimum },
            onClick = { onEdit(part) },
        )
    }

    val inUse = profile.useCustomColors && draft == profile.customColors
    if (failures.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(iconSizeFor(MaterialTheme.typography.bodyLarge)),
            )
            Text(stringResource(R.string.colors_some_hard_to_read), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (inUse) {
        Text(
            stringResource(R.string.colors_in_use),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else {
        SwitchButton(
            label = stringResource(R.string.colors_use),
            icon = Icons.Filled.Palette,
            onClick = { onProfileChange(profile.copy(customColors = draft, useCustomColors = true)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft != starting) {
        SwitchButton(
            label = stringResource(R.string.colors_undo),
            outlined = true,
            icon = Icons.AutoMirrored.Filled.Undo,
            onClick = { onDraftChange(starting) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (profile.useCustomColors) {
        SwitchButton(
            label = stringResource(R.string.settings_use_visor_colors),
            outlined = true,
            onClick = { onProfileChange(profile.copy(useCustomColors = false)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    pendingBase?.let { base ->
        VisorDialog(
            title = stringResource(R.string.colors_start_again_title, stringResource(base.nameRes)),
            message = stringResource(R.string.colors_start_again_message),
            confirmLabel = stringResource(R.string.colors_start_again_confirm),
            onConfirm = {
                onDraftChange(CustomColors(base))
                pendingBase = null
            },
            onDismiss = { pendingBase = null },
            talk = talk,
        )
    }
}

/**
 * One part on the list: its color, what it's called, the color's name, and
 * whether it is easy to read. Pressed and focused, it looks like VISOR's
 * buttons and Settings' tiles do.
 */
@Composable
private fun PartTile(
    part: ThemeElement,
    color: Color,
    picked: Boolean,
    failures: List<ContrastCheck>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val fill = if (state.pressed) colors.onSurface else colors.surface
    val ink = if (state.pressed) colors.surface else colors.onSurface
    val titleStyle = MaterialTheme.typography.titleLarge
    val name = colorName(color).replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .clip(VisorShapes.Control)
            .background(fill)
            .border(if (state.pressed) PressedBorderWidth else 2.dp, colors.outline, VisorShapes.Control)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = {
                    haptics?.tick()
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(color, size = 56.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(part.nameRes), style = titleStyle, fontWeight = FontWeight.SemiBold, color = ink)
            Text(
                if (picked) stringResource(R.string.colors_part_changed, name) else name,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            Readability(part, failures, ink)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(iconSizeFor(titleStyle)),
        )
    }
}

/**
 * "Easy to read", or what [part] is hard to read with and how hard: "Hard to
 * read with Text: 2.1:1, needs 4.5:1". In words and with an icon, never by
 * color alone. A screen reader hears "2.1 to 1".
 *
 * @param announce says it again whenever it changes from easy to hard, or
 *   to hard with another part, as the picker's sliders move: without the
 *   contrast, which changes with every notch and would talk over each one.
 */
@Composable
private fun Readability(
    part: ThemeElement,
    failures: List<ContrastCheck>,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    announce: Boolean = false,
) {
    val worst = failures.firstOrNull()
    val text = if (worst == null) {
        stringResource(R.string.colors_readable)
    } else {
        val ratio = ratioText(worst.ratio)
        val needs = ratioText(worst.minimum)
        val others = (worst.parts - part).map { stringResource(it.nameRes) }
        if (others.isEmpty()) stringResource(R.string.colors_hard_to_read, ratio, needs)
        else stringResource(R.string.colors_hard_to_read_with, others.joinToString(" & "), ratio, needs)
    }
    val spoken = if (announce && worst != null) text.substringBefore(':') else text
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken.replace(":1", " to 1").replace(" & ", " and ")
            if (announce) liveRegion = LiveRegionMode.Polite
        },
    ) {
        Icon(
            imageVector = if (worst == null) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(iconSizeFor(MaterialTheme.typography.bodyMedium)),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

/** The color picker for one part: before and now, quick colors, three sliders, and a hex code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(
    part: ThemeElement,
    draft: CustomColors,
    draftColors: VisorColors,
    onDraftChange: (CustomColors) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = keyColors(draft).getValue(part)
    val before = Color(rememberSaveable(part) { current.toArgbInt() })
    // Kept apart from the color itself: a gray has no hue, and the hue slider
    // shouldn't jump to red when Colorfulness passes through gray.
    var position by remember(part) { mutableStateOf(PickerPosition.of(current)) }
    var busy by remember(part) { mutableStateOf(false) }
    var notice by remember(part) { mutableStateOf<String?>(null) }
    var hexText by remember(part) { mutableStateOf(current.toHex()) }
    var hexFocused by remember { mutableStateOf(false) }
    val failures = remember(draftColors, part) { ContrastAudit.failuresOf(part, draftColors) }

    fun pick(color: Color, moveSliders: Boolean = true) {
        onDraftChange(draft.with(part, color.toArgbInt()))
        if (moveSliders) position = PickerPosition.of(color)
        notice = null
    }

    // The hex field follows the color however it was picked, except while being typed in.
    LaunchedEffect(current) { if (!hexFocused) hexText = current.toHex() }

    SwitchButton(
        label = stringResource(R.string.colors_back_to_parts),
        outlined = true,
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        onClick = onDone,
    )
    SettingSection(stringResource(part.nameRes))
    Text(stringResource(part.descriptionRes), style = MaterialTheme.typography.bodyLarge)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LabeledSwatch(before, stringResource(R.string.colors_before), Modifier.weight(1f))
        LabeledSwatch(current, stringResource(R.string.colors_now), Modifier.weight(1f))
    }
    Readability(part, failures, announce = true)

    if (failures.isNotEmpty()) {
        SwitchButton(
            label = stringResource(if (busy) R.string.colors_make_readable_working else R.string.colors_make_readable),
            icon = Icons.Filled.AutoFixHigh,
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    val fixed = withContext(Dispatchers.Default) { makeReadable(draft, part) }
                    busy = false
                    if (fixed == null) {
                        notice = context.getString(R.string.colors_make_readable_failed)
                    } else {
                        val color = keyColors(fixed).getValue(part)
                        onDraftChange(fixed)
                        position = PickerPosition.of(color)
                        notice = context.getString(R.string.colors_made_readable, colorName(color))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    notice?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    SettingSection(stringResource(R.string.colors_quick))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        QuickColors.forEach { color ->
            QuickSwatch(color, selected = color == current) { pick(color) }
        }
    }

    SettingSection(stringResource(R.string.colors_fine_tune))
    val p = position
    val hueStops = remember(p.l, p.f) {
        // The hues at a lightness and colorfulness where they can be told apart.
        val l = p.l.coerceIn(0.45, 0.8)
        val f = p.f.coerceAtLeast(0.6)
        (0..12).map { oklchColorRelative(l, f, it * 30.0) }
    }
    val colorfulnessStops = remember(p.l, p.h) { (0..5).map { oklchColorRelative(p.l, it / 5.0, p.h) } }
    val lightnessStops = remember(p.f, p.h) { (0..10).map { oklchColorRelative(it / 10.0, p.f, p.h) } }
    val name = colorName(current)

    SliderLabel(stringResource(R.string.colors_hue))
    VisorSlider(
        value = p.h.toFloat(),
        onValueChange = {
            position = position.copy(h = it.toDouble())
            pick(position.color(), moveSliders = false)
        },
        valueRange = 0f..360f,
        steps = 71,
        label = stringResource(R.string.colors_hue),
        valueDescription = stringResource(R.string.colors_hue_value, p.h.roundToInt(), name),
        decreaseDescription = stringResource(R.string.colors_hue_back),
        increaseDescription = stringResource(R.string.colors_hue_forward),
        trackColors = hueStops,
    )

    SliderLabel(stringResource(R.string.colors_colorfulness))
    VisorSlider(
        value = (p.f * 100).toFloat(),
        onValueChange = {
            position = position.copy(f = it / 100.0)
            pick(position.color(), moveSliders = false)
        },
        valueRange = 0f..100f,
        steps = 19,
        label = stringResource(R.string.colors_colorfulness),
        valueDescription = stringResource(R.string.colors_percent, (p.f * 100).roundToInt()),
        decreaseDescription = stringResource(R.string.colors_less_colorful),
        increaseDescription = stringResource(R.string.colors_more_colorful),
        trackColors = colorfulnessStops,
    )
    SliderEnds(stringResource(R.string.colors_gray), stringResource(R.string.colors_vivid))

    SliderLabel(stringResource(R.string.colors_lightness))
    VisorSlider(
        value = (p.l * 100).toFloat(),
        onValueChange = {
            position = position.copy(l = it / 100.0)
            pick(position.color(), moveSliders = false)
        },
        valueRange = 0f..100f,
        steps = 49,
        label = stringResource(R.string.colors_lightness),
        valueDescription = stringResource(R.string.colors_percent, (p.l * 100).roundToInt()),
        decreaseDescription = stringResource(R.string.colors_darker),
        increaseDescription = stringResource(R.string.colors_lighter),
        trackColors = lightnessStops,
    )
    SliderEnds(stringResource(R.string.colors_darker), stringResource(R.string.colors_lighter))

    val hexInvalid = hexText.trim().removePrefix("#").length >= 6 && parseHexColor(hexText) == null
    OutlinedTextField(
        value = hexText,
        onValueChange = { typed ->
            hexText = typed.take(7)
            val digits = typed.trim().removePrefix("#")
            if (digits.length == 6) parseHexColor(digits)?.let { pick(it) }
        },
        label = { Text(stringResource(R.string.colors_hex_label)) },
        isError = hexInvalid,
        supportingText = if (hexInvalid) {
            { Text(stringResource(R.string.colors_hex_invalid)) }
        } else {
            null
        },
        singleLine = true,
        shape = VisorShapes.Control,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrect = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { parseHexColor(hexText)?.let { pick(it) } }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .onFocusChanged { hexFocused = it.isFocused },
    )

    if (part in draft.chosen) {
        SwitchButton(
            label = stringResource(R.string.colors_reset_part),
            outlined = true,
            icon = Icons.AutoMirrored.Filled.Undo,
            onClick = {
                val reset = draft.without(part)
                onDraftChange(reset)
                position = PickerPosition.of(keyColors(reset).getValue(part))
                notice = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SwitchButton(
        label = stringResource(R.string.colors_done),
        icon = Icons.Filled.Check,
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Where the picker's three sliders are: hue in degrees, colorfulness and
 * lightness from 0 to 1 (see ColorMath: OKLCH, colorfulness as a share of the
 * most the screen can show).
 */
private data class PickerPosition(val h: Double, val f: Double, val l: Double) {
    fun color(): Color = oklchColorRelative(l, f, h)

    companion object {
        fun of(color: Color): PickerPosition {
            val lch = color.toOklch()
            return PickerPosition(h = lch.h, f = colorfulnessOf(color), l = lch.l)
        }
    }
}

/**
 * The colors being picked, drawn as a small piece of VISOR: a heading,
 * smaller notes, a card, a button, a picked and an unpicked choice, a focus
 * ring and an error. Only for looking at: a screen reader says what it is,
 * and none of it can be pressed. Framed in the colors in use, so it stands
 * apart from the page whatever its own colors are.
 */
@Composable
private fun ThemePreview(colors: VisorColors) {
    val description = stringResource(R.string.colors_preview_description)
    val frame = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .border(2.dp, frame, VisorShapes.Surface)
            .padding(6.dp),
    ) {
        VisorColorsProvider(colors) {
            val c = MaterialTheme.colorScheme
            val type = MaterialTheme.typography
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VisorShapes.Surface)
                    .background(c.background)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.colors_preview_title), style = type.titleLarge, fontWeight = FontWeight.Bold, color = c.onBackground)
                Text(stringResource(R.string.colors_preview_supporting), style = type.bodyMedium, color = c.onSurfaceVariant)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(VisorShapes.Control)
                        .background(c.surface)
                        .border(2.dp, c.outline, VisorShapes.Control)
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.colors_preview_card), style = type.titleMedium, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                    Text(stringResource(R.string.colors_preview_supporting), style = type.bodyMedium, color = c.onSurfaceVariant)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(VisorShapes.Control)
                        .background(c.primary)
                        .padding(8.dp),
                ) {
                    Text(stringResource(R.string.colors_preview_button), style = type.labelLarge, fontWeight = FontWeight.Bold, color = c.onPrimary)
                }
                SampleChip(stringResource(R.string.colors_preview_selected), c.primaryContainer, c.onPrimaryContainer, c.primary, 2.dp)
                SampleChip(stringResource(R.string.colors_preview_unselected), c.surface, c.onSurface, c.outline, 1.dp)
                Box(
                    Modifier
                        .border(3.dp, VisorTheme.focusRing, VisorShapes.Control)
                        .padding(5.dp),
                ) {
                    SampleChip(stringResource(R.string.colors_preview_focused), c.surface, c.onSurface, c.outline, 1.dp)
                }
                Text(stringResource(R.string.colors_preview_error), style = type.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.error)
            }
        }
    }
}

@Composable
private fun SampleChip(label: String, fill: Color, ink: Color, edge: Color, edgeWidth: Dp) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(VisorShapes.Control)
            .background(fill)
            .border(edgeWidth, edge, VisorShapes.Control)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = ink)
    }
}

/** A square of [color], edged in the outline color in use so it shows on any page. */
@Composable
private fun Swatch(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(VisorShapes.Control)
            .background(color)
            .border(2.dp, MaterialTheme.colorScheme.outline, VisorShapes.Control),
    )
}

/** "Before" or "Now": a wide swatch, and under it what it is and the color's name. Read as one. */
@Composable
private fun LabeledSwatch(color: Color, label: String, modifier: Modifier = Modifier) {
    val name = colorName(color)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(VisorShapes.Control)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.outline, VisorShapes.Control),
        )
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        Text(color.toHex(), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One of the quick colors: a large square, named for a screen reader, with a
 * check mark and a thicker edge when it is the color picked. Focused, it has
 * the focus ring every VISOR control has.
 */
@Composable
private fun QuickSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val name = colorName(color)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(QuickSwatchSize)
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .clip(VisorShapes.Control)
            .background(color)
            .border(
                if (selected || state.pressed) PressedBorderWidth else 2.dp,
                MaterialTheme.colorScheme.outline,
                VisorShapes.Control,
            )
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = {
                    haptics?.tick()
                    onClick()
                },
            )
            .semantics { contentDescription = name },
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = readableOn(color, preferred = Color.Black),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun SliderLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 6.dp)
            .clearAndSetSemantics {},
    )
}

/** "2.1:1", rounded down: a pair at 4.46:1 must not show as reaching 4.5:1. */
private fun ratioText(ratio: Double): String =
    String.format(Locale.getDefault(), "%.1f:1", floor(ratio * 10) / 10)

@get:StringRes
internal val ThemeElement.nameRes: Int
    get() = when (this) {
        ThemeElement.BACKGROUND -> R.string.colors_part_background
        ThemeElement.SURFACE -> R.string.colors_part_surface
        ThemeElement.TEXT -> R.string.colors_part_text
        ThemeElement.BUTTONS -> R.string.colors_part_buttons
        ThemeElement.HIGHLIGHT -> R.string.colors_part_highlight
        ThemeElement.OUTLINE -> R.string.colors_part_outline
    }

@get:StringRes
private val ThemeElement.descriptionRes: Int
    get() = when (this) {
        ThemeElement.BACKGROUND -> R.string.colors_part_background_description
        ThemeElement.SURFACE -> R.string.colors_part_surface_description
        ThemeElement.TEXT -> R.string.colors_part_text_description
        ThemeElement.BUTTONS -> R.string.colors_part_buttons_description
        ThemeElement.HIGHLIGHT -> R.string.colors_part_highlight_description
        ThemeElement.OUTLINE -> R.string.colors_part_outline_description
    }

@get:StringRes
private val ThemeBase.nameRes: Int
    get() = when (this) {
        ThemeBase.CLARITY_LIGHT -> R.string.colors_base_clarity_light
        ThemeBase.CLARITY_DARK -> R.string.colors_base_clarity_dark
        ThemeBase.HIGH_CONTRAST_LIGHT -> R.string.colors_base_high_contrast_light
        ThemeBase.HIGH_CONTRAST_DARK -> R.string.colors_base_high_contrast_dark
    }

/** Keeps the colors being picked through a rotation, in the line they are saved as. */
private val CustomColorsSaver = Saver<CustomColors, String>(
    save = { it.encode() },
    restore = { CustomColors.decode(it) },
)

private val QuickSwatchSize = 60.dp

/**
 * Ready-made colors: VISOR's own, and a spread of plain ones at a light and a
 * dark shade, so most pairs a user wants are one tap each.
 */
private val QuickColors = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1A1A1A, 0xFFE6E6E6, 0xFFFFF8E1,
    0xFF0E2A47, 0xFF1A4D98, 0xFF9CC7FF, 0xFF0B5755, 0xFF5FE0D3,
    0xFF1B5E20, 0xFFB9F6CA, 0xFFFFD400, 0xFF6B4700, 0xFFE65100,
    0xFFFFCC80, 0xFF8D1B1B, 0xFFFFCDD2, 0xFF4A148C, 0xFFD1C4E9,
).map { Color(it) }
