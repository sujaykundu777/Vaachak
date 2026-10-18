export type CallState = 'pending' | 'success' | 'error' | 'partial' | 'cancelled';
export type ApiCall = {
  id: string; journeyId: string; journey: string; startedAt: string;
  source: string; service: string; action: string; method: string; path: string; upstream: string;
  state: CallState; http: number | null; ms: number | null; request: unknown; response: unknown;
};
export type LogState = { calls: ApiCall[]; recording: boolean; captureText: boolean; journeyId: string; journey: string };
export const EMPTY_LOG_STATE: LogState = { calls: [], recording: true, captureText: false, journeyId: '', journey: 'My Vaachak journey' };
const secretKey = /authorization|cookie|password|passwd|secret|api[-_]?key|access[-_]?token|refresh[-_]?token|session[-_]?token|^token$|credential/i;
const binaryKey = /audio_base64|inline_data|inlineData|^images$|^embedding(s)?$|^data_url$/i;
const publicTextKeys = /^(id|user_id|assistant_id|thread_id|conversation_id|operation_id|memory_operation_id|message_id|role|model|model_name|llm_provider|provider|language|source_language|preferred_language|theme|memory|send_to_llm|status|code|type|mime|content_type|source|kind|name|format|keep_alive|done_reason)$/i;
function redactString(value: string) {
  return value.replace(/Bearer\s+[^\s"']+/gi, 'Bearer [redacted]')
    .replace(/\b(?:sb_secret_|sb_publishable_|sk-)[A-Za-z0-9_-]+/g, '[redacted key]')
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, '[redacted token]')
    .slice(0, 4000);
}
export function sanitize(value: unknown, captureText = false, key = '', depth = 0): unknown {
  if (secretKey.test(key)) return '[redacted]';
  if (binaryKey.test(key)) return `[${Array.isArray(value) ? value.length + ' items' : 'binary/vector data'} omitted]`;
  if (depth > 6) return '[nested value omitted]';
  if (typeof value === 'string') {
    if (/^data:/i.test(value)) return '[data URL omitted]';
    return captureText || publicTextKeys.test(key) ? redactString(value) : '[text omitted]';
  }
  if (Array.isArray(value)) return value.length > 20 ? { length: value.length, preview: value.slice(0, 5).map(item => sanitize(item, captureText, key, depth + 1)) } : value.map(item => sanitize(item, captureText, key, depth + 1));
  if (value && typeof value === 'object') {
    const entries = Object.entries(value).slice(0, 50);
    return Object.fromEntries(entries.map(([name, item]) => [name, sanitize(item, captureText, name, depth + 1)]));
  }
  return value;
}

export function describe(path: string, method: string) {
  let service = 'Vaachak'; let upstream = path.replace(/^\/api\/backend/, '/api');
  if (path.startsWith('/api/ollama/')) { service = 'Ollama'; upstream = path.replace('/api/ollama/', '/api/'); }
  else if (path.startsWith('/api/database/')) { service = 'Supabase'; upstream = path.replace('/api/database/', '/api/db/'); }
  else if (path.includes('/sarvam/')) service = 'Sarvam';
  else if (path.includes('/gemini/')) service = 'Gemma';
  else if (path.includes('/backboard/')) service = 'Backboard';
  const actions: Record<string, string> = { health: 'Check API health', status: 'Check connection', tags: 'Discover local models', generateContent: 'Generate text', generate: 'Generate local text', chat: 'Continue conversation', transcribe: 'Transcribe audio', tts: 'Generate speech', imageToText: 'Extract image text', embed: 'Create embedding', message: 'Send memory message', operation: 'Check memory processing' };
  const last = path.split('/').at(-1) || path;
  let action = actions[last] || `${method} ${last}`;
  if (path.includes('/memories/')) action = last === 'add' ? 'Store agent context' : last === 'search' ? 'Search saved memories' : 'List saved memories';
  if (service === 'Supabase') action = last === 'preferences' ? (method === 'PUT' ? 'Save user preferences' : 'Load user preferences') : last === 'messages' ? (method === 'POST' ? 'Store conversation message' : 'Read message history') : last === 'conversations' ? (method === 'POST' ? 'Create conversation record' : 'List conversations') : last === 'users' ? (method === 'POST' ? 'Create user record' : 'List users') : action;
  return { service, upstream, action };
}

async function responsePreview(response: Response): Promise<unknown> {
  const type = response.headers.get('content-type') || '';
  if (!type.includes('json')) return { type: type || 'unknown', preview: '[non-JSON body omitted]' };
  const reader = response.clone().body?.getReader();
  if (!reader) return null;
  const chunks: Uint8Array[] = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > 32768) { void reader.cancel().catch(() => {}); return { body_omitted: true, byte_limit: 32768 }; }
    chunks.push(value);
  }
  const buffer = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { buffer.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder().decode(buffer)); } catch { return { preview: '[invalid JSON omitted]' }; }
}
function requestPreview(body: BodyInit | null | undefined, captureText: boolean): unknown {
  if (body == null) return null;
  if (typeof FormData !== 'undefined' && body instanceof FormData) {
    const fields: Record<string, unknown> = {};
    body.forEach((value, key) => { fields[key] = typeof value === 'string' ? sanitize(value, captureText, key) : { type: value.type, bytes: value.size, file: '[file bytes omitted]' }; });
    return { type: 'multipart/form-data', fields };
  }
  if (typeof body === 'string') {
    if (body.length > 32768) return { preview: '[request exceeds 32 KiB; payload omitted]' };
    try { return sanitize(JSON.parse(body), captureText); } catch { return '[non-JSON request omitted]'; }
  }
  return '[binary request omitted]';
}
function cleanPath(input: string): string {
  const url = new URL(input, 'http://localhost');
  const params = new URLSearchParams();
  for (const [key, value] of url.searchParams) params.append(key, secretKey.test(key) ? '[redacted]' : redactString(value));
  return url.pathname + (params.size ? '?' + params.toString() : '');
}

