# Image-to-text API

`POST /api/gemini/imageToText` accepts multipart form data:

- `file` (required): PNG, JPEG, or WebP, non-empty, at most 10 MiB. Type is detected from signature bytes, not the supplied filename or MIME. Google validates full image decoding.
- `prompt` (optional): extraction instructions; defaults to transcribing visible text in its original language and reading order.
- `model` (optional): defaults to `gemma-4-26b-a4b-it`; must support image input.

Uses `GOOGLE_API_KEY` on the Python backend. Image bytes are read into memory with a bounded read and passed as inline base64 data to Google's generateContent API. Images are not persisted by this endpoint. Provider data handling still applies.

Success uses the existing envelope: `{ "status": "ok", "data": { "model": "...", "generated_text": "..." } }`.

Errors: 400 invalid format/empty image/blank settings, 413 oversized image, 401 missing key, 422 missing file, 502 invalid/empty provider result or connection failure, 504 timeout. Provider HTTP errors pass through with their status.

```sh
curl -X POST http://localhost:8000/api/gemini/imageToText \
  -F 'file=@page.png' \
  -F 'model=gemma-4-26b-a4b-it' \
  -F 'prompt=Extract all visible text. Preserve line breaks.'
```

The frontend is at http://localhost:3000/gemma → Image to text. Use the generated sample image for a smoke test without selecting a personal file. Restart the backend after updating source. For Docker Compose:

```sh
docker compose up -d --build
```

Tests (from the server folder, with requirements installed):

```sh
python -m unittest discover -s tests -v
```

The existing text-only generation and chat contracts are unchanged. Image extraction is a separate route.
