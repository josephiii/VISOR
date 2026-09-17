package ucf.visor.ui.viewmodel

enum class SessionMode { HAZARD, SCENE, READER }
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