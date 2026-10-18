package com.example.smartblindstick.api

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class VaachakApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: VaachakApi
    @Before fun setup() { server = MockWebServer(); server.start(); api = VaachakApi(server.url("/").toString()); ApiJourney.clear() }
    @After fun close() { server.shutdown() }

    @Test fun generateUsesBackendContract() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","data":{"generated_text":"Hello"}}"""))
        assertEquals("Hello", api.generate("test prompt"))
        val request = server.takeRequest()
        assertEquals("/api/gemini/generateContent", request.path)
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("test prompt"))
        assertEquals(1, ApiJourney.history.value.size)
        assertFalse(ApiJourney.history.value.toString().contains("test prompt"))
    }
    @Test fun handlesChatAndPartialVoiceResponses() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","data":{"response":"Chat answer"}}"""))
        assertEquals("Chat answer", api.execute(api.jsonRequest("/api/gemini/chat", "{}" )).text())
        server.enqueue(MockResponse().setBody("""{"status":"partial","data":{"transcript":"Speech only"}}"""))
        val response = api.execute(api.request("/api/health"))
        assertEquals("partial", response.status)
        assertEquals("Speech only", response.text())
    }
    @Test fun failuresIncludeHttpAndSemanticErrors() = runBlocking {
        listOf(MockResponse().setResponseCode(503).setBody("""{"detail":"Unavailable"}"""),
            MockResponse().setBody("""{"status":"FAILED","content":"No credits"}"""),
            MockResponse().setBody("not json")).forEach {
            server.enqueue(it)
            try { api.execute(api.request("/api/health")); fail("Expected API failure") }
            catch (e: ApiFailure) { assertTrue(e.message!!.isNotBlank()) }
        }
    }
    @Test fun multipartUsesCorrectFieldNames() {
        val voice = api.uploadRequest("/api/voice-first", byteArrayOf(1,2), "audio/wav")
        val image = api.uploadRequest("/api/gemini/imageToText", byteArrayOf(1,2), "image/png", mapOf("prompt" to "Read"))
        val buffer = okio.Buffer(); voice.body!!.writeTo(buffer)
        assertTrue(buffer.readUtf8().contains("name=\"audio_file\""))
        image.body!!.writeTo(buffer)
        val raw = buffer.readUtf8()
        assertTrue(raw.contains("name=\"file\"")); assertTrue(raw.contains("name=\"prompt\""))
    }
    @Test fun validatesPathsAndBodies() {
        for (path in listOf("https://evil.test/api/health", "//evil.test/api/health", "/api/../secret")) {
            assertThrows(IllegalArgumentException::class.java) { api.request(path) }
        }
        assertThrows(IllegalArgumentException::class.java) { api.jsonRequest("/api/health", "[]") }
        assertThrows(IllegalArgumentException::class.java) { api.uploadRequest("/api/voice-first", byteArrayOf(), "audio/wav") }
    }
    @Test fun cancellingCoroutineCancelsHttpCall() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
        val job = launch(Dispatchers.Default) { api.execute(api.request("/api/health")) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
    @Test fun journeyIsBounded() {
        repeat(150) { ApiJourney.record(ApiEvent("GET", "/api/health", "200", 2)) }
        assertEquals(100, ApiJourney.history.value.size)
        ApiJourney.clear(); assertTrue(ApiJourney.history.value.isEmpty())
    }
}