export function createLogStore(fetcher: typeof fetch = (...args) => fetch(...args), source: () => string = () => typeof window === 'undefined' ? '' : window.location.pathname) {
  let state: LogState = { ...EMPTY_LOG_STATE }; const listeners = new Set<() => void>();
  function update(next: LogState) { state = next; listeners.forEach(listener => listener()); }
  function patch(id: string, changes: Partial<ApiCall>) { if (state.calls.some(call => call.id === id)) update({ ...state, calls: state.calls.map(call => call.id === id ? { ...call, ...changes } : call) }); }
  const store = {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    setRecording: (recording: boolean) => update({ ...state, recording }),
    setCaptureText: (captureText: boolean) => update({ ...state, captureText }),
    startJourney: (name: string) => update({ ...state, recording: true, journeyId: crypto.randomUUID(), journey: redactString(name.trim() || 'My Vaachak journey').slice(0, 100) }),
    clear: () => update({ ...state, calls: [] }),
    async fetch(input: string, init?: RequestInit): Promise<Response> {
      // This wrapper is only used for our own playground API routes.
      if (!state.recording || !/^\/api\/(backend|ollama|database)\//.test(input)) return fetcher(input, init);
      const capture = state.captureText;
      const path = cleanPath(input); const pathname = path.split('?')[0];
      const method = (init?.method || 'GET').toUpperCase();
      const id = crypto.randomUUID(); const start = performance.now();
      const journeyId = state.journeyId || crypto.randomUUID();
      const call: ApiCall = { id, journeyId, journey: state.journey, startedAt: new Date().toISOString(), source: source(), ...describe(pathname, method), method, path, state: 'pending', http: null, ms: null, request: requestPreview(init?.body, capture), response: null };
      update({ ...state, journeyId, calls: [...state.calls, call].slice(-200) });
      try {
        const response = await fetcher(input, init);
        let preview: unknown;
        try { preview = await responsePreview(response); } catch { preview = { preview: '[response preview unavailable]' }; }
        const status = preview && typeof preview === 'object' ? String((preview as Record<string, unknown>).status || '').toLowerCase() : '';
        const result: CallState = !response.ok || ['error', 'failed'].includes(status) ? 'error' : ['partial', 'requires_action', 'in_progress'].includes(status) ? 'partial' : 'success';
        patch(id, { state: result, http: response.status, ms: Math.round(performance.now() - start), response: sanitize(preview, capture) });
        return response;
      } catch (error) {
        const cancelled = error instanceof Error && error.name === 'AbortError';
        patch(id, { state: cancelled ? 'cancelled' : 'error', http: null, ms: Math.round(performance.now() - start), response: { error: capture && error instanceof Error ? redactString(error.message) : cancelled ? 'Request cancelled' : 'Network request failed' } });
        throw error;
      }
    },
  };
  return store;
}

export const apiLogs = createLogStore();
export const playgroundFetch = apiLogs.fetch;
