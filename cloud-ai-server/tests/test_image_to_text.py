import base64
import sys
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import httpx
from fastapi.testclient import TestClient
import main

PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8XcAAAAASUVORK5CYII=')


class ImageToTextTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)
        self.key = patch.object(main, 'GOOGLE_API_KEY', 'test-key')
        self.key.start()
        self.addCleanup(self.key.stop)

    def upload(self, content=PNG, mime='image/png', **fields):
        return self.client.post('/api/gemini/imageToText', files={'file': ('test.png', content, mime)}, data=fields)

    def upstream(self, status=200, body=None, failure=None):
        response = httpx.Response(status, json=body if body is not None else {
            'candidates': [{'content': {'parts': [{'text': 'private reasoning', 'thought': True}, {'text': 'Hello '}, {'text': 'Vaachak'}]}}],
        })
        mock = AsyncMock()
        mock.__aenter__.return_value.post = AsyncMock(return_value=response, side_effect=failure)
        return patch.object(main.httpx, 'AsyncClient', return_value=mock), mock

    def test_success_forwards_actual_image_mime_and_form_fields(self):
        patcher, mock = self.upstream()
        with patcher:
            response = self.upload(mime='application/octet-stream', prompt='Read the page', model='custom-vision')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['data']['generated_text'], 'Hello Vaachak')
        args, kwargs = mock.__aenter__.return_value.post.call_args
        self.assertIn('custom-vision:generateContent', args[0])
        self.assertEqual(kwargs['headers'], {'x-goog-api-key': 'test-key'})
        image, prompt = kwargs['json']['contents'][0]['parts']
        self.assertEqual(image['inline_data']['mime_type'], 'image/png')
        self.assertEqual(base64.b64decode(image['inline_data']['data']), PNG)
        self.assertEqual(prompt['text'], 'Read the page')

    def test_missing_empty_fake_and_oversize_images(self):
        with patch.object(main.httpx, 'AsyncClient') as upstream:
            self.assertEqual(self.client.post('/api/gemini/imageToText').status_code, 422)
            self.assertEqual(self.upload(b'').status_code, 400)
            self.assertEqual(self.upload(b'<html>fake image</html>').status_code, 400)
            with patch.object(main, 'MAX_IMAGE_BYTES', 32):
                self.assertEqual(self.upload(PNG).status_code, 413)
            upstream.assert_not_called()

    def test_blank_fields_and_missing_key(self):
        self.assertEqual(self.upload(prompt=' ', model='test').status_code, 400)
        self.assertEqual(self.upload(model=' ').status_code, 400)
        with patch.object(main, 'GOOGLE_API_KEY', None):
            self.assertEqual(self.upload().status_code, 401)

    def test_provider_errors_and_empty_response(self):
        for status, body, expected in [(429, {'error': 'quota'}, 429), (200, {'candidates': []}, 502), (200, {'candidates': 'invalid'}, 502)]:
            with self.subTest(status=status, body=body):
                patcher, _ = self.upstream(status, body)
                with patcher:
                    self.assertEqual(self.upload().status_code, expected)

    def test_network_errors(self):
        for failure, expected in [(httpx.ReadTimeout('timeout'), 504), (httpx.ConnectError('offline'), 502)]:
            with self.subTest(expected=expected):
                patcher, _ = self.upstream(failure=failure)
                with patcher:
                    self.assertEqual(self.upload().status_code, expected)


if __name__ == '__main__':
    unittest.main()
