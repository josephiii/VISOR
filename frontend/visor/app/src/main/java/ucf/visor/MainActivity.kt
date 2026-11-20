package ucf.visor

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import ucf.visor.capture.PhoneCameraSource
import ucf.visor.data.SoundCategory
import ucf.visor.data.VoiceCategory
import ucf.visor.engine.VisorEngine
import ucf.visor.tts.Chimes.Chime
import ucf.visor.tts.SpokenText
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.feedback.LocalHaptics
import ucf.visor.ui.theme.VisorColors
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.wearables.VoiceLaunches
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    // MWDAT User Permissions and Initial Integration
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        // Required Android permissions for the DAT SDK to function properly
        val PERMISSIONS: Array<String> =
            arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, CAMERA, INTERNET, RECORD_AUDIO)

        /** How long a voice launch waits for its stream delivery before acting on the intent alone. */
        private const val VOICE_STREAM_GRACE_MS = 3_000L
    }

    val viewModel: VisorViewModel by viewModels()

    /**
     * Everything that listens and speaks. It belongs to the process, not to
     * this screen, so VISOR can go on answering with the screen off
     * (background listening); this screen tells it when it is shown and lends
     * it the phone's camera and the Meta AI permission prompt.
     */
    private val engine: VisorEngine by lazy { VisorApp.from(this).engine }

    // Whether this screen has acted on the intent that opened it yet.
    private var launchIntentHandled = false
    private val permissionCheckLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
            viewModel.onPermissionsResult(permissionsResult) {
                // Initializes the DAT SDK (once per process) and starts listening.
                engine.onWearablesReady()
                if (launchIntentHandled) return@onPermissionsResult
                launchIntentHandled = true
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
    private lateinit var phoneCamera: PhoneCameraSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A screen recreated (rotation) has acted on its intent already.
        launchIntentHandled = savedInstanceState != null

        // What only a screen has, lent to the engine while this one exists.
        phoneCamera = PhoneCameraSource(this)
        engine.phoneCamera.attach(phoneCamera)
        engine.glassesPermissionRequester = permissionRequester

        setContent {
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val colors = VisorColors.forProfile(profile, isSystemInDarkTheme())

            // The status bar's icons follow VISOR's page, not the phone's dark
            // mode: VISOR can be dark on a light phone (Settings > Light or
            // dark), and a user's own colors can be anything, so icons drawn
            // for the phone's mode could be white on white.
            LaunchedEffect(colors.isDark) {
                val bars = if (colors.isDark) SystemBarStyle.dark(TRANSPARENT)
                else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }

            VisorTheme(colors = colors, textScale = profile.textScale.multiplier) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalHaptics provides engine.haptics) {
                        VisorLayout(
                            talk = { text, category -> engine.speaker.speak(text, category) },
                            speakAt = { text, volume -> engine.speaker.speakAt(text, volume) },
                            playSample = ::playSample,
                            lastSpoken = { engine.speaker.lastUtterance },
                            viewModel = viewModel,
                            onRequestWearablesPermission = ::requestWearablesPermission,
                            onCapture = engine::runCameraFeature,
                            voiceNav = engine.voiceNav,
                            glassesNav = engine.glassesNav,
                            screenCommands = engine.screenCommands,
                            reading = engine.speaker,
                            lookAround = engine.lookAround,
                            voices = engine.speaker,
                        )
                    }
                }

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
                    // Said by the glasses: "Visor", not "V. I. S. O. R."
                    VoiceLaunches.get().acknowledge(launch, result.success, SpokenText.forSpeech(result.spoken))
                }
            }
        }
    }

    /** This screen's Meta AI permission prompt, as lent to the engine (and taken back in onDestroy). */
    private val permissionRequester: suspend (Permission) -> PermissionStatus = { requestWearablesPermission(it) }

    /** Settings' preview of a sound effect's volume: one of its sounds, at that volume. */
    private fun playSample(category: SoundCategory) = when (category) {
        SoundCategory.THINKING_BEEPS -> engine.thinkingBeeper.beepOnce()
        SoundCategory.READY_CHIME -> engine.chimes.play(Chime.READY)
        SoundCategory.GUIDANCE_TONES -> engine.playGuidanceSample()
    }

    override fun onStart() {
        super.onStart()
        engine.onScreenShown()
        permissionCheckLauncher.launch(PERMISSIONS)
    }

    override fun onStop() {
        super.onStop()
        // A rotation is not leaving the screen: the new one is shown at once.
        if (!isChangingConfigurations) engine.onScreenHidden()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Before the SDK is up, the permission callback handles getIntent()
        // itself — which setIntent() just made this one.
        if (engine.isWearablesReady) {
            launchIntentHandled = true
            handleWearablesIntent(intent)
        }
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
            engine.chimes.play(Chime.READY)
            engine.haptics.ready()
        }
        // Why VISOR isn't ready (not logged in) is an answer, too important to
        // go quiet along with the status updates.
        engine.speaker.speak(
            result.spoken,
            if (result.success) VoiceCategory.STATUS_UPDATES else VoiceCategory.ANSWERS,
        )
        return result
    }

    /** Registration begun in the Meta AI app: continue it here, and say so. */
    private fun onRegistrationRequest(request: RegistrationRequest) {
        engine.speaker.speak("Connecting VISOR to your glasses.", VoiceCategory.STATUS_UPDATES)
        request.continueRegistration(this)
            .onFailure { error, _ -> Log.w("VISOR", "Registration request: ${error.description}") }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The engine lives on (background listening); only what this screen lent it goes.
        engine.phoneCamera.detach(phoneCamera)
        if (engine.glassesPermissionRequester === permissionRequester) {
            engine.glassesPermissionRequester = null
        }
        phoneCamera.close()
    }
}
