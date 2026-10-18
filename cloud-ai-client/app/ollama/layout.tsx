import type { Metadata } from 'next';
export const metadata: Metadata = { title: 'Vaachak · Ollama playground', description: 'Test local Ollama models, generation, chat, and embeddings.' };
export default function Layout({ children }: { children: React.ReactNode }) { return children; }
