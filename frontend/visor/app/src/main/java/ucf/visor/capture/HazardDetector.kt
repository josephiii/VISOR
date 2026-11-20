package ucf.visor.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ucf.visor.data.Verbosity
import ucf.visor.data.VisionPlan
import ucf.visor.network.HazardClient
import ucf.visor.tts.SpokenPhrases

// Hazard awareness: takes a photo and asks the backend what is in it and how far
// away each thing is (YOLO object detection plus depth estimation), nearest
// first, with which side each is on. A Brief Detail setting names only the nearest few.
// The user's vision (data/VisionPlan) decides which part of the picture comes
// first, and how many a Brief answer names.
class HazardDetector(
    private val photoSource: PhotoSource,
    private val client: HazardClient,
    // Main-thread scope (MainActivity's lifecycleScope): answers are spoken from it.
    private val scope: CoroutineScope,
    // Read at each capture, so a change in Settings applies to the very next one.
    private val verbosity: () -> Verbosity = { Verbosity.STANDARD },
    private val plan: () -> VisionPlan? = { null },
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                scope.launch {
                    val hazards = client.detect(bitmap)
                    val vision = plan()
                    val limit = when {
                        verbosity() != Verbosity.BRIEF -> Int.MAX_VALUE
                        vision != null -> vision.briefHazardLimit
                        else -> SpokenPhrases.BRIEF_HAZARD_LIMIT
                    }
                    onText(
                        if (hazards == null) SpokenPhrases.HAZARDS_NOT_CHECKED
                        // The boxes are in the pixels of the photo sent, which is this bitmap.
                        else SpokenPhrases.hazardsAhead(
                            hazards,
                            imageWidth = bitmap.width,
                            limit = limit,
                            focus = vision?.hazardFocus ?: VisionPlan.HazardFocus.NEAREST,
                        )
                    )
                }
            },
            onError = { message -> onText(message) },
        )
    }
}
