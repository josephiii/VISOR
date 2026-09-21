package ucf.visor

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ucf.visor.capture.FakePhotoSource
import ucf.visor.capture.ReadRequester
import ucf.visor.capture.RealCapture
import ucf.visor.ocr.TextReaderOCR
import ucf.visor.stt.Listener
import ucf.visor.tts.Speaker
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.glasses.GlassesNavigationController
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import ucf.visor.ui.voice.VoiceNavigationController
import kotlin.coroutines.resume

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
        private const val NAV_WAKE_PHRASE = "VISORGO"

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
                speaker.setSpeechRate(profile.speechRate.multiplier)
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
        textReaderOCR = TextReaderOCR()
        speaker = Speaker(this)

        // swap for RealPhoto once glasses session exists
        reader = RealCapture(FakePhotoSource(this), textReaderOCR)

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
                NAV_WAKE_PHRASE -> voiceNav.activate()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        permissionCheckLauncher.launch(PERMISSIONS)
    }

    override fun onDestroy() {
        super.onDestroy()
        textReaderOCR.close()
        speaker.shutdown()
        listener.shutdown()
        voiceNav.shutdown()
        glassesNav.shutdown()
    }
}