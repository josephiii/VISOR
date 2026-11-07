package ucf.visor.ui.voice

import ucf.visor.data.SpeechRates
import ucf.visor.data.TextScale
import ucf.visor.data.Verbosity
import ucf.visor.ui.viewmodel.SettingsSection

/**
 * What VISOR says back when a voice command (or a glasses tap) has done what
 * was asked: where it went, or what it changed. A user who isn't looking at
 * the screen would otherwise hear nothing, and silence sounds like failure.
 * Each is short, so it is over before the next command.
 */
object VoiceReplies {
    const val HOME = "Home."
    const val SETTINGS = "Settings."
    const val HELP = "Help."
    const val PAIR = "Pair screen."
    const val BACK = "Going back."
    const val LOG_IN = "Log in."
    const val SIGN_UP = "Sign up."
    const val FORGOT_PASSWORD = "Forgot password."
    const val ALREADY_LOGGED_IN = "You're already logged in. To log out, say \"log out\"."
    const val CANCELLED = "Cancelled."
    const val NOTHING_TO_CONFIRM = "There's nothing to confirm right now."
    const val NOTHING_TO_CANCEL = "There's nothing to cancel right now."

    fun settingsSection(section: SettingsSection): String = when (section) {
        SettingsSection.ABOUT_YOU -> "About you settings."
        SettingsSection.SPEECH -> "Speech settings."
        SettingsSection.SOUND -> "Sound settings."
        SettingsSection.DISPLAY -> "Text and display settings."
        SettingsSection.COMMANDS -> "Command settings."
        SettingsSection.ACCOUNT -> "Account settings."
    }

    fun highContrast(on: Boolean): String = if (on) "High contrast on." else "High contrast off."

    fun speechRate(rate: Float): String = "Speech rate set to ${SpeechRates.spokenLabel(rate)}."

    fun detail(verbosity: Verbosity): String = "Detail set to " + when (verbosity) {
        Verbosity.BRIEF -> "brief."
        Verbosity.STANDARD -> "standard."
        Verbosity.DETAILED -> "detailed."
    }

    fun textSize(scale: TextScale): String = "Text size set to " + when (scale) {
        TextScale.STANDARD -> "standard."
        TextScale.LARGE -> "large."
        TextScale.EXTRA_LARGE -> "extra large."
    }
}
