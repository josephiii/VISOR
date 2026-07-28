package ucf.visor

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

// MWDAT
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import ucf.visor.ocr.TextReaderOCR
import ucf.visor.tts.Speaker
import ucf.visor.ui.VisorLayout
import ucf.visor.ui.screens.auth.PreviewLoginScreen
import ucf.visor.ui.theme.VisorTheme
import ucf.visor.wearables.WearablesViewModel

import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {

    // MWDAT User Permissions and Initial Integration
    ///////////////////////////////////////////////////////////////////////////
    companion object {
        // Required Android permissions for the DAT SDK to function properly
        val PERMISSIONS: Array<String> = arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, CAMERA, INTERNET)
    }

    val viewModel: WearablesViewModel by viewModels()

    private val permissionCheckLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
            viewModel.onPermissionsResult(permissionsResult) {
                // Initialize the DAT SDK once the permissions are granted
                // This is REQUIRED before using any Wearables APIs
                Wearables.initialize(this)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VisorLayout(
                        viewModel = viewModel,
                        onRequestWearablesPermission = ::requestWearablesPermission
                    )
                }

            }


        }
        textReaderOCR = TextReaderOCR()
        speaker = Speaker(this)
    }

    override fun onStart() {
        super.onStart()
        // First, ensure the app has necessary Android permissions
        permissionCheckLauncher.launch(PERMISSIONS)
    }

    // Make sure these are actually destroyed.
    fun onDestory() {
        super.onDestroy()
        textReaderOCR.close()
        speaker.shutdown()
    }
}

///////////////////////////////////////////////////////////////////////////////
// Insert the component here to view it with Visor Themes applied.
@Preview
@Composable
fun PreviewWithTheme() {
    VisorTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Place component here.
            //...
            PreviewLoginScreen()
        }
    }
}