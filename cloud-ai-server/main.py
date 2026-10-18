"""
Vaachak Cloud AI Server
========================
FastAPI server integrating Gemini API (Gemma models) and Sarvam Audio APIs
for voice-first interaction with the Vaachak reading companion.

Usage:
    cp .env.example .env          # fill in your API keys
    pip install -r requirements.txt
    uvicorn main:app --reload
"""

from __future__ import annotations

import tempfile
from pathlib import Path
from typing import List, Optional, Union

import httpx
from fastapi import Body, FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=Path(__file__).resolve().parent / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    GOOGLE_API_KEY: Optional[str] = None
    SARVAM_API_KEY: Optional[str] = None


settings = Settings()
GOOGLE_API_KEY: Optional[str] = settings.GOOGLE_API_KEY
SARVAM_API_KEY: Optional[str] = settings.SARVAM_API_KEY


class GeminiRequest(BaseModel):
    prompt: str
    model: str = "gemini-2.0-flash"


class ChatMessage(BaseModel):
    role: str = "user"
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    model: str = "gemini-2.0-flash"


class SarvamTranscribeResponse(BaseModel):
    status: str
    text: Optional[str] = None
    segments: Optional[list] = None
    error: Optional[str] = None


class SarvamTTSRequest(BaseModel):
    text: str
    language: str = "en"
    model: str = "bulbul:v3"


class ApiResponse(BaseModel):
    status: str
    data: Optional[Union[dict, list, str]] = None
    message: Optional[str] = None
    audio_url: Optional[str] = None
    audio_base64: Optional[str] = None


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Vaachak Cloud AI Server",
    description=(
        "Server providing Gemini (Gemma) LLM and Sarvam Audio APIs "
        "(Speech-to-Text & Text-to-Speech) for the Vaachak offline reading companion."
    ),
    version="0.1.0",
)


# ===================================================================
#  GEMINI ENDPOINTS
# ===================================================================

@app.post("/api/gemini/generateContent")
async def generate_content(req: GeminiRequest) -> ApiResponse:
    """Generate text from a prompt using Gemini / Gemma models."""
    if not GOOGLE_API_KEY:
        raise HTTPException(401, "GOOGLE_API_KEY not set in environment")

    url = (
        f"https://generativelanguage.googleapis.com"
        f"/v1beta/models/{req.model}:generateContent?key={GOOGLE_API_KEY}"
    )
    payload = {
        "contents": [
            {"parts": [{"text": req.prompt}]}
        ],
        "generationConfig": {"temperature": 0.7, "maxOutputTokens": 2048},
    }

    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(url, json=payload)

    if resp.status_code != 200:
        raise HTTPException(resp.status_code, resp.text)

    data = resp.json()
    candidate = (
        data["candidates"][0]["content"]["parts"][0].get("text", "")
        if data.get("candidates")
        else ""
    )
    return ApiResponse(
        status="ok",
        data={"model": req.model, "generated_text": candidate},
    )


@app.post("/api/gemini/chat")
async def chat(messages: ChatRequest) -> ApiResponse:
    """Conversational chat endpoint that includes message history."""
    if not GOOGLE_API_KEY:
        raise HTTPException(401, "GOOGLE_API_KEY not set in environment")

    url = (
        f"https://generativelanguage.googleapis.com"
        f"/v1beta/models/{(messages.model).replace('gemini-', 'gemini-pro-')}:generateContent?key={GOOGLE_API_KEY}"
    )
    contents = [
        {"role": m.role if m.role in ("user", "model") else "user", "parts": [{"text": m.content}]}
        for m in messages.messages
    ]

    payload: dict = {
        "contents": contents,
        "generationConfig": {"temperature": 0.7, "maxOutputTokens": 4096},
    }

    async with httpx.AsyncClient(timeout=90) as client:
        resp = await client.post(url, json=payload)

    if resp.status_code != 200:
        raise HTTPException(resp.status_code, resp.text)

    data = resp.json()
    text = (
        data["candidates"][0]["content"]["parts"][0].get("text", "")
        if data.get("candidates")
        else ""
    )
    return ApiResponse(
        status="ok",
        data={"model": messages.model, "response": text},
    )


