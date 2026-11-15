package ucf.visor.ui.viewmodel

/**
 * The parts Settings is split into. Settings opens on a hub of large tiles,
 * one per part, and each opens a short screen of its own: as one page,
 * Settings ran to about eight screens at Extra large text.
 *
 * A part with a [parent] opens from inside that part, not from the hub, and
 * Back returns to it: My colors ([COLORS]) is in Text and display.
 */
enum class SettingsSection(val parent: SettingsSection? = null) {
    ABOUT_YOU, SPEECH, SOUND, DISPLAY, COMMANDS, HISTORY, ACCOUNT,
    COLORS(parent = DISPLAY),
    ;

    /** Shown as a tile on the Settings hub. */
    val onHub: Boolean get() = parent == null
}
