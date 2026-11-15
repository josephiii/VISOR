package ucf.visor.data

/**
 * The parts of VISOR's look a user can color for themself in Settings > Text
 * and display > My colors (ui/screens/profile/ThemeEditor). Six, named as a
 * user sees them, not the ~35 roles of Material's ColorScheme: VISOR works the
 * rest out from these (see ui/theme/CustomTheme).
 */
enum class ThemeElement {
    /** The page behind everything. */
    BACKGROUND,

    /** Settings' tiles, cards, and chips that aren't selected. */
    SURFACE,

    /** Text, and the icons beside it. */
    TEXT,

    /** Filled buttons, headings, and the edge of a selected chip. */
    BUTTONS,

    /** What is selected: the fill of a chosen chip or a switch that is on. */
    HIGHLIGHT,

    /** Borders around controls, and the ring around the one with focus. */
    OUTLINE,
}

/**
 * Which of VISOR's own four looks a user's colors start from. The ones a
 * user doesn't change keep that look's exact colors.
 */
enum class ThemeBase(val dark: Boolean, val highContrast: Boolean) {
    CLARITY_LIGHT(dark = false, highContrast = false),
    CLARITY_DARK(dark = true, highContrast = false),
    HIGH_CONTRAST_LIGHT(dark = false, highContrast = true),
    HIGH_CONTRAST_DARK(dark = true, highContrast = true),
    ;

    companion object {
        fun of(dark: Boolean, highContrast: Boolean): ThemeBase =
            entries.first { it.dark == dark && it.highContrast == highContrast }
    }
}

/**
 * A user's own colors: one of VISOR's looks to start from ([base]), and only
 * the colors they changed ([chosen], each an opaque ARGB int). Kept even while
 * VISOR's own colors are in use ([UserProfile.useCustomColors] off), so
 * switching back finds them as they were.
 *
 * Plain Kotlin, no Compose, so the data layer and its tests don't need it.
 */
data class CustomColors(
    val base: ThemeBase = ThemeBase.CLARITY_LIGHT,
    val chosen: Map<ThemeElement, Int> = emptyMap(),
) {
    /** True until the user changes a color: these colors are then exactly [base]'s. */
    val isUnchanged: Boolean get() = chosen.isEmpty()

    fun with(element: ThemeElement, argb: Int): CustomColors =
        copy(chosen = chosen + (element to (argb or OPAQUE)))

    fun without(element: ThemeElement): CustomColors = copy(chosen = chosen - element)

    /**
     * One line, for saving (ProfileStore) and for surviving a rotation
     * (the editor's rememberSaveable): `base=CLARITY_DARK;TEXT=FFF4F8FC;…`.
     */
    fun encode(): String = buildString {
        append("base=").append(base.name)
        ThemeElement.entries.forEach { element ->
            chosen[element]?.let { append(';').append(element.name).append('=').append(hex(it)) }
        }
    }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        private fun hex(argb: Int): String = "%08X".format(argb)

        /**
         * Reads what [encode] wrote. Anything it doesn't understand (a part
         * renamed in a later version, a damaged value) is left out rather than
         * failing, as ProfileStore does for every saved setting.
         */
        fun decode(text: String?): CustomColors {
            if (text.isNullOrBlank()) return CustomColors()
            var base = ThemeBase.CLARITY_LIGHT
            val chosen = mutableMapOf<ThemeElement, Int>()
            text.split(';').forEach { part ->
                val key = part.substringBefore('=', "").trim()
                val value = part.substringAfter('=', "").trim()
                if (key == "base") {
                    base = runCatching { ThemeBase.valueOf(value) }.getOrDefault(base)
                } else {
                    val element = runCatching { ThemeElement.valueOf(key) }.getOrNull()
                    val argb = value.toLongOrNull(16)?.toInt()
                    if (element != null && argb != null) chosen[element] = argb or OPAQUE
                }
            }
            return CustomColors(base, chosen)
        }
    }
}
