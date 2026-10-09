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
import ucf.visor.capture.FakePhotoSource
import ucf.visor.capture.ReadRequester
import ucf.visor.capture.RealCapture
import ucf.visor.ocr.TextReaderOCR
import ucf.visor.ocr.ObjectDetector
import ucf.visor.capture.DescribeObject
import ucf.visor.stt.Listener
import ucf.visor.tts.Speaker
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController
import ucf.visor.wearables.VoiceLaunches
import kotlin.coroutines.resume

import ucf.visor.capture.SceneDescriber
import ucf.visor.network.VlmClient

import ucf.visor.tts.ThinkingBeeper
import ucf.visor.capture.GlassesPhotoSource


class MainActivity : ComponentActivity() {

    // MWDAT User Permissions and Initial Integration
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        // Required Android permissions for the DAT SDK to function properly
        val PERMISSIONS: Array<String> =
            arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, CAMERA, INTERNET, RECORD_AUDIO)

        private val OCR_WAKE_PHRASES = setOf(
            "VISORWHATDOESTHISSAY",
            "VISORREADTHIS",
            "VISORWHATISTHIS",
        )

        private val OBJECT_WAKE_PHRASES = setOf(
            "VISORWHATAMIHOLDING",
        )

        private val SCENE_WAKE_PHRASES = setOf(
            "VISORWHATDOYOUSEE",
            "VISORWHATSINFRONTOFME",
        )
        private const val NAV_WAKE_PHRASE = "VISORGO"

        /** How long a voice launch waits for its stream delivery before acting on the intent alone. */
        private const val VOICE_STREAM_GRACE_MS = 3_000L

        private fun normalizeKeyword(phrase: String): String =
            phrase.uppercase().filter { it.isLetter() }
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
                listener.start()
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
    private lateinit var reader: ReadRequester
    private lateinit var voiceNav: VoiceNavigationController
    private lateinit var glassesNav: GlassesNavigationController
    private lateinit var sceneReader: ReadRequester
    private lateinit var sceneDescriber: ReadRequester
    private lateinit var objectDetector: ObjectDetector
    private lateinit var thinkingBeeper: ThinkingBeeper
    private lateinit var glassesCamera: GlassesPhotoSource


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val appTheme = AppTheme.forProfile(profile, isSystemInDarkTheme())

            LaunchedEffect(profile.voiceNavigationEnabled) {
                voiceNav.setEnabled(profile.voiceNavigationEnabled)
            }

            LaunchedEffect(profile.glassesTapNavigationEnabled) {
                glassesNav.setEnabled(profile.glassesTapNavigationEnabled)
            }

            LaunchedEffect(profile.speechRate) {
                speaker.setSpeechRate(profile.speechRate)
            }

            VisorTheme(appTheme = appTheme, textScale = profile.textScale.multiplier) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VisorLayout(
                        talk = { speaker.speak(it) },
                        lastSpoken = { speaker.lastUtterance },
                        viewModel = viewModel,
                        onRequestWearablesPermission = ::requestWearablesPermission,
                        voiceNav = voiceNav,
                        glassesNav = glassesNav,
                    )
                }

            }
        }

        glassesCamera = GlassesPhotoSource(
            requestPermission = ::requestWearablesPermission,
            onCapturing = { speaker.speak("Taking picture") },
        )
        textReaderOCR = TextReaderOCR()
        objectDetector = ObjectDetector()
        speaker = Speaker(this)
        thinkingBeeper = ThinkingBeeper(isSpeaking = { speaker.isSpeaking() })

        // OCR reads the label image (swap for RealPhoto source once session exists)
        reader = RealCapture(
            FakePhotoSource(this, "photo_test/sample_pill_bottle.jpg"),
            textReaderOCR,
        )

        // object detector reads the objects image (swap for RealPhoto source once session exists)
        sceneReader = DescribeObject(
            FakePhotoSource(this, "photo_test/living_room.png"),
            objectDetector,
        )

        sceneDescriber = SceneDescriber(
            photoSource = glassesCamera,
            vlm = VlmClient(),
            fallback = objectDetector,
        )

        voiceNav = VoiceNavigationController(
            context = this,
            speak = { speaker.speak(it) },
            pauseWakeListening = { listener.stop() },
            resumeWakeListening = { listener.start() },
            isSpeaking = { speaker.isSpeaking() },
        )

        glassesNav = GlassesNavigationController { viewModel.deviceSelector }

        // Single KWS engine for every wake phrase (OCR reading + "VISOR GO"):
        // running two overlapping AudioRecord/model instances would double up
        // on the microphone and CPU for no benefit, so this callback routes by
        // which phrase actually fired instead of owning a second Listener.
        listener = Listener(assets) { phrase ->
            Log.d("VISOR", "WAKE HEARD: $phrase")
            when (normalizeKeyword(phrase)) {
                in OCR_WAKE_PHRASES -> reader.requestRead { text -> speaker.speak(text) }
                in OBJECT_WAKE_PHRASES -> sceneReader.requestRead { text -> speaker.speak(text) }
                in SCENE_WAKE_PHRASES -> {
                    speaker.speak("Awaiting scene description")
                    thinkingBeeper.start()
                    sceneDescriber.requestRead { text ->
                        thinkingBeeper.stop()
                        speaker.speak(text)
                    }
                }
                NAV_WAKE_PHRASE -> voiceNav.activate()
            }
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
        val result = viewModel.startSessionFromVoice()
        speaker.speak(result.spoken)
        return result
    }

    /** Registration begun in the Meta AI app: continue it here, and say so. */
    private fun onRegistrationRequest(request: RegistrationRequest) {
        speaker.speak("Connecting VISOR to your glasses.")
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
        glassesCamera.close()
    }
}