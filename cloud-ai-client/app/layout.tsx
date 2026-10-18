import type { Metadata } from 'next';
import './globals.css';
import LogBadge from './logs/badge';
export const metadata: Metadata = { title: 'Vaachak · Voice playground', description: 'Test the Vaachak Sarvam speech APIs.' };
export default function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}<LogBadge/></body></html>;
}
