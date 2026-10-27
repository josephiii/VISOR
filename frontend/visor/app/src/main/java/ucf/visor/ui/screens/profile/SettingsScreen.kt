package ucf.visor.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.data.AudioMix
import ucf.visor.data.Severity
import ucf.visor.data.SoundCategory
import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.ThemeMode
import ucf.visor.data.UserProfile
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import ucf.visor.data.label
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.components.ChoiceRow
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.SwitchButton
import ucf.visor.ui.components.SwitchChip
import ucf.visor.ui.components.VisorDialog
import ucf.visor.ui.components.VisorSlider
import ucf.visor.ui.components.iconSizeFor
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.feedback.PressedBorderWidth
import ucf.visor.ui.feedback.controlFeedback
import ucf.visor.ui.feedback.controlState
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.SettingsSection
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceCommandKind

/**
 * Settings — reads and writes the same UserProfile the creation wizard fills.
 *
 * It opens on a hub of large tiles, one for each part of Settings
 * ([SettingsSection]), and each part is a short screen of its own: as one
 * page, Settings ran to about eight screens at Extra large text. Back, or
 * "Back to Settings", returns to the hub, and each part can be opened by
 * voice too ("VISOR GO, open sound settings").
 *
 * @param talk says a confirmation dialog's question as it opens.
 * @param speakAt and [playSample] play what a new volume sounds like, as soon
 *   as a volume slider is let go: loudness can't be judged by looking at a
 *   slider. [speakAt] speaks at the volume given, whatever the sliders are.
 */
@Composable
fun SettingsScreen(
    viewModel: VisorViewModel,
    talk: (String, VoiceCategory) -> Unit = { _, _ -> },
    speakAt: (String, Float) -> Unit = { _, _ -> },
    playSample: (SoundCategory) -> Unit = {},
    onProfileChange: (UserProfile) -> Unit = {},
    onLogout: () -> Unit = {},          // TODO: revoke access + refresh tokens (frontend-only per Joseph)
    onDeleteAccount: () -> Unit = {},   // VisorViewModel.confirmDeleteAccount: DELETE /auth/deleteAccount, then wipes the profile
    onHelp: () -> Unit = {},
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val section = uiState.settingsSection
    // Each part opens at its top, not where the last one was scrolled to.
    val scrollState = key(section) { rememberScrollState() }

    // Back from a part of Settings goes to the hub, not to the screen before Settings.
    BackHandler(enabled = section != null) { viewModel.openSettingsSection(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState)
            // Inside the scroll, so the settings scroll right up to the navigation
            // bar instead of disappearing 24dp above it.
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (section == null) {
            ScreenTitle(stringResource(R.string.settings_title))
            SettingsSection.entries.forEach { part ->
                SettingsTile(
                    icon = part.icon,
                    title = stringResource(part.titleRes),
                    description = stringResource(part.descriptionRes),
                    onClick = { viewModel.openSettingsSection(part) },
                )
            }
            SettingsTile(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = stringResource(R.string.settings_help),
                description = stringResource(R.string.settings_hub_help_description),
                onClick = onHelp,
            )
        } else {
            SwitchButton(
                label = stringResource(R.string.settings_back_to_hub),
                outlined = true,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = { viewModel.openSettingsSection(null) },
            )
            ScreenTitle(stringResource(section.titleRes))
            when (section) {
                SettingsSection.ABOUT_YOU -> AboutYouSettings(profile, onProfileChange)
                SettingsSection.SPEECH -> SpeechSettings(profile, onProfileChange)
                SettingsSection.SOUND -> {
                    VolumeSettings(
                        profile = profile,
                        onProfileChange = onProfileChange,
                        // Read back from the ViewModel, not `profile`: a screen reader's
                        // adjustment has already saved the new volume, but this screen
                        // hasn't redrawn yet.
                        currentAudio = { viewModel.userProfile.value.audio },
                        speakAt = speakAt,
                        playSample = playSample,
                    )
                    VibrationSettings(profile, onProfileChange)
                }
                SettingsSection.DISPLAY -> DisplaySettings(profile, onProfileChange)
                SettingsSection.COMMANDS -> CommandSettings(profile, onProfileChange)
                SettingsSection.ACCOUNT -> AccountSettings(
                    onLogout = { viewModel.requestLogoutConfirm() },
                    onDeleteAccount = { viewModel.requestDeleteAccountConfirm() },
                )
            }
        }
    }

    // Both actions are hard to undo (logging out mid-session, or permanent
    // deletion) so they get a confirm step rather than firing immediately.
    // This state lives in VisorUiState (not local remember) so a spoken
    // "log out"/"delete my account" can open the same dialog a tap would.
    if (uiState.isLogoutConfirmVisible) {
        VisorDialog(
            title = stringResource(R.string.settings_log_out_confirm_title),
            message = stringResource(R.string.settings_log_out_confirm_message),
            confirmLabel = stringResource(R.string.settings_log_out),
            confirmIcon = Icons.AutoMirrored.Filled.Logout,
            destructive = true,
            onConfirm = { viewModel.cancelLogoutConfirm(); onLogout() },
            onDismiss = { viewModel.cancelLogoutConfirm() },
            talk = talk,
        )
    }

    if (uiState.isDeleteAccountConfirmVisible) {
        VisorDialog(
            title = stringResource(R.string.settings_delete_account_confirm_title),
            message = stringResource(R.string.settings_delete_account_confirm_message),
            confirmLabel = stringResource(R.string.settings_delete_account_confirm_button),
            confirmIcon = Icons.Filled.DeleteForever,
            destructive = true,
            onConfirm = { viewModel.cancelDeleteAccountConfirm(); onDeleteAccount() },
            onDismiss = { viewModel.cancelDeleteAccountConfirm() },
            talk = talk,
        )
    }
}

