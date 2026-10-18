import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient
from database_api import create_database_router

USER = '123e4567-e89b-12d3-a456-426655440000'
CONVERSATION = '123e4567-e89b-12d3-a456-426655440001'
MESSAGE = '123e4567-e89b-12d3-a456-426655440002'


def client(url='https://test.supabase.co', key='sb_secret_test'):
    app = FastAPI()
    app.include_router(create_database_router(url, key))
    return TestClient(app)


class DatabaseTests(unittest.TestCase):
    def request(self, method, path, payload=None, responses=None, failure=None, key='sb_secret_test'):
        mock = AsyncMock()
        replies = responses if responses is not None else [(200, [{'id': USER}])]
        mock.__aenter__.return_value.request = AsyncMock(side_effect=failure or [httpx.Response(status, json=body) for status, body in replies])
        with patch('database_api.httpx.AsyncClient', return_value=mock):
            response = client(key=key).request(method, '/api/db/' + path, json=payload)
        return response, mock.__aenter__.return_value.request

    def test_missing_config_and_publishable_keys_do_not_reach_db(self):
        with patch('database_api.httpx.AsyncClient') as upstream:
            self.assertEqual(client(key=None).get('/api/db/status').json(), {'configured': False, 'ready': False})
            self.assertEqual(client(key=None).get('/api/db/users').status_code, 503)
            self.assertEqual(client(key='sb_publishable_test').get('/api/db/users').status_code, 503)
            upstream.assert_not_called()

    def test_server_auth_supports_new_and_legacy_keys(self):
        for key in ['sb_secret_test', 'legacy-jwt']:
            r, request = self.request('GET', 'users', key=key)
            self.assertEqual(r.status_code, 200)
            headers = request.call_args.kwargs['headers']
            self.assertEqual(headers['apikey'], key)
            self.assertEqual('Authorization' in headers, key == 'legacy-jwt')
            self.assertNotIn(key, r.text)

    def test_create_user_preserves_client_id(self):
        r, request = self.request('POST', 'users', {'id': USER, 'display_name': 'Test user'}, [(201, [{'id': USER, 'display_name': 'Test user'}])])
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.json()['data']['id'], USER)
        self.assertEqual(request.call_args.kwargs['json']['id'], USER)
        self.assertEqual(request.call_args.kwargs['headers']['Prefer'], 'return=representation')

    def test_preference_upsert_is_scoped_to_path_user(self):
        r, request = self.request('PUT', f'users/{USER}/preferences', {'preferred_language': 'hi'}, [(200, [{'id': USER}]), (201, [{'user_id': USER, 'preferred_language': 'hi'}])])
        self.assertEqual(r.status_code, 200)
        self.assertEqual(request.call_args.kwargs['params'], {'on_conflict': 'user_id'})
        self.assertEqual(request.call_args.kwargs['json']['user_id'], USER)
        self.assertIn('resolution=merge-duplicates', request.call_args.kwargs['headers']['Prefer'])

    def test_missing_users_and_preferences_are_distinct(self):
        r, request = self.request('GET', f'users/{USER}/preferences', responses=[(200, [])])
        self.assertEqual(r.status_code, 404)
        self.assertEqual(request.call_count, 1)
        r, _ = self.request('GET', f'users/{USER}/preferences', responses=[(200, [{'id': USER}]), (200, [])])
        self.assertEqual(r.json(), {'data': None})

    def test_conversation_ownership_checked_before_message_read_or_write(self):
        path = f'users/{USER}/conversations/{CONVERSATION}/messages'
        for method, payload in [('GET', None), ('POST', {'role': 'user', 'content': 'Hi'})]:
            r, request = self.request(method, path, payload, [(200, [])])
            self.assertEqual(r.status_code, 404)
            self.assertEqual(request.call_count, 1)
            params = request.call_args.kwargs['params']
            self.assertEqual(params['user_id'], 'eq.' + USER)
            self.assertEqual(params['id'], 'eq.' + CONVERSATION)

    def test_append_and_ordered_history_use_user_and_conversation(self):
        path = f'users/{USER}/conversations/{CONVERSATION}/messages'
        r, request = self.request('POST', path, {'id': MESSAGE, 'role': 'assistant', 'content': 'Hello'}, [(200, [{'id': CONVERSATION}]), (201, [{'id': MESSAGE}])])
        self.assertEqual(r.status_code, 201)
        self.assertEqual(request.call_args.kwargs['json']['user_id'], USER)
        self.assertEqual(request.call_args.kwargs['json']['conversation_id'], CONVERSATION)
        r, request = self.request('GET', path + '?limit=50&offset=50', responses=[(200, [{'id': CONVERSATION}]), (200, [])])
        self.assertEqual(r.status_code, 200)
        params = request.call_args.kwargs['params']
        self.assertEqual(params['order'], 'message_index.asc')
        self.assertEqual(params['offset'], 50)
        self.assertEqual(params['user_id'], 'eq.' + USER)

    def test_input_validation_and_forged_owner_are_rejected(self):
        cases = [('POST', 'users', {'display_name': ' '}), ('PUT', f'users/{USER}/preferences', {'speech_rate': 9}), ('PUT', f'users/{USER}/preferences', {'user_id': CONVERSATION}), ('POST', f'users/{USER}/conversations', {'title': ' '}), ('POST', f'users/{USER}/conversations/{CONVERSATION}/messages', {'role': 'wrong', 'content': 'Hello'})]
        with patch('database_api.httpx.AsyncClient') as upstream:
            for method, path, payload in cases:
                self.assertEqual(client().request(method, '/api/db/' + path, json=payload).status_code, 422)
            self.assertEqual(client().get('/api/db/users?offset=-1').status_code, 422)
            upstream.assert_not_called()

    def test_schema_conflict_foreign_key_and_network_errors(self):
        for code, expected in [('PGRST205', 503), ('23505', 409), ('23503', 404)]:
            r, _ = self.request('GET', 'users', responses=[(400, {'code': code})])
            self.assertEqual(r.status_code, expected)
        for failure, expected in [(httpx.ReadTimeout('late'), 504), (httpx.ConnectError('offline'), 502)]:
            r, _ = self.request('GET', 'users', failure=failure)
            self.assertEqual(r.status_code, expected)

    def test_status_checks_all_tables(self):
        r, request = self.request('GET', 'status', responses=[(200, [])] * 4)
        self.assertTrue(r.json()['ready'])
        self.assertEqual(request.call_count, 4)


if __name__ == '__main__':
    unittest.main()
