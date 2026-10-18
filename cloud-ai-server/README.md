# Vaachak Cloud AI Server

FastAPI server integrating **Google Gemini** (Gemma models) and **Sarvam Audio APIs** for the [Vaachak](https://github.com/sujaykundu777/Vaachak) offline reading companion.

Provides voice-first interaction: upload audio → transcribe (Sarvam STT) → intelligent response (Gemini) → return everything.

---

## Quick Start

```bash
cd cloud-ai-server

# 1. Install dependencies
pip install -r requirements.txt

# 2. Create .env config file (from template)
cp .env.example .env
# Edit .env and add your GOOGLE_API_KEY and SARVAM_API_KEY

# 3. Run the server (hot-reload enabled)
uvicorn main:app --reload --port 8000
```

Server will be available at **`http://localhost:8000`**

API docs auto-generated at:
- Swagger UI → `http://localhost:8000/docs`
- ReDoc    → `http://localhost:8000/redoc`

---

## API Endpoints

### 1. Gemini — Text Generation (Gemini / Gemma)

**`POST /api/gemini/generateContent`**

Generate text from a prompt using any Gemini model.

| Field     | Type   | Required | Description                    |
|-----------|--------|----------|--------------------------------|
| `prompt`  | string | yes      | Your text input                |
| `model`   | string | no       | Model name (default: `gemini-2.0-flash`) |

**Request:**
```bash
curl -X POST http://localhost:8000/api/gemini/generateContent \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Explain what Vaachak does in simple language."}'
```

---

### 2. Gemini — Chat with History

**`POST /api/gemini/chat`**

Conversational endpoint that maintains message history.

| Field       | Type   | Required | Description              |
|-------------|--------|----------|--------------------------|
| `messages[]`| object | yes      | List `{role, content}`   |
| `model`     | string | no       | Model name               |

**Request:**
```bash
curl -X POST http://localhost:8000/api/gemini/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "Who is Vaachak?"},
      {"role": "model", "content": "Vaachak is an AI reading companion for elders."},
      {"role": "user", "content": "What services does it offer?"}
    ]
  }'
```

---

### 3. Sarvam — Speech-to-Text (STT)

**`POST /api/sarvam/transcribe`**

Upload audio → get transcribed text back. Works with MP3 and WAV files.

| Field              | Type   | Required | Default     | Description           |
|--------------------|--------|----------|-------------|-----------------------|
| `file`             | file   | yes      | —          | Audio file (MP3 / WAV) |
| `model`            | string | no       | `saaras:v3` | Sarvam model to use     |
| `source_language`  | string | no       | `auto`     | Language code (`hi-IN`, `en-IN`); short codes also accepted |

**Request:**
```bash
curl -X POST "http://localhost:8000/api/sarvam/transcribe?model=saaras:v3&source_language=en" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@./my_audio.mp3;type=audio/mpeg"
```

STT uses Sarvam’s `/speech-to-text` endpoint with `api-subscription-key` authentication. `source_language=auto` maps to Sarvam’s `language_code=unknown`; short codes such as `en` map to `en-IN`. The provider’s `transcript` is returned as `text` by this server.

Sarvam supports Indian languages: Hindi, Bengali, Tamil, Telugu, Marathi, Urdu, Gujarati, Malayalam, Kannada, Odia, Punjabi, Assamese, Sanskrit, Maithili, Nepali, and English.

---

### 4. Sarvam — Text-to-Speech (TTS)

**`POST /api/sarvam/tts`**

Convert text → audio file (returned as **Base64-encoded MP3**). Uses Sarvam’s `/text-to-speech` endpoint with `api-subscription-key` authentication. Short language codes such as `hi` are mapped to `hi-IN`; BCP-47 codes are accepted directly.

| Field       | Type   | Required | Default     | Description            |
|-------------|--------|----------|-------------|------------------------|
| `text`      | string | yes      | —          | Text to convert        |
| `language`  | string | no       | `en`       | Language of the text   |
| `model`     | string | no       | `bulbul:v3` | TTS model identifier   |

**Request:**
```bash
curl -X POST http://localhost:8000/api/sarvam/tts \
  -H "Content-Type: application/json" \
  -d '{"text": "Namaste dOST! Aapka Swagat hai Vaachak mein.", "language": "hi"}'
```

---

### 5. Voice-First Pipeline (Combined)

**`POST /api/voice-first`**

Single-call full pipeline: **audio → Sarvam STT → Gemini processing → response**.

Perfect for the Vaachak reading companion — upload voice input and get a smart answer back.

| Field                  | Type   | Required | Default              | Description               |
|------------------------|--------|----------|----------------------|---------------------------|
| `audio_file`           | file   | yes      | —                   | Voice recording (MP3/WAV)  |
| `model`                | string | no       | `gemini-2.0-flash`  | Gemini LLM model           |
| `sarvam_model`         | string | no       | `saaras:v3`  | Sarvam STT model           |
| `source_language`      | string | no       | `auto`              | Source language for STT     |
| `system_prompt`        | string | no       | Vaachak default     | Custom system instruction   |

**Request:**
```bash
curl -X POST "http://localhost:8000/api/voice-first" \
  -H "Content-Type: multipart/form-data" \
  -F "audio_file=@./question.mp3;type=audio/mpeg" \
  -F 'model=gemini-2.0-flash' \
  -F 'system_prompt="You are Vaachak, an AI reading assistant for the elderly."'
```

**Response:**
```jsonc
{
  "status": "ok",
  "data": {
    "transcript": "What is the news today?",     // Sarvam STT result
    "gemini_response": "Today's top news includes... // Gemini answer",
    "model": "gemini-2.0-flash"
  },
  "message": "Voice-first pipeline complete: audio → STT → Gemini → response."
}
```

---

## Health Check

**`GET /health`**

Returns server status and configured services.

```bash
curl http://localhost:8000/health
```

Response:
```jsonc
{
  "status": "healthy",
  "services": {
    "gemini": true,       // true if GOOGLE_API_KEY is set
    "sarvam": true        // true if SARVAM_API_KEY is set
  }
}
```

---

## Project Structure

```
cloud-ai-server/
├── .env.example          # Environment variable template
├── main.py               # FastAPI server (all endpoints)
├── requirements.txt      # pip dependencies
└── README.md             # This file
```

---

## Development

### Install & run locally:
```bash
cd cloud-ai-server
pip install -r requirements.txt  # or `poetry add fastapi uvicorn[standard] httpx pydantic python-multipart`
cp .env.example .env            # edit and add your API keys
uvicorn main:app --reload --port 8000
```

### Test with Swagger UI (auto-generated):
Navigate to `http://localhost:8000/docs` in your browser. All endpoints are interactive — use the "Try it out" button to test them.

---

## License

Part of the [Vaachak](https://github.com/sujaykundu777/Vaachak) project — Hacktoberfest 2026 x Kraftcrowd Hackathon.
