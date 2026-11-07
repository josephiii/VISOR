package ucf.visor.ui.glasses

import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.IconName
import ucf.visor.capture.Availability
import ucf.visor.capture.availability
import ucf.visor.stt.CameraCommand
import ucf.visor.ui.viewmodel.VisorUiState
import ucf.visor.ui.voice.VoiceCommand

/**
 * One button rendered on the glasses' own screen. [command] is emitted on
 * [GlassesNavigationController.commands] when the button is tapped/selected —
 * the exact same [VoiceCommand] type VoiceNavigationController emits, so
 * VisorLayout's single dispatcher handles a glasses tap identically to a
 * spoken command, with no separate glasses-specific cases to keep in sync.
 * Every item carries a real icon (no "no icon" option): the display's 20°
 * FoV leaves little room for label text, so the icon is load-bearing, not
 * decorative — see documentation/research/meta-mrbd-capabilities.md.
 */
data class GlassesNavItem(
    val label: String,
    val icon: IconName,
    val command: VoiceCommand,
    val style: ButtonStyle = ButtonStyle.PRIMARY,
)

/**
 * The full on-glasses nav button set for one moment of the app's state.
 * [subtitle] carries a status line for states that have no buttons to offer,
 * so the glasses still show something the wearer can see — without it, a
 * screen with no items would render blank and be indistinguishable from the
 * display never having connected at all.
 */
data class GlassesNavScreen(
    val title: String,
    val items: List<GlassesNavItem>,
    val subtitle: String? = null,
)

/**
 * The most buttons shown on the glasses at once. The display is about 20°
 * across, and six buttons crowded it past what a low-vision wearer could pick
 * out; the rest go on further pages, behind a More button.
 */
const val MaxGlassesButtons = 4

/**
 * The buttons for page [index] of a screen, [count] pages in all. With more
 * than one page, the last of each page's [MaxGlassesButtons] places is More,
 * which goes on to the next page and from the last back to the first.
 */
data class GlassesNavPage(val items: List<GlassesNavItem>, val index: Int, val count: Int) {
    val hasMore: Boolean get() = count > 1
}

/** Page [index] of this screen's buttons; any index wraps round onto a real page. */
fun GlassesNavScreen.page(index: Int): GlassesNavPage {
    if (items.size <= MaxGlassesButtons) return GlassesNavPage(items, index = 0, count = 1)
    val pages = items.chunked(MaxGlassesButtons - 1)
    val shown = index.mod(pages.size)
    return GlassesNavPage(pages[shown], index = shown, count = pages.size)
}

/**
 * Chooses which buttons appear on the glasses for the phone's current screen —
 * the on-glasses mirror of [ucf.visor.ui.components.VisorNavigationBar] (touch)
 * and [ucf.visor.ui.voice.VoiceCommandParser] (voice), so all three input
 * methods reach the same destinations. Kept as a pure function (route + state
 * in, screen out) so it needs no Compose/MWDAT session context to reason about
 * or test.
 */
