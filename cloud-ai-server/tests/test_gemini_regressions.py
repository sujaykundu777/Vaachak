import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import httpx
from fastapi.testclient import TestClient
import main


class GeminiRegressionTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)

    def test_generated_text_skips_malformed_missing_and_thought_parts(self):
        body = {'candidates': [{'content': {'parts': [
            {'text': 'Hello '}, None, 'invalid', 7, [], {},
            {'text': None}, {'text': 42}, {'inlineData': {}},
            {'text': 'hidden', 'thought': True}, {'text': 'world'},
        ]}}]}
        self.assertEqual(main._generated_text(body), 'Hello world')

    def test_missing_keys_return_401_without_contacting_google(self):
        for key in [None, '']:
            for route, request in [
                ('generateContent', {'prompt': 'Hello'}),
                ('chat', {'messages': [{'role': 'user', 'content': 'Hello'}]}),
            ]:
                with self.subTest(key=key, route=route):
                    with patch.object(main, 'GOOGLE_API_KEY', key), patch.object(main.httpx, 'AsyncClient') as upstream:
                        response = self.client.post(f'/api/gemini/{route}', json=request)
                    self.assertEqual(response.status_code, 401)
                    self.assertEqual(response.json()['detail'], 'GOOGLE_API_KEY not set in environment')
                    upstream.assert_not_called()

    def test_generation_and_chat_handle_malformed_later_parts(self):
        body = {'candidates': [{'content': {'parts': [{'text': 'Hello'}, None, {}, {'text': ' world'}]}}]}
        for route, request, field in [
            ('generateContent', {'prompt': 'Hello'}, 'generated_text'),
            ('chat', {'messages': [{'role': 'user', 'content': 'Hello'}]}, 'response'),
        ]:
            with self.subTest(route=route):
                mock = AsyncMock()
                mock.__aenter__.return_value.post.return_value = httpx.Response(200, json=body)
                with patch.object(main, 'GOOGLE_API_KEY', 'test-key'), patch.object(main.httpx, 'AsyncClient', return_value=mock):
                    response = self.client.post(f'/api/gemini/{route}', json=request)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.json()['data'][field], 'Hello world')

    def test_explicit_model_ids_and_auth_header_are_preserved(self):
        for route, request in [
            ('generateContent', {'prompt': 'Hello'}),
            ('chat', {'messages': [{'role': 'user', 'content': 'Hello'}]}),
        ]:
            for model in ['gemini-2.0-flash', 'gemma-4-26b-a4b-it']:
                with self.subTest(route=route, model=model):
                    mock = AsyncMock()
                    mock.__aenter__.return_value.post.return_value = httpx.Response(200, json={'candidates': []})
                    with patch.object(main, 'GOOGLE_API_KEY', 'test-key'), patch.object(main.httpx, 'AsyncClient', return_value=mock):
                        response = self.client.post(f'/api/gemini/{route}', json={**request, 'model': model})
                    self.assertEqual(response.status_code, 200)
                    args, kwargs = mock.__aenter__.return_value.post.call_args
                    self.assertEqual(args[0], f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent')
                    self.assertEqual(kwargs['headers'], {'x-goog-api-key': 'test-key'})


if __name__ == '__main__':
    unittest.main()
