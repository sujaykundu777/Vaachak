package com.example.smartblindstick

import android.graphics.Bitmap
import com.example.smartblindstick.api.VaachakApi
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Compatibility adapter for existing camera flows, now served by Vaachak on Render. */
class NvidiaApiClient {
    private val api = VaachakApi()
    fun generateResponse(image: Bitmap, prompt: String, onResponse: (String?) -> Unit) {
        val scale = minOf(1f, 1200f / maxOf(image.width, image.height))
        val resized = Bitmap.createScaledBitmap(image, (image.width * scale).roundToInt().coerceAtLeast(1),
            (image.height * scale).roundToInt().coerceAtLeast(1), true)
        val bytes = ByteArrayOutputStream().use { output ->
            resized.compress(Bitmap.CompressFormat.JPEG, 80, output)
            output.toByteArray()
        }
        if (resized !== image) resized.recycle()
        api.enqueue(api.uploadRequest("/api/gemini/imageToText", bytes, "image/jpeg", mapOf(
            "prompt" to "You assist a blind person. Give a concise helpful answer. $prompt"
        ))) { result -> onResponse(result.getOrNull()?.text()?.takeIf { it.isNotBlank() }) }
    }
}
