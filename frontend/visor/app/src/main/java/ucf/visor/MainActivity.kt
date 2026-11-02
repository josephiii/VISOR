package ucf.visor

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.registration.RegistrationRequest
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.voiceinvocations.isVoiceInvocationsIntent
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ucf.visor.capture.ChosenPhotoSource
import ucf.visor.capture.GlassesPhotoSource
import ucf.visor.capture.HazardDetector
import ucf.visor.capture.NotifyingPhotoSource
import ucf.visor.capture.ObjectIdentifier
import ucf.visor.capture.PhoneCameraSource
import ucf.visor.capture.PhotoSource
import ucf.visor.capture.ReadRequester
import ucf.visor.capture.SceneDescriber
import ucf.visor.capture.TextReader
import ucf.visor.data.AudioMix
import ucf.visor.data.CameraChoice
import ucf.visor.data.SessionStore
import ucf.visor.data.SoundCategory
import ucf.visor.data.Verbosity
import ucf.visor.data.VoiceCategory
import ucf.visor.network.HazardClient
import ucf.visor.network.VlmClient
import ucf.visor.recognition.ObjectDetector
import ucf.visor.recognition.TextReaderOCR
import ucf.visor.stt.CameraCommand
import ucf.visor.stt.CommandRecognizer
import ucf.visor.stt.Listener
import ucf.visor.stt.WakePhrases
import ucf.visor.tts.Chimes
import ucf.visor.tts.Chimes.Chime
import ucf.visor.tts.Speaker
import ucf.visor.tts.SpokenPhrases
import ucf.visor.tts.ThinkingBeeper
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.feedback.Haptics
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.SceneQuestionController
import ucf.visor.ui.voice.VoiceNavState
import ucf.visor.ui.voice.VoiceNavigationController
import ucf.visor.wearables.VoiceLaunches
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    // MWDAT User Permissions and Initial Integration
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        // Required Android permissions for the DAT SDK to function properly
        val PERMISSIONS: Array<String> =
            arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, CAMERA, INTERNET, RECORD_AUDIO)

        // The camera wake phrases are CameraCommand's (stt/WakePhrases.kt).
        private val NAV_WAKE_PHRASE = WakePhrases.keyword(WakePhrases.VOICE_NAVIGATION)

        /** How long a voice launch waits for its stream delivery before acting on the intent alone. */
        private const val VOICE_STREAM_GRACE_MS = 3_000L
    }

    val viewModel: VisorViewModel by viewModels()
    private var wearablesInitialized = false
    private val permissionCheckLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
            viewModel.onPermissionsResult(permissionsResult) @androidx.annotation.RequiresPermission(
                android.Manifest.permission.RECORD_AUDIO
            ) {
                if (wearablesInitialized) return@onPermissionsResult
                wearablesInitialized = true
                // Initialize the DAT SDK once the permissions are granted
                // This is REQUIRED before using any Wearables APIs
                Wearables.initialize(this)
                updateWakeListening()
                // Only now may the on-glasses nav touch the SDK — resolving
                // viewModel.deviceSelector any earlier throws WearablesException.
                glassesNav.onWearablesReady()
                // "Hey Meta, start VISOR": open the stream early so a voice
                // command that cold-launched the app is still delivered.
                VoiceLaunches.get().start()
                // The intent that launched VISOR may be a voice launch or a
                // Meta AI registration request; neither could be read before
                // the SDK existed.
                handleWearablesIntent(intent)
            }
        }

    private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
    private val permissionMutex = Mutex()

    // Requesting wearable device permissions via the Meta AI app
    private val permissionsResultLauncher =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
            permissionContinuation?.resume(permissionStatus)
            permissionContinuation = null
        }



    // Convenience method to make a permission request in a sequential manner
    // Uses a Mutex to ensure requests are processed one at a time, preventing race conditions
    suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
        return permissionMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                permissionContinuation = continuation
                continuation.invokeOnCancellation { permissionContinuation = null }
                permissionsResultLauncher.launch(permission)
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // MAIN
    private lateinit var textReaderOCR: TextReaderOCR
    private lateinit var speaker: Speaker
    private lateinit var listener: Listener
    private lateinit var textReader: ReadRequester
    private lateinit var voiceNav: VoiceNavigationController
    private lateinit var glassesNav: GlassesNavigationController
    private lateinit var objectIdentifier: ReadRequester
    private lateinit var objectDetector: ObjectDetector
    private lateinit var sceneDescriber: ReadRequester
    private lateinit var hazardDetector: ReadRequester
    private lateinit var thinkingBeeper: ThinkingBeeper
    private lateinit var chimes: Chimes
    private lateinit var haptics: Haptics
    private lateinit var glassesCamera: GlassesPhotoSource
    private lateinit var phoneCamera: PhoneCameraSource

    private lateinit var commandRecognizer: CommandRecognizer
    private lateinit var sceneQuestion: SceneQuestionController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val appTheme = AppTheme.forProfile(profile, isSystemInDarkTheme())

            LaunchedEffect(profile.voiceNavigationEnabled) {
                voiceNav.setEnabled(profile.voiceNavigationEnabled)
            }

            LaunchedEffect(profile.voiceNavigationEnabled, profile.cameraCommands) {
                updateWakeListening()
            }

            LaunchedEffect(profile.glassesTapNavigationEnabled) {
                glassesNav.setEnabled(profile.glassesTapNavigationEnabled)
            }

            LaunchedEffect(profile.speechRate) {
                speaker.setSpeechRate(profile.speechRate)
            }

            VisorTheme(appTheme = appTheme, textScale = profile.textScale.multiplier) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalHaptics provides haptics) {
                        VisorLayout(
                            talk = { text, category -> speaker.speak(text, category) },
                            speakAt = { text, volume -> speaker.speakAt(text, volume) },
                            playSample = ::playSample,
                            lastSpoken = { speaker.lastUtterance },
                            viewModel = viewModel,
                            onRequestWearablesPermission = ::requestWearablesPermission,
                            onCapture = ::runCameraFeature,
                            voiceNav = voiceNav,
                            glassesNav = glassesNav,
                        )
                    }
                }

            }
        }

        glassesCamera = GlassesPhotoSource(
            requestPermission = ::requestWearablesPermission,
        )
        phoneCamera = PhoneCameraSource(this)
        textReaderOCR = TextReaderOCR()
        objectDetector = ObjectDetector()
        // Volumes are read from the profile as each sound plays, not pushed in a
        // LaunchedEffect like the speech rate: Settings plays a preview the
        // moment a volume changes, before an effect would have run.
        chimes = Chimes(loudness = { audio().loudness(it) })
        haptics = Haptics(this) { viewModel.userProfile.value.vibrationEnabled }
        speaker = Speaker(
            this,
            loudness = { audio().loudness(it) },
            leadInMs = { chimes.remainingMs() },
        )
        thinkingBeeper = ThinkingBeeper(
            isSpeaking = { speaker.isSpeaking() },
            volume = { audio().loudness(SoundCategory.THINKING_BEEPS) },
        )

        // Each picture comes from the camera chosen on the Pair screen: the
        // glasses, or the phone's own. Without real glasses, either choose the
        // phone (the emulator's camera works too), or pair mock glasses in the
        // debug menu and pick the photo they should "take" (Select image).
        val chosenCamera = ChosenPhotoSource(glassesCamera, phoneCamera, ::chosenCamera)
        val chosenReadingCamera =
            ChosenPhotoSource(glassesCamera.forReading, phoneCamera.forReading, ::chosenCamera)

        // Each camera feature says when its picture is taken, so its button on
        // Home can show "Thinking…" in place of "Taking picture…".
        fun cameraFor(command: CameraCommand, source: PhotoSource = chosenCamera) =
            NotifyingPhotoSource(source) { viewModel.onPictureTaken(command) }

        textReader = TextReader(cameraFor(CameraCommand.READ_TEXT, chosenReadingCamera), textReaderOCR)

        objectIdentifier = ObjectIdentifier(cameraFor(CameraCommand.IDENTIFY_OBJECTS), objectDetector)

        val sessionStore = SessionStore(this)
        sceneDescriber = SceneDescriber(
            photoSource = NotifyingPhotoSource(cameraFor(CameraCommand.DESCRIBE_SCENE)) {
                speaker.speak(SpokenPhrases.AWAITING_SCENE_DESCRIPTION, VoiceCategory.STATUS_UPDATES)
            },
            vlm = VlmClient(accessToken = sessionStore::accessToken),
            scope = lifecycleScope,
            fallback = objectDetector,
            verbosity = ::verbosity,
        )

        commandRecognizer = CommandRecognizer(this)
        sceneQuestion = SceneQuestionController(
            recognizer = commandRecognizer,
            describer = SceneDescriber(
                photoSource = cameraFor(CameraCommand.ASK_QUESTION),
                vlm = VlmClient(accessToken = sessionStore::accessToken),
                scope = lifecycleScope,
                verbosity = ::verbosity,
            ),
            beeper = thinkingBeeper,
            speak = { text -> speaker.speak(text, VoiceCategory.VOICE_COMMANDS) },
            isSpeaking = { speaker.isSpeaking() },
            pauseWakeListening = { listener.stop() },
            resumeWakeListening = { if (wantsWakeListening()) listener.start() },
            onAsked = { viewModel.onCaptureStarted(CameraCommand.ASK_QUESTION) },
            onAnswered = { answer -> viewModel.onAnswered(CameraCommand.ASK_QUESTION, answer) },
            takingPicture = ::takingPicturePhrase,
        )

        hazardDetector = HazardDetector(
            photoSource = cameraFor(CameraCommand.CHECK_HAZARDS),
            client = HazardClient(accessToken = sessionStore::accessToken),
            scope = lifecycleScope,
            verbosity = ::verbosity,
        )

        voiceNav = VoiceNavigationController(
            context = this,
            speak = { speaker.speak(it, VoiceCategory.VOICE_COMMANDS) },
            pauseWakeListening = { listener.stop() },
            // Not updateWakeListening(): this runs while voice navigation still
            // holds the microphone, which that waits out.
            resumeWakeListening = { if (wantsWakeListening()) listener.start() },
            isSpeaking = { speaker.isSpeaking() },
        )

        glassesNav = GlassesNavigationController { viewModel.deviceSelector }

        // Single KWS engine for every wake phrase (OCR reading + "VISOR GO"):
        // running two overlapping AudioRecord/model instances would double up
        // on the microphone and CPU for no benefit, so this callback routes by
        // which phrase actually fired instead of owning a second Listener.
        listener = Listener(assets) { phrase ->
            Log.d("VISOR", "WAKE HEARD: $phrase")
            val keyword = WakePhrases.keyword(phrase)
            if (keyword == NAV_WAKE_PHRASE) voiceNav.activate()
            else CameraCommand.forKeyword(keyword)?.let(::onCameraCommand)
        }

        // Answer every "Hey Meta, start VISOR" the glasses deliver, exactly once.
        // Collected only while VISOR is visible: acting on a launch means
        // showing Home, and the answer should describe what the wearer gets.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VoiceLaunches.get().pending.collect { launches ->
                    val launch = launches.firstOrNull() ?: return@collect
                    val result = performVoiceLaunch()
                    VoiceLaunches.get().acknowledge(launch, result.success, result.spoken)
                }
            }
        }
    }

    /** The camera chosen on the Pair screen (or by voice), as of right now. */
    private fun chosenCamera(): CameraChoice = viewModel.userProfile.value.camera

    /** How much the camera features say: Settings > Speech > Detail. */
    private fun verbosity(): Verbosity = viewModel.userProfile.value.verbosity

    /** What VISOR says as a camera feature takes its picture: which camera, when it is the phone's. */
    private fun takingPicturePhrase(): String = when (chosenCamera()) {
        CameraChoice.GLASSES -> SpokenPhrases.TAKING_PICTURE
        CameraChoice.PHONE -> SpokenPhrases.TAKING_PICTURE_WITH_PHONE
    }

    /** The volumes Settings has set for each kind of sound, as of right now. */
    private fun audio(): AudioMix = viewModel.userProfile.value.audio

    /**
     * Every camera feature sounds the same, whether a wake phrase, a button
     * on Home or one on the glasses asked for it: "Taking picture" ("with
     * your phone", when that is the camera), beeps while it thinks, then the
     * answer. Its button on Home shows each step too
     * (see VisorViewModel.captures), and Home keeps the answer on screen, for
     * anyone who can't hear them.
     */
    private fun runCameraFeature(command: CameraCommand) {
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
        }
        viewModel.onCaptureStarted(command)
        speaker.speak(takingPicturePhrase(), VoiceCategory.STATUS_UPDATES)
        thinkingBeeper.start()
        feature.requestRead { text ->
            viewModel.onAnswered(command, text)
            // Another picture may still be being worked on: its beeps go on.
            if (viewModel.captures.value.isEmpty()) thinkingBeeper.stop()
            speaker.speak(text, VoiceCategory.ANSWERS)
        }
    }

    /** A camera wake phrase: its feature, unless Settings has turned it off or no one is logged in. */
    private fun onCameraCommand(command: CameraCommand) {
        if (command !in viewModel.userProfile.value.cameraCommands) {
            // Silent on purpose. A command is often turned off because it kept
            // starting by mistake, and announcing that would be the same nuisance.
            Log.d("VISOR", "Wake phrase for $command ignored: turned off in Settings")
            return
        }
        // A camera command works the moment someone is logged in: nothing else
        // to start first. Said as voice navigation's own replies are, with the
        // wake phrase listener paused (it says "VISOR GO"), and kept at the top
        // of the screen as well, with the rejected buzz, for anyone who can't
        // hear it.
        if (!viewModel.uiState.value.isAuthComplete) {
            val problem = getString(R.string.camera_command_needs_login)
            haptics.rejected()
            voiceNav.speakGuarded(problem)
            viewModel.setRecentError(problem)
            return
        }
        runCameraFeature(command)
    }

    /** Settings' preview of a sound effect's volume: one of its sounds, at that volume. */
    private fun playSample(category: SoundCategory) = when (category) {
        SoundCategory.THINKING_BEEPS -> thinkingBeeper.beepOnce()
        SoundCategory.READY_CHIME -> chimes.play(Chime.READY)
    }

    /**
     * Whether any spoken command is on. With every one of them turned off in
     * Settings, VISOR leaves the microphone alone.
     */
    private fun wantsWakeListening(): Boolean {
        val profile = viewModel.userProfile.value
        return profile.voiceNavigationEnabled || profile.cameraCommands.isNotEmpty()
    }

    /**
     * Starts or stops listening for wake phrases to match Settings. Not while
     * "VISOR GO" has the microphone: voice navigation hands it back itself, and
     * checks [wantsWakeListening] as it does.
     */
    private fun updateWakeListening() {
        if (!wearablesInitialized || voiceNav.state.value != VoiceNavState.IDLE) return
        if (wantsWakeListening()) listener.start() else listener.stop()
    }

    override fun onStart() {
        super.onStart()
        permissionCheckLauncher.launch(PERMISSIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Before the SDK is up, the permission callback handles getIntent()
        // itself — which setIntent() just made this one.
        if (wearablesInitialized) handleWearablesIntent(intent)
    }

    /**
     * The two MWDAT 1.0 intents VISOR can be opened with: a registration
     * request started from the Meta AI app, and a voice launch.
     */
    private fun handleWearablesIntent(intent: Intent) {
        Wearables.handleIntent(intent) { request -> onRegistrationRequest(request) }
            .onFailure { error, _ -> Log.w("VISOR", "Registration intent: ${error.description}") }

        if (isVoiceInvocationsIntent(intent)) {
            // The launch itself arrives on the voice-invocation stream and is
            // answered there. If the stream cannot deliver it (not connected,
            // or not approved in the Developer Center), still do what the
            // wearer asked for rather than open silently.
            // The stream can deliver just before or just after the intent, so
            // any launch handled from a few seconds before it counts.
            val openedAt = SystemClock.elapsedRealtime()
            lifecycleScope.launch {
                delay(VOICE_STREAM_GRACE_MS)
                if (lastVoiceLaunchAt < openedAt - VOICE_STREAM_GRACE_MS) performVoiceLaunch()
            }
        }
    }

    private var lastVoiceLaunchAt = 0L

    private fun performVoiceLaunch(): VisorViewModel.VoiceLaunchResult {
        lastVoiceLaunchAt = SystemClock.elapsedRealtime()
        val result = viewModel.openFromVoice()
        // Felt and heard before the words: VISOR is ready for a question.
        if (result.success) {
            chimes.play(Chime.READY)
            haptics.ready()
        }
        // Why VISOR isn't ready (not logged in) is an answer, too important to
        // go quiet along with the status updates.
        speaker.speak(
            result.spoken,
            if (result.success) VoiceCategory.STATUS_UPDATES else VoiceCategory.ANSWERS,
        )
        return result
    }

    /** Registration begun in the Meta AI app: continue it here, and say so. */
    private fun onRegistrationRequest(request: RegistrationRequest) {
        speaker.speak("Connecting VISOR to your glasses.", VoiceCategory.STATUS_UPDATES)
        request.continueRegistration(this)
            .onFailure { error, _ -> Log.w("VISOR", "Registration request: ${error.description}") }
    }

    override fun onDestroy() {
        super.onDestroy()
        textReaderOCR.close()
        objectDetector.close()
        speaker.shutdown()
        listener.shutdown()
        voiceNav.shutdown()
        glassesNav.shutdown()
        thinkingBeeper.stop()
        chimes.release()
        glassesCamera.close()
        commandRecognizer.shutdown()
        phoneCamera.close()
    }
}