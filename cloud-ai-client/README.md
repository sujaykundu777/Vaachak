# Vaachak AI playgrounds

Next.js App Router + TypeScript UI for the existing FastAPI Sarvam and Gemma APIs.

## Run

Requires Node.js 20.9+ and the Python cloud-ai-server running on port 8000.

```sh
npm install
cp .env.example .env.local
npm run dev
```

Open http://localhost:3000. Change `VAACHAK_API_URL` in `.env.local` for another backend origin (without `/api`), then restart Next.js. Configure `SARVAM_API_KEY` in the Python server environment, never in this frontend.

## Features

- Text to speech: editable text, language and model (`bulbul:v3` default), MP3 playback/download, Hindi sample.
- Speech to text: MP3/WAV/OGG upload and preview, auto-detection/language selection, editable model (`saaras:v3` default), transcript transfer to TTS.
- Health/key readiness check, request/response inspector, HTTP status and timing, loading/error states including HTTP 200 provider errors.
- Allowlisted same-origin Next.js proxy avoids backend CORS changes. Backend routes: `/api/health`, `/api/sarvam/tts`, `/api/sarvam/transcribe`. Transcription settings are query parameters, matching main.py rather than outdated API.md.

Requests are real and may incur provider usage. Results stay in component memory until reload; large base64 data is abbreviated in the inspector. Microphone recording is excluded because many browsers record WebM, which this backend does not accept.

## Verify

```sh
npm run typecheck
npm run build
```

A running Python backend and valid Sarvam key are needed for a full provider smoke test. This playground is for local development; add authentication before public deployment.

## Gemma playground

Open `/gemma` or use the sidebar. Tests `/api/gemini/generateContent` with `{prompt, model}` and `/api/gemini/chat` with `{messages: [{role, content}], model}`. The default model is `gemma-4-26b-a4b-it`, matching the current Python backend. The model field is editable before starting a chat. New chat clears history and unlocks model selection.

Configure `GOOGLE_API_KEY` on the Python server. Health readiness checks the backend's `services.gemini` flag; it confirms key configuration, not provider access or quota. Errors show HTTP status, timing, raw responses, and preserve the input for manual retries. Only successful user/model turns join chat history. No automatic retries or streaming; the backend returns a complete response. Request inspection separates the next request from the last sent request.

## Image to text

The Gemma playground now includes an **Image to text** tab. Upload one PNG/JPEG/WebP (up to 10 MiB), preview it, edit the vision model and extraction instructions, then extract text. A generated sample image is available. Results can be copied or downloaded as UTF-8 `.txt`; HTTP status, timing, request metadata and the raw response are inspectable. Binary image data is omitted from the inspector.

Requires the new Python `POST /api/gemini/imageToText` route, with multipart fields `file`, `prompt`, and `model`. Rebuild/restart the backend after adding this route. A 404 in the UI explains this requirement. Uses the existing backend `GOOGLE_API_KEY`. Images are sent to Google only when you click Extract text; browser previews use local object URLs and are released on replacement/unmount. No automatic upload or local persistence.
