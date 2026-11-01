package ucf.visor.ui.viewmodel

import ucf.visor.stt.CameraCommand

/**
 * What a camera feature (one of Home's buttons, or a wake phrase such as
 * "VISOR, read this") is doing. Home's button for that feature shows it in
 * place of its label, so a user can see VISOR is working even with its sounds
 * turned off.
 */
enum class CaptureStatus { IDLE, TAKING_PICTURE, THINKING }

/**
 * The last thing a camera feature said in answer, or why it could not, and
 * when. Home's Last answer card shows it, so an answer missed on a noisy
 * street, or with Answers turned down, can still be read or heard again.
 *
 * [command] is the feature that answered (or, for a Phase 1 demo script, the
 * one it stands in for), shown with it: four features feed the card, and "No
 * text found" means more beside "Read text".
 */
data class LastAnswer(val text: String, val atMillis: Long, val command: CameraCommand)
