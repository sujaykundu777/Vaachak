"""Local administrative APIs backed by Supabase's PostgREST Data API."""
from typing import Literal, Optional
from uuid import UUID, uuid4
from urllib.parse import urlparse

import httpx
from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field


class Request(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True, allow_inf_nan=False)


class UserRequest(Request):
    id: UUID = Field(default_factory=uuid4)
    display_name: str = Field(min_length=1, max_length=120)


class PreferencesRequest(Request):
    preferred_language: str = Field(default='en', min_length=2, max_length=20)
    speech_rate: float = Field(default=1, ge=0.5, le=2)
    voice: str = Field(default='', max_length=100)
    theme: Literal['system', 'light', 'dark'] = 'system'
    font_size: int = Field(default=18, ge=14, le=48)
    auto_read: bool = False
    extra: dict = Field(default_factory=dict)


class ConversationRequest(Request):
    id: UUID = Field(default_factory=uuid4)
    title: str = Field(min_length=1, max_length=200)
    provider: str = Field(default='', max_length=100)
    model: str = Field(default='', max_length=200)
    metadata: dict = Field(default_factory=dict)


class MessageRequest(Request):
    id: UUID = Field(default_factory=uuid4)
    role: Literal['system', 'user', 'assistant', 'tool']
    content: str = Field(min_length=1, max_length=100000)
    metadata: dict = Field(default_factory=dict)


def create_database_router(url: Optional[str], key: Optional[str]) -> APIRouter:
    router = APIRouter(prefix='/api/db', tags=['Supabase database'])
    base = (url or '').strip().rstrip('/')
    secret = (key or '').strip()
    parsed = urlparse(base)
    configured = bool(base and secret)

    async def call(method, table, *, params=None, payload=None, prefer=None):
        if not configured:
            raise HTTPException(503, 'Set SUPABASE_URL and SUPABASE_SECRET_KEY (or SUPABASE_SERVICE_ROLE_KEY) in the Python server .env, then restart')
        if parsed.scheme not in ('http', 'https') or not parsed.netloc or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
            raise HTTPException(503, 'SUPABASE_URL must be the project origin, without /rest/v1')
        if secret.startswith('sb_publishable_'):
            raise HTTPException(503, 'Use a Supabase server secret key, not a publishable key')
        headers = {'apikey': secret, 'Accept': 'application/json'}
        # New secret keys are not JWTs; legacy service_role keys use Bearer auth too.
        if not secret.startswith('sb_secret_'):
            headers['Authorization'] = 'Bearer ' + secret
        if prefer: headers['Prefer'] = prefer
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                response = await client.request(method, base + '/rest/v1/' + table,
                    headers=headers, params=params, json=payload)
        except httpx.TimeoutException as exc:
            raise HTTPException(504, 'Supabase timed out. Reload records before retrying a write.') from exc
        except httpx.RequestError as exc:
            raise HTTPException(502, 'Cannot reach Supabase. Check the project URL and availability.') from exc
        try: body = response.json()
        except ValueError as exc: raise HTTPException(502, 'Supabase returned a non-JSON response') from exc
        if not response.is_success:
            code = body.get('code') if isinstance(body, dict) else None
            if code in ('PGRST205', '42P01'):
                raise HTTPException(503, 'Database schema is missing. Run supabase/migrations/001_preferences_conversations.sql in the Supabase SQL editor.')
            if code == '23505': raise HTTPException(409, 'Record ID already exists. Reload the records before retrying.')
            if code == '23503': raise HTTPException(404, 'The selected user or conversation no longer exists.')
            raise HTTPException(response.status_code, body)
        if not isinstance(body, list): raise HTTPException(502, 'Supabase returned an unexpected record response')
        return body

    async def require_user(user_id):
        rows = await call('GET', 'vaachak_users', params={'id': f'eq.{user_id}', 'select': 'id', 'limit': 1})
        if not rows: raise HTTPException(404, 'User not found')

    async def require_conversation(user_id, conversation_id):
        rows = await call('GET', 'vaachak_conversations', params={'id': f'eq.{conversation_id}', 'user_id': f'eq.{user_id}', 'select': 'id', 'limit': 1})
        if not rows: raise HTTPException(404, 'Conversation not found for this user')

    def inserted(rows):
        if len(rows) != 1: raise HTTPException(502, 'Supabase did not return the saved record')
        return {'data': rows[0]}

    @router.get('/status')
    async def status():
        if not configured: return {'configured': False, 'ready': False}
        await call('GET', 'vaachak_users', params={'select': 'id', 'limit': 1})
        # Check every required table, not just the users table.
        for table in ['vaachak_preferences', 'vaachak_conversations', 'vaachak_messages']:
            await call('GET', table, params={'select': '*', 'limit': 0})
        return {'configured': True, 'ready': True}

    @router.get('/users')
    async def users(limit: int = Query(25, ge=1, le=100), offset: int = Query(0, ge=0)):
        return {'data': await call('GET', 'vaachak_users', params={'select': '*', 'order': 'created_at.desc,id.asc', 'limit': limit, 'offset': offset})}

    @router.post('/users', status_code=201)
    async def create_user(req: UserRequest):
        return inserted(await call('POST', 'vaachak_users', payload=req.model_dump(mode='json'), prefer='return=representation'))

    @router.get('/users/{user_id}/preferences')
    async def preferences(user_id: UUID):
        await require_user(user_id)
        rows = await call('GET', 'vaachak_preferences', params={'user_id': f'eq.{user_id}', 'select': '*'})
        return {'data': rows[0] if rows else None}

    @router.put('/users/{user_id}/preferences')
    async def save_preferences(user_id: UUID, req: PreferencesRequest):
        await require_user(user_id)
        return inserted(await call('POST', 'vaachak_preferences', params={'on_conflict': 'user_id'},
            payload={**req.model_dump(mode='json'), 'user_id': str(user_id)}, prefer='resolution=merge-duplicates,return=representation'))

    @router.get('/users/{user_id}/conversations')
    async def conversations(user_id: UUID, limit: int = Query(25, ge=1, le=100), offset: int = Query(0, ge=0)):
        await require_user(user_id)
        return {'data': await call('GET', 'vaachak_conversations', params={'user_id': f'eq.{user_id}', 'select': '*', 'order': 'updated_at.desc,id.asc', 'limit': limit, 'offset': offset})}

    @router.post('/users/{user_id}/conversations', status_code=201)
    async def create_conversation(user_id: UUID, req: ConversationRequest):
        await require_user(user_id)
        return inserted(await call('POST', 'vaachak_conversations', payload={**req.model_dump(mode='json'), 'user_id': str(user_id)}, prefer='return=representation'))

    @router.get('/users/{user_id}/conversations/{conversation_id}/messages')
    async def messages(user_id: UUID, conversation_id: UUID, limit: int = Query(50, ge=1, le=100), offset: int = Query(0, ge=0)):
        await require_conversation(user_id, conversation_id)
        return {'data': await call('GET', 'vaachak_messages', params={'user_id': f'eq.{user_id}', 'conversation_id': f'eq.{conversation_id}', 'select': '*', 'order': 'message_index.asc', 'limit': limit, 'offset': offset})}

    @router.post('/users/{user_id}/conversations/{conversation_id}/messages', status_code=201)
    async def append_message(user_id: UUID, conversation_id: UUID, req: MessageRequest):
        await require_conversation(user_id, conversation_id)
        return inserted(await call('POST', 'vaachak_messages', payload={**req.model_dump(mode='json'), 'user_id': str(user_id), 'conversation_id': str(conversation_id)}, prefer='return=representation'))

    return router
