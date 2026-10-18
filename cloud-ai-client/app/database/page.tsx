'use client';
import { useEffect, useRef, useState } from 'react';

type Row = Record<string, unknown>;
type Preferences = { preferred_language: string; speech_rate: number; voice: string; theme: string; font_size: number; auto_read: boolean };
type Trace = { method: string; path: string; request?: unknown; response: unknown; http: number; ms: number };
const defaults: Preferences = { preferred_language: 'en', speech_rate: 1, voice: '', theme: 'system', font_size: 18, auto_read: false };
function obj(value: unknown): Row { return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Row : {}; }
function text(value: unknown): string { return typeof value === 'string' ? value : JSON.stringify(value); }
function jsonObject(raw: string): Row { const value: unknown = JSON.parse(raw); if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Enter a JSON object, such as {"source":"playground"}.'); return value as Row; }

export default function DatabasePlayground() {
  const [tab, setTab] = useState<'preferences' | 'conversations'>('preferences');
  const [ready, setReady] = useState(false);
  const [status, setStatus] = useState('Checking connection…');
  const [users, setUsers] = useState<Row[]>([]);
  const [user, setUser] = useState('');
  const [userOffset, setUserOffset] = useState(0);
  const [name, setName] = useState('Vaachak test user');
  const [prefs, setPrefs] = useState<Preferences>(defaults);
  const [extra, setExtra] = useState('{"reading_goal":"daily news"}');
  const [conversations, setConversations] = useState<Row[]>([]);
  const [conversation, setConversation] = useState('');
  const [conversationOffset, setConversationOffset] = useState(0);
  const [title, setTitle] = useState('Reading companion test');
  const [provider, setProvider] = useState('ollama');
  const [model, setModel] = useState('');
  const [metadata, setMetadata] = useState('{"source":"database-playground"}');
  const [role, setRole] = useState('user');
  const [content, setContent] = useState('Please read today’s headlines in simple Hindi.');
  const [messages, setMessages] = useState<Row[]>([]);
  const [messageOffset, setMessageOffset] = useState(0);
  const [trace, setTrace] = useState<Trace | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const writeIntent = useRef<{ key: string; id: string } | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function check() {
    if (pending.current) return;
    pending.current = true; setBusy(true);
    try {
      const r = await fetch('/api/database/status'); const b = obj(await r.json());
      setReady(r.ok && b.ready === true);
      setStatus(!r.ok ? text(b.detail || 'Database unavailable') : b.ready === true ? 'Supabase connected' : 'Supabase setup required');
    } catch { setReady(false); setStatus('Backend unavailable. Start the Python server.'); }
    finally { pending.current = false; setBusy(false); }
  }
  useEffect(() => { void check(); }, []);

  async function run(method: string, path: string, request?: unknown): Promise<Row | null> {
    if (pending.current) return null;
    pending.current = true; setBusy(true); setError(''); setNotice('');
    const start = performance.now();
    try {
      const r = await fetch('/api/database/' + path, { method, headers: request === undefined ? undefined : { 'Content-Type': 'application/json' }, body: request === undefined ? undefined : JSON.stringify(request) });
      const raw = await r.text(); let body: unknown; try { body = JSON.parse(raw); } catch { body = { detail: raw || 'Empty response' }; }
      setTrace({ method, path, request, response: body, http: r.status, ms: Math.round(performance.now() - start) });
      if (!r.ok) { setError(text(obj(body).detail || body)); return null; }
      return obj(body);
    } catch (e) { const detail = e instanceof Error ? e.message : 'Request failed'; setError(detail); setTrace({ method, path, request, response: { detail }, http: 0, ms: Math.round(performance.now() - start) }); return null; }
    finally { pending.current = false; setBusy(false); }
  }
  // Reuse the ID after a network failure; duplicate writes become a visible 409.
  function intent(key: string) { if (writeIntent.current?.key !== key) writeIntent.current = { key, id: crypto.randomUUID() }; return writeIntent.current.id; }
  function selectUser(id: string) { setUser(id); setConversation(''); setConversations([]); setMessages([]); setPrefs(defaults); setExtra('{}'); setConversationOffset(0); setMessageOffset(0); setError(''); setNotice('User selected. Load preferences or conversations to read saved data.'); }
  async function listUsers(offset = 0) { const b = await run('GET', `users?limit=25&offset=${offset}`); if (b) { setUsers(Array.isArray(b.data) ? b.data : []); setUserOffset(offset); } }
  async function createUser() {
    if (!name.trim()) { setError('Enter a display name.'); return; }
    const id = intent('user:' + name.trim()); const b = await run('POST', 'users', { id, display_name: name.trim() });
    const row = obj(b?.data); if (b && typeof row.id === 'string') { setUsers(previous => [row, ...previous.filter(entry => entry.id !== row.id)]); selectUser(row.id); setNotice('User saved. You can now store preferences and conversations.'); writeIntent.current = null; }
  }
  async function loadPreferences() {
    const b = await run('GET', `users/${user}/preferences`); if (!b) return;
    if (!b.data) { setPrefs(defaults); setExtra('{}'); setNotice('No saved preferences yet. Defaults are shown; save to persist them.'); return; }
    const row = obj(b.data); setPrefs({ preferred_language: String(row.preferred_language), speech_rate: Number(row.speech_rate), voice: String(row.voice), theme: String(row.theme), font_size: Number(row.font_size), auto_read: row.auto_read === true }); setExtra(JSON.stringify(row.extra || {}, null, 2)); setNotice('Preferences loaded from Supabase.');
  }
  async function savePreferences() {
    try {
      if (!prefs.preferred_language.trim() || !Number.isFinite(prefs.speech_rate) || prefs.speech_rate < .5 || prefs.speech_rate > 2 || !Number.isInteger(prefs.font_size) || prefs.font_size < 14 || prefs.font_size > 48) throw new Error('Enter a language, speech rate 0.5–2, and font size 14–48.');
      const b = await run('PUT', `users/${user}/preferences`, { ...prefs, extra: jsonObject(extra) }); if (b) setNotice('Preferences saved. Load them again to verify persistence.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Invalid preferences'); }
  }
  async function listConversations(offset = 0) { const b = await run('GET', `users/${user}/conversations?limit=25&offset=${offset}`); if (b) { setConversations(Array.isArray(b.data) ? b.data : []); setConversationOffset(offset); } }
  async function createConversation() {
    try {
      if (!title.trim()) throw new Error('Enter a conversation title.');
      const request = { title: title.trim(), provider, model, metadata: jsonObject(metadata) };
      const b = await run('POST', `users/${user}/conversations`, { ...request, id: intent(user + JSON.stringify(request)) });
      const row = obj(b?.data); if (b && typeof row.id === 'string') { setConversations(previous => [row, ...previous.filter(entry => entry.id !== row.id)]); setConversation(row.id); setMessages([]); setMessageOffset(0); setNotice('Conversation saved. Append messages below.'); writeIntent.current = null; }
    } catch (e) { setError(e instanceof Error ? e.message : 'Invalid conversation'); }
  }
  async function loadMessages(offset = 0) { const b = await run('GET', `users/${user}/conversations/${conversation}/messages?limit=50&offset=${offset}`); if (b) { setMessages(Array.isArray(b.data) ? b.data : []); setMessageOffset(offset); setNotice(`Loaded messages ${offset + 1}–${offset + (Array.isArray(b.data) ? b.data.length : 0)}. Stored in database order.`); } }
  async function appendMessage() {
    try {
      if (!content.trim()) throw new Error('Enter message content.');
      const request = { role, content: content.trim(), metadata: jsonObject(metadata) };
      const b = await run('POST', `users/${user}/conversations/${conversation}/messages`, { ...request, id: intent(conversation + JSON.stringify(request)) });
      if (b) { setContent(''); writeIntent.current = null; setNotice('Message saved. Load the message history to verify it.'); }
    } catch (e) { setError(e instanceof Error ? e.message : 'Invalid message'); }
  }
  const enabled = ready && !busy;
  return <div className="shell"><aside><a className="brand" href="/"><b>व</b>vaachak<span>.</span></a><div className="workspace">DEVELOPER WORKSPACE</div><a className="nav nav-link nav-idle" href="/">◉ &nbsp; Voice playground <span>↗</span></a><a className="nav nav-link nav-idle" href="/gemma">✧ &nbsp; Gemma playground <span>↗</span></a><a className="nav nav-link nav-idle" href="/ollama">◎ &nbsp; Ollama playground <span>↗</span></a><a className="nav nav-link nav-idle" href="/memory">▤ &nbsp; Memory playground <span>↗</span></a><a className="nav nav-link" href="/database" aria-current="page">▦ &nbsp; Database playground <span>↗</span></a><div className="aside-note"><div className="little-wave">▦</div><h3>A place for every conversation.</h3><p>Preferences and message history, stored with Supabase.</p></div><div className="version">VAACHAK DATABASE <span>v0.1</span></div></aside>
    <main><header><span>Workspace <em>/</em><strong>Database playground</strong></span><button className={`health ${ready ? 'ready' : ''}`} onClick={check} disabled={busy}><i/>{ready ? 'Supabase connected' : 'Check database'} ↻</button></header><div className="page gemma-page"><div className="eyebrow">THE DATA LAB <span>POWERED BY SUPABASE</span></div><h1>Keep the conversation going<span>.</span></h1><p className="intro">Remember how each user likes to read. Store the conversations that follow.<br/>Test preference and conversation APIs against your database.</p>
    {!ready && <div className="connection-error" role="status"><strong>{status}</strong><p>Set SUPABASE_URL and SUPABASE_SECRET_KEY (or SUPABASE_SERVICE_ROLE_KEY) in the Python server’s .env. Run <code>supabase/migrations/001_preferences_conversations.sql</code> in your Supabase SQL editor, restart the backend, then refresh.</p></div>}
    <section className="card session-card"><div className="card-head"><span className="step">01</span><div><h2>Select a playground user</h2><p>These are app test records, separate from Supabase Auth accounts.</p></div></div><div className="session-body"><div className="fields"><label>Display name<input value={name} onChange={e => setName(e.target.value)} maxLength={120} disabled={busy}/></label><label>Saved users<select value={users.some(row => row.id === user) ? user : ''} onChange={e => selectUser(e.target.value)} disabled={!enabled}><option value="">Select a user</option>{users.map(row => <option key={String(row.id)} value={String(row.id)}>{String(row.display_name)} · {String(row.id).slice(0,8)}</option>)}</select></label></div><div className="session-actions"><button className="secondary" onClick={createUser} disabled={!enabled}>Create user</button><button className="secondary" onClick={() => void listUsers(0)} disabled={!enabled}>Load users</button></div><div className="session-actions database-paging"><button className="text-button" disabled={!enabled || userOffset === 0} onClick={() => void listUsers(Math.max(0,userOffset-25))}>← Previous users</button><span>Page {userOffset / 25 + 1}</span><button className="text-button" disabled={!enabled || users.length < 25} onClick={() => void listUsers(userOffset+25)}>Next users →</button></div><p className="field-note">Active user: {user || 'select or create a user'}</p></div></section>
    <div className="tabs" role="tablist" aria-label="Database APIs"><button role="tab" aria-selected={tab === 'preferences'} disabled={busy} onClick={() => { setTab('preferences'); setError(''); setNotice(''); }}>User preferences</button><button role="tab" aria-selected={tab === 'conversations'} disabled={busy} onClick={() => { setTab('conversations'); setError(''); setNotice(''); }}>Conversations & messages</button></div>
    <div className="grid"><section className="card"><div className="card-head"><span className="step">02</span><div><h2>{tab === 'preferences' ? 'Make reading feel personal' : 'Start a stored conversation'}</h2><p>{tab === 'preferences' ? 'Save one preference record per user.' : 'Store history from any AI provider.'}</p></div></div><div className="memory-form">{tab === 'preferences' ? <><div className="fields"><label>Preferred language<input value={prefs.preferred_language} onChange={e => setPrefs({...prefs,preferred_language:e.target.value})} disabled={busy} maxLength={20}/></label><label>Speech rate<input type="number" min="0.5" max="2" step="0.1" value={prefs.speech_rate} onChange={e => setPrefs({...prefs,speech_rate:Number(e.target.value)})} disabled={busy}/></label><label>Font size<input type="number" min="14" max="48" value={prefs.font_size} onChange={e => setPrefs({...prefs,font_size:Number(e.target.value)})} disabled={busy}/></label><label>Theme<select value={prefs.theme} onChange={e => setPrefs({...prefs,theme:e.target.value})} disabled={busy}><option value="system">System</option><option value="light">Light</option><option value="dark">Dark</option></select></label></div><label className="model-field">Voice<input value={prefs.voice} onChange={e => setPrefs({...prefs,voice:e.target.value})} maxLength={100} disabled={busy}/></label><label className="checkbox-label"><input type="checkbox" checked={prefs.auto_read} onChange={e => setPrefs({...prefs,auto_read:e.target.checked})} disabled={busy}/> Automatically read responses</label><label className="model-field system-field">Additional preferences (JSON)<textarea value={extra} onChange={e => setExtra(e.target.value)} disabled={busy}/></label><button className="primary gemma-submit" disabled={!enabled || !user} onClick={savePreferences}>Save preferences →</button><button className="secondary" disabled={!enabled || !user} onClick={loadPreferences}>Load saved preferences</button><p className="field-note">Save replaces the preference document with the complete form. It does not change the other playgrounds’ settings.</p></> : <><label className="model-field">Conversation title<input value={title} onChange={e => setTitle(e.target.value)} maxLength={200} disabled={busy}/></label><div className="fields"><label>Provider<input value={provider} onChange={e => setProvider(e.target.value)} disabled={busy} maxLength={100}/></label><label>Model<input value={model} onChange={e => setModel(e.target.value)} disabled={busy} maxLength={200}/></label></div><label className="model-field">Metadata for new conversation/message (JSON)<textarea value={metadata} onChange={e => setMetadata(e.target.value)} disabled={busy}/></label><button className="primary gemma-submit" disabled={!enabled || !user} onClick={createConversation}>Create conversation →</button><button className="secondary" disabled={!enabled || !user} onClick={() => void listConversations(0)}>Load conversations</button><label className="model-field system-field">Saved conversations<select value={conversations.some(row => row.id === conversation) ? conversation : ''} disabled={!enabled || !user} onChange={e => { setConversation(e.target.value); setMessages([]); setMessageOffset(0); }}><option value="">Select a conversation</option>{conversations.map(row => <option key={String(row.id)} value={String(row.id)}>{String(row.title)} · {String(row.id).slice(0,8)}</option>)}</select></label><div className="session-actions database-paging"><button className="text-button" disabled={!enabled || !user || conversationOffset === 0} onClick={() => void listConversations(Math.max(0,conversationOffset-25))}>← Previous</button><span>Page {conversationOffset / 25 + 1}</span><button className="text-button" disabled={!enabled || !user || conversations.length < 25} onClick={() => void listConversations(conversationOffset+25)}>Next →</button></div><label className="model-field system-field">Message role<select value={role} onChange={e => setRole(e.target.value)} disabled={busy}>{['user','assistant','system','tool'].map(r => <option key={r}>{r}</option>)}</select></label><label className="model-field system-field">Message content<textarea value={content} onChange={e => setContent(e.target.value)} disabled={busy}/></label><button className="primary gemma-submit" disabled={!enabled || !conversation || !user} onClick={appendMessage}>Append message →</button><button className="secondary" disabled={!enabled || !conversation || !user} onClick={() => void loadMessages(0)}>Load message history</button><p className="field-note">Stores messages only. Appending an assistant message does not generate an AI response.</p></>}
    {error && <p className="error" role="alert">{error}</p>}{notice && <p className="field-note" role="status">{notice}</p>}</div></section>
    <section className="card output"><div className="card-head"><span className="step">03</span><div><h2>{tab === 'preferences' ? 'Storage response' : 'Stored message history'}</h2><p>{tab === 'preferences' ? 'The saved record is shown after each API action.' : 'Messages load in insertion order, 50 per page.'}</p></div></div><div className="result gemma-result" aria-live="polite" aria-busy={busy}>{busy ? <div className="empty"><div className="thinking">▦</div><h3>Working with Supabase…</h3></div> : tab === 'conversations' && messages.length ? <div className="conversation">{messages.map(row => <article className={`message ${row.role === 'assistant' ? 'model' : 'user'}`} key={String(row.id)}><span className="result-label">{String(row.role).toUpperCase()} · #{String(row.message_index)}</span><p>{String(row.content)}</p><span className="field-note">{String(row.created_at)}</span></article>)}</div> : trace ? <div className="success"><span className="result-label">{trace.http >= 400 || !trace.http ? 'API ERROR' : 'API RESPONSE'}</span><pre>{JSON.stringify(trace.response,null,2)}</pre></div> : <div className="empty"><div className="thinking">▦</div><h3>A place to pick up where you left off</h3><p>Select a user and test a storage API.{'\n'}Your database response appears here.</p></div>}</div>{tab === 'conversations' && <div className="session-actions database-message-pages"><button className="secondary" disabled={!enabled || !conversation || messageOffset === 0} onClick={() => void loadMessages(Math.max(0,messageOffset-50))}>Previous messages</button><button className="secondary" disabled={!enabled || !conversation || messages.length < 50} onClick={() => void loadMessages(messageOffset+50)}>Next messages</button></div>}<div className="result-footer"><span>{trace ? `HTTP ${trace.http || '—'} · ${trace.ms.toLocaleString()} ms` : 'Ready when you are'}</span><span>▦ Supabase</span></div></section></div>
    <details><summary><span>⌘ &nbsp; Request & response inspector</span><span>View details +</span></summary><div className="inspector"><div><h3>LAST REQUEST</h3><pre>{trace ? `${trace.method} /api/db/${trace.path}\n${trace.request === undefined ? '' : JSON.stringify(trace.request,null,2)}` : 'Run an API action.'}</pre></div><div><h3>LAST RESPONSE</h3><pre>{trace ? JSON.stringify(trace.response,null,2) : 'No response yet.'}</pre></div></div></details><p className="field-note">Local admin playground. The backend uses a privileged Supabase key and selected user IDs are test-record scopes, not authenticated identities. Keep this endpoint local until application authentication is added. The other playgrounds do not automatically save here. Writes go to your configured Supabase project; no automatic retries or delete actions are exposed.</p><footer><span><i/> Preferences and conversations, remembered.</span><span>Vaachak × Supabase</span></footer></div></main></div>;
}
