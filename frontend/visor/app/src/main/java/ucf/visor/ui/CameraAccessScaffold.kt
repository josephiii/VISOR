package ucf.visor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

import ucf.visor.BuildConfig
import ucf.visor.ui.screens.HomeScreen
import ucf.visor.ui.screens.MockDeviceKitScreen
import ucf.visor.ui.screens.NonStreamScreen
import ucf.visor.ui.screens.StreamScreen
import ucf.visor.wearables.WearablesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,
) {
    // For more details on the uiState, see the data class wearables/WearablesUiState.kt
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Used to display errors. Pass errors strings through uiState.recentError to be displayed through the snackbar.
    val snackbarHostState = remember { SnackbarHostState() }

    // DEBUG TOOL: Mock Device Kit Debug button
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Observe recent errors and show snackbar.
    LaunchedEffect(uiState.recentError) {
        uiState.recentError?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearRecentError()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {

                // Will route to VerifyScreen() and EnterCodeScreen().
//                uiState.isSigningUp ->
//                    CreateAccountScreen(
//                        wearablesViewModel = viewModel,
//                    )
//
//                uiState.isLoggingIn ->
//                    CreateAccountScreen(
//                        wearablesViewModel = viewModel,
//                    )
//
//                uiState.hasForgotPassword ->
//                    CreateAccountScreen(
//                        wearablesViewModel = viewModel,
//                    )
//
//                uiState.isNewUser ->
//                    CreateAccountScreen(
//                        wearablesViewModel = viewModel,
//                    )

                uiState.isStreaming ->
                    StreamScreen(
                        wearablesViewModel = viewModel,
                    )

                uiState.isRegistered ->
                    NonStreamScreen(
                        viewModel = viewModel,
                        onRequestWearablesPermission = onRequestWearablesPermission,
                    )

                // HomeScreen, the "root" screen for VISOR users.
                else ->
                    HomeScreen(
                        viewModel = viewModel,
                    )
            }

            // Error logging snackbar.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                snackbar = { data ->
                    Snackbar(
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Camera Access error",
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(data.visuals.message)
                        }
                    }
                },
            )

            // DEBUG TOOL: debugging button tools -> Mock Device Toolkit
            if (BuildConfig.DEBUG) {
                FloatingActionButton(
                    onClick = { viewModel.showDebugMenu() },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = "Debug Menu")
                }

                if (uiState.isDebugMenuVisible) {
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.hideDebugMenu() },
                        sheetState = bottomSheetState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        MockDeviceKitScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
