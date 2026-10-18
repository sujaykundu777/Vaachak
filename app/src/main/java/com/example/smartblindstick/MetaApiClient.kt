package com.example.smartblindstick

import com.example.smartblindstick.api.VaachakApi
import kotlinx.coroutines.CancellationException

class MetaApiClient {
    private val api = VaachakApi()
    suspend fun generateResponse(prompt: String): String? = try {
        api.generate(prompt)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
