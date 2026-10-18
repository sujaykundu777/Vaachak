package com.example.smartblindstick.api

import com.example.smartblindstick.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiEvent(val method: String, val path: String, val status: String, val durationMs: Long)
object ApiJourney {
    private val events = MutableStateFlow<List<ApiEvent>>(emptyList())
    val history = events.asStateFlow()
    @Synchronized fun record(event: ApiEvent) { events.value = (events.value + event).takeLast(100) }
    @Synchronized fun clear() { events.value = emptyList() }
}

class ApiFailure(val statusCode: Int, message: String) : IOException(message)
data class ApiResult(val code: Int, val json: JsonObject) {
    val status: String get() = json.get("status")?.takeIf { it.isJsonPrimitive }?.asString ?: "ok"
    fun text(): String {
        val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        return listOf("generated_text", "response", "gemini_response", "transcript")
            .firstNotNullOfOrNull { data?.get(it)?.takeIf { v -> v.isJsonPrimitive }?.asString }
            ?: json.get("response")?.takeIf { it.isJsonPrimitive }?.asString
            ?: json.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("content")?.takeIf { it.isJsonPrimitive }?.asString
            ?: json.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
    }
}

/** One backend client; provider credentials never belong in the APK. */
class VaachakApi(
    baseUrl: String = BuildConfig.VAACHAK_API_URL,
    private val client: OkHttpClient = sharedClient,
    private val useFrontend: Boolean = BuildConfig.USE_FRONTEND_PROXY
) {
    private val base = baseUrl.toHttpUrl()
    private val readClient = client.newBuilder().retryOnConnectionFailure(true).build()
    init { require(base.isHttps || base.host in listOf("localhost", "127.0.0.1", "10.0.2.2")) }

    fun request(path: String, method: String = "GET", body: RequestBody? = null): Request {
        require(path.startsWith("/api/") && !path.contains("..") && !path.contains("#"))
        val routedPath = if (!useFrontend) {
            require(!path.startsWith("/api/ollama/")) { "Ollama requires the frontend build variant" }
            path
        } else when {
            path.startsWith("/api/db/") -> path.replaceFirst("/api/db/", "/api/database/")
            path.startsWith("/api/ollama/") -> path
            else -> path.replaceFirst("/api/", "/api/backend/")
        }
        val url = requireNotNull(base.resolve(routedPath))
        require(url.host == base.host)
        return Request.Builder().url(url).header("Accept", "application/json")
            .method(method, body).build()
    }

    fun jsonRequest(path: String, body: String, method: String = "POST"): Request {
        require(JsonParser.parseString(body).isJsonObject) { "Request must be a JSON object" }
        return request(path, method, body.toRequestBody("application/json; charset=utf-8".toMediaType()))
    }

    fun uploadRequest(path: String, bytes: ByteArray, mime: String, fields: Map<String, String> = emptyMap()): Request {
        require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) { "Choose a file smaller than 10 MB" }
        val partName = if (path == "/api/voice-first") "audio_file" else "file"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(partName, "upload", bytes.toRequestBody(mime.toMediaType()))
        fields.forEach { (key, value) -> body.addFormDataPart(key, value) }
        return request(path, "POST", body.build())
    }

    fun enqueue(request: Request, callback: (Result<ApiResult>) -> Unit): Call {
        val start = System.nanoTime()
        val call = (if (request.method == "GET") readClient else client).newCall(request)
        fun finish(result: Result<ApiResult>) {
            val status = result.fold({ "${it.code} ${it.status}" }, {
                if (call.isCanceled()) "cancelled" else if (it is ApiFailure) "${it.statusCode} error" else "network error"
            })
            // No request/response bodies, query values, or credentials in the journey log.
            ApiJourney.record(ApiEvent(request.method, request.url.encodedPath, status, (System.nanoTime() - start) / 1_000_000))
            callback(result)
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = finish(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val raw = it.body?.string().orEmpty()
                        val json = try { JsonParser.parseString(raw).asJsonObject } catch (_: Exception) {
                            throw ApiFailure(it.code, "Server returned an invalid response (HTTP ${it.code})")
                        }
                        if (!it.isSuccessful) throw ApiFailure(it.code, "HTTP ${it.code}: " + (json.get("detail")?.toString()?.take(500) ?: it.message))
                        val status = json.get("status")?.takeIf { v -> v.isJsonPrimitive }?.asString.orEmpty()
                        if (status.lowercase() in listOf("error", "failed")) {
                            throw ApiFailure(it.code, json.get("error")?.toString()?.take(500)
                                ?: json.get("content")?.toString()?.take(500) ?: "Service reported $status")
                        }
                        ApiResult(it.code, json)
                    }
                }
                finish(result)
            }
        })
        return call
    }

    suspend fun execute(request: Request): ApiResult = suspendCancellableCoroutine { continuation ->
        val call = enqueue(request) { result ->
            if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
        }
        continuation.invokeOnCancellation { call.cancel() }
    }

    suspend fun generate(prompt: String): String = execute(jsonRequest("/api/gemini/generateContent",
        JsonObject().apply { addProperty("prompt", prompt) }.toString())).text()

    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build()
    }
}