fun glassesNavScreenFor(route: String?, uiState: VisorUiState): GlassesNavScreen {
    // Pre-login screens (title/login/sign-up/etc.) have no touch nav bar and no
    // voice-reachable destinations either — see VisorLayout's VoiceCommand.GoHome
    // handling — so the glasses fallback stays quiet there too.
    if (!uiState.isAuthComplete) {
        return GlassesNavScreen(
            title = "VISOR",
            items = emptyList(),
            subtitle = "Connected. Log in on your phone to continue.",
        )
    }

    // Logout/delete-account confirmation is a modal step in front of Settings —
    // mirror that on the glasses instead of showing normal nav underneath it.
    if (uiState.isLogoutConfirmVisible) {
        return GlassesNavScreen(
            title = "Log out?",
            items = listOf(
                GlassesNavItem("Confirm", IconName.CHECKMARK, VoiceCommand.Confirm),
                GlassesNavItem(
                    "Cancel",
                    IconName.X,
                    VoiceCommand.Cancel,
                    ButtonStyle.SECONDARY
                ),
            ),
        )
    }
    if (uiState.isDeleteAccountConfirmVisible) {
        return GlassesNavScreen(
            title = "Delete account?",
            items = listOf(
                GlassesNavItem("Confirm", IconName.CHECKMARK, VoiceCommand.Confirm),
                GlassesNavItem(
                    "Cancel",
                    IconName.X,
                    VoiceCommand.Cancel,
                    ButtonStyle.SECONDARY
                ),
            ),
        )
    }

    // Home is for its camera buttons, and so are the glasses there: the four
    // that take a picture straight away fill the display exactly, with no More.
    // The rest of the navigation is on every other screen, with a Home button
    // back to these.
    //
    // Offline, the same four stay in the same places, so a wearer's habits
    // still work; the ones that need the internet show a padlock and the
    // quieter style, and a tap on one says why it can't run (MainActivity).
    if (route == "home") {
        return GlassesNavScreen(
            title = "Home",
            items = CameraCommand.entries.mapNotNull { cameraItem(it, uiState.isOnline) },
            subtitle = if (uiState.isOnline) null else OFFLINE_SUBTITLE,
        )
    }

    val items = mutableListOf<GlassesNavItem>()

    items += GlassesNavItem("Home", IconName.HOUSE, VoiceCommand.GoHome)

    if (route != "settings") {
        items += GlassesNavItem("Settings", IconName.GEAR, VoiceCommand.OpenSettings)
    }

    if (route != "hardware_pairing") {
        items += GlassesNavItem("Pair glasses", IconName.SMART_GLASSES, VoiceCommand.PairDevice)
    }

    if (route != "help") {
        items += GlassesNavItem("Help", IconName.I_CIRCLE, VoiceCommand.OpenHelp)
    }

    items += GlassesNavItem(
        "Back",
        IconName.ARROW_LEFT,
        VoiceCommand.GoBack,
        ButtonStyle.SECONDARY
    )

    val title = when (route) {
        "settings" -> "Settings"
        "hardware_pairing" -> "Pair glasses"
        "help" -> "Help"
        "motion_lab" -> "Head-motion lab"
        "p1_test_mode_config" -> "Phase 1 scripts"
        else -> "VISOR"
    }
    return GlassesNavScreen(title = title, items = items, subtitle = if (uiState.isOnline) null else OFFLINE_SUBTITLE)
}

/** Under each screen's title on the glasses while the phone is offline. */
const val OFFLINE_SUBTITLE = "Offline. Some features are locked."

/**
 * [cameraItem] as it is right now: offline, a feature that needs the internet
 * keeps its place and its label, with a padlock for its icon, in the quieter style.
 */
private fun cameraItem(command: CameraCommand, isOnline: Boolean): GlassesNavItem? {
    val item = cameraItem(command) ?: return null
    return if (command.availability(isOnline) == Availability.LOCKED) {
        item.copy(icon = IconName.PADLOCK_CLOSED, style = ButtonStyle.SECONDARY)
    } else {
        item
    }
}

/**
 * Home's button for [command], on the glasses. Its label matches the phone's
 * (R.string.camera_command_*), and its icon is the display's nearest to the
 * phone's: it has no book, so reading is a speech bubble, for being read to.
 *
 * Null for Ask a question, which has no button here: a fifth would push
 * Describe the scene behind More, and asking means speaking anyway, where
 * "VISOR, I have a question" is as quick as a tap.
 */
private fun cameraItem(command: CameraCommand): GlassesNavItem? = when (command) {
    CameraCommand.CHECK_HAZARDS ->
        GlassesNavItem("Check for hazards", IconName.EXCLAMATION_TRIANGLE, VoiceCommand.RunCamera(command))
    CameraCommand.READ_TEXT ->
        GlassesNavItem("Read text", IconName.SPEECH_BUBBLE, VoiceCommand.RunCamera(command))
    CameraCommand.IDENTIFY_OBJECTS ->
        GlassesNavItem("Identify objects", IconName.FOUR_CORNER_FRAME, VoiceCommand.RunCamera(command))
    CameraCommand.DESCRIBE_SCENE ->
        GlassesNavItem("Describe the scene", IconName.MOUNTAIN_SQUARE, VoiceCommand.RunCamera(command))
    CameraCommand.ASK_QUESTION -> null
}
