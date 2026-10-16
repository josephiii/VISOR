package ucf.visor.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ucf.visor.network.HazardClient
import ucf.visor.tts.SpokenPhrases

// Hazard awareness: takes a photo and asks the backend what is in it and how far
// away each thing is (YOLO object detection plus depth estimation), nearest first
class HazardDetector(
    private val photoSource: PhotoSource,
    private val client: HazardClient,
    // Main-thread scope (MainActivity's lifecycleScope): answers are spoken from it.
    private val scope: CoroutineScope,
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                scope.launch {
                    val hazards = client.detect(bitmap)
                    onText(
                        if (hazards == null) SpokenPhrases.HAZARDS_NOT_CHECKED
                        else SpokenPhrases.hazardsAhead(hazards)
                    )
                }
            },
            onError = { message -> onText(message) },
        )
    }
}
