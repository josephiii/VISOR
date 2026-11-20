package ucf.visor.data

import ucf.visor.lookaround.ScanPlan
import ucf.visor.stt.CameraCommand

/**
 * How VISOR fits itself to one user's vision: what setup asked (which kinds of
 * vision loss, how much it affects daily life, and their own words), turned
 * into what VISOR does differently for them.
 *
 * Nothing here is saved. It is worked out from the profile each time it is
 * needed, so a change in Settings > About you applies to the very next
 * picture. Two kinds of tailoring come out of it:
 *
 *  - **Behavior**, always following the profile: what the vision language
 *    model is asked to put first ([sceneGuidance]), which hazards are said
 *    first ([hazardFocus], [briefHazardLimit]), and the order of the camera
 *    features on Home and the glasses ([featureOrder]). No setting controls
 *    these, so they can never fight a choice the user made.
 *  - **Suggestions** the user owns once they accept them: the Detail level
 *    ([recommendedVerbosity]) and the look ([suggestsDarkHighContrast]).
 *    Setup offers them; Settings shows them; neither ever changes them silently.
 *
 * Pure Kotlin, so every rule here is unit-tested (VisionPlanTest).
 */
data class VisionPlan(
    /** The kinds of vision loss the user picked, without "Not sure". */
    val types: Set<VisionType>,
    val severity: Severity,
    /**
     * The user's own description, trimmed to [OwnWordsMaxLength], or null when
     * there is none or they chose not to share it with VISOR's AI.
     */
    val ownWords: String?,
) {

    /** Which hazards are said first: see [ucf.visor.tts.SpokenPhrases.hazardsAhead]. */
    enum class HazardFocus {
        /** Nearest first, wherever they are: today's order, and the default. */
        NEAREST,

        /** Left and right first: what someone with trouble seeing the sides would miss. */
        SIDES_FIRST,

        /** Straight ahead first: what someone with trouble seeing the center would miss. */
        AHEAD_FIRST,
    }

    /**
     * One thing VISOR does differently for this user, as the "Because you…"
     * lines in setup's last step and Settings > About you say it. Their words
     * are in strings.xml (tailoring_*), so a screen and a voice say the same.
     */
    enum class Tailoring {
        AHEAD_FIRST,
        SIDES_FIRST,
        WIDE_LOOK_AROUND,
        READ_DETAILS,
        CONTRAST_AND_LIGHT,
        SEVERE_MORE_SAFETY,
        MILD_SKIP_OBVIOUS,
        OWN_WORDS,
    }

    /** True when setup gave VISOR nothing to tailor to: it behaves as it always has. */
    val isUntailored: Boolean
        get() = types.isEmpty() && severity == Severity.MODERATE && ownWords == null

    /**
     * Sentences for the vision language model about who it is describing for,
     * added to every Describe the scene and Ask a question prompt (see
     * capture.ScenePrompts), or "" when there is nothing to add. Kept short:
     * the smaller models VISOR can run follow a long prompt worse.
     */
    val sceneGuidance: String
        get() = buildList {
            if (VisionType.CENTRAL_LOSS in types) {
                add(
                    "They can't see what they look straight at, but notice things to the sides. " +
                        "Start with what is directly ahead of them and name it exactly; read any text on it."
                )
            }
            if (VisionType.PERIPHERAL_LOSS in types) {
                add(
                    "They only see a narrow area straight ahead. Start with what is on their left and " +
                        "right that they could miss, above all anything they could walk into."
                )
            }
            if (VisionType.BLUR_LOW_ACUITY in types) {
                add(
                    "Everything looks blurry to them: they see shapes but not details. " +
                        "Read any text, and give exact numbers, like prices, times and signs."
                )
            }
            if (VisionType.CONTRAST_LIGHT in types) {
                add(
                    "Low contrast and bright or dim light are hard for them. Mention glare or dim light, " +
                        "point out things that blend into what is behind them, like a clear glass or the " +
                        "edge of a step, and name colors."
                )
            }
            when (severity) {
                Severity.SEVERE -> add(
                    "They see very little, so describe things as you would for someone who can't check " +
                        "with their eyes, and mention anything they could trip over or walk into."
                )
                Severity.MILD -> add("They see fairly well, so skip what is obvious.")
                Severity.MODERATE -> Unit
            }
            ownWords?.let {
                add(
                    "In their own words, their vision is like this: \"$it\". " +
                        "Use this to choose what to mention; don't repeat it back."
                )
            }
        }.joinToString(" ")

    /** Which hazards are said first, from which part of their sight is missing. */
    val hazardFocus: HazardFocus
        get() {
            val center = VisionType.CENTRAL_LOSS in types
            val sides = VisionType.PERIPHERAL_LOSS in types
            return when {
                sides && !center -> HazardFocus.SIDES_FIRST
                center && !sides -> HazardFocus.AHEAD_FIRST
                // Both, or neither: nearest first is the safest order.
                else -> HazardFocus.NEAREST
            }
        }

    /**
     * How many hazards a Brief answer names. Someone who sees very little
     * can't check the rest with their eyes, so they hear more of them.
     */
    val briefHazardLimit: Int
        get() = if (severity == Severity.SEVERE) SevereBriefHazardLimit else BriefHazardLimit

    /**
     * The camera features, most useful to this user first, for Home and the
     * glasses. Each kind of vision loss gives points to the features it needs
     * most; ties keep the usual order (hazards at the top), as does "Not sure".
     * The order only changes when the user changes their answers, so each
     * button stays where their fingers learned it.
     */
    val featureOrder: List<CameraCommand>
        get() {
            val points = CameraCommand.entries.associateWith { command ->
                types.sumOf { type -> FeaturePoints[type]?.get(command) ?: 0 }
            }
            // sortedByDescending is stable, so ties keep CameraCommand's own order.
            return CameraCommand.entries.sortedByDescending { points.getValue(it) }
        }

    /**
     * Where Look around takes its pictures. Someone with trouble seeing the
     * sides gets the wide scan, out to about 90° each side: what a single
     * picture leaves out is the part of the room they are most likely to miss.
     */
    val lookAroundPlan: ScanPlan
        get() = if (VisionType.PERIPHERAL_LOSS in types) ScanPlan.WIDE else ScanPlan.STANDARD

    /** The Detail level setup marks "Recommended for you". */
    val recommendedVerbosity: Verbosity
        get() = when (severity) {
            Severity.SEVERE -> Verbosity.DETAILED
            Severity.MILD -> Verbosity.BRIEF
            Severity.MODERATE -> Verbosity.STANDARD
        }

    /**
     * True when VISOR suggests its dark, high-contrast look: for light
     * sensitivity (dark gives off less light) and low contrast (high contrast).
     */
    val suggestsDarkHighContrast: Boolean
        get() = VisionType.CONTRAST_LIGHT in types

    /** Everything VISOR does differently for this user, in the order they are explained. */
    val tailorings: List<Tailoring>
        get() = buildList {
            when (hazardFocus) {
                HazardFocus.AHEAD_FIRST -> add(Tailoring.AHEAD_FIRST)
                HazardFocus.SIDES_FIRST -> add(Tailoring.SIDES_FIRST)
                HazardFocus.NEAREST -> Unit
            }
            if (VisionType.PERIPHERAL_LOSS in types) add(Tailoring.WIDE_LOOK_AROUND)
            if (VisionType.BLUR_LOW_ACUITY in types) add(Tailoring.READ_DETAILS)
            if (VisionType.CONTRAST_LIGHT in types) add(Tailoring.CONTRAST_AND_LIGHT)
            when (severity) {
                Severity.SEVERE -> add(Tailoring.SEVERE_MORE_SAFETY)
                Severity.MILD -> add(Tailoring.MILD_SKIP_OBVIOUS)
                Severity.MODERATE -> Unit
            }
            if (ownWords != null) add(Tailoring.OWN_WORDS)
        }

    companion object {

        /** The longest description sent with a picture; enough for a few sentences. */
        const val OwnWordsMaxLength = 300

        /** How many hazards a Brief answer names, as it always has. */
        const val BriefHazardLimit = 3

        /** How many a Brief answer names for someone whose vision affects daily life a lot. */
        const val SevereBriefHazardLimit = 5

        /** Points each kind of vision loss gives the features it needs most (see [featureOrder]). */
        private val FeaturePoints: Map<VisionType, Map<CameraCommand, Int>> = mapOf(
            VisionType.CENTRAL_LOSS to mapOf(CameraCommand.READ_TEXT to 3, CameraCommand.IDENTIFY_OBJECTS to 2),
            VisionType.PERIPHERAL_LOSS to mapOf(
                CameraCommand.CHECK_HAZARDS to 3,
                CameraCommand.LOOK_AROUND to 2,
                CameraCommand.DESCRIBE_SCENE to 1,
            ),
            VisionType.BLUR_LOW_ACUITY to mapOf(CameraCommand.READ_TEXT to 3, CameraCommand.IDENTIFY_OBJECTS to 1),
            VisionType.CONTRAST_LIGHT to mapOf(CameraCommand.DESCRIBE_SCENE to 3, CameraCommand.CHECK_HAZARDS to 1),
        )

        fun from(profile: UserProfile): VisionPlan = VisionPlan(
            types = profile.visionTypes - VisionType.NOT_SURE,
            severity = profile.severity,
            ownWords = if (profile.shareVisionDescription) ownWords(profile.visionDescription) else null,
        )

        /**
         * [description] made fit to send: one line, no double quotes (it is
         * quoted in the prompt), and cut at a word to [OwnWordsMaxLength].
         * Null when nothing is left.
         */
        internal fun ownWords(description: String): String? {
            val oneLine = description.replace('"', '\'').replace(Regex("\\s+"), " ").trim()
            if (oneLine.isEmpty()) return null
            if (oneLine.length <= OwnWordsMaxLength) return oneLine
            val cut = oneLine.take(OwnWordsMaxLength)
            val lastSpace = cut.lastIndexOf(' ')
            return (if (lastSpace > OwnWordsMaxLength / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
        }

        /**
         * Eye conditions people often name, and the kind of vision loss each
         * usually brings. Setup offers the matching answer when someone names
         * one in their own words; it never adds it without asking.
         */
        private val Conditions: List<Pair<List<String>, Set<VisionType>>> = listOf(
            listOf("glaucoma", "retinitis pigmentosa", "tunnel vision", "hemianopia", "usher syndrome") to
                setOf(VisionType.PERIPHERAL_LOSS),
            listOf("macular degeneration", "macular", "amd", "stargardt", "blind spot in the middle") to
                setOf(VisionType.CENTRAL_LOSS),
            listOf("cataract", "cataracts") to setOf(VisionType.BLUR_LOW_ACUITY, VisionType.CONTRAST_LIGHT),
            listOf("diabetic retinopathy", "retinopathy", "keratoconus", "nystagmus") to
                setOf(VisionType.BLUR_LOW_ACUITY),
            listOf("night blindness", "night blind", "photophobia", "albinism", "color blind", "colour blind", "achromatopsia") to
                setOf(VisionType.CONTRAST_LIGHT),
        )

        /**
         * The kinds of vision loss the conditions named in [description]
         * usually bring, each with the name that suggested it, leaving out
         * those in [chosen]. Whole words only, so "amd" is not found in "Amdi".
         */
        fun suggestedTypes(description: String, chosen: Set<VisionType>): Map<VisionType, String> {
            val text = " " + description.lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex(" +"), " ") + " "
            val found = linkedMapOf<VisionType, String>()
            for ((names, types) in Conditions) {
                val named = names.firstOrNull { " $it " in text } ?: continue
                for (type in types) if (type !in chosen && type !in found) found[type] = named
            }
            return found
        }
    }
}
