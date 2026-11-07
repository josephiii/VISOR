package ucf.visor.capture

import ucf.visor.stt.CameraCommand

/**
 * Whether a feature can be used right now, given the phone's connection.
 * Online, every feature is [AVAILABLE]. Offline, each camera feature is one
 * of the three, and every way of starting it (Home's buttons, the glasses'
 * buttons, its wake phrase) shows and says the same thing.
 */
enum class Availability {
    /** Works as usual. */
    AVAILABLE,

    /** Works, but does less than usual: still offered, with a note saying what it does instead. */
    LIMITED,

    /** Can't work: shown locked, and says why when it is asked for. */
    LOCKED,
}

/**
 * What [this] camera feature can do with ([isOnline]) or without VISOR's
 * server. See documentation/research/phase1-offline-capabilities.md.
 *
 *  - Read text and Identify objects run ML Kit on the phone, with its models
 *    in the app: they work the same offline.
 *  - Describe the scene asks the server's vision language model. Offline it
 *    names the objects it sees with ML Kit instead ("I see cup, table.").
 *  - Check for hazards (YOLO and depth on the server) and Ask a question (the
 *    server's VLM) have nothing on the phone to fall back on.
 */
fun CameraCommand.availability(isOnline: Boolean): Availability = when {
    isOnline -> Availability.AVAILABLE
    else -> when (this) {
        CameraCommand.READ_TEXT, CameraCommand.IDENTIFY_OBJECTS -> Availability.AVAILABLE
        CameraCommand.DESCRIBE_SCENE -> Availability.LIMITED
        CameraCommand.CHECK_HAZARDS, CameraCommand.ASK_QUESTION -> Availability.LOCKED
    }
}
