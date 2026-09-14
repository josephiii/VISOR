package ucf.visor.ui.glasses

import com.meta.wearable.dat.display.views.ButtonStyle
import com.meta.wearable.dat.display.views.IconName
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

/** The full on-glasses nav button set for one moment of the app's state. */
data class GlassesNavScreen(
    val title: String,
    val items: List<GlassesNavItem>,
)

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
        return GlassesNavScreen(title = "VISOR", items = emptyList())
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

    val items = mutableListOf<GlassesNavItem>()

    if (route == "home") {
        items += if (uiState.isSessionActive) {
            GlassesNavItem("End Session", IconName.X, VoiceCommand.ToggleSession)
        } else {
            GlassesNavItem("Start Session", IconName.TRIANGLE_RIGHT, VoiceCommand.ToggleSession)
        }
    } else {
        items += GlassesNavItem("Home", IconName.HOUSE, VoiceCommand.GoHome)
    }

    if (route != "settings") {
        items += GlassesNavItem("Settings", IconName.GEAR, VoiceCommand.OpenSettings)
    }

    if (route != "hardware_pairing") {
        items += GlassesNavItem("Pair Glasses", IconName.SMART_GLASSES, VoiceCommand.PairDevice)
    }

    if (route != "help") {
        items += GlassesNavItem("Help", IconName.I_CIRCLE, VoiceCommand.OpenHelp)
    }

    if (route != "home") {
        items += GlassesNavItem(
            "Back",
            IconName.ARROW_LEFT,
            VoiceCommand.GoBack,
            ButtonStyle.SECONDARY
        )
    }

    val title = when (route) {
        "home" -> "Home"
        "settings" -> "Settings"
        "hardware_pairing" -> "Pair Glasses"
        "help" -> "Help"
        else -> "VISOR"
    }
    return GlassesNavScreen(title = title, items = items)
}
