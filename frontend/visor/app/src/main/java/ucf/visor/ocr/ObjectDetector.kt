package ucf.visor.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

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
                val names = labels.take(3).map { it.text.lowercase() }
                val sentence =
                    if (names.isEmpty()) "I'm not sure what I'm looking at."
                    else "I see " + names.joinToString(", ") + "."
                onResult(sentence)
            }
            .addOnFailureListener { onError(it) }
    }

    fun close() {
        labeler.close()
    }
}