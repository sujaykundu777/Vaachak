import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient
from backboard_api import create_backboard_router

ASSISTANT = '123e4567-e89b-12d3-a456-426655440000'
THREAD = '123e4567-e89b-12d3-a456-426655440001'


def client(key='test-key'):
    app = FastAPI()
    app.include_router(create_backboard_router(key))
    return TestClient(app)


class BackboardTests(unittest.TestCase):
    def request(self, action, payload, status=200, body=None, failure=None):
        mock = AsyncMock()
        mock.__aenter__.return_value.request = AsyncMock(
            return_value=httpx.Response(status, json=body if body is not None else {'status': 'COMPLETED'}),
            side_effect=failure)
        with patch('backboard_api.httpx.AsyncClient', return_value=mock):
            response = client().post('/api/backboard/' + action, json=payload)
        return response, mock.__aenter__.return_value.request

    def test_no_key_blocks_all_provider_requests(self):
        with patch('backboard_api.httpx.AsyncClient') as upstream:
            c = client(None)
            self.assertEqual(c.get('/api/backboard/status').json()['configured'], False)
            self.assertEqual(c.post('/api/backboard/message', json={'content': 'Hello'}).status_code, 401)
            upstream.assert_not_called()

    def test_first_turn_and_continuation_send_memory_every_time(self):
        for mode in ['Auto', 'Readonly', 'off']:
            for ids in [{}, {'assistant_id': ASSISTANT, 'thread_id': THREAD}]:
                with self.subTest(mode=mode, ids=ids):
                    response, request = self.request('message', {'content': 'Hello', 'memory': mode, **ids}, body={'content': 'Hi', 'assistant_id': ASSISTANT, 'thread_id': THREAD})
                    self.assertEqual(response.status_code, 200)
                    args, kwargs = request.call_args
                    self.assertEqual(args, ('POST', 'https://app.backboard.io/api/threads/messages'))
                    self.assertEqual(kwargs['headers']['X-API-Key'], 'test-key')
                    self.assertEqual(kwargs['json']['memory'], mode)
                    self.assertIs(kwargs['json']['stream'], False)
                    if ids: self.assertEqual(kwargs['json']['thread_id'], THREAD)
                    else: self.assertNotIn('thread_id', kwargs['json'])
                    self.assertNotIn('test-key', response.text)

    def test_memory_only_ingestion_skips_llm(self):
        response, request = self.request('message', {'content': 'Agent context', 'send_to_llm': 'false', 'memory': 'Auto'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(request.call_args.kwargs['json']['send_to_llm'], 'false')
        self.assertEqual(request.call_args.kwargs['json']['memory'], 'Auto')

    def test_new_thread_preserves_assistant_without_forwarding_old_thread(self):
        _, request = self.request('message', {'content': 'Recall?', 'assistant_id': ASSISTANT})
        payload = request.call_args.kwargs['json']
        self.assertEqual(payload['assistant_id'], ASSISTANT)
        self.assertNotIn('thread_id', payload)

    def test_list_add_search_and_operation_paths(self):
        cases = [
            ('memories/list', {'assistant_id': ASSISTANT, 'page': 2}, 'GET', f'assistants/{ASSISTANT}/memories'),
            ('memories/add', {'assistant_id': ASSISTANT, 'content': 'Hindi', 'metadata': {'source': 'test'}}, 'POST', f'assistants/{ASSISTANT}/memories'),
            ('memories/search', {'assistant_id': ASSISTANT, 'query': 'language'}, 'POST', f'assistants/{ASSISTANT}/memories/search'),
            ('operation', {'operation_id': THREAD}, 'GET', f'assistants/memories/operations/{THREAD}'),
        ]
        for action, payload, method, path in cases:
            with self.subTest(action=action):
                r, request = self.request(action, payload, status=201 if action == 'memories/add' else 200)
                self.assertLess(r.status_code, 300)
                self.assertEqual(request.call_args.args, (method, 'https://app.backboard.io/api/' + path))
                if action == 'memories/list': self.assertEqual(request.call_args.kwargs['params'], {'page': 2, 'page_size': 25})
                if action == 'memories/add': self.assertEqual(request.call_args.kwargs['json']['metadata'], {'source': 'test'})

    def test_invalid_inputs_rejected_before_network(self):
        with patch('backboard_api.httpx.AsyncClient') as upstream:
            for payload in [{'content': ' '}, {'content': 'Hi', 'memory': 'invalid'}, {'content': 'Hi', 'assistant_id': '../other'}, {'content': 'Hi', 'api_key': 'bad'}]:
                self.assertEqual(client().post('/api/backboard/message', json=payload).status_code, 422)
            self.assertEqual(client().post('/api/backboard/memories/list', json={'assistant_id': ASSISTANT, 'page': 0}).status_code, 422)
            upstream.assert_not_called()

    def test_http_errors_and_timeouts(self):
        response, _ = self.request('message', {'content': 'Hi'}, status=429, body={'detail': 'quota'})
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.json()['detail'], 'quota')
        for failure, expected in [(httpx.ReadTimeout('late'), 504), (httpx.ConnectError('offline'), 502)]:
            response, _ = self.request('message', {'content': 'Hi'}, failure=failure)
            self.assertEqual(response.status_code, expected)


if __name__ == '__main__':
    unittest.main()
