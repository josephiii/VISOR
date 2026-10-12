package ucf.visor.recognition

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import ucf.visor.tts.SpokenPhrases

class ObjectDetector {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()
    )

    fun describe(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        labeler.process(image)
            .addOnSuccessListener { labels ->
                onResult(SpokenPhrases.objectsSeen(labels.take(3).map { it.text.lowercase() }))
            }
            .addOnFailureListener { onError(it) }
    }

    fun close() {
        labeler.close()
    }
}