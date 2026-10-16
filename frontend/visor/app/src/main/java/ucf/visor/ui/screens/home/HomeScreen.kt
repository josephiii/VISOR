package ucf.visor.ui.screens.home

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import ucf.visor.R
import ucf.visor.stream.StreamUiState
import ucf.visor.stream.StreamViewModel
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel

@Composable

fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {},
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    modifier: Modifier = Modifier,

) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.session.collectAsStateWithLifecycle()

    //Import the stream model so it starts when a session starts
    val streamViewModel: StreamViewModel = viewModel(
        factory =
            StreamViewModel.Factory(
                application = (LocalActivity.current as ComponentActivity).application,
                visorViewModel = viewModel,
            ),
    )
    //Import the stream model as state to track live status (for api)
    val streamView = streamViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSessionActive) {
        if(uiState.isSessionActive){
            streamViewModel.startStream()
        } else {
            streamViewModel.stopStream()
        }
    }

    LaunchedEffect(streamView.value.sceneDescription) {
        if (streamView.value.sceneDescription.isNotEmpty()) {
            talk(streamView.value.sceneDescription)
        }
    }

    LaunchedEffect(streamView.value.hazards) {
        if(streamView.value.hazards.isNotEmpty()){
            talk(streamView.value.hazards.joinToString(". "){it.prepareSpeech()})
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(all = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            SessionToggleButton(
                isSessionActive = uiState.isSessionActive,
                onToggle = {
                    viewModel.toggleSession(onRequestWearablesPermission)
                    if (uiState.isSessionActive) {
                        talk("Ending Session")
                    } else {
                        talk("Starting Session")
                    }
                }
            )

            Text(
                text =
                    if (uiState.isSessionActive) {
                        stringResource(R.string.end_session)
                    } else {
                        stringResource(R.string.start_session)
                    },
                style = MaterialTheme.typography.headlineSmall
            )


            Spacer(modifier = Modifier.size(100.dp))
            Column(){
                Row(
                    modifier= Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Text("OCR")
                    Switch(checked = mode.inReadingAssistanceMode && uiState.isSessionActive, onCheckedChange = {viewModel.setMode(SessionMode.READER)})
                }
                Row(
                    modifier= Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Text("Scene Description")
                    Switch(checked = mode.inSceneDescriptionMode && uiState.isSessionActive, onCheckedChange = {viewModel.setMode(SessionMode.SCENE)})
                }
                Row(
                    modifier= Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ){
                    Text("Hazard Detection")
                    Switch(checked = mode.inHazardAwarenessMode && uiState.isSessionActive, onCheckedChange = {viewModel.setMode(SessionMode.HAZARD)})
                }
                if(uiState.isSessionActive){
                    Button({ streamViewModel.capturePhoto() }) { Text("Tmp Capture Button") }
                }
            }
        }
    }
}

suspend fun runOCR(){}

//import function from sharephotodialog.kt file
suspend fun runSceneDescription(){}
//Use glasses capture button to take pictures (change to cron timer for prod)
