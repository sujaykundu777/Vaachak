# Supabase preferences & conversation layer

## Setup

1. Add server-only settings to `cloud-ai-server/.env`:

```dotenv
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SECRET_KEY=your_server_secret_key
# Legacy alternative: SUPABASE_SERVICE_ROLE_KEY=your_service_role_jwt
```

Use the project origin without `/rest/v1`. Do not use a publishable/anon key. The modern secret is sent as `apikey`; legacy service-role JWTs also use Bearer auth. Neither key is exposed to the frontend.

2. Run `supabase/migrations/001_preferences_conversations.sql` once in the project's Supabase SQL editor. It creates four prefixed tables, indexes, timestamps, and an ordered message identity column. The migration is transactional and can be rerun. It does not remove existing application rows.
3. Restart the Python server, or run `docker compose up -d --build` in its folder.
4. Open http://localhost:3000/database and refresh connection status. It checks all four tables. Create a test user, save/load preferences, create a conversation, append messages, and load their history. The request inspector shows real HTTP requests and responses.

## API

All routes are prefixed `/api/db`:

| Method | Path | Action |
| --- | --- | --- |
| GET | `/status` | Check configuration and table connectivity |
| GET / POST | `/users` | List / create app users |
| GET / PUT | `/users/{user_id}/preferences` | Read / replace the full preference document |
| GET / POST | `/users/{user_id}/conversations` | List / create a user's conversations |
| GET / POST | `/users/{user_id}/conversations/{conversation_id}/messages` | Read ordered history / append a message |

Lists take `limit` (1–100) and `offset` (≥0). Defaults: users/conversations 25; messages 50. Responses use `{ "data": record }` or `{ "data": [records] }`. Successful creates return 201. Preferences upsert by user ID and return 200; reading an existing user without saved preferences returns `data: null`.

```json
// POST /api/db/users
{"display_name":"Test reader"}
// PUT /api/db/users/{id}/preferences (omitted fields use defaults)
{"preferred_language":"hi","speech_rate":0.9,"voice":"","theme":"system","font_size":22,"auto_read":true,"extra":{"reading_goal":"daily news"}}
// POST /api/db/users/{id}/conversations
{"title":"Reading session","provider":"ollama","model":"gemma4:e2b","metadata":{"source":"test"}}
// POST /api/db/users/{id}/conversations/{conversation_id}/messages
{"role":"user","content":"Read the headlines in Hindi.","metadata":{}}
```

Create requests accept an optional UUID `id`. The playground generates one and keeps it for a retry of the same write intent. Reusing a committed ID returns 409 instead of silently inserting a duplicate. After a timeout, load records before retrying. No automatic retries or delete endpoints are provided. Preferences use a full-document PUT, not a partial PATCH.

Messages accept system/user/assistant/tool roles. They are ordered by a database-generated `message_index` rather than client clocks. The number is globally increasing and may contain gaps. Message insertion updates the parent conversation's timestamp. Composite foreign keys prevent messages from referencing a conversation belonging to a different app user, and APIs filter by both IDs. Pagination uses offsets; concurrent writes may change pages, so reload when necessary.

## Access model

This is a **local admin/testing layer**, not a Supabase Auth login implementation. User records are app-generated UUIDs with display names. The backend uses a privileged key; selecting a user ID is a scope, not proof of identity. Keep the backend and frontend local until authentication/authorization is added. For production, derive user identity from a verified session and use appropriate RLS policies or authenticated server checks.

RLS is enabled on all four tables and browser roles have no table privileges/policies. Only service_role receives explicit select/insert/update grants. No browser Supabase client is used. The server-only key bypasses RLS by design. Existing Gemma/Ollama/Backboard playgrounds do not automatically save here; the database APIs are ready to be called by those flows in a later integration.

Missing configuration/schema returns 503; missing user or mismatched conversation returns 404; duplicate ID returns 409; invalid inputs return 422; connection failures return 502; timeouts return 504. Other PostgREST errors retain their status/detail for debugging.

## Validation

```sh
python -m unittest discover -s tests -v
```

The API tests mock PostgREST and verify scoped queries, credentials, ordering, upserts, validation and error mappings. Full persistence testing needs the migration applied to your Supabase project.

## Local Supabase

For a local Supabase API gateway on port 54321, use `SUPABASE_URL=http://127.0.0.1:54321` when Python runs on the host. When Python runs inside the existing Docker container on macOS, use `SUPABASE_URL=http://host.docker.internal:54321`. Use the local instance's server secret/service-role key, not a hosted project key. Keep those values only in `.env`.

The Vaachak migration uses `vaachak_*` table/function names so it can coexist with other apps in a local Supabase database. The local gateway, schema, preference save/load and ordered two-message conversation flow were verified through the frontend. The Log Playground at `/logs` can capture those UI calls as a named journey; log entries are browser memory and are not written to these tables.
