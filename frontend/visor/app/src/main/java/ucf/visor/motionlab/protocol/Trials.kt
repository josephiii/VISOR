package ucf.visor.motionlab.protocol

/** How a trial's pacing cue reaches the wearer. */
enum class CueStyle {
    /** The cue word is spoken (TTS) after a short blip — for cues a second or more apart. */
    SPOKEN,

    /**
     * Alternating high/low tones, no speech — for fast pacing, where a spoken
     * word would still be playing when the next beat is due. High = first step
     * (e.g. left/up), low = second (right/down).
     */
    METRONOME,
}

/** Alternate through [steps] every [intervalMs] for the whole recording. */
data class Cue(
    val intervalMs: Long,
    val steps: List<String>,
    val style: CueStyle = CueStyle.SPOKEN,
)

/**
 * One fixed, repeatable procedure.
 *
 * @property camera whether the glasses camera streams during the trial. Only
 *   the vestibulo-ocular tier needs it; every other trial is IMU-only so its
 *   numbers stay comparable with the web IMU Lab's, and so the camera's power
 *   and heat do not colour the endurance trial.
 */
data class Trial(
    val id: String,
    val tier: String,
    val title: String,
    val worn: Boolean,
    val prepSec: Int,
    val durationSec: Int,
    val purpose: String,
    val setup: String,
    val instructions: List<String>,
    val cue: Cue? = null,
    val camera: Boolean = false,
)

/**
 * The head-motion lab's trial battery.
 *
 * Tiers A–D are the web IMU Lab battery (frontend/visor-webapp/protocol.js),
 * carried over with the same ids, timings and cues, so a trial recorded here
 * through MWDAT Motion can be analyzed next to the same trial recorded through
 * the browser's DeviceMotion API — the comparison that says what the native
 * path adds. See documentation/testing/imu-test-protocol.md for the rationale
 * behind each one.
 *
 * Tier V is new, and exists because MWDAT 1.0 can stream the glasses' IMU and
 * camera in the same session — something the Web Apps API cannot do (it has no
 * camera access). Each V trial records head rotation (gyroscope) and the image
 * shift that rotation produces in the head-fixed camera, which is the raw
 * material for the IMU-versus-image discrepancy analysis (analysis/visor_imu/vor.py).
 */
object Trials {

    val tiers: Map<String, String> = linkedMapOf(
        "V" to "Head motion vs. camera (VOR research)",
        "A" to "Instrument characterization",
        "B" to "Dynamic response",
        "C" to "Rehabilitation behaviour",
        "D" to "System constraints",
    )

