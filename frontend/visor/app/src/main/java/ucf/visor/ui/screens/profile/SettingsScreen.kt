package ucf.visor.ui.screens.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ucf.visor.R
import ucf.visor.data.AudioMix
import ucf.visor.data.Severity
import ucf.visor.data.SoundCategory
import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.UserProfile
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionType
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import ucf.visor.data.label
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.components.SelectableChip
import ucf.visor.ui.components.scrollIndicator
import ucf.visor.ui.theme.VisorShapes
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceCommandKind

/**
 * Settings — reads and writes the same UserProfile the creation wizard fills.
 *
 * @param speakAt and [playSample] play what a new volume sounds like, as soon
 *   as a volume slider is let go: loudness can't be judged by looking at a
 *   slider. [speakAt] speaks at the volume given, whatever the sliders are.
 */
@Composable
fun SettingsScreen(
    viewModel: VisorViewModel,
    speakAt: (String, Float) -> Unit = { _, _ -> },
    playSample: (SoundCategory) -> Unit = {},
    onProfileChange: (UserProfile) -> Unit = {},
    onLogout: () -> Unit = {},          // TODO: revoke access + refresh tokens (frontend-only per Joseph)
    onDeleteAccount: () -> Unit = {},   // VisorViewModel.confirmDeleteAccount: DELETE /auth/deleteAccount, then wipes the profile
    onHelp: () -> Unit = {},
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val showLogoutConfirm = uiState.isLogoutConfirmVisible
    val showDeleteConfirm = uiState.isDeleteAccountConfirmVisible
    val speechRateDescription = stringResource(
        R.string.settings_speech_rate_description,
        SpeechRates.spokenLabel(profile.speechRate),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .scrollIndicator(scrollState, MaterialTheme.colorScheme.outline)
            .verticalScroll(scrollState)
            // Inside the scroll, so the settings scroll right up to the navigation
            // bar instead of disappearing 24dp above it.
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )

        SettingSection(stringResource(R.string.settings_section_about_you))
        OutlinedTextField(
            value = profile.displayName,
            onValueChange = { onProfileChange(profile.copy(displayName = it)) },
            label = { Text(stringResource(R.string.settings_name_label)) },
            singleLine = true,
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            stringResource(R.string.step_vision_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VisionType.entries.forEach { type ->
                val selected = type in profile.visionTypes
                SelectableChip(
                    label = type.label(),
                    selected = selected,
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

        Text(stringResource(R.string.step_severity_title), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Severity.entries.forEach { s ->
                SelectableChip(
                    label = s.label(),
                    selected = profile.severity == s,
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(severity = s)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = profile.visionDescription,
            onValueChange = { onProfileChange(profile.copy(visionDescription = it)) },
            label = { Text(stringResource(R.string.settings_vision_description_label)) },
            shape = VisorShapes.Control,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingSection(stringResource(R.string.settings_section_speech_rate))
        Text(
            text = SpeechRates.label(profile.speechRate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = profile.speechRate,
            onValueChange = { position ->
                val rate = SpeechRates.snap(position)
                if (rate != profile.speechRate) onProfileChange(profile.copy(speechRate = rate))
            },
            valueRange = SpeechRates.Min..SpeechRates.Max,
            steps = SpeechRates.SliderSteps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = speechRateDescription },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_speech_rate_slower), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.settings_speech_rate_faster), style = MaterialTheme.typography.bodySmall)
        }

        SettingSection(stringResource(R.string.settings_section_detail))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(verbosity = v)) }
            }
        }

        // Next to speed and detail: together, everything about what VISOR sounds like.
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

        SettingSection(stringResource(R.string.settings_section_text_size))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    showCheckmark = false,
                    modifier = Modifier.weight(1f),
                ) { onProfileChange(profile.copy(textScale = scale)) }
            }
        }

        SettingSection(stringResource(R.string.settings_section_display))
        SelectableChip(
            label = stringResource(
                if (profile.appHighContrast) R.string.settings_high_contrast_on
                else R.string.settings_high_contrast_off
            ),
            selected = profile.appHighContrast,
            modifier = Modifier.fillMaxWidth(),
        ) { onProfileChange(profile.copy(appHighContrast = !profile.appHighContrast)) }

        CommandSettings(profile = profile, onProfileChange = onProfileChange)

        Spacer(Modifier.height(24.dp))
        SettingSection(stringResource(R.string.settings_section_support))
        SelectableChip(
            stringResource(R.string.settings_help),
            selected = false,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onHelp,
        )

        Spacer(Modifier.height(24.dp))
        SettingSection(stringResource(R.string.settings_section_account))
        SelectableChip(
            stringResource(R.string.settings_log_out),
            selected = false,
            vital = true,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.requestLogoutConfirm() },
        )
        SelectableChip(
            stringResource(R.string.settings_delete_account),
            selected = false,
            vital = true,
            showCheckmark = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.requestDeleteAccountConfirm() },
        )
    }

    // Both actions are hard to undo (logging out mid-session, or permanent
    // deletion) so they get a confirm step rather than firing immediately.
    // This state lives in VisorUiState (not local remember) so a spoken
    // "log out"/"delete my account" can open the same dialog a tap would.
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogoutConfirm() },
            title = { Text(stringResource(R.string.settings_log_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_log_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm(); onLogout() }) {
                    Text(stringResource(R.string.settings_log_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelLogoutConfirm() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAccountConfirm() },
            title = { Text(stringResource(R.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_account_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm(); onDeleteAccount() }) {
                    Text(stringResource(R.string.settings_delete_account_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteAccountConfirm() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
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
    Slider(
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
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = title
                stateDescription = Volumes.spokenLabel(volume)
            },
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.settings_volume_off), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_volume_louder), style = MaterialTheme.typography.bodySmall)
    }
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
        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null)
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
    SettingSection(stringResource(R.string.settings_section_commands))
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
    SelectableChip(
        label = stringResource(
            if (profile.voiceNavigationEnabled) R.string.settings_voice_navigation_on
            else R.string.settings_voice_navigation_off
        ),
        selected = profile.voiceNavigationEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) { onProfileChange(profile.copy(voiceNavigationEnabled = !profile.voiceNavigationEnabled)) }
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
    SelectableChip(
        label = stringResource(
            if (profile.glassesTapNavigationEnabled) R.string.settings_tap_navigation_on
            else R.string.settings_tap_navigation_off
        ),
        selected = profile.glassesTapNavigationEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        onProfileChange(profile.copy(glassesTapNavigationEnabled = !profile.glassesTapNavigationEnabled))
    }
}

/** An on/off switch for one command, drawn like the other toggles here: "Read text: ON". */
@Composable
private fun CommandSwitch(title: String, on: Boolean, onToggle: () -> Unit) {
    SelectableChip(
        label = stringResource(if (on) R.string.settings_command_on else R.string.settings_command_off, title),
        selected = on,
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
    )
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
}

@StringRes
private fun VoiceCommandKind.titleRes(): Int = when (this) {
    VoiceCommandKind.GO_PLACES -> R.string.settings_voice_commands_go_places
    VoiceCommandKind.SESSIONS -> R.string.settings_voice_commands_sessions
    VoiceCommandKind.SETTINGS -> R.string.settings_voice_commands_settings
    VoiceCommandKind.ACCOUNT -> R.string.settings_voice_commands_account
    VoiceCommandKind.ANSWERING -> R.string.settings_voice_commands_answering
}