# ===================================================================
#  SARVAM AUDIO ENDPOINTS
# ===================================================================

SARVAM_STT_URL = "https://api.sarvam.ai/speech-to-text"


def _stt_form_data(model: str, source_language: str) -> dict[str, str]:
    # Keep the public API's short language codes and "auto" compatible.
    language = source_language.strip()
    if language.lower() in {"auto", "unknown"}:
        language = "unknown"
    elif "-" not in language:
        language = f"{language.lower()}-IN"
    return {"model": model, "language_code": language}


@app.post("/api/sarvam/transcribe", response_model=SarvamTranscribeResponse)
async def transcribe_audio(file: UploadFile = File(...), model: str = "saaras:v3", source_language: str = "auto") -> SarvamTranscribeResponse:
    """Speech-to-text: converts uploaded audio (MP3 / WAV) to text via Sarvam."""
    if not SARVAM_API_KEY:
        raise HTTPException(401, "SARVAM_API_KEY not set in environment")

    allowed_types = {"audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg"}
    if file.content_type and file.content_type.split(";")[0] not in allowed_types:
        raise HTTPException(400, f"Only MP3 / WAV / OGG allowed. Got: {file.content_type}")

    # Read audio bytes into a temp file for form-based upload
    with tempfile.NamedTemporaryFile(suffix=".audio", delete=False) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    headers = {"api-subscription-key": SARVAM_API_KEY, "Accept": "application/json"}
    files = {"file": (file.filename or "upload.audio", content, file.content_type or "application/octet-stream")}

    try:
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(
                SARVAM_STT_URL,
                headers=headers,
                files=files,
                data=_stt_form_data(model, source_language),
            )

        if resp.status_code != 200:
            return SarvamTranscribeResponse(status="error", error=resp.text)

        json_data = resp.json()
        final_text: str = json_data["transcript"]
        segments = json_data.get("segments", [])

        return SarvamTranscribeResponse(
            status="ok",
            text=final_text,
            segments=segments,
        )
    finally:
        Path(tmp_path).unlink(missing_ok=True)


@app.post("/api/sarvam/tts", response_model=ApiResponse)
async def text_to_speech(req: SarvamTTSRequest) -> ApiResponse:
    """Text-to-speech: converts text → audio file via Sarvam."""
    if not SARVAM_API_KEY:
        raise HTTPException(401, "SARVAM_API_KEY not set in environment")

    url = "https://api.sarvam.ai/text-to-speech"
    headers = {"api-subscription-key": SARVAM_API_KEY}
    language = req.language.strip()
    if "-" not in language:
        language = f"{language.lower()}-IN"
    payload = {
        "text": req.text,
        "language_code": language,
        "model": req.model,
        "output_audio_codec": "mp3",
    }

    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(url, headers=headers, json=payload)

    if resp.status_code != 200:
        raise HTTPException(resp.status_code, resp.text)

    try:
        audios = resp.json()["audios"]
        if not isinstance(audios, list) or not audios or not isinstance(audios[0], str) or not audios[0]:
            raise ValueError("Missing audio")
    except (ValueError, KeyError, TypeError) as exc:
        raise HTTPException(502, "Sarvam returned an invalid audio response") from exc

    # Sarvam already returns base64-encoded audio in its JSON response.
    return ApiResponse(
        status="ok",
        data={"message": f"TTS audio generated for '{req.language}' text"},
        audio_base64=audios[0],
    )


# ===================================================================
#  COMBINED / VOICE-FIRST ENDPOINT
# ===================================================================

class VoiceFirstRequest(BaseModel):
    """Multi-part: audio + prompt for Gemini processing."""
    model: str = "gemini-2.0-flash"
    sarvam_model: str = "saaras:v3"
    source_language: str = "auto"
    system_prompt: Optional[str] = None


