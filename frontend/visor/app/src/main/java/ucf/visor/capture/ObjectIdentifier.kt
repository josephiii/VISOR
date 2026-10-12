package ucf.visor.capture

import ucf.visor.recognition.ObjectDetector
import ucf.visor.tts.SpokenPhrases

// "VISOR, what am I holding?": takes a photo and names what's in it with on-device labeling
class ObjectIdentifier(
    private val photoSource: PhotoSource,
    private val detector: ObjectDetector,
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                detector.describe(
                    bitmap = bitmap,
                    onResult = { sentence -> onText(sentence) },
                    onError = { onText(SpokenPhrases.RECOGNITION_FAILED) },
                )
            },
            onError = { message -> onText(message) },
        )
    }
}
