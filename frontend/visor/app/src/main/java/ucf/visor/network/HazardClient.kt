package ucf.visor.network

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JSON
import kotlinx.serialization.json.Json

class HazardClient {
    //val url = "http://10.0.2.2:8000/yolo/runHazardDetection"
    //Use above for local below for server
    private val json = Json{ ignoreUnknownKeys=true}
    private val url = "http://54.173.104.183:8000/yolo/runHazardDetection"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(
            300,
            TimeUnit.SECONDS
        )   // 5 minutes for CPU inference BECAUSE IT KEEPS TIMING OUT AAAAAAAAA
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun runHazardDetection(
        frame: Bitmap
    ): List<Hazard>?= withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        frame.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageBytes = outputStream.toByteArray()

        val imageBody = imageBytes.toRequestBody("image/jpeg".toMediaType())

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "photo.jpg",
                imageBody
            )
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()

        try {
            client.newCall(request).execute().use{ response ->
                if(response.isSuccessful){
                    val string = response.body?.string()
                    Log.d("Hazard Detection", "Success: $string" )
                    //Deserialize object
                    json.decodeFromString<List<Hazard>>(string)
                    string //deserialized object
                }
                else{
                    val e = response.body?.string()
                    Log.d("Hazard Detection", "Error: $e")
                    null
                }
            }

        } catch (e: Exception){
            Log.d("Hazard Detection", "Error running hazard detection: $e")
            null
        }

    }

}