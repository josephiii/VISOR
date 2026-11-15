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
     * [watch] asks for the hazard watch's filtered answer: only confident,
     * nearby hazards, one box each (backend/services/HazardWatchFilter.py).
     */
    suspend fun detect(bitmap: Bitmap, watch: Boolean = false): List<Hazard>? {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // The watch sends about two a second: quality 70 is a third the data
            // of 85, and YOLO loses nothing at the watch's 640 px frames.
            .addFormDataPart("image", "photo.jpg", ApiClient.jpeg(bitmap, quality = if (watch) 70 else 85))
            .build()

        return when (val result = ApiClient.send(
            path = if (watch) "$PATH?watch=1" else PATH,
            accessToken = accessToken(),
            // The watch checks again in a moment: an answer this late is no use to it.
            timeoutMs = if (watch) WATCH_TIMEOUT_MS else null,
        ) { post(body) }) {
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
        const val PATH = "/yolo/runHazardDetection"
        const val WATCH_TIMEOUT_MS = 2_500L
    }
}