    val all: List<Trial> = listOf(
        // ---------- Tier V: head motion vs. camera image shift (worn, camera on) ----------
        Trial(
            id = "V1_vor_yaw_paced",
            tier = "V",
            title = "Side-to-side head turns, eyes on a target",
            worn = true,
            prepSec = 10,
            durationSec = 45,
            purpose = "Yaw head rotation and the camera image shift it produces, at a paced " +
                "1 Hz — the movement of the VOR x1 gaze-stabilization exercise. Gives the " +
                "IMU-to-image lag, the pixels-per-degree scale and the residual discrepancy.",
            setup = "Sit facing a wall or scene with plenty of detail — shelves, pictures, " +
                "furniture — about two metres away. Not a blank wall: the camera needs " +
                "texture to measure. Pick one point at eye level and keep your eyes on it. " +
                "Turn your head gently left and right, about a hand's width each way, on each " +
                "tone: high tone left, low tone right.",
            instructions = listOf("Eyes on one point", "Turn left and right with the tones", "Small, steady turns"),
            cue = Cue(intervalMs = 500, steps = listOf("LEFT", "RIGHT"), style = CueStyle.METRONOME),
            camera = true,
        ),
        Trial(
            id = "V2_vor_pitch_paced",
            tier = "V",
            title = "Up-and-down nods, eyes on a target",
            worn = true,
            prepSec = 10,
            durationSec = 45,
            purpose = "Pitch rotation against vertical image shift at 1 Hz; checks the " +
                "second camera axis and the IMU-to-camera axis alignment.",
            setup = "Same place as the side-to-side trial. Keep your eyes on the same point " +
                "and nod gently up and down on each tone: high tone up, low tone down.",
            instructions = listOf("Eyes on one point", "Nod with the tones", "Small, steady nods"),
            cue = Cue(intervalMs = 500, steps = listOf("UP", "DOWN"), style = CueStyle.METRONOME),
            camera = true,
        ),
        Trial(
            id = "V3_vor_yaw_fast",
            tier = "V",
            title = "Faster side-to-side head turns",
            worn = true,
            prepSec = 10,
            durationSec = 30,
            purpose = "Yaw at 2 Hz: higher head velocity, where motion blur and the camera's " +
                "frame rate start to limit the image measurement. Brackets how fast a " +
                "VOR-style exercise can be tracked.",
            setup = "Sit down, same scene and target. Turn your head left and right on the " +
                "faster tones, keeping the turns small. Stop at once if you feel dizzy or " +
                "unwell — you can end the trial from the phone at any time.",
            instructions = listOf("Sit down", "Small, quick turns with the tones", "Stop if dizzy"),
            cue = Cue(intervalMs = 250, steps = listOf("LEFT", "RIGHT"), style = CueStyle.METRONOME),
            camera = true,
        ),
        Trial(
            id = "V4_slow_pan",
            tier = "V",
            title = "Slow look-around (camera calibration)",
            worn = true,
            prepSec = 10,
            durationSec = 45,
            purpose = "Slow, smooth head turns with little motion blur, for calibrating the " +
                "camera's pixels-per-degree scale and the IMU-to-camera lag.",
            setup = "Sit facing a detailed scene. When VISOR says left or right, turn your " +
                "head slowly and smoothly that way — about as far as you would to look at " +
                "someone sitting beside you. Let your eyes move with your head.",
            instructions = listOf("Slow, smooth turns", "Follow the spoken cue"),
            cue = Cue(intervalMs = 3000, steps = listOf("LEFT", "RIGHT")),
            camera = true,
        ),

        // ---------- Tier A: instrument characterization (NOT worn) ----------
        Trial(
            id = "A1_static_rest",
            tier = "A",
            title = "Static rest",
            worn = false,
            prepSec = 20,
            durationSec = 120,
            purpose = "Noise floor, accelerometer bias, gyroscope zero-rate offset, Allan deviation.",
            setup = "Start the trial, then take the glasses off and lay them flat on a solid " +
                "surface before the countdown ends. Do not touch them again until you hear " +
                "the three falling tones.",
            instructions = listOf("Start, then set glasses down", "Hands off until the end chime"),
        ),
        Trial(
            id = "A2_static_extended",
            tier = "A",
            title = "Static extended",
            worn = false,
            prepSec = 20,
            durationSec = 300,
            purpose = "Bias instability and low-frequency drift at longer averaging times.",
            setup = "As for Static rest, but five minutes. Start the trial, lay the glasses flat " +
                "before the countdown ends, and leave them completely undisturbed. " +
                "Do not lean on or bump the table.",
            instructions = listOf("Start, then set glasses down", "5 minutes undisturbed"),
        ),

        // ---------- Tier B: dynamic response (worn) ----------
        Trial(
            id = "B1_yaw_paced",
            tier = "B",
            title = "Yaw sweeps (paced)",
            worn = true,
            prepSec = 8,
            durationSec = 60,
            purpose = "Yaw-axis response and repeatability at a controlled cadence.",
            setup = "Wear the glasses and sit or stand comfortably. Turn your head left and " +
                "right, following the spoken cue. Use your full comfortable range.",
            instructions = listOf("Turn head to the cue", "Full comfortable range"),
            cue = Cue(intervalMs = 2000, steps = listOf("LEFT", "RIGHT")),
        ),
        Trial(
            id = "B2_pitch_paced",
            tier = "B",
            title = "Pitch sweeps (paced)",
            worn = true,
            prepSec = 8,
            durationSec = 60,
            purpose = "Pitch-axis response; nod detection feasibility.",
            setup = "Wear the glasses. Nod your head up and down, following the cue.",
            instructions = listOf("Nod to the cue"),
            cue = Cue(intervalMs = 2000, steps = listOf("UP", "DOWN")),
        ),
        Trial(
            id = "B3_roll_paced",
            tier = "B",
            title = "Roll sweeps (paced)",
            worn = true,
            prepSec = 8,
            durationSec = 60,
            purpose = "Roll-axis response; axis cross-coupling assessment.",
            setup = "Wear the glasses. Tilt your head ear-toward-shoulder, following the cue.",
            instructions = listOf("Tilt head to the cue"),
            cue = Cue(intervalMs = 2000, steps = listOf("TILT LEFT", "TILT RIGHT")),
        ),
        Trial(
            id = "B4_rapid_turns",
            tier = "B",
            title = "Rapid head turns",
            worn = true,
            prepSec = 8,
            durationSec = 30,
            purpose = "Angular-rate range and saturation; aliasing at the reported sample rate.",
            setup = "Wear the glasses and sit down. Turn your head left and right as fast as is " +
                "comfortable and safe. Stop immediately if you feel dizzy.",
            instructions = listOf("Turn as FAST as comfortable", "Sit down · stop if dizzy"),
            cue = Cue(intervalMs = 1000, steps = listOf("FAST LEFT", "FAST RIGHT")),
        ),

        // ---------- Tier C: rehabilitation-relevant behaviour (worn) ----------
        Trial(
            id = "C1_stillness_hold",
            tier = "C",
            title = "Stillness hold",
            worn = true,
            prepSec = 8,
            durationSec = 60,
            purpose = "Worn-but-still baseline. Sets the head-stability threshold VISOR would " +
                "use to gate camera capture on the wearer having settled.",
            setup = "Wear the glasses. Sit still and look steadily at one fixed point across " +
                "the room. Breathe normally — do not try to freeze.",
            instructions = listOf("Sit still", "Fix gaze on one point"),
        ),
        Trial(
            id = "C2_scanning_pattern",
            tier = "C",
            title = "Compensatory scanning",
            worn = true,
            prepSec = 8,
            durationSec = 90,
            purpose = "Systematic horizontal scanning as taught in visual-field-loss training. " +
                "Tests whether scan amplitude, rate and left/right symmetry are " +
                "recoverable from the IMU alone.",
            setup = "Wear the glasses. Sweep your gaze and head across the room in wide, " +
                "deliberate left-and-right scans, following the cue.",
            instructions = listOf("Wide deliberate scans", "Sweep the full field"),
            cue = Cue(intervalMs = 3000, steps = listOf("SCAN LEFT", "SCAN RIGHT")),
        ),
        Trial(
            id = "C3_walk_straight",
            tier = "C",
            title = "Walking gait",
            worn = true,
            prepSec = 12,
            durationSec = 60,
            purpose = "Step cadence and head-bob amplitude from a head-mounted IMU; " +
                "ambulation detection for context-aware assistance.",
            setup = "Wear the glasses. Walk at a natural, comfortable pace along a clear, " +
                "level path. Have someone nearby if your balance is at all uncertain.",
            instructions = listOf("Walk naturally", "Clear level path"),
        ),
        Trial(
            id = "C4_sit_stand",
            tier = "C",
            title = "Sit-to-stand cycles",
            worn = true,
            prepSec = 12,
            durationSec = 75,
            purpose = "Postural-transition signature; mobility-event detection feasibility.",
            setup = "Wear the glasses and sit in a stable chair with no wheels. Stand up and " +
                "sit down following the cue. Use the armrests if you need them.",
            instructions = listOf("Stand / sit on cue", "Stable chair, no wheels"),
            cue = Cue(intervalMs = 5000, steps = listOf("STAND UP", "SIT DOWN")),
        ),
        Trial(
            id = "C5_reading_posture",
            tier = "C",
            title = "Reading posture",
            worn = true,
            prepSec = 8,
            durationSec = 60,
            purpose = "Sustained near-task head pose. Separating reading from walking is the " +
                "basis for context-appropriate assistance.",
            setup = "Wear the glasses and read printed text held at a comfortable distance. " +
                "Read normally — do not hold unnaturally still.",
            instructions = listOf("Read printed text", "Natural posture"),
        ),
        Trial(
            id = "C6_free_living",
            tier = "C",
            title = "Unstructured baseline",
            worn = true,
            prepSec = 8,
            durationSec = 300,
            purpose = "Realistic mixed activity for classifier training data and " +
                "false-positive rate estimation.",
            setup = "Wear the glasses and go about ordinary activity for five minutes: sit, " +
                "stand, walk about, look around. Nothing scripted.",
            instructions = listOf("Ordinary activity", "5 minutes"),
        ),

        // ---------- Tier D: system constraints (worn) ----------
        Trial(
            id = "D1_endurance",
            tier = "D",
            title = "Endurance capture",
            worn = true,
            prepSec = 8,
            durationSec = 600,
            purpose = "Sample-rate stability, dropout rate and battery drain over a " +
                "sustained session.",
            setup = "Wear the glasses for ten minutes of ordinary activity. Keep VISOR open on " +
                "the phone. The glasses' battery level is recorded at the start and the end.",
            instructions = listOf("Keep VISOR open", "10 minutes"),
        ),
    )

    fun byId(id: String): Trial? = all.firstOrNull { it.id == id }
}
