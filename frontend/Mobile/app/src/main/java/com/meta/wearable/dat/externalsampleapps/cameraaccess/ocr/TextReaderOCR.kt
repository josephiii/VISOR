package com.meta.wearable.dat.externalsampleapps.cameraaccess.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextReaderOCR {
    private val reader = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    fun readText(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (Exception) -> Unit){
        val image = InputImage.fromBitmap(bitmap, 0)

        reader.process(image)
            .addOnSuccessListener { result -> onSuccess(result.text) }
            .addOnFailureListener { error -> onError(error) }
    }

    fun close(){
        reader.close()
    }
}