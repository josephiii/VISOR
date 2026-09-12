package ucf.visor

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.Manifest.permission.RECORD_AUDIO
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import ucf.visor.ocr.TextReaderOCR
import ucf.visor.stt.Listener
import ucf.visor.tts.Speaker
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.ui.viewmodel.VisorViewModel
import kotlin.coroutines.resume
import android.util.Log
import ucf.visor.capture.FakePhotoSource
import ucf.visor.capture.ReadRequester
import ucf.visor.capture.RealCapture

class MainActivity : ComponentActivity() {

    // MWDAT User Permissions and Initial Integration
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        // Required Android permissions for the DAT SDK to function properly
        val PERMISSIONS: Array<String> = arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, CAMERA, INTERNET, RECORD_AUDIO)
    }

    val viewModel: VisorViewModel by viewModels()

    private val permissionCheckLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
            viewModel.onPermissionsResult(permissionsResult) {
                // Initialize the DAT SDK once the permissions are granted
                // This is REQUIRED before using any Wearables APIs
                Wearables.initialize(this)
                listener.start()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // High Contrast and Text size are user preferences (Settings / onboarding),
            // not just the OS light/dark setting — see AppTheme.forProfile.
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val appTheme = AppTheme.forProfile(profile, isSystemInDarkTheme())

            VisorTheme(appTheme = appTheme, textScale = profile.textScale.multiplier) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VisorLayout(
                        talk = { speaker.speak(it) },
                        viewModel = viewModel,
                        onRequestWearablesPermission = ::requestWearablesPermission,
                    )
                }

            }
        }
        textReaderOCR = TextReaderOCR()
        speaker = Speaker(this)

        // swap for RealPhoto once glasses session exists
        reader = RealCapture(FakePhotoSource(this), textReaderOCR)

        listener = Listener(assets) { phrase ->
            Log.d("VISOR", "WAKE HEARD: $phrase")
            reader.requestRead { text -> speaker.speak(text) }
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
    }
}