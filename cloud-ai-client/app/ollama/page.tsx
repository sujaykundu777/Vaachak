'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';

type Mode = 'generate' | 'chat' | 'embed';
type Message = { role: 'user' | 'assistant' | 'system'; content: string; thinking?: string };
type Model = { name: string; size?: number; capabilities?: string[]; details?: { parameter_size?: string; quantization_level?: string } };
type Result = { request: unknown; body: unknown; text: string; error: string; http: number; ms: number };
const titles: Record<Mode, string> = { generate: 'Generate text', chat: 'Conversation', embed: 'Embeddings' };
function object(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
function message(value: unknown): string { return typeof value === 'string' ? value : JSON.stringify(value); }

export default function OllamaPlayground() {
  const [mode, setMode] = useState<Mode>('generate');
  const [models, setModels] = useState<Model[]>([]);
  const [selected, setSelected] = useState<Record<Mode, string>>({ generate: '', chat: '', embed: '' });
  const model = selected[mode];
  const [loading, setLoading] = useState(true);
  const [connectionError, setConnectionError] = useState('');
  const [prompt, setPrompt] = useState('Explain how a reading companion can help someone with weak eyesight. Use three short sentences.');
  const [draft, setDraft] = useState('Hello! Help me understand what Vaachak does.');
  const [input, setInput] = useState('Vaachak makes reading accessible to everyone.');
  const [system, setSystem] = useState('');
  const [temperature, setTemperature] = useState('0.7');
  const [tokens, setTokens] = useState('256');
  const [keepAlive, setKeepAlive] = useState('5m');
  const [thinking, setThinking] = useState('default');
  const [history, setHistory] = useState<Message[]>([]);
  const [results, setResults] = useState<Partial<Record<Mode, Result>>>({});
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [validation, setValidation] = useState('');
  const [copyStatus, setCopyStatus] = useState('');
  const result = results[mode];
  const compatible = (entry: Model, target: Mode) => !entry.capabilities?.length || entry.capabilities.includes(target === 'embed' ? 'embedding' : 'completion');
  const available = models.filter(entry => compatible(entry, mode));
  const info = models.find(entry => entry.name === model);

  async function refreshModels() {
    setLoading(true); setConnectionError('');
    try {
      const response = await fetch('/api/ollama/tags');
      const body = object(await response.json());
      if (!response.ok || body.error) throw new Error(message(body.error || 'Could not list Ollama models.'));
      if (!Array.isArray(body.models)) throw new Error('Ollama returned an invalid model list.');
      const entries = body.models.filter((entry): entry is Model => typeof object(entry).name === 'string');
      setModels(entries);
      setSelected(previous => {
        const next = { ...previous };
        for (const target of ['generate', 'chat', 'embed'] as const) {
          const options = entries.filter(entry => compatible(entry, target));
          if (!options.some(entry => entry.name === previous[target])) next[target] = options[0]?.name || '';
        }
        return next;
      });
    } catch (error) { setConnectionError(error instanceof Error ? error.message : 'Could not connect to Ollama.'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void refreshModels(); }, []);

  function requestBody() {
    const options = { temperature: Number(temperature), num_predict: Number(tokens) };
    if (mode === 'embed') return { model, input, keep_alive: keepAlive };
    if (mode === 'generate') return { model, prompt: prompt.trim(), ...(system.trim() ? { system: system.trim() } : {}), stream: false, ...(thinking !== 'default' ? { think: thinking === 'on' } : {}), options, keep_alive: keepAlive };
    return { model, messages: [...(system.trim() ? [{ role: 'system', content: system.trim() }] : []), ...history, { role: 'user', content: draft.trim() }], stream: false, ...(thinking !== 'default' ? { think: thinking === 'on' } : {}), options, keep_alive: keepAlive };
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const content = (mode === 'generate' ? prompt : mode === 'chat' ? draft : input).trim();
    if (!model || !content) { setValidation('Choose an installed model and enter some text.'); return; }
    if (mode !== 'embed' && (!Number.isFinite(Number(temperature)) || Number(temperature) < 0 || Number(temperature) > 2 || !Number.isInteger(Number(tokens)) || Number(tokens) < 1 || Number(tokens) > 8192)) {
      setValidation('Temperature must be between 0 and 2, and the output limit must be 1–8192 tokens.'); return;
    }
    const target = mode;
    const request = requestBody();
    setValidation(''); setCopyStatus(''); pending.current = true; setBusy(true);
    const start = performance.now();
    try {
      const response = await fetch(`/api/ollama/${target}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
      const raw = await response.text(); let body: unknown;
      try { body = JSON.parse(raw); } catch { body = { error: raw || 'Empty Ollama response' }; }
      const data = object(body);
      const text = target === 'generate' ? data.response : target === 'chat' ? object(data.message).content : '';
      const output = typeof text === 'string' ? text : '';
      const vectors = data.embeddings;
      const valid = target === 'embed'
        ? Array.isArray(vectors) && vectors.length > 0 && vectors.every(vector => Array.isArray(vector) && vector.length > 0 && vector.every(value => typeof value === 'number' && Number.isFinite(value)))
        : !!output.trim();
      const error = !response.ok || data.error ? message(data.error || 'Ollama request failed.') : !valid ? 'No usable output was returned. Check the selected model supports this API, or increase the output token limit for a thinking model.' : '';
      setResults(previous => ({ ...previous, [target]: { request, body, text: output, error, http: response.status, ms: Math.round(performance.now() - start) } }));
      if (target === 'chat' && !error) {
        const thinking = object(data.message).thinking;
        setHistory(previous => [...previous, { role: 'user', content }, { role: 'assistant', content: output, ...(typeof thinking === 'string' ? { thinking } : {}) }]);
        setDraft('');
      }
    } catch (error) {
      const detail = error instanceof Error ? error.message : 'Request failed';
      setResults(previous => ({ ...previous, [target]: { request, body: { error: detail }, text: '', error: detail, http: 0, ms: Math.round(performance.now() - start) } }));
    } finally { pending.current = false; setBusy(false); }
  }

  function newChat() {
    if (pending.current) return;
    setHistory([]); setDraft(''); setResults(previous => ({ ...previous, chat: undefined })); setValidation('');
  }
  const response = object(result?.body);
  const embeddings = Array.isArray(response.embeddings) ? response.embeddings : [];
  const vector = Array.isArray(embeddings[0]) ? embeddings[0] : [];
  const rate = typeof response.eval_count === 'number' && typeof response.eval_duration === 'number' && response.eval_duration > 0 ? (response.eval_count / (response.eval_duration / 1e9)).toFixed(1) : null;

  return <div className="shell">
    <aside><a className="brand" href="/"><b>व</b>vaachak<span>.</span></a><div className="workspace">DEVELOPER WORKSPACE</div>
      <a className="nav nav-link nav-idle" href="/">◉ &nbsp; Voice playground <span>↗</span></a>
      <a className="nav nav-link nav-idle" href="/gemma">✧ &nbsp; Gemma playground <span>↗</span></a>
      <a className="nav nav-link" href="/ollama" aria-current="page">◎ &nbsp; Ollama playground <span>↗</span></a>
      <a className="nav nav-link nav-idle" href="/memory">▤ &nbsp; Memory playground <span>↗</span></a><div className="aside-note"><div className="little-wave">◎</div><h3>Your models. Your machine.</h3><p>A space to explore local inference with Ollama.</p></div><div className="version">VAACHAK LOCAL AI <span>v0.1</span></div>
    </aside>
    <main><header><span>Workspace <em>/</em><strong>Ollama playground</strong></span><button className={`health ${!connectionError && !loading ? 'ready' : ''}`} onClick={refreshModels} disabled={loading || busy || history.length > 0}><i/>{loading ? 'Connecting…' : connectionError ? 'Ollama unavailable' : `${models.length} models available`} ↻</button></header>
      <div className="page gemma-page"><div className="eyebrow">THE LOCAL AI LAB <span>POWERED BY OLLAMA</span></div><h1>A playground closer to home<span>.</span></h1><p className="intro">Choose an installed model. Try a prompt, a conversation, or an embedding.<br/>Test Ollama’s native APIs through your local frontend.</p>
        {connectionError && <div className="connection-error" role="alert"><strong>Could not connect to Ollama</strong><p>{connectionError}</p><button className="text-button" onClick={refreshModels} disabled={loading || busy}>Retry connection ↻</button></div>}
        {!loading && !connectionError && !models.length && <div className="connection-error"><strong>No models installed</strong><p>Install a model using <code>ollama pull &lt;model-name&gt;</code>, then refresh the model list.</p></div>}
        <div className="tabs" role="tablist" aria-label="Ollama APIs">{(['generate', 'chat', 'embed'] as const).map(target => <button key={target} role="tab" aria-selected={mode === target} disabled={busy} onClick={() => { setMode(target); setValidation(''); setCopyStatus(''); }}>{titles[target]} <small>{target === 'embed' ? 'VECTOR' : target.toUpperCase()}</small></button>)}</div>
        <div className="grid"><section className="card"><div className="card-head"><span className="step">01</span><div><h2>Choose your local model</h2><p>{mode === 'embed' ? 'Turn text into a vector for semantic search.' : mode === 'chat' ? 'Continue a conversation with its full history.' : 'Send a prompt to the model you choose.'}</p></div><span className="method">POST</span></div>
          <form onSubmit={submit}><label className="model-field">Installed model<select value={model} disabled={busy || loading || (mode === 'chat' && history.length > 0)} onChange={e => { setSelected(previous => ({ ...previous, [mode]: e.target.value })); setResults(previous => ({ ...previous, [mode]: undefined })); }} required>{!available.length && <option value="">No compatible models available</option>}{available.map(entry => <option key={entry.name} value={entry.name}>{entry.name}</option>)}</select></label>
            <div className="model-info">{info ? <><span>{info.size ? `${(info.size / 1e9).toFixed(2)} GB on disk` : 'Installed model'}</span>{info.details?.parameter_size && <span>{info.details.parameter_size}</span>}{info.details?.quantization_level && <span>{info.details.quantization_level}</span>}</> : <span>Refresh models to discover your Ollama installation.</span>}</div>
            {mode === 'chat' && history.length > 0 && <p className="field-note">Start a new chat to change the model or system instruction.</p>}
            <div className="label-row"><label htmlFor="ollama-input">{mode === 'chat' ? 'Your message' : mode === 'embed' ? 'Text to embed' : 'Your prompt'}</label></div><textarea id="ollama-input" required disabled={busy} value={mode === 'chat' ? draft : mode === 'embed' ? input : prompt} onChange={e => mode === 'chat' ? setDraft(e.target.value) : mode === 'embed' ? setInput(e.target.value) : setPrompt(e.target.value)}/>
            {mode !== 'embed' && <><label className="model-field system-field">System instruction (optional)<textarea value={system} onChange={e => setSystem(e.target.value)} disabled={busy || (mode === 'chat' && history.length > 0)} placeholder="Use simple, clear language."/></label><div className="fields"><label>Temperature<input type="number" min="0" max="2" step="0.1" value={temperature} onChange={e => setTemperature(e.target.value)} disabled={busy} required/></label><label>Output token limit<input type="number" min="1" max="8192" value={tokens} onChange={e => setTokens(e.target.value)} disabled={busy} required/></label></div></>}
            <label className="model-field">Thinking mode<select value={thinking} onChange={e => setThinking(e.target.value)} disabled={busy || mode === 'embed'}><option value="default">Model default</option><option value="off">Off (if supported)</option><option value="on">On (if supported)</option></select></label><p className="field-note">Thinking can consume the output token budget before an answer appears. Support depends on the model.</p><label className="model-field">Keep model loaded<select value={keepAlive} onChange={e => setKeepAlive(e.target.value)} disabled={busy}><option value="5m">5 minutes after request</option><option value="1m">1 minute after request</option><option value="0">Unload after request</option></select></label>
            {validation && <p className="error" role="alert">{validation}</p>}<button className="primary gemma-submit" disabled={busy || loading || !!connectionError || !model} type="submit">{busy ? 'Waiting for your local model…' : mode === 'chat' ? 'Send message' : mode === 'embed' ? 'Generate embedding' : 'Generate response'} <span>{busy ? '◌' : '→'}</span></button>{mode === 'chat' && <button className="secondary" type="button" onClick={newChat} disabled={busy}>New chat</button>}<p className="endpoint">Ollama /api/{mode}</p>
          </form></section>
          <section className="card output"><div className="card-head"><span className="step">02</span><div><h2>{mode === 'chat' ? 'Your conversation' : mode === 'embed' ? 'Your embedding' : 'Your local response'}</h2><p>Output from the selected Ollama model.</p></div>{result && !busy && <span className={result.error ? 'badge error' : 'badge'}>{result.error ? 'Error' : 'Success'}</span>}</div>
            <div className="result gemma-result" aria-live="polite" aria-busy={busy}>{mode === 'chat' && !!history.length && <div className="conversation">{history.map((entry, index) => <article className={`message ${entry.role === 'assistant' ? 'model' : 'user'}`} key={index}><span className="result-label">{entry.role === 'assistant' ? 'OLLAMA' : 'YOU'}</span><p>{entry.content}</p></article>)}</div>}
              {busy ? <div className="empty"><div className="thinking">◎</div><h3>Working on your machine…</h3><p>The first request may need to load the model.{'\n'}Larger models can take a few minutes.</p></div> : result?.error ? <div className="failure" role="alert"><h3>Request failed</h3><p>{result.error}</p><p>Your input is preserved for another attempt.</p></div> : result && mode === 'embed' ? <div className="success"><span className="result-label">EMBEDDING VECTOR</span><h3>{vector.length.toLocaleString()} dimensions</h3><p>{embeddings.length} vector(s) returned</p><pre>{JSON.stringify(vector.slice(0, 16), null, 2)}</pre><p className="field-note">First 16 values shown. The full vector is in the response inspector.</p></div> : result && mode === 'generate' ? <div className="success"><span className="result-label">MODEL RESPONSE</span><p>{result.text}</p><button className="text-button" onClick={async () => { try { await navigator.clipboard.writeText(result.text); setCopyStatus('Copied'); } catch { setCopyStatus('Select the text to copy it.'); } }}>Copy response</button><span className="copy-feedback" role="status">{copyStatus}</span></div> : !history.length || mode !== 'chat' ? <div className="empty"><div className="thinking">◎</div><h3>Local intelligence, ready to explore</h3><p>Choose a model and run a request.{'\n'}Its response will appear here.</p></div> : null}
            </div><div className="result-footer"><span>{result ? `HTTP ${result.http || '—'} · ${result.ms.toLocaleString()} ms` : 'Ready when you are'}</span><span>{rate ? `${rate} tokens/s` : '◎ Ollama'}</span></div>
            {result && !result.error && <div className="ollama-metrics"><span>Model load: {typeof response.load_duration === 'number' ? `${(response.load_duration / 1e9).toFixed(2)} s` : '—'}</span><span>Output tokens: {typeof response.eval_count === 'number' ? response.eval_count : '—'}</span></div>}
          </section></div>
        <details><summary><span>⌘ &nbsp; Request & response inspector</span><span>View details +</span></summary><div className="inspector"><div><h3>NEXT REQUEST · POST /api/{mode}</h3><pre>{JSON.stringify(requestBody(), null, 2)}</pre>{result && <><h3>LAST SENT REQUEST</h3><pre>{JSON.stringify(result.request, null, 2)}</pre></>}</div><div><h3>LAST RESPONSE</h3><pre>{result ? JSON.stringify(result.body, null, 2) : 'Run a request to inspect its response.'}</pre></div></div></details>
        <p className="field-note">Connects to OLLAMA_API_URL (default: 127.0.0.1:11434) from the Next.js server. No Google or Sarvam key required. Only installed models are listed; inference location follows your Ollama configuration. Responses stay in page memory until reload. Requests wait for a complete response; there are no automatic retries.</p><footer><span><i/> Built for testing. Designed for conversations.</span><span>Vaachak × Ollama</span></footer>
      </div></main>
  </div>;
}
