import { NextRequest } from 'next/server';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 150;
async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const endpoint = path.join('/');
  if (!((['health', 'backboard/status'].includes(endpoint) && request.method === 'GET') || (['sarvam/transcribe', 'sarvam/tts', 'gemini/generateContent', 'gemini/chat', 'gemini/imageToText', 'backboard/message', 'backboard/memories/list', 'backboard/memories/add', 'backboard/memories/search', 'backboard/operation'].includes(endpoint) && request.method === 'POST'))) {
    return Response.json({ detail: 'Unsupported endpoint or method' }, { status: 404 });
  }
  try {
    const base = (process.env.VAACHAK_API_URL || 'http://127.0.0.1:8000').replace(/\/$/, '');
    const url = new URL(`${base}/api/${endpoint}`);
    for (const name of ['model', 'source_language']) {
      const value = request.nextUrl.searchParams.get(name);
      if (value !== null) url.searchParams.set(name, value);
    }
    const headers = new Headers();
    const type = request.headers.get('content-type');
    if (type) headers.set('content-type', type);
    const upstream = await fetch(url, {
      method: request.method, headers, cache: 'no-store',
      body: request.method === 'POST' ? await request.arrayBuffer() : undefined,
      signal: AbortSignal.timeout(135_000),
    });
    return new Response(upstream.body, { status: upstream.status, headers: { 'content-type': upstream.headers.get('content-type') || 'application/json', 'cache-control': 'no-store' } });
  } catch (error) {
    const timeout = error instanceof Error && error.name === 'TimeoutError';
    return Response.json({ detail: timeout ? 'Backend timed out. Try a shorter input or retry the request.' : 'Cannot reach the Vaachak backend. Check VAACHAK_API_URL and start the FastAPI server.' }, { status: timeout ? 504 : 502 });
  }
}
export { proxy as GET, proxy as POST };
