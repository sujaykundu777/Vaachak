# Backboard agent memory playground

Open http://localhost:3000/memory. Configure `BACKBOARD_API_KEY` in the Python server's `.env` file. Never put the key in frontend environment variables or commit it.

```dotenv
BACKBOARD_API_KEY=your_backboard_key
```

Restart Python, or rebuild the existing Docker service:

```sh
docker compose up -d --build
```

The new `backboard_api.py` router uses the existing httpx dependency to call the documented REST endpoints. Installing `backboard-sdk` is not required. The Dockerfile copies the new router into the container.

## Try a recall test

1. Send the sample context message with memory **Auto** and empty session IDs. Backboard returns the assistant/thread IDs; the UI captures both.
2. If a `memory_operation_id` is returned, use **Check memory operation** until the provider reports completion. Automatic memory extraction may not create a fact for every message.
3. **Browse & search** lists saved memories with pagination or searches them semantically. **Store context** adds an explicit assistant memory and optional metadata without generating a chat reply.
4. Use **New thread, keep memory**, switch to **Readonly**, and ask the agent to recall its role, language, and response style. The next request omits `thread_id` and keeps `assistant_id`.
5. For a baseline, use another new thread with memory **off**. Off does not disable existing conversation history; that is why a fresh thread matters.

Existing assistant and thread UUIDs can be entered via **Use these IDs**. Only those two IDs persist in browser localStorage. Local displayed turns start when this page resumes; the app does not fetch old thread history. Clearing IDs selects a new automatic session on the next send; it does not erase remote data. Memory is scoped to the assistant, not a global store across all assistants.

## Local routes

All routes are under `/api/backboard`. `GET /status` reports key configuration only (it does not validate the key). The following POST routes accept JSON:

- `/message`: `content`, optional assistant/thread UUIDs, `memory` (`Auto`, `Readonly`, `off`), optional `system_prompt`, `llm_provider`, `model_name`. Memory is sent explicitly every turn. Uses `POST /threads/messages` upstream, `stream=false`, memory citations enabled.
- `/memories/list`: assistant UUID, page ≥1, page_size 1–100 → GET assistant memories.
- `/memories/add`: assistant UUID, content, metadata object → POST assistant memory.
- `/memories/search`: assistant UUID, query, limit 1–50 → semantic search.
- `/operation`: operation UUID → GET memory operation status.

Provider HTTP codes and JSON bodies pass through. Missing key returns 401; invalid inputs return 422; connection/non-JSON errors return 502; timeouts return 504. There are no automatic retries because a remote write may complete after a timeout. The app does not execute returned tool calls. Context and messages are sent to Backboard when you click an action and may consume provider credits. No deletion/reset API is exposed.

This is a standalone memory playground; it does not automatically capture context from the other Gemma/Ollama playgrounds. Local test deployments share the backend account; add authentication before exposing it.

References: https://docs.backboard.io/sdk/memory and https://docs.backboard.io/api-reference/threads/send-message.

### Memory-only accounts

If Backboard returns a chat `FAILED` response explaining that free credits are reserved for Memory & RAG, use **Store context** or choose **Store context without an LLM reply** in Chat & recall. The latter sends `send_to_llm: "false"` with memory Auto to ingest a message without a chat-model call. Browse/search can test retrieval without an LLM response. Generating a conversational recall answer requires sufficient Backboard LLM credits; the playground surfaces this provider restriction and never changes billing settings.
