/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package ucf.visor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ucf.visor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64

import ucf.visor.BuildConfig

@Composable
fun SharePhotoDialog(photo: Bitmap, onDismiss: () -> Unit, onShare: (Bitmap) -> Unit) {

    // VARS FOR DESCRIPTION
    var description by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // ON START
    LaunchedEffect(photo) {
        isLoading = true
        description = describeImageWithGemini(photo)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = stringResource(R.string.photo_captured))

                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = stringResource(R.string.captured_photo),
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )


                // THIS IS WHERE THE GENERATED TEXT IS PLACED
                if (isLoading) { // LOADING BTHING
                    CircularProgressIndicator()
                } else {
                    Text(text = description ?: "No description available")
                }

                Button(onClick = { onShare(photo) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share))
                }
            }
        }
    }
}

private suspend fun describeImageWithGemini(
    bitmap: Bitmap,
    prompt: String = "Describe this image to a blind person in two sentences",
): String =
    withContext(Dispatchers.IO) {

        // PREP IMAGE
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray())

        // PREP JSON
        val requestJson =
            JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().apply {
                                    put(JSONObject().put("text", prompt))
                                    put(
                                        JSONObject().apply {
                                            put(
                                                "inline_data",
                                                JSONObject().apply {
                                                    put("mime_type", "image/jpeg")
                                                    put("data", base64Image)
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
            }

        //
        val body =
            requestJson
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

        // REQUEST
        val request =
            Request.Builder()
                .url(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")
                .addHeader("x-goog-api-key", "") // FIXME: GEMINI API KEY WAS REMOVED
                .post(body)
                .build()

        val client = OkHttpClient() //

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"

            JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }