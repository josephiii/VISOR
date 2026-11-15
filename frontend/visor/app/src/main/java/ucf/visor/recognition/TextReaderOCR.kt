package ucf.visor.recognition

import android.graphics.Bitmap
import android.util.Log
import ucf.visor.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextReaderOCR {
    private val reader = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    fun readText(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (Exception) -> Unit){
        val image = InputImage.fromBitmap(enlarged(bitmap), 0)

        reader.process(image)
            .addOnSuccessListener { result ->
                // What was read, in debug builds only: it may be someone's mail.
                if (BuildConfig.DEBUG) Log.i(TAG, "Read ${bitmap.width} x ${bitmap.height}: ${result.text.take(300)}")
                onSuccess(result.text)
            }
            .addOnFailureListener { error -> onError(error) }
    }

    /**
     * A small picture, doubled. The glasses' pictures are video frames of at
     * most 720 x 1280, in which ordinary print is about 12 pixels tall, and
     * ML Kit asks for characters of at least 16 (it reads nothing reliable
     * below that). The phone's own pictures are large enough as they are.
     */
    private fun enlarged(bitmap: Bitmap): Bitmap =
        if (maxOf(bitmap.width, bitmap.height) > SMALL_PICTURE_PX) bitmap
        else Bitmap.createScaledBitmap(bitmap, bitmap.width * 2, bitmap.height * 2, true)

    fun close(){
        reader.close()
    }

    private companion object {
        const val TAG = "VisorTextReader"

        /** Pictures no longer than this on their long side are doubled before reading. */
        const val SMALL_PICTURE_PX = 1600
    }
}