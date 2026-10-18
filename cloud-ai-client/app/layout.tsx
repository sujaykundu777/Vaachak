import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = { title: 'Vaachak · Voice playground', description: 'Test the Vaachak Sarvam speech APIs.' };
export default function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
