package ucf.visor.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ucf.visor.data.Verbosity
import ucf.visor.network.HazardClient
import ucf.visor.tts.SpokenPhrases

// Hazard awareness: takes a photo and asks the backend what is in it and how far
// away each thing is (YOLO object detection plus depth estimation), nearest
// first, with which side each is on. A Brief Detail setting names only the nearest few.
class HazardDetector(
    private val photoSource: PhotoSource,
    private val client: HazardClient,
    // Main-thread scope (MainActivity's lifecycleScope): answers are spoken from it.
    private val scope: CoroutineScope,
    // Read at each capture, so a change in Settings applies to the very next one.
    private val verbosity: () -> Verbosity = { Verbosity.STANDARD },
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                scope.launch {
                    val hazards = client.detect(bitmap)
                    val limit =
                        if (verbosity() == Verbosity.BRIEF) SpokenPhrases.BRIEF_HAZARD_LIMIT else Int.MAX_VALUE
                    onText(
                        if (hazards == null) SpokenPhrases.HAZARDS_NOT_CHECKED
                        // The boxes are in the pixels of the photo sent, which is this bitmap.
                        else SpokenPhrases.hazardsAhead(hazards, imageWidth = bitmap.width, limit = limit)
                    )
                }
            },
            onError = { message -> onText(message) },
        )
    }
}