@app.post("/api/voice-first", response_model=ApiResponse)
async def voice_first(
    audio_file: UploadFile = File(...),
    req: VoiceFirstRequest = Body(
        default_factory=lambda: VoiceFirstRequest(),
        embed=False,
    ),
):
    """One-call voice-first pipeline:

    1. **Upload audio** → Sarvam STT → transcribed text
    2. **Send text**   → Gemini generateContent → intelligent response
    3. Return both the transcription AND the LLM answer.
    """
    # --- Step 1: Sarvam STT ---
    if not SARVAM_API_KEY:
        raise HTTPException(401, "SARVAM_API_KEY not set in environment")

    allowed_types = {"audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg"}
    if audio_file.content_type and audio_file.content_type.split(";")[0] not in allowed_types:
        raise HTTPException(400, f"Allowed formats: MP3 / WAV. Got {audio_file.content_type}")

    with tempfile.NamedTemporaryFile(suffix=".audio", delete=False) as tmp:
        content = await audio_file.read()
        tmp.write(content)
        tmp_path = tmp.name

    stt_headers = {"api-subscription-key": SARVAM_API_KEY, "Accept": "application/json"}
    stt_files = {"file": (audio_file.filename or "voice.audio", content, audio_file.content_type or "application/octet-stream")}

    final_text: str = ""
    try:
        async with httpx.AsyncClient(timeout=120) as stt_client:
            stt_resp = await stt_client.post(
                SARVAM_STT_URL,
                headers=stt_headers,
                files=stt_files,
                data=_stt_form_data(req.sarvam_model, req.source_language),
            )

        if stt_resp.status_code != 200:
            raise HTTPException(422, f"STT failed: {stt_resp.text}")

        final_text = stt_resp.json()["transcript"]

    finally:
        Path(tmp_path).unlink(missing_ok=True)

    if not final_text.strip():
        return ApiResponse(status="error", data={"message": "Transcription produced empty result"})

    # --- Step 2: Gemini generateContent ---
    system_instruction = req.system_prompt or (
        "You are Vaachak, an offline-first AI reading companion for elders with "
        "weak eyesight. Use simple, clear language."
    )
    full_text = f"{system_instruction}\n\nUser said: {final_text}"

    if not GOOGLE_API_KEY:
        return ApiResponse(
            status="partial",
            data={"transcript": final_text},
            message="GOOGLE_API_KEY not set — returning only the STT transcript. "
                    "Set your key to get a Gemini-powered response.",
        )

    model_name = req.model.replace("gemini-", "gemini-")
    url = (
        f"https://generativelanguage.googleapis.com"
        f"/v1beta/models/{model_name}:generateContent?key={GOOGLE_API_KEY}"
    )
    gpt_payload: dict = {
        "contents": [{"parts": [{"text": full_text}]}],
        "generationConfig": {"temperature": 0.7, "maxOutputTokens": 4096},
    }

    async with httpx.AsyncClient(timeout=90) as gemini_client:
        gemini_resp = await gemini_client.post(url, json=gpt_payload)

    if gemini_resp.status_code != 200:
        return ApiResponse(
            status="partial",
            data={"transcript": final_text},
            message=f"LLM call failed (HTTP {gemini_resp.status_code}). Returning STT transcript only.",
        )

    llm_data = gemini_resp.json()
    answer: str = ""
    if llm_data.get("candidates"):
        answer = llm_data["candidates"][0]["content"]["parts"][0].get("text", "")

    return ApiResponse(
        status="ok",
        data={
            "transcript": final_text,
            "gemini_response": answer,
            "model": req.model,
        },
        message="Voice-first pipeline complete: audio → STT → Gemini → response.",
    )


# ===================================================================
#  Health Check
# ===================================================================

@app.get("/api/health")
async def health():
    return {
        "status": "healthy",
        "services": {
            "gemini": bool(GOOGLE_API_KEY),
            "sarvam": bool(SARVAM_API_KEY),
        },
    }
