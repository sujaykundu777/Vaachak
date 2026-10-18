package com.example.smartblindstick

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartblindstick.api.VaachakApi
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Explicitly opt in: -Pandroid.testInstrumentationRunnerArguments.liveRender=true.
 * Uses small provider requests and creates labeled synthetic database/memory records.
 */
@RunWith(AndroidJUnit4::class)
class RenderIntegrationTest {
    private val api = VaachakApi()
    @Before fun liveOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveRender") == "true")
        assertFalse(BuildConfig.USE_FRONTEND_PROXY)
        assertEquals("https://vaachakfastapibackend.onrender.com/", BuildConfig.VAACHAK_API_URL)
    }
    private suspend fun post(path: String, json: String) = api.execute(api.jsonRequest(path, json))

    @Test fun gemmaTextChatAndImage() = runBlocking {
        assertEquals("healthy", api.execute(api.request("/api/health")).status)
        assertTrue(api.generate("Reply with a brief greeting for an Android integration test.").isNotBlank())
        val chat = post("/api/gemini/chat", """{"messages":[{"role":"user","content":"Say hello briefly."}]}""")
        assertTrue(chat.text().isNotBlank())
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("VAACHAK TEST 123", 20f, 110f, Paint().apply { color = Color.BLACK; textSize = 46f })
        }
        val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        val image = api.execute(api.uploadRequest("/api/gemini/imageToText", bytes, "image/png",
            mapOf("prompt" to "Return only the visible text.")))
        assertTrue("Expected visible test text", image.text().contains("123"))
    }

    @Test fun sarvamSpeechRoundTripAndVoiceFirst() = runBlocking {
        val tts = post("/api/sarvam/tts", """{"text":"Hello from Vaachak. This is an Android test.","language":"en-IN"}""")
        val audio = Base64.decode(tts.json.get("audio_base64").asString, Base64.DEFAULT)
        assertTrue(audio.size > 100)
        val transcript = api.execute(api.uploadRequest("/api/sarvam/transcribe?model=saaras:v3&source_language=unknown", audio, "audio/wav"))
        assertTrue(transcript.text().isNotBlank())
        val voice = api.execute(api.uploadRequest("/api/voice-first", audio, "audio/wav"))
        assertEquals("ok", voice.status)
        assertTrue(voice.json.getAsJsonObject("data").get("gemini_response").asString.isNotBlank())
    }

    @Test fun preferencesConversationAndMemoryPersistence() = runBlocking {
        assertTrue(api.execute(api.request("/api/db/status")).json.get("ready").asBoolean)
        val label = "Android instrumentation " + UUID.randomUUID().toString().take(8)
        val user = post("/api/db/users", JsonObject().apply { addProperty("display_name", label) }.toString())
        val userId = user.json.getAsJsonObject("data").get("id").asString
        val base = "/api/db/users/$userId"
        api.execute(api.jsonRequest("$base/preferences", """{"preferred_language":"hi","font_size":22}""", "PUT"))
        assertEquals("hi", api.execute(api.request("$base/preferences")).json.getAsJsonObject("data").get("preferred_language").asString)
        val conversation = post("$base/conversations", """{"title":"Android instrumentation test","provider":"gemma"}""")
        val conversationId = conversation.json.getAsJsonObject("data").get("id").asString
        val path = "$base/conversations/$conversationId/messages"
        post(path, """{"role":"user","content":"Synthetic Android persistence test."}""")
        post(path, """{"role":"assistant","content":"Synthetic test reply."}""")
        assertEquals(2, api.execute(api.request(path)).json.getAsJsonArray("data").size())
        val memory = post("/api/backboard/message", """{"content":"Android instrumentation test: prefer brief replies.","memory":"Auto","send_to_llm":"false"}""")
        val assistant = memory.json.get("assistant_id").asString
        assertTrue(assistant.isNotBlank())
        post("/api/backboard/memories/add", """{"assistant_id":"$assistant","content":"Android test preference: brief replies."}""")
        assertEquals(200, post("/api/backboard/memories/list", """{"assistant_id":"$assistant"}""").code)
        assertEquals(200, post("/api/backboard/memories/search", """{"assistant_id":"$assistant","query":"reply preference"}""").code)
    }
}
