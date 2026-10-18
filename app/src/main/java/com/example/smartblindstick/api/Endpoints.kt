package com.example.smartblindstick.api

data class Endpoint(val title: String, val method: String, val path: String, val template: String, val upload: String = "")

private val cloudEndpoints = listOf(
    Endpoint("Memory status", "GET", "/api/backboard/status", """{}""", ""),
    Endpoint("Memory message / store context", "POST", "/api/backboard/message", """{
  "content": "I prefer short answers in Hindi.",
  "memory": "Auto",
  "send_to_llm": "false"
}""", ""),
    Endpoint("List memories", "POST", "/api/backboard/memories/list", """{
  "assistant_id": "",
  "page": 1,
  "page_size": 25
}""", ""),
    Endpoint("Add memory", "POST", "/api/backboard/memories/add", """{
  "assistant_id": "",
  "content": "Hello from Vaachak Android",
  "metadata": {}
}""", ""),
    Endpoint("Search memories", "POST", "/api/backboard/memories/search", """{
  "assistant_id": "",
  "query": "Hello from Vaachak Android",
  "limit": 5
}""", ""),
    Endpoint("Memory operation", "POST", "/api/backboard/operation", """{
  "operation_id": ""
}""", ""),
    Endpoint("Database status", "GET", "/api/db/status", """{}""", ""),
    Endpoint("List users", "GET", "/api/db/users", """{}""", ""),
    Endpoint("Create user", "POST", "/api/db/users", """{
  "display_name": "Android test user"
}""", ""),
    Endpoint("Load preferences", "GET", "/api/db/users/{user_id}/preferences", """{}""", ""),
    Endpoint("Save preferences", "PUT", "/api/db/users/{user_id}/preferences", """{
  "preferred_language": "en",
  "speech_rate": 1,
  "voice": "",
  "theme": "system",
  "font_size": 18,
  "auto_read": false,
  "extra": {}
}""", ""),
    Endpoint("List conversations", "GET", "/api/db/users/{user_id}/conversations", """{}""", ""),
    Endpoint("Create conversation", "POST", "/api/db/users/{user_id}/conversations", """{
  "title": "Android conversation",
  "provider": "gemma"
}""", ""),
    Endpoint("Read messages", "GET", "/api/db/users/{user_id}/conversations/{conversation_id}/messages", """{}""", ""),
    Endpoint("Append message", "POST", "/api/db/users/{user_id}/conversations/{conversation_id}/messages", """{
  "role": "user",
  "content": "Hello from Vaachak Android"
}""", ""),
    Endpoint("Generate text", "POST", "/api/gemini/generateContent", """{
  "prompt": "Hello from Vaachak Android",
  "model": "gemma-4-26b-a4b-it"
}""", ""),
    Endpoint("Read / describe image", "POST", "/api/gemini/imageToText", """{
  "prompt": "Extract all visible text. Preserve the original language and line breaks."
}""", "image"),
    Endpoint("Chat", "POST", "/api/gemini/chat", """{
  "messages": [
    {
      "role": "user",
      "content": "Hello from Vaachak Android"
    }
  ]
}""", ""),
    Endpoint("Transcribe audio", "POST", "/api/sarvam/transcribe?model=saaras:v3&source_language=unknown", """{}""", "audio"),
    Endpoint("Speak text", "POST", "/api/sarvam/tts", """{
  "text": "Hello from Vaachak",
  "language": "en-IN",
  "model": "bulbul:v3"
}""", ""),
    Endpoint("Voice \u2192 text \u2192 AI", "POST", "/api/voice-first", """{}""", "audio"),
    Endpoint("Health", "GET", "/api/health", """{}""", "")
)

val endpoints = cloudEndpoints + if (com.example.smartblindstick.BuildConfig.USE_FRONTEND_PROXY) listOf(
    Endpoint("Ollama models", "GET", "/api/ollama/tags", "{}"),
    Endpoint("Ollama generate", "POST", "/api/ollama/generate", """{"model":"","prompt":"Hello from Android","stream":false}"""),
    Endpoint("Ollama chat", "POST", "/api/ollama/chat", """{"model":"","messages":[{"role":"user","content":"Hello from Android"}],"stream":false}"""),
    Endpoint("Ollama embeddings", "POST", "/api/ollama/embed", """{"model":"","input":"Vaachak Android"}""")
) else emptyList()
