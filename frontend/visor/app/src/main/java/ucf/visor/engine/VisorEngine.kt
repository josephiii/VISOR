package ucf.visor.engine

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.R
import ucf.visor.background.BackgroundPolicy
import ucf.visor.background.BatteryAlerts
import ucf.visor.background.ListeningNotificationText
import ucf.visor.background.GlassesRestartNotification
import ucf.visor.background.ListeningService
import ucf.visor.capture.Availability
import ucf.visor.capture.ChosenPhotoSource
import ucf.visor.capture.GlassesPhotoSource
import ucf.visor.capture.HazardDetector
import ucf.visor.capture.scaledDownToWidth
import ucf.visor.hazards.HAZARD_WATCH_DETECT_TIMEOUT_MS
import ucf.visor.hazards.HAZARD_WATCH_FRAME_WIDTH
import ucf.visor.hazards.HAZARD_WATCH_JPEG_QUALITY
import ucf.visor.hazards.HazardAnnouncer
import ucf.visor.hazards.HazardWatch
import ucf.visor.hazards.HazardWatchSpeaker
import ucf.visor.capture.NotifyingPhotoSource
import ucf.visor.capture.ObjectIdentifier
import ucf.visor.capture.PhoneCameraSlot
import ucf.visor.capture.PhotoSource
import android.graphics.Bitmap
import java.io.File
import ucf.visor.BuildConfig
import ucf.visor.capture.ReadRequester
import ucf.visor.capture.SceneDescriber
import ucf.visor.capture.TextReader
import ucf.visor.capture.availability
import ucf.visor.data.AudioMix
import ucf.visor.data.CameraChoice
import ucf.visor.data.SoundCategory
import ucf.visor.data.SpeechRates
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionPlan
import ucf.visor.data.VoiceCategory
import ucf.visor.data.Volumes
import ucf.visor.lookaround.GuideTonePlayer
import ucf.visor.lookaround.LookAround
import ucf.visor.lookaround.LookAroundPhrases
import ucf.visor.motion.HeadMotion
import ucf.visor.network.HazardClient
import ucf.visor.network.VlmClient
import ucf.visor.recognition.ObjectDetector
import ucf.visor.recognition.TextReaderOCR
import ucf.visor.stt.CameraCommand
import ucf.visor.stt.CommandRecognizer
import ucf.visor.stt.Listener
import ucf.visor.stt.MicCheck
import ucf.visor.stt.MicRoute
import ucf.visor.stt.WakePhrases
import ucf.visor.tts.Chimes
import ucf.visor.tts.ReadingState
import ucf.visor.tts.Speaker
import ucf.visor.tts.SpokenPhrases
import ucf.visor.tts.ThinkingBeeper
import ucf.visor.tts.VoiceCatalog
import ucf.visor.ui.feedback.Haptics
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.labelRes
import ucf.visor.ui.viewmodel.CaptureStatus
import ucf.visor.ui.voice.SceneQuestionController
import ucf.visor.ui.voice.VoiceCommand
import ucf.visor.ui.voice.VoiceNavState
import ucf.visor.ui.voice.VoiceNavigationController
import ucf.visor.ui.voice.VoiceReplies
import ucf.visor.wearables.GlassesConnectionNotice
import ucf.visor.wearables.GlassesLease
import ucf.visor.wearables.GlassesLinkNudge
import ucf.visor.wearables.GlassesRecovery
import ucf.visor.wearables.HeldGlassesSession
import ucf.visor.wearables.VoiceLaunches
import ucf.visor.wearables.preferredDevice

/**
 * Everything that listens and speaks: the wake phrases, "VISOR GO" and its
 * commands, the camera features, VISOR's voice, chimes and buzzes.
 *
 * One per process (VisorApp), not one per screen, so it can go on answering
 * with no screen showing: with the screen off and the phone in a pocket, while
 * background listening's service ([ListeningService]) keeps the process in the
 * foreground. The screen (MainActivity) tells it when it is shown and hidden
 * ([onScreenShown], [onScreenHidden]) and lends it what only a screen has: the
 * phone's camera ([phoneCamera]) and the Meta AI permission prompt
 * ([glassesPermissionRequester]).
 *
 * When VISOR listens:
 * - On screen: whenever any spoken command is on in Settings.
 * - Off screen, with background listening on: the same, through the service.
 * - Off screen, with it off: not at all. The microphone is released.
 *
 * Commands that need no screen are carried out here; the rest go to the
 * screen ([screenCommands]) or, with none showing, are answered with how to
 * do them ([BackgroundPolicy]).
 */
