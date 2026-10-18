# Vaachak Cloud AI Server — API Reference

Complete reference for every endpoint exposed by the FastAPI backend in [`main.py`](./main.py).

- **Base URL (local):** `http://localhost:8000`
- **Server title:** Vaachak Cloud AI Server
- **Version:** 0.1.0
- **Content types:** JSON for most endpoints; `multipart/form-data` for audio uploads

---

## Table of Contents

1. [Running Locally](#running-locally)
2. [Configuration](#configuration)
3. [Response Conventions](#response-conventions)
4. [Endpoints](#endpoints)
   - [GET /health](#get-health)
   - [POST /api/gemini/generateContent](#post-apigeminigeneratecontent)
   - [POST /api/gemini/chat](#post-apigeminichat)
   - [POST /api/sarvam/transcribe](#post-apisarvamtranscribe)
   - [POST /api/sarvam/tts](#post-apisarvamtts)
   - [POST /api/voice-first](#post-apivoice-first)
5. [Interactive Docs](#interactive-docs)
6. [Error Handling](#error-handling)

---

## Running Locally

### 1. Prerequisites

- Python 3.10+ (the code uses `str | None` union syntax)
- API keys for:
  - **Google Gemini** → https://aistudio.google.com/apikey (env var `GOOGLE_API_KEY`)
  - **Sarvam AI** → https://dashboard.sarvam.ai (env var `SARVAM_API_KEY`)

### 2. Install dependencies

```bash
cd cloud-ai-server
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Configure environment

```bash
cp .env.example .env
# then edit .env and set your keys
```

`.env`:

```dotenv
GOOGLE_API_KEY=your_google_gemini_api_key_here
SARVAM_API_KEY=your_sarvam_platform_api_key_here
```

> Keys are loaded from `.env` (next to `main.py`) via `pydantic-settings`. If a key is
> missing, the endpoints that need it return **HTTP 401**.

### 4. Start the server

```bash
uvicorn main:app --reload --port 8000
```

Server: `http://localhost:8000`

### 5. One-shot copy/paste smoke test

```bash
# health
curl -s http://localhost:8000/health | python -m json.tool

# gemini text generation
curl -s -X POST http://localhost:8000/api/gemini/generateContent \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Say hello in one sentence."}' | python -m json.tool
```

---

## Configuration

| Env var          | Required by                            | Purpose                          |
|------------------|----------------------------------------|----------------------------------|
| `GOOGLE_API_KEY` | Gemini endpoints, `/api/voice-first`   | Auth for Google Generative Language API |
| `SARVAM_API_KEY` | Sarvam endpoints, `/api/voice-first`   | Auth for Sarvam STT + TTS APIs   |

`GET /health` reports which of these are currently set.

---

## Response Conventions

Most endpoints return a shared envelope (`ApiResponse`):

| Field          | Type              | Description                                   |
|----------------|-------------------|-----------------------------------------------|
| `status`       | string            | `ok` \| `partial` \| `error`                  |
| `data`         | object \| array \| string \| null | Endpoint-specific payload      |
| `message`      | string \| null    | Human-readable note (optional)                |
| `audio_url`    | string \| null    | Reserved for audio URL (optional)             |
| `audio_base64` | string \| null    | Base64-encoded audio bytes (TTS only)         |

`POST /api/sarvam/transcribe` uses its own `SarvamTranscribeResponse`:

| Field      | Type        | Description                        |
|------------|-------------|------------------------------------|
| `status`   | string      | `ok` \| `error`                    |
| `text`     | string\|null| Transcribed text (provider `transcript`) |
| `segments` | array\|null | Provider segment metadata          |
| `error`    | string\|null| Error detail when `status=error`   |

---

## Endpoints

### GET /health

Liveness probe. Reports whether each upstream service key is configured.

- **Auth:** none
- **Method:** `GET`
- **Path:** `/health`

**Example**

```bash
curl http://localhost:8000/health
```

**Response `200`**

```json
{
  "status": "healthy",
  "services": {
    "gemini": true,
    "sarvam": true
  }
}
```

---

### POST /api/gemini/generateContent

Generate text from a single prompt using a Gemini / Gemma model.

- **Auth:** `GOOGLE_API_KEY` (otherwise `401`)
- **Content type:** `application/json`
- **Upstream:** `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`

**Request body** (`GeminiRequest`)

| Field    | Type   | Required | Default             | Description              |
|----------|--------|----------|---------------------|--------------------------|
| `prompt` | string | yes      | —                   | Text input               |
| `model`  | string | no       | `gemma-4-26b-a4b-it`  | Gemini model name        |

**Example**

```bash
curl -X POST http://localhost:8000/api/gemini/generateContent \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "Explain what Vaachak does in simple language.",
    "model": "gemma-4-26b-a4b-it"
  }'
```

**Response `200`**

```json
{
  "status": "ok",
  "data": {
    "model": "gemma-4-26b-a4b-it",
    "generated_text": "Vaachak is a reading companion..."
  }
}
```

---

### POST /api/gemini/chat

Conversational chat that accepts full message history.

- **Auth:** `GOOGLE_API_KEY` (otherwise `401`)
- **Content type:** `application/json`

**Request body** (`ChatRequest`)

| Field      | Type            | Required | Default            | Description                       |
|------------|-----------------|----------|--------------------|-----------------------------------|
| `messages` | array<object>   | yes      | —                  | List of `{role, content}` items   |
| `model`    | string          | no       | `gemma-4-26b-a4b-it` | Gemini model name                 |

`messages[].role` accepts `user` or `model` (anything else is coerced to `user`).

**Example**

```bash
curl -X POST http://localhost:8000/api/gemini/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-26b-a4b-it",
    "messages": [
      {"role": "user",  "content": "Who is Vaachak?"},
      {"role": "model", "content": "Vaachak is an AI reading companion for elders."},
      {"role": "user",  "content": "What services does it offer?"}
    ]
  }'
```

**Response `200`**

```json
{
  "status": "ok",
  "data": {
    "model": "gemma-4-26b-a4b-it",
    "response": "Vaachak offers speech-to-text, text-to-speech..."
  }
}
```

---

### POST /api/sarvam/transcribe

Speech-to-Text: upload an audio file, receive the transcript.

- **Auth:** `SARVAM_API_KEY` (otherwise `401`)
- **Content type:** `multipart/form-data`
- **Upstream:** `https://api.sarvam.ai/speech-to-text`
- **Allowed file types:** `audio/mpeg`, `audio/wav`, `audio/x-wav`, `audio/ogg` (otherwise `400`)

**Form fields**

| Field             | Type   | Required | Default     | Description                                                     |
|-------------------|--------|----------|-------------|-----------------------------------------------------------------|
| `file`            | file   | yes      | —           | Audio file (MP3 / WAV / OGG)                                    |
| `model`           | string | no       | `saaras:v3` | Sarvam STT model                                                |
| `source_language` | string | no       | `auto`      | `auto`/`unknown` → `unknown`; short codes like `en` → `en-IN`   |

**Example**

```bash
curl -X POST "http://localhost:8000/api/sarvam/transcribe?model=saaras:v3&source_language=en" \
  -H "Content-Type: multipart/form-data" \
  -F "file=@./my_audio.mp3;type=audio/mpeg"
```

**Response `200`**

```json
{
  "status": "ok",
  "text": "Namaste, aap kaise hain?",
  "segments": []
}
```

**Error response `200` (provider failure — note: upstream errors are surfaced with a 200 body)**

```json
{
  "status": "error",
  "text": null,
  "segments": null,
  "error": "<upstream error text>"
}
```

---

### POST /api/sarvam/tts

Text-to-Speech: convert text → Base64-encoded MP3.

- **Auth:** `SARVAM_API_KEY` (otherwise `401`)
- **Content type:** `application/json`
- **Upstream:** `https://tts.sarvam.ai/tts/v2` (`format=mp3`)

**Request body** (`SarvamTTSRequest`)

| Field      | Type   | Required | Default               | Description             |
|------------|--------|----------|-----------------------|-------------------------|
| `text`     | string | yes      | —                     | Text to synthesize      |
| `language` | string | no       | `en`                  | Language code           |
| `model`    | string | no       | `sarvam-0.7b-v1-low`  | TTS model identifier    |

**Example**

```bash
curl -X POST http://localhost:8000/api/sarvam/tts \
  -H "Content-Type: application/json" \
  -d '{"text": "Namaste dost! Aapka swagat hai Vaachak mein.", "language": "hi"}'
```

**Response `200`** — audio is returned Base64-encoded in `audio_base64`.

```json
{
  "status": "ok",
  "data": {"message": "TTS audio generated for 'hi' text"},
  "audio_base64": "SUQzBAAAAAAAI1RTU0UAAAA..."
}
```

**Decode the audio to an MP3 file**

```bash
curl -s -X POST http://localhost:8000/api/sarvam/tts \
  -H "Content-Type: application/json" \
  -d '{"text": "Namaste dost!", "language": "hi"}' \
  | python -c "import sys,json,base64;open('out.mp3','wb').write(base64.b64decode(json.load(sys.stdin)['audio_base64']))"
```

---

### POST /api/voice-first

One-call voice-first pipeline: **audio → Sarvam STT → Gemini → response**.

- **Auth:** `SARVAM_API_KEY` required; `GOOGLE_API_KEY` optional (returns `partial` transcript-only result if missing)
- **Content type:** `multipart/form-data`
- **Allowed file types:** `audio/mpeg`, `audio/wav`, `audio/x-wav`, `audio/ogg`

**Form fields**

| Field             | Type   | Required | Default                       | Description                                |
|-------------------|--------|----------|-------------------------------|--------------------------------------------|
| `audio_file`      | file   | yes      | —                             | Voice recording (MP3 / WAV / OGG)          |
| `model`           | string | no       | `gemma-4-26b-a4b-it`            | Gemini LLM model                           |
| `sarvam_model`    | string | no       | `saaras:v3`                   | Sarvam STT model                           |
| `source_language` | string | no       | `auto`                        | STT source language                        |
| `system_prompt`   | string | no       | Vaachak default instruction   | Custom system instruction for Gemini       |

**Example**

```bash
curl -X POST "http://localhost:8000/api/voice-first" \
  -H "Content-Type: multipart/form-data" \
  -F "audio_file=@./question.mp3;type=audio/mpeg" \
  -F "model=gemma-4-26b-a4b-it" \
  -F "sarvam_model=saaras:v3" \
  -F "source_language=auto" \
  -F "system_prompt=You are Vaachak, an AI reading assistant for the elderly."
```

**Response `200` — full pipeline**

```json
{
  "status": "ok",
  "data": {
    "transcript": "What is the news today?",
    "gemini_response": "Today's top news includes...",
    "model": "gemma-4-26b-a4b-it"
  },
  "message": "Voice-first pipeline complete: audio → STT → Gemini → response."
}
```

**Response `200` — STT-only (Gemini key missing or LLM call failed)**

```json
{
  "status": "partial",
  "data": {"transcript": "What is the news today?"},
  "message": "GOOGLE_API_KEY not set — returning only the STT transcript..."
}
```

---

## Interactive Docs

FastAPI generates live docs automatically:

| UI         | URL                          |
|------------|------------------------------|
| Swagger UI | http://localhost:8000/docs   |
| ReDoc      | http://localhost:8000/redoc  |
| OpenAPI JSON | http://localhost:8000/openapi.json |

---

## Error Handling

| Status | When                                                              |
|--------|-------------------------------------------------------------------|
| `400`  | Unsupported audio content type (STT / voice-first)               |
| `401`  | Required API key (`GOOGLE_API_KEY` / `SARVAM_API_KEY`) not set   |
| `422`  | STT step failed inside `/api/voice-first` (includes `STT failed: ...`) |

Notes:

- `/api/gemini/*` and `/api/sarvam/tts` propagate the upstream HTTP status and body when Google/Sarvam return non-200.
- `/api/sarvam/transcribe` instead returns HTTP `200` with `{"status": "error", ...}` when the provider fails.
- `/api/voice-first` degrades gracefully: STT errors raise `422`, but Gemini errors return a `partial` result rather than failing the request.

---

Part of the [Vaachak](https://github.com/sujaykundu777/Vaachak) project — Hacktoberfest 2026 × Kraftcrowd Hackathon.
