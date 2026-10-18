package com.example.smartblindstick

import android.util.Log
import com.example.smartblindstick.api.VaachakApi
import kotlinx.coroutines.CancellationException

class MetaApiClient {
    private val api = VaachakApi()
    suspend fun generateResponse(prompt: String): String? = try {
        api.generate(prompt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Do not log prompts, response bodies, or upstream error text.
        Log.w("MetaApiClient", "Vaachak text request failed (${e.javaClass.simpleName}); see the API journey for status.")
        null
    }
}
