import { NextRequest } from 'next/server';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
const uuid = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const path = (await context.params).path.join('/');
  const read = path === 'status' || path === 'users' || new RegExp(`^users/${uuid}/(preferences|conversations)$`).test(path) || new RegExp(`^users/${uuid}/conversations/${uuid}/messages$`).test(path);
  const write = path === 'users' || new RegExp(`^users/${uuid}/conversations$`).test(path) || new RegExp(`^users/${uuid}/conversations/${uuid}/messages$`).test(path);
  if (!((request.method === 'GET' && read) || (request.method === 'POST' && write) || (request.method === 'PUT' && new RegExp(`^users/${uuid}/preferences$`).test(path)))) return Response.json({ detail: 'Unsupported database endpoint or method' }, { status: 404 });
  try {
    const url = new URL(`${(process.env.VAACHAK_API_URL || 'http://127.0.0.1:8000').replace(/\/$/, '')}/api/db/${path}`);
    for (const key of ['limit', 'offset']) { const value = request.nextUrl.searchParams.get(key); if (value !== null) url.searchParams.set(key, value); }
    const response = await fetch(url, { method: request.method, cache: 'no-store', headers: request.method === 'GET' ? undefined : { 'Content-Type': 'application/json' }, body: request.method === 'GET' ? undefined : await request.text(), signal: AbortSignal.timeout(125_000) });
    return new Response(response.body, { status: response.status, headers: { 'Content-Type': response.headers.get('content-type') || 'application/json', 'Cache-Control': 'no-store' } });
  } catch { return Response.json({ detail: 'Cannot reach the database backend. Check the Python server and VAACHAK_API_URL. Reload records before retrying a write.' }, { status: 502 }); }
}
export { proxy as GET, proxy as POST, proxy as PUT };
