"""Backboard memory playground; credentials never reach the browser."""
from typing import Literal, Optional
from uuid import UUID

import httpx
from fastapi import APIRouter, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field


class Request(BaseModel):
    model_config = ConfigDict(extra='forbid', str_strip_whitespace=True)


class MessageRequest(Request):
    content: str = Field(min_length=1, max_length=50000)
    assistant_id: Optional[UUID] = None
    thread_id: Optional[UUID] = None
    memory: Literal['Auto', 'Readonly', 'off'] = 'Auto'
    send_to_llm: Literal['true', 'false'] = 'true'
    system_prompt: Optional[str] = Field(default=None, max_length=10000)
    llm_provider: Optional[str] = Field(default=None, max_length=100)
    model_name: Optional[str] = Field(default=None, max_length=200)


class AssistantRequest(Request):
    assistant_id: UUID


class ListRequest(AssistantRequest):
    page: int = Field(default=1, ge=1)
    page_size: int = Field(default=25, ge=1, le=100)


class AddRequest(AssistantRequest):
    content: str = Field(min_length=1, max_length=50000)
    metadata: dict = Field(default_factory=dict)


class SearchRequest(AssistantRequest):
    query: str = Field(min_length=1, max_length=5000)
    limit: int = Field(default=5, ge=1, le=50)


class OperationRequest(Request):
    operation_id: UUID


def create_backboard_router(api_key: Optional[str]) -> APIRouter:
    router = APIRouter(prefix='/api/backboard', tags=['Backboard memory'])

    async def call(method: str, path: str, *, payload=None, params=None):
        if not api_key or not api_key.strip():
            raise HTTPException(401, 'Set BACKBOARD_API_KEY in the Python server .env and restart the backend')
        try:
            async with httpx.AsyncClient(timeout=120) as client:
                response = await client.request(method, 'https://app.backboard.io/api/' + path,
                    headers={'X-API-Key': api_key.strip(), 'Accept': 'application/json'},
                    json=payload, params=params)
        except httpx.TimeoutException as exc:
            raise HTTPException(504, 'Backboard timed out. The operation may still have completed; check the thread or memories before retrying.') from exc
        except httpx.RequestError as exc:
            raise HTTPException(502, 'Could not reach Backboard') from exc
        try:
            body = response.json()
        except ValueError as exc:
            raise HTTPException(502, 'Backboard returned a non-JSON response') from exc
        # Keep provider HTTP codes and payloads for accurate testing.
        return JSONResponse(body, status_code=response.status_code)

    @router.get('/status')
    async def status():
        return {'configured': bool(api_key and api_key.strip()), 'provider': 'Backboard'}

    @router.post('/message')
    async def send(req: MessageRequest):
        payload = req.model_dump(mode='json', exclude_none=True)
        payload.update(stream=False, memory_response_citation=True)
        # Memory is explicitly supplied every turn, including continuation calls.
        return await call('POST', 'threads/messages', payload=payload)

    @router.post('/memories/list')
    async def memories(req: ListRequest):
        return await call('GET', f'assistants/{req.assistant_id}/memories', params={'page': req.page, 'page_size': req.page_size})

    @router.post('/memories/add')
    async def add(req: AddRequest):
        return await call('POST', f'assistants/{req.assistant_id}/memories', payload={'content': req.content, 'metadata': req.metadata})

    @router.post('/memories/search')
    async def search(req: SearchRequest):
        return await call('POST', f'assistants/{req.assistant_id}/memories/search', payload={'query': req.query, 'limit': req.limit})

    @router.post('/operation')
    async def operation(req: OperationRequest):
        return await call('GET', f'assistants/memories/operations/{req.operation_id}')

    return router