/**
 * One large tile on the Settings hub: an icon, what the part is called, and
 * what is in it. Pressed and focused, it looks like VISOR's buttons do.
 */
@Composable
private fun SettingsTile(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val state = interactionSource.controlState()
    val open = stringResource(R.string.settings_hub_open)
    // Pressed, the tile fills with its text's color and its text takes the tile's.
    val fill = if (state.pressed) colors.onSurface else colors.surface
    val ink = if (state.pressed) colors.surface else colors.onSurface
    val titleStyle = MaterialTheme.typography.titleLarge

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .controlFeedback(state, VisorShapes.Control, VisorTheme.focusRing)
            .clip(VisorShapes.Control)
            .background(fill)
            .border(if (state.pressed) PressedBorderWidth else 2.dp, colors.outline, VisorShapes.Control)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClickLabel = open,
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (state.pressed) ink else colors.primary,
            modifier = Modifier.size(iconSizeFor(titleStyle)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(title, style = titleStyle, fontWeight = FontWeight.SemiBold, color = ink)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(iconSizeFor(titleStyle)),
        )
    }
}

/** "About you": the user's name, and their vision, as setup asked. */
@Composable
private fun AboutYouSettings(profile: UserProfile, onProfileChange: (UserProfile) -> Unit) {
    OutlinedTextField(
        value = profile.displayName,
        onValueChange = { onProfileChange(profile.copy(displayName = it)) },
        label = { Text(stringResource(R.string.settings_name_label)) },
        singleLine = true,
        shape = VisorShapes.Control,
        modifier = Modifier.fillMaxWidth(),
    )

    SettingSection(stringResource(R.string.step_vision_title))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VisionType.entries.forEach { type ->
            val selected = type in profile.visionTypes
            SelectableChip(
                label = type.label(),
                selected = selected,
                multiSelect = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val base = profile.visionTypes - VisionType.NOT_SURE
                onProfileChange(
                    profile.copy(
                        visionTypes = (if (selected) base - type else base + type)
                            .ifEmpty { setOf(VisionType.NOT_SURE) }
                    )
                )
            }
        }
    }

    SettingSection(stringResource(R.string.step_severity_title))
    ChoiceRow {
        Severity.entries.forEach { s ->
            SelectableChip(
                label = s.label(),
                selected = profile.severity == s,
            ) { onProfileChange(profile.copy(severity = s)) }
        }
    }

    SettingSection(stringResource(R.string.settings_section_vision_description))
    OutlinedTextField(
        value = profile.visionDescription,
        onValueChange = { onProfileChange(profile.copy(visionDescription = it)) },
        label = { Text(stringResource(R.string.settings_vision_description_label)) },
        shape = VisorShapes.Control,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Speech": how fast VISOR talks, and how much it says. */
@Composable
private fun SpeechSettings(profile: UserProfile, onProfileChange: (UserProfile) -> Unit) {
    SettingSection(stringResource(R.string.settings_section_speech_rate))
    Text(
        text = SpeechRates.label(profile.speechRate),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    VisorSlider(
        value = profile.speechRate,
        onValueChange = { position ->
            val rate = SpeechRates.snap(position)
            if (rate != profile.speechRate) onProfileChange(profile.copy(speechRate = rate))
        },
        valueRange = SpeechRates.Min..SpeechRates.Max,
        steps = SpeechRates.SliderSteps,
        label = stringResource(R.string.settings_speech_rate_label),
        valueDescription = SpeechRates.spokenLabel(profile.speechRate),
        decreaseDescription = stringResource(R.string.settings_speech_rate_slower_button),
        increaseDescription = stringResource(R.string.settings_speech_rate_faster_button),
    )
    SliderEnds(
        start = stringResource(R.string.settings_speech_rate_slower),
        end = stringResource(R.string.settings_speech_rate_faster),
    )

    SettingSection(stringResource(R.string.settings_section_detail))
    ChoiceRow {
        Verbosity.entries.forEach { v ->
            SelectableChip(
                label = stringResource(
                    when (v) {
                        Verbosity.BRIEF -> R.string.settings_verbosity_brief
                        Verbosity.STANDARD -> R.string.settings_verbosity_standard
                        Verbosity.DETAILED -> R.string.settings_verbosity_detailed
                    }
                ),
                selected = profile.verbosity == v,
            ) { onProfileChange(profile.copy(verbosity = v)) }
        }
    }
}

/** Vibration, beside the sounds it goes with: each of VISOR's buzzes, and the switch for them all. */
@Composable
private fun VibrationSettings(profile: UserProfile, onProfileChange: (UserProfile) -> Unit) {
    SettingSection(stringResource(R.string.settings_section_vibration))
    Text(
        stringResource(R.string.settings_vibration_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    SwitchChip(
        title = stringResource(R.string.settings_vibration),
        on = profile.vibrationEnabled,
        onToggle = { onProfileChange(profile.copy(vibrationEnabled = !profile.vibrationEnabled)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Text & display": text size, light or dark, and High contrast. */
@Composable
private fun DisplaySettings(profile: UserProfile, onProfileChange: (UserProfile) -> Unit) {
    SettingSection(stringResource(R.string.settings_section_text_size))
    ChoiceRow {
        TextScale.entries.forEach { scale ->
            SelectableChip(
                label = stringResource(
                    when (scale) {
                        TextScale.STANDARD -> R.string.settings_text_size_standard
                        TextScale.LARGE -> R.string.settings_text_size_large
                        TextScale.EXTRA_LARGE -> R.string.settings_text_size_extra_large
                    }
                ),
                selected = profile.textScale == scale,
            ) { onProfileChange(profile.copy(textScale = scale)) }
        }
    }

    SettingSection(stringResource(R.string.settings_section_theme))
    ChoiceRow {
        ThemeMode.entries.forEach { mode ->
            SelectableChip(
                label = stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                    }
                ),
                selected = profile.themeMode == mode,
            ) { onProfileChange(profile.copy(themeMode = mode)) }
        }
    }

    SettingSection(stringResource(R.string.settings_section_contrast))
    Text(
        stringResource(R.string.settings_high_contrast_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    SwitchChip(
        title = stringResource(R.string.settings_high_contrast),
        on = profile.appHighContrast,
        onToggle = { onProfileChange(profile.copy(appHighContrast = !profile.appHighContrast)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Account": logging out and deleting the account, each confirmed in a dialog first. */
@Composable
private fun AccountSettings(onLogout: () -> Unit, onDeleteAccount: () -> Unit) {
    SwitchButton(
        label = stringResource(R.string.settings_log_out),
        isDestructive = true,
        icon = Icons.AutoMirrored.Filled.Logout,
        onClick = onLogout,
    )
    SwitchButton(
        label = stringResource(R.string.settings_delete_account),
        isDestructive = true,
        icon = Icons.Filled.DeleteForever,
        onClick = onDeleteAccount,
    )
}

private val SettingsSection.icon: ImageVector
    get() = when (this) {
        SettingsSection.ABOUT_YOU -> Icons.Filled.Person
        SettingsSection.SPEECH -> Icons.Filled.RecordVoiceOver
        SettingsSection.SOUND -> Icons.AutoMirrored.Filled.VolumeUp
        SettingsSection.DISPLAY -> Icons.Filled.TextFields
        SettingsSection.COMMANDS -> Icons.Filled.Mic
        SettingsSection.ACCOUNT -> Icons.Filled.AccountCircle
    }

@get:StringRes
private val SettingsSection.titleRes: Int
    get() = when (this) {
        SettingsSection.ABOUT_YOU -> R.string.settings_section_about_you
        SettingsSection.SPEECH -> R.string.settings_hub_speech
        SettingsSection.SOUND -> R.string.settings_hub_sound
        SettingsSection.DISPLAY -> R.string.settings_hub_display
        SettingsSection.COMMANDS -> R.string.settings_section_commands
        SettingsSection.ACCOUNT -> R.string.settings_section_account
    }

@get:StringRes
private val SettingsSection.descriptionRes: Int
    get() = when (this) {
        SettingsSection.ABOUT_YOU -> R.string.settings_hub_about_you_description
        SettingsSection.SPEECH -> R.string.settings_hub_speech_description
        SettingsSection.SOUND -> R.string.settings_hub_sound_description
        SettingsSection.DISPLAY -> R.string.settings_hub_display_description
        SettingsSection.COMMANDS -> R.string.settings_hub_commands_description
        SettingsSection.ACCOUNT -> R.string.settings_hub_account_description
    }

/**
 * "How loud VISOR is": All sounds, then a slider for each kind of thing VISOR
 * says and plays (see AudioMix). Every slider's far left is silent.
 *
 * With All sounds off, the rest give way to a note saying VISOR is silent:
 * nothing they were set to could be heard, not even their previews.
 */
@Composable
private fun VolumeSettings(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    currentAudio: () -> AudioMix,
    speakAt: (String, Float) -> Unit,
    playSample: (SoundCategory) -> Unit,
) {
    val audio = profile.audio

    SettingSection(stringResource(R.string.settings_section_volume))
    Text(
        stringResource(R.string.settings_volume_description),
        style = MaterialTheme.typography.bodyMedium,
    )

    val allSoundsTitle = stringResource(R.string.settings_volume_all_title)
    VolumeSlider(
        title = allSoundsTitle,
        description = stringResource(R.string.settings_volume_all_description),
        volume = audio.master,
        onVolumeChange = { onProfileChange(profile.copy(audio = audio.withMaster(it))) },
        onPreview = { before ->
            val master = currentAudio().master
            // Silence can't confirm itself, so "off" is said at the volume it
            // was just turned down from.
            if (Volumes.isOff(master)) speakAt("$allSoundsTitle off.", before)
            else speakAt("$allSoundsTitle, ${Volumes.spokenLabel(master)}.", master)
        },
    )
    if (Volumes.isOff(audio.master)) {
        VolumeWarning(stringResource(R.string.settings_volume_all_off_warning))
        return
    }

    SettingSubsection(stringResource(R.string.settings_volume_voice_title))
    val answersOff = stringResource(R.string.settings_volume_answers_off_warning)
    VoiceCategory.entries.forEach { category ->
        val title = stringResource(category.titleRes())
        VolumeSlider(
            title = title,
            description = stringResource(category.descriptionRes()),
            volume = audio.level(category),
            onVolumeChange = { onProfileChange(profile.copy(audio = audio.with(category, it))) },
            onPreview = {
                val now = currentAudio()
                val level = now.level(category)
                if (Volumes.isOff(level)) speakAt("$title off.", now.master)
                else speakAt("$title, ${Volumes.spokenLabel(level)}.", now.loudness(category))
            },
            // The answers are what VISOR is for, so turning them off says so.
            warning = answersOff.takeIf {
                category == VoiceCategory.ANSWERS && Volumes.isOff(audio.level(category))
            },
        )
    }

    SettingSubsection(stringResource(R.string.settings_volume_effects_title))
    SoundCategory.entries.forEach { category ->
        val title = stringResource(category.titleRes())
        VolumeSlider(
            title = title,
            description = stringResource(category.descriptionRes()),
            volume = audio.level(category),
            onVolumeChange = { onProfileChange(profile.copy(audio = audio.with(category, it))) },
            onPreview = {
                val now = currentAudio()
                // Off is silent, so say so rather than play nothing.
                if (Volumes.isOff(now.level(category))) speakAt("$title off.", now.master)
                else playSample(category)
            },
        )
    }
}

/**
 * One volume slider, in ten-percent notches from off to full volume.
 * [onPreview] runs once the slider is let go, or after each screen reader
 * adjustment, to play what the new volume sounds like. It is given the volume
 * from before that change, so a slider turned all the way off can still be
 * heard confirming it.
 */
@Composable
private fun VolumeSlider(
    title: String,
    description: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onPreview: (before: Float) -> Unit,
    warning: String? = null,
) {
    // The volume as this drag, or screen reader adjustment, began.
    var before by remember { mutableStateOf<Float?>(null) }

    Text(
        text = "$title: ${Volumes.label(volume)}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(description, style = MaterialTheme.typography.bodyMedium)
    VisorSlider(
        value = volume,
        onValueChange = { position ->
            if (before == null) before = volume
            val snapped = Volumes.snap(position)
            if (snapped != volume) onVolumeChange(snapped)
        },
        onValueChangeFinished = {
            onPreview(before ?: volume)
            before = null
        },
        valueRange = Volumes.Off..Volumes.Max,
        steps = Volumes.SliderSteps,
        label = title,
        valueDescription = Volumes.spokenLabel(volume),
        decreaseDescription = stringResource(R.string.settings_volume_down_button, title),
        increaseDescription = stringResource(R.string.settings_volume_up_button, title),
    )
    SliderEnds(
        start = stringResource(R.string.settings_volume_off),
        end = stringResource(R.string.settings_volume_louder),
    )
    if (warning != null) VolumeWarning(warning)
}

/**
 * Something a volume setting has silenced, with an icon so it reads as a
 * warning without relying on color. A polite live region, so a screen reader
 * says it as it appears.
 */
@Composable
private fun VolumeWarning(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            modifier = Modifier.size(iconSizeFor(MaterialTheme.typography.bodyLarge)),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * "Commands": every way of asking VISOR for something without touching the
 * phone, each with its own switch, and the words each one listens for. All of
 * them are on until the user turns one off.
 */
@Composable
private fun CommandSettings(profile: UserProfile, onProfileChange: (UserProfile) -> Unit) {
    Text(
        stringResource(R.string.settings_commands_description),
        style = MaterialTheme.typography.bodyMedium,
    )

    SettingSubsection(stringResource(R.string.settings_camera_commands_title))
    Text(
        stringResource(R.string.settings_camera_commands_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    CameraCommand.entries.forEach { command ->
        val on = command in profile.cameraCommands
        // Each switch held close to its own phrases, and apart from the next
        // switch's, so it is clear which phrases a switch turns off.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            CommandSwitch(stringResource(command.titleRes()), on) {
                onProfileChange(
                    profile.copy(
                        cameraCommands = if (on) profile.cameraCommands - command
                        else profile.cameraCommands + command
                    )
                )
            }
            PhraseList(command.phrases)
        }
    }

    SettingSubsection(stringResource(R.string.settings_section_voice_navigation))
    Text(
        stringResource(R.string.settings_voice_navigation_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    CommandSwitch(stringResource(R.string.settings_section_voice_navigation), profile.voiceNavigationEnabled) {
        onProfileChange(profile.copy(voiceNavigationEnabled = !profile.voiceNavigationEnabled))
    }
    Text(
        stringResource(R.string.settings_voice_navigation_examples_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
    )
    // Indented under that heading: each kind, then what to say for it.
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(start = 12.dp),
    ) {
        VoiceCommandKind.entries.forEach { kind ->
            // One stop for a screen reader.
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    stringResource(kind.titleRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    kind.examples.joinToString(", ") { "\"$it\"" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    SettingSubsection(stringResource(R.string.settings_voice_launch_title))
    Text(
        stringResource(R.string.settings_voice_launch_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    CommandSwitch(stringResource(R.string.settings_voice_launch_switch), profile.voiceLaunchEnabled) {
        onProfileChange(profile.copy(voiceLaunchEnabled = !profile.voiceLaunchEnabled))
    }

    SettingSubsection(stringResource(R.string.settings_section_tap_navigation))
    Text(
        stringResource(R.string.settings_tap_navigation_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    CommandSwitch(stringResource(R.string.settings_section_tap_navigation), profile.glassesTapNavigationEnabled) {
        onProfileChange(profile.copy(glassesTapNavigationEnabled = !profile.glassesTapNavigationEnabled))
    }
}

/** An on/off switch for one command, drawn like the other switches here: "Read text", then ON or OFF. */
@Composable
private fun CommandSwitch(title: String, on: Boolean, onToggle: () -> Unit) {
    SwitchChip(title = title, on = on, onToggle = onToggle, modifier = Modifier.fillMaxWidth())
}

/**
 * What each end of a slider means, such as "Off" and "Louder", under its
 * minus and plus buttons. A screen reader skips them: the slider and its
 * buttons are named in full.
 */
@Composable
internal fun SliderEnds(start: String, end: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start, style = MaterialTheme.typography.bodyMedium)
        Text(end, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What to say for a command, one phrase to a line. A screen reader reads it as one stop. */
@Composable
private fun PhraseList(phrases: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(start = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(stringResource(R.string.settings_command_say), style = MaterialTheme.typography.bodyMedium)
        phrases.forEach { phrase ->
            Text(
                "\"$phrase\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 10.dp)
            .semantics { heading() },
    )
}

/** A heading inside a section, such as "VISOR's voice" under "How loud VISOR is". */
@Composable
private fun SettingSubsection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@StringRes
private fun VoiceCategory.titleRes(): Int = when (this) {
    VoiceCategory.ANSWERS -> R.string.settings_volume_answers_title
    VoiceCategory.SESSION_UPDATES -> R.string.settings_volume_session_updates_title
    VoiceCategory.VOICE_COMMANDS -> R.string.settings_volume_voice_commands_title
}

@StringRes
private fun VoiceCategory.descriptionRes(): Int = when (this) {
    VoiceCategory.ANSWERS -> R.string.settings_volume_answers_description
    VoiceCategory.SESSION_UPDATES -> R.string.settings_volume_session_updates_description
    VoiceCategory.VOICE_COMMANDS -> R.string.settings_volume_voice_commands_description
}

@StringRes
private fun SoundCategory.titleRes(): Int = when (this) {
    SoundCategory.THINKING_BEEPS -> R.string.settings_volume_thinking_beeps_title
    SoundCategory.SESSION_CHIMES -> R.string.settings_volume_session_chimes_title
}

@StringRes
private fun SoundCategory.descriptionRes(): Int = when (this) {
    SoundCategory.THINKING_BEEPS -> R.string.settings_volume_thinking_beeps_description
    SoundCategory.SESSION_CHIMES -> R.string.settings_volume_session_chimes_description
}

@StringRes
private fun CameraCommand.titleRes(): Int = when (this) {
    CameraCommand.READ_TEXT -> R.string.settings_command_read_text
    CameraCommand.IDENTIFY_OBJECTS -> R.string.settings_command_identify_objects
    CameraCommand.DESCRIBE_SCENE -> R.string.settings_command_describe_scene
    CameraCommand.ASK_QUESTION -> R.string.settings_command_ask_question
}

@StringRes
private fun VoiceCommandKind.titleRes(): Int = when (this) {
    VoiceCommandKind.GO_PLACES -> R.string.settings_voice_commands_go_places
    VoiceCommandKind.SESSIONS -> R.string.settings_voice_commands_sessions
    VoiceCommandKind.SETTINGS -> R.string.settings_voice_commands_settings
    VoiceCommandKind.ACCOUNT -> R.string.settings_voice_commands_account
    VoiceCommandKind.ANSWERING -> R.string.settings_voice_commands_answering
}
