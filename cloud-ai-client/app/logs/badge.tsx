'use client';
import Link from 'next/link';
import { useApiLogs } from '../lib/use-api-logs';
export default function LogBadge() {
  const state = useApiLogs();
  return <Link href="/logs" className="log-badge" aria-label={`Open API logs, ${state.calls.length} calls captured`}><i className={state.recording ? 'recording' : ''}/>{state.recording ? 'Recording' : 'Paused'} · {state.calls.length} API calls ↗</Link>;
}
