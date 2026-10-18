import { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 300;

async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const endpoint = (await context.params).path.join('/');
  if (!((request.method === 'GET' && endpoint === 'tags') ||
    (request.method === 'POST' && ['generate', 'chat', 'embed'].includes(endpoint)))) {
    return Response.json({ error: 'Unsupported Ollama endpoint or method' }, { status: 404 });
  }
  let body: Record<string, unknown> | undefined;
  if (request.method === 'POST') {
    try {
      const value: unknown = await request.json();
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error();
      body = value as Record<string, unknown>;
      if (typeof body.model !== 'string' || !body.model.trim()) throw new Error();
      if (['generate', 'chat'].includes(endpoint)) body.stream = false;
    } catch { return Response.json({ error: 'A JSON object with a non-empty model is required' }, { status: 400 }); }
  }
  try {
    const base = (process.env.OLLAMA_API_URL || 'http://127.0.0.1:11434').replace(/\/$/, '');
    const response = await fetch(`${base}/api/${endpoint}`, {
      method: request.method, cache: 'no-store',
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(endpoint === 'tags' ? 10_000 : 290_000)]),
    });
    return new Response(response.body, { status: response.status, headers: {
      'content-type': response.headers.get('content-type') || 'application/json', 'cache-control': 'no-store',
    } });
  } catch (error) {
    const timeout = error instanceof Error && error.name === 'TimeoutError';
    return Response.json({ error: timeout
      ? 'Ollama timed out. Try a smaller model or lower the output token limit. The model may still be processing.'
      : 'Cannot reach Ollama. Start Ollama (ollama serve), check OLLAMA_API_URL, then refresh models.' }, { status: timeout ? 504 : 502 });
  }
}

export { proxy as GET, proxy as POST };
