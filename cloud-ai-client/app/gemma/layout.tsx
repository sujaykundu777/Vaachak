import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Vaachak · Gemma playground',
  description: 'Test Gemma text generation and multi-turn chat through the Vaachak APIs.',
};

export default function GemmaLayout({ children }: { children: React.ReactNode }) {
  return children;
}
