package ucf.visor.ui.screens.home

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.PhotoData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ucf.visor.R
import ucf.visor.stream.StreamViewModel
import ucf.visor.ui.components.SessionToggleButton
import ucf.visor.ui.viewmodel.SessionMode
import ucf.visor.ui.viewmodel.VisorViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Composable

fun HomeScreen(
    viewModel: VisorViewModel,
    talk: (String) -> Unit = {},
    modifier: Modifier = Modifier,

) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mode by viewModel.session.collectAsStateWithLifecycle()
    var objects = {}

    //Import the stream model so it starts when a session starts
    val streamViewModel: StreamViewModel = viewModel(
        factory =
            StreamViewModel.Factory(
                application = (LocalActivity.current as ComponentActivity).application,
                visorViewModel = viewModel,
            ),
    )
    LaunchedEffect(uiState.isSessionActive) {
        if(uiState.isSessionActive){
            streamViewModel.startStream()
        } else {
            streamViewModel.stopStream()
        }
    }

    Log.d("Visor","Mode: ${mode.inHazardAwarenessMode}")

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
                    viewModel.toggleSession()
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
suspend fun runHazardDetection(frame: Bitmap)=
    withContext(Dispatchers.IO){
    // PREP IMAGE
    val outputStream = ByteArrayOutputStream()
    frame.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    val imageBytes = outputStream.toByteArray()

    val imageBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "image",
            "photo.jpg",
            imageBody
        )
        .build()

    //val url = "http://10.0.2.2:8000/yolo/runHazardDetection"
        //Use above for local below for server
        val url = "http://54.173.104.183:8000/yolo/runHazardDetection"
    val request = Request.Builder().url(url).post(requestBody).build()


        val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(
            300,
            TimeUnit.SECONDS
        )   // 5 minutes for CPU inference BECAUSE IT KEEPS TIMING OUT AAAAAAAAA
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

        try {
            client.newCall(request).execute().use { response->
                //TTS objects and depths

                Log.d("Hazard Detection", response.body!!.string())

            }

        }
        catch (e: Exception){
            Log.d("Hazard Detection", "Error running hazard detection: $e")
        }

}
//Use glasses capture button to take pictures (change to cron timer for prod)
