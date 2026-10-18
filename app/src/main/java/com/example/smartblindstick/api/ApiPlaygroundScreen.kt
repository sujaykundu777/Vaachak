package com.example.smartblindstick.api

import android.media.MediaPlayer
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartblindstick.BuildConfig
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun ApiPlaygroundScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val api = remember { VaachakApi() }
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("vaachak_api_ids", 0) }
    var selected by rememberSaveable { mutableIntStateOf(endpoints.indexOfFirst { it.path == "/api/health" }) }
    val endpoint = endpoints[selected]
    var menu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var body by rememberSaveable { mutableStateOf(endpoint.template) }
    var userId by rememberSaveable { mutableStateOf(preferences.getString("user", "").orEmpty()) }
    var conversationId by rememberSaveable { mutableStateOf(preferences.getString("conversation", "").orEmpty()) }
    var assistantId by rememberSaveable { mutableStateOf(preferences.getString("assistant", "").orEmpty()) }
    var threadId by rememberSaveable { mutableStateOf(preferences.getString("thread", "").orEmpty()) }
    var operationId by rememberSaveable { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileMime by remember { mutableStateOf("application/octet-stream") }
    var output by remember { mutableStateOf("Choose an API to begin.") }
    var busy by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    val logs by ApiJourney.history.collectAsState()
    DisposableEffect(Unit) { onDispose { player?.release(); audioFile?.delete() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val loaded = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            require(buffer.size() + count <= 10 * 1024 * 1024) { "File exceeds 10 MB" }
                            buffer.write(chunk, 0, count)
                        }
                        buffer.toByteArray()
                    } ?: error("Cannot read this file")
                }
                require(loaded.isNotEmpty()) { "File is empty" }
                fileBytes = loaded
                fileMime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                output = "File ready (${loaded.size} bytes)."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fileBytes = null; output = e.message ?: "Cannot open file" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back to Vaachak") }
        Text("Vaachak API Playground", style = MaterialTheme.typography.headlineSmall)
        Text(BuildConfig.VAACHAK_API_URL, style = MaterialTheme.typography.bodySmall)
        Text(if (BuildConfig.USE_FRONTEND_PROXY)
            "Connected through the Next.js frontend. Cloud APIs use its configured backend; Ollama runs on your Mac."
            else "Test cloud AI, speech, memory and database calls. Ollama is available in the frontend build variant.")
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = !busy) { Text(endpoint.title) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 400.dp)) {
                endpoints.forEachIndexed { index, item ->
                    DropdownMenuItem(text = { Text(item.title) }, onClick = {
                        selected = index; body = item.template; fileBytes = null; audio = null; menu = false
                        output = "Ready: ${item.title}"
                        player?.release(); player = null; audioFile?.delete(); audioFile = null
                    })
                }
            }
        }
        Text("${endpoint.method} ${endpoint.path}", style = MaterialTheme.typography.bodySmall)
        if (endpoint.path.startsWith("/api/db")) {
            OutlinedTextField(userId, { userId = it.trim(); conversationId = "" }, label = { Text("User ID") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (endpoint.path.contains("conversations")) OutlinedTextField(conversationId, { conversationId = it.trim() }, label = { Text("Conversation ID") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Create a user, then save preferences or create a conversation. New IDs are filled automatically. These are test records; an ID is not authentication.")
        }
        if (endpoint.path.startsWith("/api/backboard")) {
            OutlinedTextField(assistantId, { assistantId = it.trim(); threadId = "" }, label = { Text("Assistant ID") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(threadId, { threadId = it.trim() }, label = { Text("Thread ID") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(operationId, { operationId = it.trim() }, label = { Text("Operation ID") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Memory message creates an assistant and thread when IDs are empty. Set send_to_llm to \"true\" for an AI reply, or \"false\" to store context only.")
            TextButton(onClick = { threadId = "" }, enabled = !busy) { Text("New thread (keep assistant memory)") }
        }
        if (endpoint.path.startsWith("/api/ollama/") && endpoint.method == "POST") {
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val response = api.execute(api.request("/api/ollama/tags"))
                        models = response.json.getAsJsonArray("models")?.mapNotNull {
                            it.asJsonObject.get("name")?.asString
                        }.orEmpty()
                        modelMenu = models.isNotEmpty()
                        if (models.isEmpty()) output = "No local models installed. Pull a model in Ollama on your Mac."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { output = e.message ?: "Cannot load models" }
                    finally { busy = false }
                }
            }) { Text("Load and choose local model") }
            Box {
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    models.forEach { model ->
                        DropdownMenuItem(text = { Text(model) }, onClick = {
                            try {
                                val json = JsonParser.parseString(body).asJsonObject
                                json.addProperty("model", model)
                                body = GsonBuilder().setPrettyPrinting().create().toJson(json)
                            } catch (_: Exception) { output = "Fix the request JSON before choosing a model." }
                            modelMenu = false
                        })
                    }
                }
            }
        }
        if (endpoint.method != "GET" && endpoint.path != "/api/voice-first" && !endpoint.path.startsWith("/api/sarvam/transcribe")) {
            OutlinedTextField(body, { body = it }, label = { Text(if (endpoint.upload == "image") "Image options (JSON)" else "Request JSON") },
                minLines = 4, maxLines = 12, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        if (endpoint.upload.isNotEmpty()) {
            OutlinedButton(onClick = { picker.launch(if (endpoint.upload == "image") "image/*" else "audio/*") }, enabled = !busy) {
                Text("Choose ${endpoint.upload} file (up to 10 MB)")
            }
            Text(if (fileBytes == null) "No file selected" else "${fileBytes!!.size} bytes · $fileMime")
        }
        Button(enabled = !busy, onClick = {
            busy = true; audio = null; output = "Calling ${endpoint.title}…"
            scope.launch {
                try {
                    fun id(value: String, name: String): String {
                        require(runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)) { "$name must be a UUID" }
                        return value
                    }
                    var path = endpoint.path
                    if (path.contains("{user_id}")) path = path.replace("{user_id}", id(userId, "User ID"))
                    if (path.contains("{conversation_id}")) path = path.replace("{conversation_id}", id(conversationId, "Conversation ID"))
                    val json = if (endpoint.method == "GET" || endpoint.path == "/api/voice-first" || endpoint.path.startsWith("/api/sarvam/transcribe")) JsonObject() else JsonParser.parseString(body).asJsonObject
                    if (path.startsWith("/api/backboard") && endpoint.method == "POST") {
                        if (path.endsWith("/operation")) json.addProperty("operation_id", id(operationId, "Operation ID"))
                        else {
                            if (assistantId.isNotBlank()) json.addProperty("assistant_id", id(assistantId, "Assistant ID"))
                            if (path.endsWith("/message") && threadId.isNotBlank()) json.addProperty("thread_id", id(threadId, "Thread ID"))
                        }
                    }
                    val request = when {
                        endpoint.upload.isNotEmpty() -> api.uploadRequest(path, requireNotNull(fileBytes) { "Choose a file first" }, fileMime,
                            json.entrySet().associate { it.key to it.value.asString })
                        endpoint.method == "GET" -> api.request(path)
                        else -> api.jsonRequest(path, json.toString(), endpoint.method)
                    }
                    val response = api.execute(request)
                    val root = response.json
                    fun string(key: String): String? = root.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                    string("assistant_id")?.let { assistantId = it }
                    string("thread_id")?.let { threadId = it }
                    string("operation_id")?.let { operationId = it }
                    val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    if (endpoint.method == "POST") data?.get("id")?.asString?.let { created ->
                        if (path == "/api/db/users") { userId = created; conversationId = "" }
                        else if (path.endsWith("/conversations")) conversationId = created
                    }
                    preferences.edit().putString("user", userId).putString("conversation", conversationId)
                        .putString("assistant", assistantId).putString("thread", threadId).apply()
                    audio = string("audio_base64")
                    val display = root.deepCopy()
                    if (audio != null) display.addProperty("audio_base64", "[audio available below]")
                    else display.remove("audio_base64")
                    output = "HTTP ${response.code} · ${response.status}\n" + GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(display).take(20000)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { output = e.message ?: "Request failed. Check connectivity and try again." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Working…" else "Send request") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        audio?.let { encoded ->
            Button(onClick = {
                try {
                    player?.release(); audioFile?.delete()
                    val file = File.createTempFile("vaachak-speech-", ".wav", context.cacheDir)
                    audioFile = file
                    file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnPreparedListener { it.start() }
                        setOnCompletionListener { it.release(); player = null; file.delete() }
                        setOnErrorListener { _, _, _ -> output = "Audio playback failed"; true }
                        prepareAsync()
                    }
                } catch (e: Exception) { output = "Audio playback failed: ${e.message}" }
            }) { Text("Play generated speech") }
        }
        SelectionContainer { Text(output) }
        HorizontalDivider()
        Text("API journey (${logs.size})", style = MaterialTheme.typography.titleLarge)
        Text("Last 100 calls in this app session. Payloads and credentials are not logged.")
        TextButton(onClick = { ApiJourney.clear() }) { Text("Clear journey") }
        logs.asReversed().forEach { Text("${it.method} ${it.path}\n${it.status} · ${it.durationMs} ms", style = MaterialTheme.typography.bodySmall) }
    }
}
