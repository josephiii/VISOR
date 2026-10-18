package ucf.visor.ui.viewmodel

/**
 * What a camera feature (Home's capture button, or a wake phrase such as
 * "VISOR, read this") is doing. Home's capture button shows it in place of its
 * label, so a user can see VISOR is working even with its sounds turned off.
 */
enum class CaptureStatus { IDLE, TAKING_PICTURE, THINKING }

/**
 * The last thing a camera feature said in answer, or why it could not, and
 * when. Home's Last answer card shows it, so an answer missed on a noisy
 * street, or with Answers turned down, can still be read or heard again.
 */
data class LastAnswer(val text: String, val atMillis: Long)
