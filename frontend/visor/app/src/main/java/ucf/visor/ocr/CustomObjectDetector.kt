package ucf.visor.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions

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
                val names = labels.take(3).map { it.text.lowercase() }
                val sentence =
                    if (names.isEmpty()) "I'm not sure what I'm looking at."
                    else "I see " + names.joinToString(", ") + "."
                onResult(sentence)
            }
            .addOnFailureListener { onError(it) }
    }

    fun close() = labeler.close()
}