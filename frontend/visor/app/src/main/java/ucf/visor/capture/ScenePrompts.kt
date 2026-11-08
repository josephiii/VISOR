package ucf.visor.capture

import ucf.visor.data.Verbosity

/**
 * What VISOR asks the vision language model, for "VISOR, what do you see?"
 * and for a spoken question, at the user's Detail setting (Settings > Speech).
 *
 * VISOR's users have low vision, not none: they can often make out shapes and
 * light, but not detail, small text or low contrast. So the answers say where
 * things are (on the left, ahead, on the right) for someone to look towards
 * or reach for, and Detailed adds what is hardest to see: color, text, and
 * what could be tripped over or walked into.
 */
object ScenePrompts {

    private const val WHERE =
        "Say where each thing is from their point of view: on their left, ahead, or on their right."

    private const val ALOUD = "Use plain words; your answer will be read aloud."

    /** The prompt for describing the scene in front of the user. */
    fun describe(verbosity: Verbosity): String = when (verbosity) {
        Verbosity.BRIEF ->
            "Describe this scene in one short sentence for a person with low vision. " +
                "Name only the most important thing and where it is: on their left, ahead, or on their right. " +
                ALOUD

        Verbosity.STANDARD ->
            "Describe this scene in two or three short sentences for a person with low vision. " +
                "Mention the main objects. $WHERE $ALOUD"

        Verbosity.DETAILED ->
            "Describe this scene in four to six short sentences for a person with low vision. " +
                "Mention the main objects, their colors, and any text you can read. " +
                "$WHERE Say roughly how far away things are, and mention anything they could trip over " +
                "or walk into. $ALOUD"
    }

    /** The prompt for answering [question], asked aloud about a fresh photo. */
    fun question(question: String, verbosity: Verbosity): String {
        val length = when (verbosity) {
            Verbosity.BRIEF -> "Answer it directly in one short sentence."
            Verbosity.STANDARD -> "Answer it directly in one to three short sentences."
            Verbosity.DETAILED -> "Answer it directly and fully, in up to five short sentences."
        }
        return "A person with low vision is asking about what is in front of them. " +
            "Question: \"$question\" $length " +
            "When it helps, say where things are: on their left, ahead, or on their right. " +
            "If the image doesn't show enough to answer, say so. $ALOUD"
    }
}
