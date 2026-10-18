'use client';
import { useSyncExternalStore } from 'react';
import { apiLogs, EMPTY_LOG_STATE } from './api-log-store';
export function useApiLogs() { return useSyncExternalStore(apiLogs.subscribe, apiLogs.getSnapshot, () => EMPTY_LOG_STATE); }
