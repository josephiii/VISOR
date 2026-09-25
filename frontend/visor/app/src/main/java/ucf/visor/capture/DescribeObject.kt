package ucf.visor.capture
import ucf.visor.ocr.ObjectDetector
import ucf.visor.capture.PhotoSource

class DescribeObject(
    private val photoSource: PhotoSource,
    private val detector: ObjectDetector,
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                detector.describe(
                    bitmap = bitmap,
                    onResult = { sentence -> onText(sentence) },
                    onError = { onText("Sorry, I couldn't tell what's there.") },
                )
            },
            onError = { message -> onText(message) },
        )
    }
}