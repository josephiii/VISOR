package ucf.visor.recognition

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import ucf.visor.tts.SpokenPhrases

/**
 * [ObjectDetector] with VISOR's own labels instead of ML Kit's defaults.
 *
 * Kept for custom object recognition but not wired up yet: it needs a trained
 * `assets/model.tflite` (see backend/other/CustomObjectDetectorTrainer.py),
 * which is not in the repo yet.
 */
class CustomObjectDetector(context: Context) {

    private val localModel = LocalModel.Builder()
        .setAssetFilePath("model.tflite")
        .build()

    private val labeler = ImageLabeling.getClient(
        CustomImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.6f)
            .setMaxResultCount(3)
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

    fun close() = labeler.close()
}