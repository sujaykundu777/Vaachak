'use client';
import Link from 'next/link';
import { playgroundFetch } from '../lib/api-log-store';

import ImageToTextPanel from './image-to-text';
import { useEffect, useRef, useState, type FormEvent } from 'react';

type Mode = 'generateContent' | 'chat' | 'imageToText';
type Message = { role: 'user' | 'model'; content: string };
type ApiResult = {
  body: unknown;
  request: { model: string; prompt?: string; messages?: Message[] };
  status: number;
  ms: number;
  error: boolean;
  text: string;
  detail: string;
};

function object(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : {};
}

export default function GemmaPlayground() {
  const [mode, setMode] = useState<Mode>('generateContent');
  const [models, setModels] = useState<Record<Mode, string>>({
    generateContent: 'gemma-4-26b-a4b-it', chat: 'gemma-4-26b-a4b-it', imageToText: 'gemma-4-26b-a4b-it',
  });
  const model = models[mode];
  function setModel(value: string) { setModels(previous => ({ ...previous, [mode]: value })); }
  const [prompt, setPrompt] = useState('Explain how Vaachak can help someone with weak eyesight read a newspaper. Use three short, simple sentences.');
  const [draft, setDraft] = useState('Hello! Can you help me understand what Vaachak does?');
  const [messages, setMessages] = useState<Message[]>([]);
  const [results, setResults] = useState<Partial<Record<Mode, ApiResult>>>({});
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [validation, setValidation] = useState('');
  const [health, setHealth] = useState('Checking connection…');
  const [checking, setChecking] = useState(false);
  const [copied, setCopied] = useState('');
  const result = results[mode];

  async function checkHealth() {
    setChecking(true);
    try {
      const response = await playgroundFetch('/api/backend/health');
      const body = object(await response.json());
      setHealth(response.ok
        ? object(body.services).gemini ? 'Google API ready' : 'Google API key missing'
        : 'Backend unavailable');
    } catch { setHealth('Backend unavailable'); }
    finally { setChecking(false); }
  }

  useEffect(() => { void checkHealth(); }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const content = (mode === 'chat' ? draft : prompt).trim();
    if (!content || !model.trim()) {
      setValidation('Enter a model and a non-empty prompt or message.');
      return;
    }
    setValidation('');
    setCopied('');
    const target = mode;
    const nextMessages: Message[] = [...messages, { role: 'user', content }];
    const request = target === 'chat'
      ? { model: model.trim(), messages: nextMessages }
      : { model: model.trim(), prompt: content };
    pending.current = true;
    setBusy(true);
    const start = performance.now();
    try {
      const response = await playgroundFetch(`/api/backend/gemini/${target}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });
      const raw = await response.text();
      let body: unknown;
      try { body = JSON.parse(raw); }
      catch { body = { detail: raw || 'The backend returned an empty response.' }; }
      const envelope = object(body);
      const data = object(envelope.data);
      const generated = target === 'chat' ? data.response : data.generated_text;
      const text = typeof generated === 'string' ? generated : '';
      const error = !response.ok || envelope.status === 'error' || !text.trim();
      const detail = envelope.detail ?? envelope.error ?? envelope.message ?? 'The backend returned no generated text. Inspect the raw response below.';
      setResults(previous => ({ ...previous, [target]: {
        body, request, status: response.status,
        ms: Math.round(performance.now() - start), error, text,
        detail: typeof detail === 'string' ? detail : JSON.stringify(detail),
      } }));
      if (target === 'chat' && !error) {
        setMessages([...nextMessages, { role: 'model', content: text }]);
        setDraft('');
      }
    } catch (error) {
      setResults(previous => ({ ...previous, [target]: {
        body: { detail: error instanceof Error ? error.message : 'Request failed' },
        request, status: 0, ms: Math.round(performance.now() - start),
        error: true, text: '', detail: 'Could not complete the request. Check the connection and try again.',
      } }));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function resetChat() {
    if (pending.current) return;
    setMessages([]);
    setDraft('');
    setResults(previous => ({ ...previous, chat: undefined }));
    setValidation('');
  }

  const preview = mode === 'chat'
    ? { model, messages: [...messages, ...(draft.trim() ? [{ role: 'user', content: draft.trim() }] : [])] }
    : { model, prompt };

  return <div className="shell">
    <aside>
      <Link className="brand" href="/"><b>व</b>vaachak<span>.</span></Link>
      <div className="workspace">DEVELOPER WORKSPACE</div>
      <Link className="nav nav-link nav-idle" href="/">◉ &nbsp; Voice playground <span>↗</span></Link>
      <Link className="nav nav-link" href="/gemma" aria-current="page">✧ &nbsp; Gemma playground <span>↗</span></Link>
      <Link className="nav nav-link nav-idle" href="/ollama">◎ &nbsp; Ollama playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/memory">▤ &nbsp; Memory playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/database">▦ &nbsp; Database playground <span>↗</span></Link><Link className="nav nav-link nav-idle" href="/logs">≋ &nbsp; Log playground <span>↗</span></Link><div className="aside-note"><div className="little-wave">✧</div><h3>A little space to think.</h3><p>Explore prompts and conversations with Vaachak’s Gemma integration.</p></div>
      <div className="version">VAACHAK CLOUD AI <span>v0.1</span></div>
    </aside>
    <main>
      <header><span>Workspace <em>/</em><strong>Gemma playground</strong></span><button className={`health ${health === 'Google API ready' ? 'ready' : ''}`} onClick={checkHealth} disabled={checking}><i/>{checking ? 'Checking…' : health} ↻</button></header>
      <div className="page gemma-page">
        <div className="eyebrow">THE GEMMA LAB <span>GEMMA 4 INTEGRATION</span></div>
        <h1>Make room for a good conversation<span>.</span></h1>
        <p className="intro">Try a prompt, explore a follow-up, and see exactly what the API returns.<br/>Test text generation, chat, and image-to-text extraction in one place.</p>
        <div className="tabs" role="tablist" aria-label="Gemma APIs">
          <button role="tab" aria-selected={mode === 'generateContent'} disabled={busy} onClick={() => { setMode('generateContent'); setValidation(''); }}>✧ &nbsp; Generate text <small>PROMPT</small></button>
          <button role="tab" aria-selected={mode === 'chat'} disabled={busy} onClick={() => { setMode('chat'); setValidation(''); }}>☷ &nbsp; Conversation <small>CHAT</small></button>
          <button role="tab" aria-selected={mode === 'imageToText'} disabled={busy} onClick={() => { setMode('imageToText'); setValidation(''); }}>▧ &nbsp; Image to text <small>OCR</small></button>
        </div>
        {mode === 'imageToText' ? <ImageToTextPanel/> : <>
        <div className="grid">
          <section className="card">
            <div className="card-head"><span className="step">01</span><div><h2>{mode === 'chat' ? 'Keep the conversation going' : 'Start with a question'}</h2><p>{mode === 'chat' ? 'Each successful turn is included in the next request.' : 'Give Gemma a prompt to work with.'}</p></div><span className="method">POST</span></div>
            <form onSubmit={submit}>
              <label className="model-field">Model<input value={model} onChange={e => { setModel(e.target.value); }} required disabled={busy || (mode === 'chat' && messages.length > 0)} spellCheck={false}/></label>
              <p className="field-note">{mode === 'chat' && messages.length ? 'Start a new chat to change models.' : 'Defaults to the model configured in the Python backend. Edit to test another model ID.'}</p>
              <div className="label-row"><label htmlFor="gemma-prompt">{mode === 'chat' ? 'Your message' : 'Your prompt'}</label>{mode === 'generateContent' && <button className="text-button" type="button" disabled={busy} onClick={() => setPrompt('Explain what an AI reading companion does in simple Hindi. Keep your answer to three short sentences.')}>Try Hindi prompt ↗</button>}</div>
              <textarea id="gemma-prompt" value={mode === 'chat' ? draft : prompt} onChange={e => mode === 'chat' ? setDraft(e.target.value) : setPrompt(e.target.value)} placeholder={mode === 'chat' ? 'Ask a follow-up question…' : 'What would you like to explore?'} disabled={busy} required/>
              <div className="meta"><span>{mode === 'chat' ? `${messages.length / 2} completed turns` : 'Plain text · Unicode supported'}</span><span>{(mode === 'chat' ? draft : prompt).length} characters</span></div>
              {validation && <p className="error" role="alert">{validation}</p>}
              <button className="primary gemma-submit" disabled={busy} type="submit">{busy ? 'Waiting for Gemma…' : mode === 'chat' ? 'Send message' : 'Generate response'} <span>{busy ? '◌' : '→'}</span></button>
              {mode === 'chat' && <button className="secondary" type="button" onClick={resetChat} disabled={busy}>New chat</button>}
              <p className="endpoint">/api/gemini/{mode}</p>
            </form>
          </section>
          <section className="card output">
            <div className="card-head"><span className="step">02</span><div><h2>{mode === 'chat' ? 'Your conversation' : 'Read the response'}</h2><p>{mode === 'chat' ? 'User and model turns, in order.' : 'The generated text from your backend.'}</p></div>{result && !busy && <span className={result.error ? 'badge error' : 'badge'}>{result.error ? 'Error' : 'Success'}</span>}</div>
            <div className="result gemma-result" aria-live="polite" aria-busy={busy}>
              {mode === 'chat' && messages.length > 0 && <div className="conversation">{messages.map((message, index) => <article className={`message ${message.role}`} key={index}><span className="result-label">{message.role === 'user' ? 'YOU' : 'GEMMA'}</span><p>{message.content}</p></article>)}</div>}
              {busy ? <div className="empty"><div className="thinking">✧</div><h3>Gemma is thinking…</h3><p>Waiting for the backend response.</p></div>
                : result?.error ? <div className="failure" role="alert"><h3>Request failed</h3><p>{result.detail}</p><p>Your input is preserved. You can edit it and send again.</p></div>
                : mode === 'generateContent' && result ? <div className="success"><span className="result-label">GEMMA RESPONSE</span><p>{result.text}</p><button className="text-button" onClick={async () => { try { await navigator.clipboard.writeText(result.text); setCopied('Copied'); } catch { setCopied('Select the response text to copy it.'); } }}>Copy response</button><span className="copy-feedback" role="status">{copied}</span></div>
                : !messages.length && <div className="empty"><div className="thinking">✧</div><h3>A fresh perspective starts here</h3><p>{mode === 'chat' ? 'Send your first message to begin.\nFollow-up questions keep the context.' : 'Write a prompt and generate a response.\nGemma’s answer will appear here.'}</p></div>}
            </div>
            <div className="result-footer"><span>{result ? `HTTP ${result.status || '—'} · ${result.ms.toLocaleString()} ms` : 'Ready when you are'}</span><span>✧ Gemma</span></div>
          </section>
        </div>
        <details><summary><span>⌘ &nbsp; Request & response inspector</span><span>View details +</span></summary><div className="inspector"><div><h3>NEXT REQUEST · POST /api/gemini/{mode}</h3><pre>{JSON.stringify(preview, null, 2)}</pre>{result && <><h3>LAST SENT REQUEST</h3><pre>{JSON.stringify(result.request, null, 2)}</pre></>}</div><div><h3>LAST RESPONSE</h3><pre>{result ? JSON.stringify(result.body, null, 2) : 'Run a request to inspect its response.'}</pre></div></div></details>
        </>}
        <p className="field-note">Uses GOOGLE_API_KEY on the Python server. Requests are real and may use provider quota. Chat history stays in this page’s memory and clears on reload. Generation settings are managed by the backend.</p>
        <footer><span><i/> Built for testing. Designed for conversations.</span><span>Vaachak × Gemma</span></footer>
      </div>
    </main>
  </div>;
}
