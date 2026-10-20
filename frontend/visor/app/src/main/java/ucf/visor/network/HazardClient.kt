package ucf.visor.network

import android.graphics.Bitmap
import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody

/**
 * The backend's hazard detection endpoint:
 *   POST /yolo/runHazardDetection  multipart { image (JPEG) } -> [{ object, classID, coords, conf, depth }]
 * YOLO finds the objects in the photo, and a depth model measures how far away
 * each one is.
 *
 * [accessToken] is sent with every request, so the server can start requiring
 * a login without an app change.
 */
class HazardClient(
    private val accessToken: () -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The objects in [bitmap], each with its distance. Null when the server
     * can't be reached or doesn't answer with a list of them (logged).
     */
    suspend fun detect(bitmap: Bitmap): List<Hazard>? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "photo.jpg", ApiClient.jpeg(bitmap))
            .build()

        return when (val result = ApiClient.send("/yolo/runHazardDetection", accessToken()) { post(body) }) {
            is ApiResult.Success -> try {
                json.decodeFromString<List<Hazard>>(result.body)
            } catch (e: IllegalArgumentException) { // includes SerializationException
                Log.e(TAG, "Unexpected hazard detection answer: ${result.body.take(200)}", e)
                null
            }

            is ApiResult.Error -> {
                Log.e(TAG, "Hazard detection failed (${result.code}): ${result.message}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "VisorHazards"
    }
}
