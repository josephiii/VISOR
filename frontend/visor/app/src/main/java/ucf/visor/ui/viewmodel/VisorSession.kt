package ucf.visor.ui.viewmodel

enum class SessionMode { HAZARD, SCENE, READER }

/** What [VisorViewModel.toggleSession] did, for the caller to announce. */
sealed interface SessionChange {
    data object Started : SessionChange
    data object Ended : SessionChange

    /** The session could not start: [problem] says why, in words fit to speak. */
    data class NotStarted(val problem: String) : SessionChange
}

data class VisorSession(
    val inHazardAwarenessMode: Boolean = true, // Default.
    val inSceneDescriptionMode: Boolean = false,
    val inReadingAssistanceMode: Boolean = false,
) {
    val mode: SessionMode = when {
        inHazardAwarenessMode -> SessionMode.HAZARD
        inSceneDescriptionMode -> SessionMode.SCENE
        else -> SessionMode.READER
    }
}