'use client';
import Link from 'next/link';
import { playgroundFetch } from '../lib/api-log-store';
import { useEffect, useRef, useState } from 'react';

type Tab = 'chat' | 'store' | 'browse';
type Turn = { role: string; content: string };
type Trace = { request: unknown; body: unknown; http: number; ms: number; error: string };
function obj(value: unknown): Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}; }
function display(value: unknown): string { return typeof value === 'string' ? value : JSON.stringify(value); }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function MemoryPlayground() {
  const [tab, setTab] = useState<Tab>('chat');
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [statusError, setStatusError] = useState('');
  const [assistant, setAssistant] = useState('');
  const [thread, setThread] = useState('');
  const [assistantDraft, setAssistantDraft] = useState('');
  const [threadDraft, setThreadDraft] = useState('');
  const [mode, setMode] = useState('Auto');
  const [reply, setReply] = useState('true');
  const [content, setContent] = useState('Remember this context for Vaachak: our agent helps elders read, replies in simple Hindi, and uses short sentences.');
  const [context, setContext] = useState('Project: Vaachak\nAgent role: reading companion for elders\nPreferred language: Hindi\nResponse style: short, clear sentences');
  const [system, setSystem] = useState('You are a helpful agent. Use relevant saved context when answering.');
  const [provider, setProvider] = useState('');
  const [model, setModel] = useState('');
  const [metadata, setMetadata] = useState('{"source":"vaachak-playground","kind":"agent-context"}');
  const [query, setQuery] = useState('What language and response style should the Vaachak agent use?');
  const [page, setPage] = useState(1);
  const [memories, setMemories] = useState<unknown[]>([]);
  const [memoryInfo, setMemoryInfo] = useState('');
  const [turns, setTurns] = useState<Turn[]>([]);
  const [trace, setTrace] = useState<Trace | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [operation, setOperation] = useState('');
  const [opStatus, setOpStatus] = useState('');
  const [sessionReady, setSessionReady] = useState(false);

  async function check() {
    setStatusError('');
    try { const r = await playgroundFetch('/api/backend/backboard/status'); const b = obj(await r.json()); if (!r.ok) throw new Error(display(b.detail || 'Backend unavailable')); setConfigured(b.configured === true); }
    catch (e) { setConfigured(null); setStatusError(e instanceof Error ? e.message : 'Backend unavailable'); }
  }
  useEffect(() => {
    void check();
    try { const saved = obj(JSON.parse(localStorage.getItem('vaachak-backboard-session') || '{}')); if (typeof saved.assistant === 'string' && uuid.test(saved.assistant)) { setAssistant(saved.assistant); setAssistantDraft(saved.assistant); } if (typeof saved.thread === 'string' && uuid.test(saved.thread)) { setThread(saved.thread); setThreadDraft(saved.thread); } } catch { /* IDs can be entered manually if storage is unavailable. */ }
    setSessionReady(true);
  }, []);
  useEffect(() => { if (sessionReady) { try { localStorage.setItem('vaachak-backboard-session', JSON.stringify({ assistant, thread })); } catch { /* The active session still works in memory. */ } } }, [assistant, thread, sessionReady]);

  function resetThread() {
    setThread(''); setThreadDraft(''); setTurns([]); setTrace(null); setError(''); setOperation(''); setOpStatus(''); setNotice('New thread on the next message. The assistant and its saved memories are kept.');
  }
  function useSession() {
    if ((assistantDraft && !uuid.test(assistantDraft.trim())) || (threadDraft && !uuid.test(threadDraft.trim()))) { setError('Assistant and thread IDs must be UUIDs.'); return; }
    if (threadDraft.trim() && !assistantDraft.trim()) { setError('Enter both IDs when resuming, so new threads keep the same assistant memory.'); return; }
    setAssistant(assistantDraft.trim()); setThread(threadDraft.trim()); setTurns([]); setMemories([]); setMemoryInfo(''); setTrace(null); setOperation(''); setOpStatus(''); setError(''); setNotice('Session selected. Displayed turns start here; earlier thread messages remain on Backboard.');
  }
  async function run(action: string, request: Record<string, unknown>) {
    if (pending.current) return;
    setError(''); setNotice(''); pending.current = true; setBusy(true);
    const start = performance.now();
    try {
      const r = await playgroundFetch('/api/backend/backboard/' + action, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
      const raw = await r.text(); let body: unknown; try { body = JSON.parse(raw); } catch { body = { detail: raw || 'Empty response' }; }
      const b = obj(body);
      const failed = !r.ok || b.status === 'FAILED' || b.status === 'ERROR' || b.status === 'REQUIRES_ACTION';
      const problem = failed ? display(b.detail ?? b.error ?? (b.status === 'FAILED' && typeof b.content === 'string' ? b.content : undefined) ?? (b.status === 'REQUIRES_ACTION' ? 'This response requires tool actions. Inspect the response; no tools are executed by this playground.' : 'Backboard request failed')) : '';
      setTrace({ request, body, http: r.status, ms: Math.round(performance.now() - start), error: problem });
      // Preserve returned identities even when a remote operation cannot finish.
      if (action === 'message') {
        if (typeof b.assistant_id === 'string' && uuid.test(b.assistant_id)) { setAssistant(b.assistant_id); setAssistantDraft(b.assistant_id); }
        if (typeof b.thread_id === 'string' && uuid.test(b.thread_id)) { setThread(b.thread_id); setThreadDraft(b.thread_id); }
      }
      const op = b.memory_operation_id ?? b.operation_id;
      if (action === 'message' || action === 'memories/add') { setOperation(typeof op === 'string' ? op : ''); setOpStatus(typeof op === 'string' ? 'Pending — check operation status before testing recall.' : ''); }
      if (problem) { setError(problem); return; }
      if (action === 'message') {
        if (request.send_to_llm === 'false') { setTurns(previous => [...previous, { role: 'YOU', content: String(request.content) }, { role: 'BACKBOARD', content: 'Context submitted without an LLM reply. Check the memory operation status, then browse or search saved memories.' }]); setContent(''); return; }
        if (typeof b.content !== 'string' || !b.content.trim()) { setError('No assistant text returned. Inspect the raw response and session IDs before retrying.'); return; }
        setTurns(previous => [...previous, { role: 'YOU', content: String(request.content) }, { role: 'BACKBOARD', content: b.content as string }]); setContent('');
      } else if (action === 'memories/add') { setNotice('Context submitted to Backboard. Check operation status if provided, then browse or search memories.'); }
      else if (action === 'operation') { setOpStatus(display(b.status ?? 'Unknown')); }
      else { setMemories(Array.isArray(b.memories) ? b.memories : []); setMemoryInfo(action === 'memories/search' ? `Search results · ${b.total_count ?? (Array.isArray(b.memories) ? b.memories.length : 0)} matches` : `Page ${request.page} · ${b.total_count ?? '?'} total memories`); }
    } catch (e) { const detail = e instanceof Error ? e.message : 'Request failed'; setError(detail); setTrace({ request, body: { detail }, http: 0, ms: Math.round(performance.now() - start), error: detail }); }
    finally { pending.current = false; setBusy(false); }
  }
  function send() {
    if (!content.trim()) { setError('Enter a message.'); return; }
    const request = { content: content.trim(), memory: mode, send_to_llm: reply, ...(assistant ? { assistant_id: assistant } : {}), ...(thread ? { thread_id: thread } : {}), ...(system.trim() ? { system_prompt: system.trim() } : {}), ...(provider.trim() ? { llm_provider: provider.trim() } : {}), ...(model.trim() ? { model_name: model.trim() } : {}) };
    void run('message', request);
  }
  function save() {
    if (!assistant || !context.trim()) { setError('Select an assistant and enter context to store. Send the first chat message to create an assistant.'); return; }
    let parsed: unknown; try { parsed = JSON.parse(metadata); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(); } catch { setError('Metadata must be a JSON object.'); return; }
    void run('memories/add', { assistant_id: assistant, content: context.trim(), metadata: parsed });
  }
  function browse(search: boolean, targetPage = page) {
    if (!assistant) { setError('Select an assistant first.'); return; }
    if (search && !query.trim()) { setError('Enter a search query.'); return; }
    setPage(targetPage);
    void run(search ? 'memories/search' : 'memories/list', search ? { assistant_id: assistant, query: query.trim(), limit: 10 } : { assistant_id: assistant, page: targetPage, page_size: 25 });
  }
  const enabled = configured === true && !busy;
  const retrieved = obj(trace?.body).retrieved_memories;

  return <div className="shell"><aside><Link className="brand" href="/"><b>व</b>vaachak<span>.</span></Link><div className="workspace">DEVELOPER WORKSPACE</div><Link className="nav nav-link nav-idle" href="/">◉ &nbsp; Voice playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/gemma">✧ &nbsp; Gemma playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/ollama">◎ &nbsp; Ollama playground <span>↗</span></Link><Link className="nav nav-link" href="/memory" aria-current="page">▤ &nbsp; Memory playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/database">▦ &nbsp; Database playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/logs">≋ &nbsp; Log playground <span>↗</span></Link><div className="aside-note"><div className="little-wave">▤</div><h3>Context that carries on.</h3><p>Store what your agent should know. Test what it recalls.</p></div><div className="version">VAACHAK MEMORY <span>v0.1</span></div></aside>
    <main><header><span>Workspace <em>/</em><strong>Memory playground</strong></span><button className={`health ${configured ? 'ready' : ''}`} onClick={check} disabled={busy}><i/>{configured === null ? 'Check connection' : configured ? 'Backboard key configured' : 'Backboard key missing'} ↻</button></header><div className="page gemma-page"><div className="eyebrow">THE MEMORY LAB <span>POWERED BY BACKBOARD</span></div><h1>Give your agent a memory<span>.</span></h1><p className="intro">Store agent context, continue conversations, and test recall in a fresh thread.<br/>One assistant keeps its memories across conversations.</p>
    {(configured === false || statusError) && <div className="connection-error" role="status"><strong>{statusError || 'Connect your Backboard API key'}</strong><p>Add <code>BACKBOARD_API_KEY=your_key</code> to the Python server’s <code>.env</code>, then restart or rebuild the backend. Refresh the status above. The key stays on the server.</p></div>}
    <section className="card session-card"><div className="card-head"><span className="step">↔</span><div><h2>Assistant & thread</h2><p>Start with empty IDs to let Backboard create a session, or resume an existing one.</p></div></div><div className="session-body"><div className="fields"><label>Assistant ID<input value={assistantDraft} onChange={e => setAssistantDraft(e.target.value)} disabled={busy} placeholder="Auto-created on first message" spellCheck={false}/></label><label>Thread ID<input value={threadDraft} onChange={e => setThreadDraft(e.target.value)} disabled={busy} placeholder="New thread on next message" spellCheck={false}/></label></div><div className="session-actions"><button className="secondary" onClick={useSession} disabled={busy}>Use these IDs</button><button className="secondary" onClick={resetThread} disabled={busy || !assistant}>New thread, keep memory</button></div><p className="field-note">Active assistant: {assistant || 'not created yet'} · Active thread: {thread || 'new on next message'}. Only these IDs are saved in this browser; messages and memory live on Backboard.</p></div></section>
    <div className="tabs" role="tablist" aria-label="Memory actions">{(['chat','store','browse'] as const).map(t => <button key={t} role="tab" aria-selected={tab === t} disabled={busy} onClick={() => { setTab(t); setError(''); setNotice(''); }}>{t === 'chat' ? 'Chat & recall' : t === 'store' ? 'Store context' : 'Browse & search'}</button>)}</div>
    <div className="grid"><section className="card"><div className="card-head"><span className="step">01</span><div><h2>{tab === 'chat' ? 'A conversation with context' : tab === 'store' ? 'What should your agent know?' : 'Find saved context'}</h2><p>{tab === 'chat' ? 'Memory mode is sent on every message.' : 'Memories belong to the active assistant.'}</p></div></div><div className="memory-form">{tab === 'chat' ? <><label className="model-field">Memory mode<select value={mode} onChange={e => setMode(e.target.value)} disabled={busy}><option value="Auto">Auto — save and recall</option><option value="Readonly">Readonly — recall without saving</option><option value="off">Off — no persistent memory</option></select></label><label className="model-field system-field">Message action<select value={reply} onChange={e => setReply(e.target.value)} disabled={busy}><option value="true">Get an LLM reply</option><option value="false">Store context without an LLM reply</option></select></label><p className="field-note">Memory-only ingestion avoids a chat-model call. Use Auto to save context.</p><p className="field-note">Off disables persistent memory, but existing thread history still provides context. Use a new thread to compare recall modes.</p><div className="label-row"><label htmlFor="memory-message">Your message</label><button className="text-button" disabled={busy} onClick={() => setContent('What do you remember about the Vaachak agent’s role, language, and response style?')}>Try recall prompt ↗</button></div><textarea id="memory-message" value={content} onChange={e => setContent(e.target.value)} disabled={busy}/><label className="model-field system-field">System instruction<textarea value={system} onChange={e => setSystem(e.target.value)} disabled={busy}/></label><div className="fields"><label>Provider (optional)<input value={provider} onChange={e => setProvider(e.target.value)} placeholder="Backboard default" disabled={busy}/></label><label>Model (optional)<input value={model} onChange={e => setModel(e.target.value)} placeholder="Backboard default" disabled={busy}/></label></div><button className="primary" disabled={!enabled} onClick={send}>{busy ? 'Waiting for Backboard…' : reply === 'false' ? 'Store message context' : 'Send message'} →</button></> : tab === 'store' ? <><label htmlFor="agent-context">Agent context</label><textarea id="agent-context" value={context} onChange={e => setContext(e.target.value)} disabled={busy}/><label className="model-field system-field">Metadata (JSON object)<textarea value={metadata} onChange={e => setMetadata(e.target.value)} disabled={busy}/></label><button className="primary gemma-submit" disabled={!enabled || !assistant} onClick={save}>Store context in memory →</button><p className="field-note">Stores context directly, without asking for a chat response. Create or select an assistant first.</p></> : <><label htmlFor="memory-search">Semantic search query</label><textarea id="memory-search" value={query} onChange={e => setQuery(e.target.value)} disabled={busy}/><button className="primary gemma-submit" disabled={!enabled || !assistant} onClick={() => browse(true)}>Search memories →</button><button className="secondary" disabled={!enabled || !assistant} onClick={() => browse(false, 1)}>List saved memories</button><div className="session-actions"><button className="secondary" disabled={!enabled || !assistant || page <= 1} onClick={() => browse(false, page - 1)}>Previous page</button><button className="secondary" disabled={!enabled || !assistant} onClick={() => browse(false, page + 1)}>Next page</button></div></>}{error && <p className="error" role="alert">{error}</p>}{notice && <p className="field-note" role="status">{notice}</p>}{operation && <div className="operation"><p className="field-note">Memory operation: {operation}<br/>{opStatus}</p><button className="secondary" disabled={!enabled} onClick={() => void run('operation', { operation_id: operation })}>Check memory operation</button></div>}</div></section>
    <section className="card output"><div className="card-head"><span className="step">02</span><div><h2>{tab === 'chat' ? 'Your conversation' : tab === 'store' ? 'Storage result' : 'Saved memories'}</h2><p>{tab === 'browse' ? memoryInfo || 'Search or list memories to inspect them.' : 'Provider results and retrieved context.'}</p></div></div><div className="result gemma-result" aria-live="polite" aria-busy={busy}>{busy ? <div className="empty"><div className="thinking">▤</div><h3>Working with your memory…</h3></div> : tab === 'chat' && turns.length ? <div className="conversation">{turns.map((t,i) => <article key={i} className={`message ${t.role === 'YOU' ? 'user' : 'model'}`}><span className="result-label">{t.role}</span><p>{t.content}</p></article>)}{Array.isArray(retrieved) && <div><h3>Retrieved memories ({retrieved.length})</h3><pre>{JSON.stringify(retrieved,null,2)}</pre></div>}</div> : tab === 'browse' && memories.length ? <div className="conversation">{memories.map((m,i) => <article className="message model" key={i}><span className="result-label">{display(obj(m).id ?? `Memory ${i + 1}`)}</span><p>{display(obj(m).content ?? m)}</p>{typeof obj(m).score === 'number' && <p className="field-note">Relevance: {String(obj(m).score)}</p>}</article>)}</div> : <div className="empty"><div className="thinking">▤</div><h3>{tab === 'browse' && memoryInfo ? 'No memories in this result' : 'Context starts here'}</h3><p>{tab === 'store' ? notice || 'Store the agent’s goals, preferences,\nand decisions for later recall.' : 'Connect your key and send a message,\nor select an assistant to inspect its memories.'}</p></div>}</div><div className="result-footer"><span>{trace ? `HTTP ${trace.http || '—'} · ${trace.ms.toLocaleString()} ms` : 'Ready when you are'}</span><span>▤ Backboard</span></div></section></div>
    <details><summary><span>⌘ &nbsp; Last request & response</span><span>View details +</span></summary><div className="inspector"><div><h3>LAST REQUEST</h3><pre>{trace ? JSON.stringify(trace.request,null,2) : 'Run an action to inspect its request.'}</pre></div><div><h3>LAST RESPONSE</h3><pre>{trace ? JSON.stringify(trace.body,null,2) : 'No response yet.'}</pre></div></div></details><p className="field-note">Backboard is a hosted service. Sending messages or storing context transmits that text to Backboard and may use provider credits. New thread keeps assistant memories; it does not erase remote conversations. Memory updates can be asynchronous—check their status before testing recall. This playground does not automatically attach memory to the other Gemma or Ollama pages.</p><footer><span><i/> Store context. Start fresh. Test recall.</span><span>Vaachak × Backboard</span></footer></div></main></div>;
}