class VisorEngine(
    private val app: Context,
    val state: VisorState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------------------------------------------------------------------
    // Sound, speech and touch

    /** The volumes Settings has set for each kind of sound, as of right now. */
    private fun audio(): AudioMix = state.profile.value.audio

    // Volumes are read from the profile as each sound plays, not pushed:
    // Settings plays a preview the moment a volume changes.
    val chimes = Chimes(loudness = { audio().loudness(it) })
    val haptics = Haptics(app) { state.profile.value.vibrationEnabled }
    val speaker = Speaker(
        app,
        loudness = { audio().loudness(it) },
        leadInMs = { chimes.remainingMs() },
    )
    val thinkingBeeper = ThinkingBeeper(
        isSpeaking = { speaker.isSpeaking() },
        volume = { audio().loudness(SoundCategory.THINKING_BEEPS) },
    )

    // ---------------------------------------------------------------------
    // Screen

    @Volatile
    var isScreenShown = false
        private set

    /**
     * Asks the wearer, through the Meta AI app, to let VISOR use the glasses
     * camera. Only a screen can show that, so it is set while one exists; with
     * none, the camera feature says permission is missing.
     */
    var glassesPermissionRequester: (suspend (Permission) -> PermissionStatus)? = null

    /** The phone's camera, attached by the screen while it exists. */
    val phoneCamera = PhoneCameraSlot()

    private val _screenCommands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 8)

    /** Commands only the screen can carry out (see [BackgroundPolicy]), for VisorLayout. */
    val screenCommands: SharedFlow<VoiceCommand> = _screenCommands.asSharedFlow()

    fun onScreenShown() {
        isScreenShown = true
        updateBackgroundService()
        updateWakeListening()
        updateHeldSession()
        // Opened with the glasses link lost (perhaps asked to, with the screen
        // off): now VISOR may restart itself if reconnecting in place fails.
        // A fresh launch (wearablesReady still false) is already a restart.
        if (wearablesReady && sdkHasSeenGlasses && !sdkSeesGlasses()) startRecovering(confirmMs = 0L)
    }

    fun onScreenHidden() {
        isScreenShown = false
        updateWakeListening()
        updateHeldSession()
    }

    // ---------------------------------------------------------------------
    // Camera features

    private val textReaderOCR = TextReaderOCR()
    private val objectDetector = ObjectDetector()

    /**
     * The glasses session held while VISOR is in use; every glasses picture is
     * taken in it. Internal for the MockDeviceKit tests, which run Look around
     * in it with a stand-in for the server.
     */
    internal val heldSession = HeldGlassesSession(scope)

    val glassesCamera = GlassesPhotoSource(
        session = heldSession,
        requestPermission = { permission ->
            glassesPermissionRequester?.invoke(permission) ?: PermissionStatus.Denied
        },
        recoverLink = { recoverGlassesLink() },
        skipStills = { state.glassesStatus.value?.displayCapable == true },
    )

    /** The camera chosen on the Pair screen (or by voice), as of right now. */
    private fun cameraChoice(): CameraChoice = state.profile.value.camera

    /** How much the camera features say: Settings > Speech > Detail. */
    private fun verbosity(): Verbosity = state.profile.value.verbosity

    /** How VISOR fits the user's vision: Settings > About you, as of right now. */
    private fun visionPlan(): VisionPlan = VisionPlan.from(state.profile.value)

    private fun isOnline(): Boolean = state.isOnline.value

    /**
     * What VISOR says as a camera feature takes its picture, or null for
     * nothing. The glasses play their own shutter sound as they take it, so
     * VISOR says nothing over it. The phone's camera makes none, and "Taking
     * picture with your phone" is also the cue to hold the phone up.
     */
    fun takingPicturePhrase(): String? = when (cameraChoice()) {
        CameraChoice.GLASSES -> null
        CameraChoice.PHONE -> SpokenPhrases.TAKING_PICTURE_WITH_PHONE
    }

    // Each picture comes from the camera chosen on the Pair screen: the
    // glasses, or the phone's own. Without real glasses, either choose the
    // phone (the emulator's camera works too), or pair mock glasses in the
    // debug menu and pick the photo they should "take" (Select image).
    private val chosenCamera = ChosenPhotoSource(glassesCamera, phoneCamera, ::cameraChoice)
    // Hazard watch takes a picture every few seconds: video frames from the
    // glasses (no shutter sound), and no link recovery from inside the loop.
    private val chosenWatchCamera = ChosenPhotoSource(glassesCamera.forWatch, phoneCamera.forWatch, ::cameraChoice)
    private val chosenReadingCamera =
        ChosenPhotoSource(glassesCamera.forReading, phoneCamera.forReading, ::cameraChoice)

    // Each camera feature says when its picture is taken, so its button on
    // Home can show "Thinking…" in place of "Taking picture…".
    private fun cameraFor(command: CameraCommand, source: PhotoSource = chosenCamera) =
        NotifyingPhotoSource(source) { state.onPictureTaken(command) }

    private val textReader: ReadRequester =
        TextReader(cameraFor(CameraCommand.READ_TEXT, keepingLastPicture(chosenReadingCamera)), textReaderOCR)

    /**
     * In debug builds only, keeps the last picture Read Text was given in
     * VISOR's own cache (last_reading.jpg, replaced each time), so what the
     * text reader saw can be looked at: `adb exec-out run-as edu.ucf.visor cat cache/last_reading.jpg`.
     */
    private fun keepingLastPicture(source: PhotoSource): PhotoSource =
        if (!BuildConfig.DEBUG) source
        else object : PhotoSource {
            override fun capturePhoto(onPhoto: (Bitmap) -> Unit, onError: (String) -> Unit) =
                source.capturePhoto(
                    onPhoto = { bitmap ->
                        runCatching {
                            File(app.cacheDir, "last_reading.jpg").outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                            }
                        }
                        onPhoto(bitmap)
                    },
                    onError = onError,
                )
        }

    private val objectIdentifier: ReadRequester =
        ObjectIdentifier(cameraFor(CameraCommand.IDENTIFY_OBJECTS), objectDetector)

    private val sceneDescriber: ReadRequester = SceneDescriber(
        photoSource = NotifyingPhotoSource(cameraFor(CameraCommand.DESCRIBE_SCENE)) {
            // Offline, the answer is on-device labels, there in a moment.
            if (isOnline()) {
                speaker.speak(SpokenPhrases.AWAITING_SCENE_DESCRIPTION, VoiceCategory.STATUS_UPDATES)
            }
        },
        vlm = VlmClient(accessToken = state.sessionStore::accessToken),
        scope = scope,
        fallback = objectDetector,
        verbosity = ::verbosity,
        plan = ::visionPlan,
        isOnline = ::isOnline,
    )

    private val hazardClient = HazardClient(accessToken = state.sessionStore::accessToken)

    private val hazardDetector: ReadRequester = HazardDetector(
        photoSource = cameraFor(CameraCommand.CHECK_HAZARDS),
        client = hazardClient,
        scope = scope,
        verbosity = ::verbosity,
        plan = ::visionPlan,
    )

    // ---------------------------------------------------------------------
    // Hazard watch: looks every few seconds, says only what is new

    private val hazardAnnouncer = HazardAnnouncer()

    private val hazardWatch = HazardWatch<Bitmap>(
        speaker = object : HazardWatchSpeaker {
            override fun say(text: String) = speaker.speak(text, VoiceCategory.ANSWERS)
            override fun isSpeaking() = speaker.isSpeaking()
        },
        announcer = hazardAnnouncer,
        capture = ::captureForHazardWatch,
        detect = { bitmap ->
            hazardClient.detect(
                bitmap,
                jpegQuality = HAZARD_WATCH_JPEG_QUALITY,
                timeoutMs = HAZARD_WATCH_DETECT_TIMEOUT_MS,
            )
        },
        widthOf = { it.width },
        isOnline = ::isOnline,
        // Any camera feature at work (Look around too) goes first, and the
        // watch never talks into an open microphone: not while "VISOR GO" is
        // listening or being answered, nor while a question is being asked.
        busy = {
            state.captures.value.isNotEmpty() || voiceNav.state.value != VoiceNavState.IDLE || commandMicOpen
        },
        clockMs = SystemClock::elapsedRealtime,
        // As the one-shot check says them (HazardDetector).
        limit = { if (verbosity() == Verbosity.BRIEF) visionPlan().briefHazardLimit else Int.MAX_VALUE },
        focus = { visionPlan().hazardFocus },
    )

    private var hazardWatchJob: Job? = null
    private var hazardWatchWasOn = false

    /** A picture from the camera chosen on the Pair screen, scaled down for the watch; null if none could be taken. */
    private suspend fun captureForHazardWatch(): Bitmap? {
        if (cameraChoice() == CameraChoice.GLASSES) {
            // The SDK has lost the glasses, or VISOR is getting them back
            // (watchGlassesLink): asking for a session every few seconds on
            // top of that only gets in the way of the reconnect. A failed
            // look, with nothing sent to the glasses.
            if (!sdkSeesGlasses()) return null
            // As before any glasses stream: after a change of microphone has settled.
            micRoute.awaitSettled()
        }
        val photo = suspendCancellableCoroutine<Bitmap?> { cont ->
            chosenWatchCamera.capturePhoto(
                onPhoto = { if (cont.isActive) cont.resume(it) },
                onError = { if (cont.isActive) cont.resume(null) },
            )
        }
        return photo?.let { withContext(Dispatchers.Default) { it.scaledDownToWidth(HAZARD_WATCH_FRAME_WIDTH) } }
    }

    /** Starts or stops the watch's loop to match [VisorState.hazardWatchOn]. */
    private fun onHazardWatchChanged(on: Boolean) {
        hazardWatchJob?.cancel()
        hazardWatchJob = null
        hazardAnnouncer.reset()
        // One glasses session for as long as the watch is on. On display
        // glasses with tap navigation VISOR otherwise closes its session after
        // every picture, to give the display its own back; a session closed
        // and another opened every few seconds lost the SDK's link to the
        // glasses within two pictures (field log, 2026-11-16), and VISOR
        // restarted itself each time. The glasses' own display is blank
        // meanwhile; it comes back when the watch is turned off.
        if (on) heldSession.pin(HAZARD_WATCH_PIN) else heldSession.unpin(HAZARD_WATCH_PIN)
        updateHeldSession()
        if (on) {
            reply(SpokenPhrases.HAZARD_WATCH_ON)
            if (Volumes.isOff(audio().loudness(VoiceCategory.ANSWERS))) reply(SpokenPhrases.HAZARD_WATCH_MUTED)
            hazardWatchJob = scope.launch { hazardWatch.run() }
        } else if (hazardWatchWasOn) {
            reply(SpokenPhrases.HAZARD_WATCH_OFF)
        }
        hazardWatchWasOn = on
    }

    // ---------------------------------------------------------------------
    // Look around: the glasses camera and their motion sensors, in one session

    /** The glasses' head movement (MWDAT Motion), attached to the held session while Look around needs it. */
    private val headMotion = HeadMotion()

    private val guideTones = GuideTonePlayer(loudness = { audio().loudness(SoundCategory.GUIDANCE_TONES) })

    /** Settings' preview of the guidance tones' volume: two beeps, near and far. */
    fun playGuidanceSample() = guideTones.sample(scope)

    val lookAround = LookAround(
        scope = scope,
        session = heldSession,
        camera = glassesCamera,
        motion = headMotion,
        detect = { bitmap -> hazardClient.detect(bitmap) },
        tones = guideTones,
        say = object : LookAround.Say {
            override fun step(text: String) = speaker.speak(text, VoiceCategory.VOICE_COMMANDS)
            override fun answer(text: String) = speaker.speak(text, VoiceCategory.ANSWERS)
        },
        plan = { visionPlan().lookAroundPlan },
        limit = {
            if (verbosity() == Verbosity.BRIEF) visionPlan().briefHazardLimit else Int.MAX_VALUE
        },
        guideHint = { verbosity() != Verbosity.BRIEF },
        isSpeaking = { speaker.isSpeaking() },
        events = object : LookAround.Events {
            override fun onScanStarted() {
                state.onCaptureStarted(CameraCommand.LOOK_AROUND)
                state.setCaptureStatus(CameraCommand.LOOK_AROUND, CaptureStatus.LOOKING_AROUND)
            }

            override fun onScanThinking() {
                state.setCaptureStatus(CameraCommand.LOOK_AROUND, CaptureStatus.THINKING)
                thinkingBeeper.start()
            }

            override fun onAnswered(text: String) {
                state.onAnswered(CameraCommand.LOOK_AROUND, text)
                if (state.captures.value.isEmpty()) thinkingBeeper.stop()
            }

            override fun onGuideStarted() {
                state.onCaptureStarted(CameraCommand.LOOK_AROUND)
                state.setCaptureStatus(CameraCommand.LOOK_AROUND, CaptureStatus.GUIDING)
            }
        },
    )

    /** "Guide me to [what]", from "VISOR GO". */
    private fun guideTo(what: String) = lookAround.guideTo(what)

    // ---------------------------------------------------------------------
    // Listening

    private var wearablesReady = false

    /** Whether the Meta Wearables SDK is initialized ([onWearablesReady]). */
    val isWearablesReady: Boolean get() = wearablesReady

    private val micRoute = MicRoute(
        app,
        onRouteChanged = { updateWakeListening() },
        onGaveUp = {
            speaker.speak(app.getString(R.string.mic_falling_back_to_phone), VoiceCategory.STATUS_UPDATES)
        },
    )

    // Single KWS engine for every wake phrase (the camera phrases + "VISOR GO"):
    // two AudioRecord/model instances would double up on the microphone and
    // CPU for no benefit, so this routes by which phrase actually fired.
    private val listener = Listener(app.assets) { phrase -> onWakePhrase(phrase) }

    private val commandRecognizer = CommandRecognizer(app)

    /**
     * Whether a spoken command or question has the microphone (voice
     * navigation or a scene question paused wake listening for it), so
     * [micCheck] can tell the speech recognizer's recording apart.
     */
    @Volatile
    private var commandMicOpen = false

    /** Logs which microphone each recording really uses, for field tests (see MicCheck). */
    private val micCheck = MicCheck(
        app,
        route = { micRoute.source.value },
        wakeSessionId = { listener.audioSessionId },
        commandListening = { commandMicOpen },
        glassesName = { state.glassesStatus.value?.name },
    )

    val voiceNav = VoiceNavigationController(
        context = app,
        speak = { speaker.speak(it, VoiceCategory.VOICE_COMMANDS) },
        pauseWakeListening = {
            commandMicOpen = true
            listener.stop()
        },
        // Not updateWakeListening(): this runs while voice navigation still
        // holds the microphone, which that waits out.
        resumeWakeListening = {
            commandMicOpen = false
            if (canListenNow()) listener.start()
        },
        isSpeaking = { speaker.isSpeaking() },
    )

    private val sceneQuestion = SceneQuestionController(
        recognizer = commandRecognizer,
        describer = SceneDescriber(
            photoSource = cameraFor(CameraCommand.ASK_QUESTION),
            vlm = VlmClient(accessToken = state.sessionStore::accessToken),
            scope = scope,
            verbosity = ::verbosity,
            plan = ::visionPlan,
        ),
        beeper = thinkingBeeper,
        speak = { text -> speaker.speak(text, VoiceCategory.VOICE_COMMANDS) },
        sayAnswer = { text -> speaker.speak(text, VoiceCategory.ANSWERS) },
        isSpeaking = { speaker.isSpeaking() },
        pauseWakeListening = {
            commandMicOpen = true
            listener.stop()
        },
        resumeWakeListening = {
            commandMicOpen = false
            if (canListenNow()) listener.start()
        },
        onAsked = { state.onCaptureStarted(CameraCommand.ASK_QUESTION) },
        onAnswered = { question, answer -> state.onAnswered(CameraCommand.ASK_QUESTION, answer, question) },
        takingPicture = ::takingPicturePhrase,
    )

    val glassesNav = GlassesNavigationController { state.deviceSelector }

    /**
     * Keeps the phone awake from a wake phrase to its answer when no screen is
     * showing: a picture and a trip to the server can outlast the moment the
     * phone would otherwise sleep. Listening itself needs none: Android keeps
     * the phone awake while it records.
     */
    private val commandWakeLock = app.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "visor:command")
        .apply { setReferenceCounted(false) }

    // ---------------------------------------------------------------------
    // Background listening

    @Volatile
    private var serviceRunning = false

    init {
        scope.launch {
            state.profile.collect { profile ->
                voiceNav.setEnabled(profile.voiceNavigationEnabled)
                glassesNav.setEnabled(profile.glassesTapNavigationEnabled)
                speaker.setSpeechRate(profile.speechRate)
                updateBackgroundService()
                updateWakeListening()
                updateHeldSession()
            }
        }
        // The voice chosen in Settings, or, offline, one that works without
        // the internet in place of one that doesn't (see
        // VoiceCatalog.voiceToUse). Back online, the chosen one returns.
        scope.launch {
            combine(
                state.profile.map { it.voiceName }.distinctUntilChanged(),
                state.isOnline,
                speaker.voices,
            ) { name, online, voices -> VoiceCatalog.voiceToUse(name, voices, online) }
                .distinctUntilChanged()
                .collect { speaker.useVoice(it) }
        }
        scope.launch {
            state.loggedIn.collect {
                updateBackgroundService()
                updateWakeListening()
                updateHeldSession()
            }
        }
        // The glasses coming and going decides which microphone VISOR hears
        // through, and whether there is a session to hold.
        scope.launch {
            state.glassesStatus
                .map { Triple(it?.connected, it?.name, it?.displayCapable) }
                .distinctUntilChanged()
                .collect {
                    updateWakeListening()
                    updateHeldSession()
                }
        }
        // The glasses ended the held session (tap and hold, a dropped link):
        // open a fresh one while VISOR still wants it.
        scope.launch {
            heldSession.state
                .map { it == null }
                .distinctUntilChanged()
                .drop(1)
                .collect { gone -> if (gone) updateHeldSession() }
        }
        scope.launch {
            micRoute.source.collect {
                state.setListeningThrough(it)
                micCheck.reportNow()
            }
        }
        // Turning "Listen through my glasses" back on is a request to try now.
        scope.launch {
            state.profile
                .map { it.listenThroughGlasses }
                .distinctUntilChanged()
                .drop(1)
                .collect { on -> if (on) micRoute.resetBackoff() }
        }
        // The glasses are free again (the lab finished): hold them again if wanted.
        scope.launch {
            GlassesLease.holder
                .map { it == null }
                .distinctUntilChanged()
                .drop(1)
                .collect { free -> if (free) updateHeldSession() }
        }
        // The microphone is left as it is while the glasses take a picture
        // (see updateWakeListening); catch up once they are done.
        scope.launch {
            heldSession.busy.collect { busy -> if (!busy) updateWakeListening() }
        }

        // Turning background listening on or off, from Settings, by voice or
        // from the notification: said aloud, since it changes what the
        // microphone does with the phone out of sight.
        scope.launch {
            state.profile
                .map { it.backgroundListeningEnabled }
                .distinctUntilChanged()
                .drop(1) // the setting at launch is not a change
                .collect { on ->
                    if (on) voiceNav.speakGuarded(app.getString(R.string.background_listening_on_spoken))
                    else speaker.speak(app.getString(R.string.background_listening_off_spoken), VoiceCategory.STATUS_UPDATES)
                }
        }

        // Voice navigation listening, felt as well as seen: a firm buzz as the
        // microphone opens for a command, and a light tick as it closes. Here,
        // not on a screen, so it is felt in a pocket too.
        scope.launch {
            voiceNav.state
                .map { it == VoiceNavState.LISTENING }
                .distinctUntilChanged()
                .drop(1) // the state at launch is not a change
                .collect { listening -> if (listening) haptics.listeningStarted() else haptics.listeningStopped() }
        }

        // Anything that changed while "VISOR GO" had the microphone (the glasses
        // connecting, the screen going off) is caught up with once it hands it back.
        scope.launch {
            voiceNav.state
                .map { it == VoiceNavState.IDLE }
                .distinctUntilChanged()
                .collect { idle -> if (idle) updateWakeListening() }
        }

        scope.launch { state.hazardWatchOn.collect(::onHazardWatchChanged) }
        // Nobody to watch for: turned off at logout.
        scope.launch { state.loggedIn.collect { if (!it) state.setHazardWatch(false) } }

        // Commands said after "VISOR GO", and taps on the glasses' own display.
        scope.launch { merge(voiceNav.commands, glassesNav.commands).collect(::dispatch) }

        // Nothing left to do: let the phone sleep again.
        scope.launch {
            combine(state.captures, voiceNav.state) { captures, voice ->
                captures.isEmpty() && voice == VoiceNavState.IDLE
            }.collect { idle -> if (idle && commandWakeLock.isHeld) commandWakeLock.release() }
        }

        watchGlassesLink()

        // A low glasses battery is said aloud while listening in the background:
        // the notification that also shows it can't be seen from a pocket.
        scope.launch {
            var lastSaid: Int? = null
            state.glassesStatus.collect { glasses ->
                val (sayNow, said) = BatteryAlerts.next(lastSaid, glasses?.batteryPercent, glasses?.charging)
                lastSaid = said
                if (sayNow != null && serviceRunning && glasses?.batteryPercent != null) {
                    speaker.speak(BatteryAlerts.spoken(glasses.batteryPercent), VoiceCategory.STATUS_UPDATES)
                }
            }
        }
    }

    /**
     * Call once VISOR's phone permissions are granted. Initializes the
     * Meta Wearables SDK, once per process as Meta requires, and starts
     * listening. Safe to call again.
     */
    fun onWearablesReady() {
        if (wearablesReady) return
        wearablesReady = true
        Wearables.initialize(app)
        state.startMonitoring()
        // Only now may the on-glasses nav touch the SDK — resolving the
        // device selector any earlier throws WearablesException.
        glassesNav.onWearablesReady()
        // "Hey Meta, start VISOR": open the stream early so a voice
        // command that cold-launched the app is still delivered.
        VoiceLaunches.get().start()
        updateBackgroundService()
        updateWakeListening()
        updateHeldSession()
        announceAfterRestart()
    }

    /** Whether any spoken command is on. With every one turned off, VISOR leaves the microphone alone. */
    private fun wantsWakeListening(): Boolean {
        val profile = state.profile.value
        return profile.voiceNavigationEnabled || profile.cameraCommands.isNotEmpty()
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(app, RECORD_AUDIO) == PERMISSION_GRANTED

    /** Whether VISOR may listen right now: on screen, or off it with background listening running. */
    private fun canListenNow(): Boolean =
        wearablesReady && wantsWakeListening() && hasMicrophonePermission() && (isScreenShown || serviceRunning)

    /**
     * Starts or stops listening for wake phrases, through the glasses or the
     * phone, to match the screen, Settings and the glasses. Not while
     * "VISOR GO" has the microphone: voice navigation hands it back itself.
     */
    fun updateWakeListening() {
        if (!wearablesReady || voiceNav.state.value != VoiceNavState.IDLE) return
        val listen = canListenNow()
        // Never change the glasses' audio link while they take a picture: a
        // Bluetooth call link coming or going mid-stream can cut the camera
        // session off (Meta: settle HFP before a camera stream starts).
        if (!heldSession.busy.value) {
            val glasses = state.glassesStatus.value
            micRoute.update(
                useGlasses = listen && state.profile.value.listenThroughGlasses && glasses?.connected == true,
                glassesName = glasses?.name,
            )
            listener.setPreferredInput(micRoute.preferredInput())
        }
        if (listen) listener.start() else listener.stop()
    }

    // ---------------------------------------------------------------------
    // The held glasses session

    /**
     * Whether to hold a glasses session between pictures (see
     * HeldGlassesSession): while VISOR is in use with the glasses camera. Not
     * on display glasses with tap navigation on, which keeps a session of its
     * own for the display (one session per device).
     */
    private fun wantsHeldSession(): Boolean {
        val profile = state.profile.value
        val glasses = state.glassesStatus.value
        return wearablesReady && state.isLoggedIn() && profile.camera == CameraChoice.GLASSES &&
            (isScreenShown || serviceRunning || state.hazardWatchOn.value) &&
            !(glasses?.displayCapable == true && profile.glassesTapNavigationEnabled)
    }

    private var openingJob: Job? = null
    private var lastOpenAt = 0L

    /**
     * Holds the session or lets it go, to match [wantsHeldSession], and opens
     * it ahead of the first picture once the glasses are there: after any
     * change of microphone has settled, so the call link is up before the
     * session starts, the order Meta asks for. Opened at most every
     * [MIN_OPEN_GAP_MS], so glasses that refuse can't make VISOR hammer them.
     */
    private fun updateHeldSession() {
        val want = wantsHeldSession()
        val glasses = state.glassesStatus.value
        // Display glasses with tap navigation: the display wants its session
        // back, but not after every picture. Closing at once lost the SDK's
        // link within two pictures (field log, 2026-11-16), so a run of
        // commands shares one session, and the display is blank that long.
        heldSession.lingerMs =
            if (glasses?.displayCapable == true && state.profile.value.glassesTapNavigationEnabled) {
                HeldGlassesSession.DISPLAY_LINGER_MS
            } else {
                HeldGlassesSession.LINGER_MS
            }
        heldSession.setKeep(want)
        if (!want || heldSession.state.value != null || openingJob?.isActive == true) return
        if (!sdkSeesGlasses()) return
        // Another feature (the head-motion lab) has the glasses: wait for it.
        if (GlassesLease.holder.value.let { it != null && it != HeldGlassesSession.LEASE_OWNER }) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpenAt < MIN_OPEN_GAP_MS) return
        lastOpenAt = now
        openingJob = scope.launch {
            micRoute.awaitSettled()
            val opened = heldSession.withSession { true }
            Log.i(SESSION_TAG, "Held session opened ahead of use: ${opened is HeldGlassesSession.Result.Done}")
        }
    }

    // ---------------------------------------------------------------------
    // Recovering the glasses link

    private val recoveryLock = Mutex()

    private val recoveryPrefs = app.getSharedPreferences("visor_recovery", Context.MODE_PRIVATE)

    /** Whether the Meta Wearables SDK sees connected glasses right now. */
    private fun sdkSeesGlasses(): Boolean =
        runCatching { preferredDevice()?.second?.linkState == LinkState.CONNECTED }.getOrDefault(false)

    /** Whether the glasses are connected to the phone (for audio), whatever the SDK thinks. */
    private fun glassesOnPhone(): Boolean = micRoute.glassesConnected(state.glassesStatus.value?.name)

    private fun sinceLastRestartMs(): Long? {
        val at = recoveryPrefs.getLong(KEY_LAST_RESTART_AT, 0L)
        return if (at == 0L) null else (System.currentTimeMillis() - at).coerceAtLeast(0L)
    }

    private var reconnectedJob: Job? = null

    /**
     * Shows what VISOR is doing about the glasses link: at the top of every
     * screen and on the listening notification (GlassesConnectionNotice).
     * "Reconnected" stays a few seconds, long enough to read under a magnifier.
     */
    private fun showConnection(notice: GlassesConnectionNotice) {
        reconnectedJob?.cancel()
        state.setGlassesConnection(notice)
        if (notice == GlassesConnectionNotice.RECONNECTED) {
            reconnectedJob = scope.launch {
                delay(RECONNECTED_SHOWN_MS)
                state.setGlassesConnection(GlassesConnectionNotice.NONE)
            }
        }
    }

    /**
     * Gets the SDK to reconnect to the glasses in place (GlassesLinkNudge),
     * which works with the screen off and needs no restart, and waits for it.
     * Returns whether the glasses are back. The watcher ([watchGlassesLink])
     * says so when they are.
     */
    private suspend fun reconnectInPlace(say: Boolean): Boolean {
        if (!GlassesLinkNudge.isAvailable) {
            Log.w(RECOVERY_TAG, "Can't reconnect in place: the SDK's Bluetooth listener wasn't found")
            return false
        }
        showConnection(GlassesConnectionNotice.RECONNECTING)
        if (say) speaker.speak(app.getString(R.string.glasses_reconnecting), VoiceCategory.STATUS_UPDATES)
        val back = withTimeoutOrNull(NUDGE_WAIT_MS) {
            var nudgedAt = 0L
            while (!sdkSeesGlasses()) {
                val now = SystemClock.elapsedRealtime()
                if (nudgedAt == 0L || now - nudgedAt >= NUDGE_REPEAT_MS) {
                    nudgedAt = now
                    withContext(Dispatchers.IO) { GlassesLinkNudge.nudge(app) }
                }
                delay(RECONNECT_POLL_MS)
            }
            true
        } ?: false
        Log.i(RECOVERY_TAG, if (back) "Reconnected in place" else "The in-place reconnect didn't bring the glasses back")
        return back
    }

    /**
     * A glasses picture failed in a way a recovery may fix: works through
     * [GlassesRecovery]'s steps, saying what it is doing, and says whether to
     * try the picture again.
     */
    suspend fun recoverGlassesLink(): GlassesRecovery.Outcome = recoveryLock.withLock {
        var retried = false
        var nudged = false
        repeat(MAX_RECOVERY_STEPS) {
            val situation = GlassesRecovery.Situation(
                glassesOnPhone = glassesOnPhone(),
                micOnGlasses = micRoute.source.value == MicRoute.Source.GLASSES,
                sdkSeesGlasses = sdkSeesGlasses(),
                retried = retried,
                nudged = nudged,
                screenShown = isScreenShown,
                sinceLastRestartMs = sinceLastRestartMs(),
            )
            val step = GlassesRecovery.next(situation)
            Log.i(RECOVERY_TAG, "$situation -> $step")
            when (step) {
                GlassesRecovery.Step.GIVE_UP -> {
                    // Lost, on the phone, and a restart too soon after the last:
                    // the watcher keeps trying, and the wearer is told so.
                    if (situation.glassesOnPhone && !situation.sdkSeesGlasses) {
                        showConnection(GlassesConnectionNotice.LOST)
                        ensureRecovering()
                        return@withLock GlassesRecovery.Outcome.Stop(app.getString(R.string.glasses_still_trying))
                    }
                    return@withLock GlassesRecovery.Outcome.Stop()
                }

                GlassesRecovery.Step.YIELD_MIC -> {
                    speaker.speak(app.getString(R.string.mic_yield_for_camera), VoiceCategory.STATUS_UPDATES)
                    heldSession.close()
                    micRoute.yieldToCamera(MIC_YIELD_MS)
                    listener.setPreferredInput(micRoute.preferredInput())
                    micRoute.awaitSettled()
                }

                GlassesRecovery.Step.RETRY -> {
                    retried = true
                    heldSession.close()
                    return@withLock GlassesRecovery.Outcome.TryAgain
                }

                GlassesRecovery.Step.NUDGE_SDK -> {
                    nudged = true
                    reconnectInPlace(say = true)
                }

                GlassesRecovery.Step.RESTART_VISOR -> {
                    restartVisor(replay = lastCameraCommand)
                    return@withLock GlassesRecovery.Outcome.Stop(app.getString(R.string.glasses_restarting_visor))
                }

                GlassesRecovery.Step.ASK_TO_OPEN_VISOR -> {
                    showConnection(GlassesConnectionNotice.LOST)
                    ensureRecovering()
                    return@withLock GlassesRecovery.Outcome.Stop(app.getString(R.string.glasses_open_visor_to_reconnect))
                }
            }
        }
        GlassesRecovery.Outcome.Stop()
    }

    /**
     * Restarts VISOR's process, the one fix that always brought the glasses
     * back by hand, once what VISOR is saying has been said. Only with VISOR on
     * screen (Android lets a visible app relaunch itself) and at most every
     * [GlassesRecovery.MIN_RESTART_GAP_MS]. Meanwhile the screen and a
     * notification say VISOR is restarting; the notification stays through
     * the restart. The relaunched VISOR says whether the glasses are back
     * ([announceAfterRestart]). Called off if the glasses come back first.
     */
    private fun restartVisor(replay: CameraCommand? = null) {
        recoveryPrefs.edit()
            .putLong(KEY_LAST_RESTART_AT, System.currentTimeMillis())
            .putBoolean(KEY_ANNOUNCE_AFTER_RESTART, true)
            .putString(KEY_REPLAY_COMMAND, replay?.name)
            .putLong(KEY_REPLAY_AT, System.currentTimeMillis())
            .commit()
        showConnection(GlassesConnectionNotice.RESTARTING)
        GlassesRestartNotification.show(app)
        scope.launch {
            delay(RESTART_LEAD_IN_MS)
            withTimeoutOrNull(RESTART_SPEECH_WAIT_MS) { while (speaker.isSpeaking()) delay(100) }
            if (sdkSeesGlasses()) {
                Log.i(RECOVERY_TAG, "The glasses came back by themselves: no restart")
                recoveryPrefs.edit().putBoolean(KEY_ANNOUNCE_AFTER_RESTART, false).remove(KEY_REPLAY_COMMAND).apply()
                GlassesRestartNotification.cancel(app)
                return@launch
            }
            Log.i(RECOVERY_TAG, "Restarting VISOR to reconnect the glasses")
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName) ?: return@launch
            app.startActivity(Intent.makeRestartActivityTask(launch.component))
            Runtime.getRuntime().exit(0)
        }
    }

    /**
     * After VISOR restarted itself for the glasses: shows it is reconnecting
     * until they are back (the watcher says so) and carries on with the camera
     * command that was cut off, so the wearer does not have to ask again; or
     * says it still can't reach them.
     */
    private fun announceAfterRestart() {
        if (!recoveryPrefs.getBoolean(KEY_ANNOUNCE_AFTER_RESTART, false)) return
        val replay = recoveryPrefs.getString(KEY_REPLAY_COMMAND, null)
            ?.let { name -> runCatching { CameraCommand.valueOf(name) }.getOrNull() }
            ?.takeIf { System.currentTimeMillis() - recoveryPrefs.getLong(KEY_REPLAY_AT, 0L) < REPLAY_WITHIN_MS }
        recoveryPrefs.edit()
            .putBoolean(KEY_ANNOUNCE_AFTER_RESTART, false)
            .remove(KEY_REPLAY_COMMAND)
            .apply()
        if (!sdkSeesGlasses()) showConnection(GlassesConnectionNotice.RECONNECTING)
        scope.launch {
            val back = withTimeoutOrNull(ANNOUNCE_WAIT_MS) {
                while (!sdkSeesGlasses()) delay(RECONNECT_POLL_MS)
                true
            } ?: false
            GlassesRestartNotification.cancel(app)
            if (!back) {
                showConnection(GlassesConnectionNotice.LOST)
                speaker.speak(app.getString(R.string.glasses_still_unreachable), VoiceCategory.STATUS_UPDATES)
                return@launch
            }
            if (replay != null && state.isLoggedIn()) {
                Log.i(RECOVERY_TAG, "Carrying on with $replay after the restart")
                // Let the session open (updateHeldSession) before asking for a picture.
                delay(REPLAY_DELAY_MS)
                runCameraFeature(replay)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Watching the glasses link

    /** The SDK has seen the glasses in this process, so losing them is news. */
    private var sdkHasSeenGlasses = false

    /** VISOR asked the wearer to open it to reconnect, and has not heard back. */
    private var openToReconnectSaid = false

    private var recoveryJob: Job? = null

    /**
     * Watches for the SDK losing glasses that are still connected to the
     * phone (MWDAT 1.0.0 does not reconnect them by itself; see
     * GlassesRecovery) and recovers before the wearer's next command needs
     * them, every time, screen or not ([recover]). Says when they are back.
     */
    private fun watchGlassesLink() {
        scope.launch {
            state.glassesStatus
                .map { it?.connected == true }
                .distinctUntilChanged()
                .collect { seen ->
                    if (seen) {
                        sdkHasSeenGlasses = true
                        openToReconnectSaid = false
                        recoveryJob?.cancel()
                        val wasRecovering = state.glassesConnection.value.let {
                            it != GlassesConnectionNotice.NONE && it != GlassesConnectionNotice.RECONNECTED
                        }
                        if (wasRecovering) {
                            Log.i(RECOVERY_TAG, "The glasses are back")
                            showConnection(GlassesConnectionNotice.RECONNECTED)
                            speaker.speak(app.getString(R.string.glasses_reconnected), VoiceCategory.STATUS_UPDATES)
                        }
                        return@collect
                    }
                    // A moment's drop the SDK recovers from by itself is not one.
                    if (sdkHasSeenGlasses) startRecovering(confirmMs = WEDGE_CONFIRM_MS)
                }
        }
    }

    /** Starts recovering now if a lost link isn't being recovered already. */
    private fun ensureRecovering() {
        if (recoveryJob?.isActive != true) startRecovering(confirmMs = 0L)
    }

    private fun startRecovering(confirmMs: Long) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            delay(confirmMs)
            var round = 0
            while (!recoveryLock.withLock { recover(round) }) {
                round++
                delay(if (round < FAST_ROUNDS) RECOVERY_RETRY_MS else RECOVERY_SLOW_RETRY_MS)
            }
        }
    }

    /**
     * One try at bringing back glasses the SDK lost, in [GlassesRecovery]'s
     * order: reconnect in place, then (on screen) restart VISOR, or (off
     * screen) ask to open it. Returns true when there is nothing more to do;
     * false to try again shortly, so a lost link is never left alone while
     * VISOR is in use.
     */
    private suspend fun recover(round: Int): Boolean {
        if (sdkSeesGlasses()) return true
        // Glasses that left the phone (off, folded, out of range) are not a stuck SDK.
        if (!glassesOnPhone()) {
            if (state.glassesConnection.value != GlassesConnectionNotice.RESTARTING) {
                showConnection(GlassesConnectionNotice.NONE)
            }
            return true
        }
        // Only while VISOR is in use: opening it starts this again (onScreenShown).
        if (!isScreenShown && !serviceRunning) return true
        if (round == 0) Log.i(RECOVERY_TAG, "The SDK lost the glasses, which are still connected to the phone")
        if (reconnectInPlace(say = round == 0)) return true
        val step = GlassesRecovery.next(
            GlassesRecovery.Situation(
                glassesOnPhone = true,
                // The watcher leaves the microphone alone: a picture that needs it moved does that.
                micOnGlasses = false,
                sdkSeesGlasses = false,
                retried = true,
                nudged = true,
                screenShown = isScreenShown,
                sinceLastRestartMs = sinceLastRestartMs(),
            )
        )
        Log.i(RECOVERY_TAG, "Watcher, round $round -> $step")
        return when (step) {
            GlassesRecovery.Step.RESTART_VISOR -> {
                speaker.speak(app.getString(R.string.glasses_restarting_visor), VoiceCategory.STATUS_UPDATES)
                restartVisor()
                true
            }
            GlassesRecovery.Step.ASK_TO_OPEN_VISOR -> {
                showConnection(GlassesConnectionNotice.LOST)
                if (!openToReconnectSaid) {
                    openToReconnectSaid = true
                    speaker.speak(app.getString(R.string.glasses_open_visor_to_reconnect), VoiceCategory.STATUS_UPDATES)
                }
                false
            }
            // Restarted too recently: keep trying in place, and restart once it may.
            else -> {
                showConnection(GlassesConnectionNotice.LOST)
                false
            }
        }
    }

    /** Whether background listening should be running: on in Settings, with something to listen for. */
    private fun wantsBackgroundService(): Boolean =
        state.profile.value.backgroundListeningEnabled && state.isLoggedIn() &&
            wantsWakeListening() && wearablesReady && hasMicrophonePermission()

    /**
     * Starts or stops the listening service to match Settings. Android only
     * lets it start while VISOR is on screen, so off screen this only stops it;
     * it starts the next time VISOR is opened.
     */
    private fun updateBackgroundService() {
        val wanted = wantsBackgroundService()
        when {
            wanted && !serviceRunning && isScreenShown -> ListeningService.start(app)
            !wanted && serviceRunning -> ListeningService.stop(app)
        }
    }

    /** The listening service is in the foreground: VISOR may listen with the screen off. */
    fun onServiceStarted() {
        serviceRunning = true
        state.setBackgroundListeningActive(true)
        // Turned off again while it was starting.
        if (!wantsBackgroundService()) ListeningService.stop(app)
        updateWakeListening()
        updateHeldSession()
    }

    fun onServiceStopped() {
        serviceRunning = false
        state.setBackgroundListeningActive(false)
        updateWakeListening()
        updateHeldSession()
    }

    /** The notification's "Stop listening": the same as turning the switch off in Settings. */
    fun stopBackgroundListening() {
        state.updateProfile(state.profile.value.copy(backgroundListeningEnabled = false))
    }

    /** The notification's "Repeat answer": the last answer, said again as an answer. */
    fun repeatLastAnswer() {
        val answer = state.lastAnswer.value ?: return speaker.speak(
            app.getString(R.string.background_no_answer_yet),
            VoiceCategory.VOICE_COMMANDS,
        )
        speaker.speak(answer.text, VoiceCategory.ANSWERS)
    }

    /** What the listening notification says, as it changes (see ListeningNotificationText). */
    val notificationContent: Flow<ListeningNotificationText.Content> =
        combine(
            micRoute.source,
            state.lastAnswer,
            state.glassesStatus,
            state.glassesConnection,
        ) { source, answer, glasses, connection ->
            ListeningNotificationText.build(
                throughGlasses = source == MicRoute.Source.GLASSES,
                connection = connection,
                answer = answer?.let {
                    ListeningNotificationText.Answer(app.getString(it.command.labelRes), it.text, it.atMillis)
                },
                glasses = glasses?.let {
                    ListeningNotificationText.Glasses(
                        connected = it.connected,
                        batteryPercent = it.batteryPercent,
                        charging = it.charging,
                        worn = it.worn,
                    )
                },
            )
        }.distinctUntilChanged()

    // ---------------------------------------------------------------------
    // Wake phrases and commands

    private fun onWakePhrase(phrase: String) {
        Log.d(TAG, "WAKE HEARD: $phrase")
        if (!isScreenShown) commandWakeLock.acquire(COMMAND_WAKE_LOCK_MS)
        val keyword = WakePhrases.keyword(phrase)
        if (keyword == NAV_WAKE_PHRASE) voiceNav.activate()
        else CameraCommand.forKeyword(keyword)?.let(::onCameraCommand)
    }

    /**
     * Every camera feature sounds the same, whether a wake phrase, a button
     * on Home or one on the glasses asked for it: the glasses' shutter sound
     * (or "Taking picture with your phone", when that is the camera), beeps
     * while it thinks, then the answer. Its button on Home shows each step too (VisorState.captures),
     * and Home keeps the answer on screen, for anyone who can't hear them.
     */
    /** The last camera command asked for: carried on with after a restart for the glasses. */
    @Volatile
    private var lastCameraCommand: CameraCommand? = null

    fun runCameraFeature(command: CameraCommand) {
        lastCameraCommand = command
        if (command.availability(isOnline()) == Availability.LOCKED) {
            refuseOffline(command)
            return
        }
        val feature = when (command) {
            CameraCommand.CHECK_HAZARDS -> hazardDetector
            CameraCommand.READ_TEXT -> textReader
            CameraCommand.IDENTIFY_OBJECTS -> objectIdentifier
            CameraCommand.DESCRIBE_SCENE -> sceneDescriber
            // Asks what the wearer wants to know before it takes its picture,
            // so it runs its own steps, and reports each to Home all the same.
            CameraCommand.ASK_QUESTION -> {
                sceneQuestion.activate()
                return
            }
            // The head's movement is the glasses': with the phone's camera
            // chosen there is none to follow. Its own steps, like a question's.
            CameraCommand.LOOK_AROUND -> {
                if (cameraChoice() != CameraChoice.GLASSES) {
                    haptics.rejected()
                    speaker.speak(LookAroundPhrases.NEEDS_GLASSES, VoiceCategory.ANSWERS)
                    state.showAnswer(LookAroundPhrases.NEEDS_GLASSES, command)
                } else {
                    lookAround.start()
                }
                return
            }
        }
        state.onCaptureStarted(command)
        takingPicturePhrase()?.let { speaker.speak(it, VoiceCategory.STATUS_UPDATES) }
        thinkingBeeper.start()
        feature.requestRead { text ->
            state.onAnswered(command, text)
            // Another picture may still be being worked on: its beeps go on.
            if (state.captures.value.isEmpty()) thinkingBeeper.stop()
            speaker.speak(text, VoiceCategory.ANSWERS)
        }
    }

    /** A camera wake phrase: its feature, unless Settings has turned it off or no one is logged in. */
    private fun onCameraCommand(command: CameraCommand) {
        if (command !in state.profile.value.cameraCommands) {
            // Silent on purpose. A command is often turned off because it kept
            // starting by mistake, and announcing that would be the same nuisance.
            Log.d(TAG, "Wake phrase for $command ignored: turned off in Settings")
            return
        }
        // A camera command works the moment someone is logged in: nothing else
        // to start first. Said as voice navigation's own replies are, with the
        // wake phrase listener paused (it says "VISOR GO"), and kept at the top
        // of the screen as well, with the rejected buzz, for anyone who can't
        // hear it.
        if (!state.isLoggedIn()) {
            val problem = app.getString(R.string.camera_command_needs_login)
            haptics.rejected()
            voiceNav.speakGuarded(problem)
            state.reportError(problem)
            return
        }
        runCameraFeature(command)
    }

    /**
     * A camera feature that needs the internet, asked for by its wake phrase
     * or a button on the glasses while the phone is offline: why it can't run,
     * and what still works, with the rejected buzz, and kept at the top of the
     * screen. Home's own button for it is locked and says so itself.
     */
    private fun refuseOffline(command: CameraCommand) {
        val problem = app.getString(R.string.offline_feature_locked, app.getString(command.labelRes))
        haptics.rejected()
        voiceNav.speakGuarded(problem)
        state.reportError(problem)
    }

    private fun reply(text: String) = speaker.speak(text, VoiceCategory.VOICE_COMMANDS)

    private fun dispatch(command: VoiceCommand) {
        when (BackgroundPolicy.handler(command)) {
            BackgroundPolicy.Handler.ANYWHERE -> carryOut(command)
            BackgroundPolicy.Handler.ON_SCREEN ->
                if (isScreenShown) _screenCommands.tryEmit(command)
                else reply(BackgroundPolicy.NEEDS_SCREEN)
        }
    }

    /** The commands that need no screen: the same with the screen on or off. */
    private fun carryOut(command: VoiceCommand) {
        val profile = state.profile.value
        val loggedIn = state.isLoggedIn()
        when (command) {
            // From the glasses: one of Home's camera buttons, from wherever
            // the phone is. The answer is said, and kept on Home's Last answer card.
            is VoiceCommand.RunCamera ->
                if (loggedIn) runCameraFeature(command.command)
                else reply("Log in first to take a picture.")

            is VoiceCommand.SetHazardWatch ->
                if (loggedIn) state.setHazardWatch(command.on)
                else reply("Log in first to start hazard watch.")

            is VoiceCommand.GuideTo ->
                if (loggedIn) guideTo(command.what)
                else reply("Log in first to take a picture.")

            // As the Pair screen's choice is, so post-login like it; said
            // back, with how to hold the phone when that is the camera.
            is VoiceCommand.UseCamera ->
                if (loggedIn) {
                    state.updateProfile(profile.copy(camera = command.camera))
                    reply(
                        when (command.camera) {
                            CameraChoice.PHONE -> SpokenPhrases.USING_PHONE_CAMERA
                            CameraChoice.GLASSES -> SpokenPhrases.USING_GLASSES_CAMERA
                        }
                    )
                } else {
                    reply("Log in first to choose a camera.")
                }

            is VoiceCommand.SetSpeechRate -> {
                state.updateProfile(profile.copy(speechRate = command.rate))
                reply(VoiceReplies.speechRate(command.rate))
            }

            VoiceCommand.SpeakFaster -> {
                val rate = SpeechRates.faster(profile.speechRate)
                state.updateProfile(profile.copy(speechRate = rate))
                reply("Faster. ${SpeechRates.spokenLabel(rate)}.")
            }

            VoiceCommand.SpeakSlower -> {
                val rate = SpeechRates.slower(profile.speechRate)
                state.updateProfile(profile.copy(speechRate = rate))
                reply("Slower. ${SpeechRates.spokenLabel(rate)}.")
            }

            // Switched at once, before the reply, so the reply is the new
            // voice saying its own name. Offline, voices that need the
            // internet are passed over, as Settings locks them.
            VoiceCommand.NextVoice, VoiceCommand.PreviousVoice, VoiceCommand.DefaultVoice -> {
                val all = speaker.voices.value
                val online = isOnline()
                if (command != VoiceCommand.DefaultVoice && !VoiceCatalog.hasChoices(all, online)) {
                    reply(VoiceReplies.NO_OTHER_VOICES)
                } else {
                    val name = when (command) {
                        VoiceCommand.NextVoice -> VoiceCatalog.next(profile.voiceName, all, online)
                        VoiceCommand.PreviousVoice -> VoiceCatalog.previous(profile.voiceName, all, online)
                        else -> null
                    }
                    state.updateProfile(profile.copy(voiceName = name))
                    speaker.useVoice(VoiceCatalog.voiceToUse(name, all, online))
                    reply(VoiceReplies.voice(VoiceCatalog.option(name, all)?.label))
                }
            }

            is VoiceCommand.SetVerbosity -> {
                state.updateProfile(profile.copy(verbosity = command.verbosity))
                reply(VoiceReplies.detail(command.verbosity))
            }

            // Said again as the kind of speech it was, at that kind's volume.
            VoiceCommand.RepeatLast -> speaker.lastUtterance?.let { speaker.speak(it.text, it.category) }
                ?: reply("I haven't said anything yet.")

            // "VISOR GO" itself paused the reading ("Listening." was said
            // over it), so by now a reading being read is a paused one.
            // Resuming says nothing first: the reading carrying on is the reply.
            // "Stop" also stops Look around turning the head, or leading it.
            VoiceCommand.StopReading ->
                if (lookAround.stop()) {
                    Unit
                } else if (speaker.reading.value != ReadingState.NONE) {
                    speaker.stop()
                    reply(VoiceReplies.READING_STOPPED)
                } else {
                    reply(VoiceReplies.NOTHING_BEING_READ)
                }

            VoiceCommand.PauseReading ->
                if (speaker.reading.value != ReadingState.NONE) {
                    speaker.pause()
                    reply(VoiceReplies.READING_PAUSED)
                } else {
                    reply(VoiceReplies.NOTHING_BEING_READ)
                }

            VoiceCommand.ResumeReading ->
                if (speaker.reading.value == ReadingState.PAUSED) speaker.resume()
                else reply(VoiceReplies.NOTHING_TO_RESUME)

            // Android only lets the listening service start with VISOR on screen.
            VoiceCommand.KeepListening -> when {
                profile.backgroundListeningEnabled && serviceRunning ->
                    reply(app.getString(R.string.background_listening_already_on))
                !isScreenShown -> reply(app.getString(R.string.background_listening_needs_screen))
                !loggedIn -> reply(app.getString(R.string.background_listening_needs_login))
                else -> state.updateProfile(profile.copy(backgroundListeningEnabled = true))
            }

            VoiceCommand.StopListening ->
                if (profile.backgroundListeningEnabled) stopBackgroundListening()
                else reply(app.getString(R.string.background_listening_already_off))

            VoiceCommand.Unrecognized -> Unit // already told the user via VoiceNavigationController

            // Carried out on screen (VisorLayout); see BackgroundPolicy.
            else -> Unit
        }
    }

    private companion object {
        const val TAG = "VISOR"
        const val HAZARD_WATCH_PIN = "hazard_watch"
        val NAV_WAKE_PHRASE = WakePhrases.keyword(WakePhrases.VOICE_NAVIGATION)
        const val COMMAND_WAKE_LOCK_MS = 60_000L

        const val RECOVERY_TAG = "VisorGlassesRecovery"
        const val SESSION_TAG = "VisorHeldSession"
        const val MAX_RECOVERY_STEPS = 4

        /** How long VISOR stays on the phone's microphone after giving the glasses' up for the camera. */
        const val MIC_YIELD_MS = 10 * 60_000L

        /** The held session is opened ahead of use at most this often. */
        const val MIN_OPEN_GAP_MS = 15_000L

        const val RESTART_LEAD_IN_MS = 1_500L
        const val RESTART_SPEECH_WAIT_MS = 8_000L
        const val ANNOUNCE_WAIT_MS = 15_000L

        const val KEY_LAST_RESTART_AT = "lastRestartAt"
        const val KEY_ANNOUNCE_AFTER_RESTART = "announceAfterRestart"
        const val KEY_REPLAY_COMMAND = "replayCommand"
        const val KEY_REPLAY_AT = "replayAt"

        /** A cut-off command is carried on with after a restart only if it was this recent. */
        const val REPLAY_WITHIN_MS = 60_000L
        const val REPLAY_DELAY_MS = 1_500L

        /** How long the SDK must have lost the glasses before VISOR treats it as stuck. */
        const val WEDGE_CONFIRM_MS = 3_000L

        /** How long an in-place reconnect has, and how often the SDK is told again meanwhile. */
        const val NUDGE_WAIT_MS = 10_000L
        const val NUDGE_REPEAT_MS = 5_000L

        /** While the link stays lost: try again this often, then less often after [FAST_ROUNDS] tries. */
        const val RECOVERY_RETRY_MS = 20_000L
        const val RECOVERY_SLOW_RETRY_MS = 60_000L
        const val FAST_ROUNDS = 6

        /** How long "Glasses reconnected" stays shown. */
        const val RECONNECTED_SHOWN_MS = 6_000L

        /** How long the restarted SDK has to find the glasses again. */
        const val RECONNECT_POLL_MS = 250L
    }
}
