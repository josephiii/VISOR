
package ucf.visor.capture

import ucf.visor.ocr.TextReaderOCR

// calls readrequester interface and connects to ocr
class RealCapture(
    private val photoSource: PhotoSource,
    private val ocr: TextReaderOCR,
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                ocr.readText(
                    bitmap = bitmap,
                    onSuccess = { text ->
                        onText(
                            if (text.isBlank()) "No text found. Try moving closer."
                            else text
                        )
                    },
                    onError = { onText("Sorry, I couldn't read that.") },
                )
            },
            onError = { message -> onText(message) },
        )
    }
}