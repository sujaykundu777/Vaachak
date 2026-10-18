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

## Ollama local playground

Open `/ollama` or use the sidebar. Requires an existing Ollama service, normally at `http://127.0.0.1:11434`. Start it with the Ollama app or `ollama serve`. Set `OLLAMA_API_URL` in `.env.local` to use another instance (origin only, without `/api`), then restart Next.js. If Next.js runs in Docker, use `http://host.docker.internal:11434` to reach Ollama on the host.

- Model discovery uses `GET /api/tags`; refresh updates installed models. The picker filters completion/embedding models when the service reports capabilities, and otherwise lists them for testing.
- Generation uses `POST /api/generate`; multi-turn chat uses `POST /api/chat` with native `user`/`assistant` roles. Only successful turns enter chat history. Start a new chat before changing its model or system instruction.
- Embeddings uses `POST /api/embed` with the selected embedding model; displays dimensions and a preview, with the full vector in the inspector.
- Temperature, output limit, optional thinking mode, system instruction and keep-alive controls are exposed. Thinking defaults to the model setting; unsupported overrides surface Ollama errors. Increase output tokens or turn thinking off if the budget is consumed before a final answer.
- Shows HTTP status, wall time, model load time and generation tokens/second (when reported). Request/response inspection distinguishes the next request from the last request.
- Allowlisted Next.js `/api/ollama/*` proxy uses the server-configured origin and forces `stream: false` for generation/chat. Requests time out after 290 seconds. No automatic retries, downloads, model pulls, or deletion. No cloud API key needed. Inference routing depends on your Ollama instance and selected models; this app does not override its configuration.

The local playground is independent of the Python cloud-ai-server. No Python route changes are required.

## Backboard memory playground

Open `/memory`. Set `BACKBOARD_API_KEY` in the **Python server's** `.env`, restart/rebuild it, then refresh the readiness badge. Keys stay on the backend. This uses Backboard's documented REST API with existing httpx, so no new SDK installation is needed.

Chat supports Auto, Readonly and off on every turn, optional provider/model overrides, assistant/thread continuation, and a fresh thread under the same assistant for cross-thread recall testing. Store explicit agent context with JSON metadata; browse paginated memories or search semantically. Check asynchronous memory operation status when provided. The request inspector includes provider responses, retrieved memories and timings. Only active assistant/thread IDs persist in browser storage; no API key is collected by the UI. This page is independent of the other playgrounds; it does not automatically store their context. See the backend `BACKBOARD.md` for setup and the recall test workflow.

For Backboard accounts with Memory & RAG credits but no chat credits, use **Store context** or **Store context without an LLM reply** (`send_to_llm: "false"`), then test retrieval through Browse & search. LLM reply failures display the provider's explanation. The playground never changes billing or subscriptions.

## Supabase database playground

Open `/database`. The Python backend needs `SUPABASE_URL` and `SUPABASE_SECRET_KEY` (or legacy `SUPABASE_SERVICE_ROLE_KEY`) in its private `.env`. Run the backend migration `supabase/migrations/001_preferences_conversations.sql` in the project's SQL editor and restart/rebuild Python. No Supabase secret or SDK is bundled in the frontend.

Create/select an app test user, save/load reading preferences, create/list conversations, append role-tagged messages, and load ordered history. Supports pagination and a request/response inspector. The proxy exposes only the supported database paths and GET/POST/PUT methods. The Python router uses existing httpx to reach Supabase's REST Data API.

This is a local admin playground, separate from Supabase Auth. The other playgrounds do not automatically persist their messages or settings here. Backend `SUPABASE.md` documents APIs, schema, setup, access limitations, and example payloads.

## API Log Playground

Open `/logs`. All playground UI requests now use a shared `playgroundFetch` wrapper: Voice (STT/TTS), Gemma (generation/chat/image-to-text), Ollama (models/generation/chat/embeddings), Backboard (messages/memory actions), and Database (user/preferences/conversation/message actions). Automatic readiness checks are included. This is frontend request tracing, not server-wide logging or provider subrequest tracing.

- Start named journeys, navigate using sidebar links, and inspect a chronological timeline. Client-side Next.js links keep the shared in-memory log alive across playgrounds.
- See action, source page, service, frontend/target API paths, HTTP status, duration, pending/error/partial states and bounded request/response previews.
- Default capture omits free-text payload fields. Optional text previews apply to future requests and may include personal content. Headers are never captured. Known credential fields/token patterns are redacted before log storage; uploaded bytes, base64 media, and embedding vectors are omitted. Bodies over 32 KiB are summarized and large/deep structures are capped. Redaction cannot identify every secret embedded in arbitrary prose—review exports before sharing.
- Logs are browser memory only, capped at 200 calls, and clear on reload. No localStorage, Supabase, external logging provider or automatic export is used. Calls before the new instrumentation loads cannot be reconstructed.
- Pause/resume new captures, filter by journey/service/status or search endpoint/action/source, inspect a call, export the filtered redacted JSON, or clear logs. Clearing does not cancel requests or delete application/provider records. In-flight calls finish in their original journey. Summed request time is not wall-clock journey duration.
- Complete JSON responses containing `status: error`/`FAILED` are classified as failed even with HTTP 200; partial/action-required/in-progress statuses are distinguished. Large omitted responses cannot be inspected for semantic errors.

```sh
npm run test:logs
```

The focused logger suite uses Node.js 22+ TypeScript stripping and tests redaction, file/body limits, response readability, semantic errors, network failures, pause/clear behavior and journey bounds.
