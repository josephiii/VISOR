
package ucf.visor.capture

import ucf.visor.recognition.TextReaderOCR
import ucf.visor.tts.SpokenPhrases

// "VISOR, read this": takes a photo and reads its text aloud with on-device OCR
class TextReader(
    private val photoSource: PhotoSource,
    private val ocr: TextReaderOCR,
) : ReadRequester {

    override fun requestRead(onText: (String) -> Unit) {
        photoSource.capturePhoto(
            onPhoto = { bitmap ->
                ocr.readText(
                    bitmap = bitmap,
                    onSuccess = { text ->
                        onText(text.ifBlank { SpokenPhrases.NO_TEXT_FOUND })
                    },
                    onError = { onText(SpokenPhrases.TEXT_UNREADABLE) },
                )
            },
            onError = { message -> onText(message) },
        )
    }
}
