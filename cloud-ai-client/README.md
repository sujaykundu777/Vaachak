# Vaachak voice playground

Next.js App Router + TypeScript UI for the existing FastAPI Sarvam APIs.

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
