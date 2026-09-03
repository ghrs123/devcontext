import Link from 'next/link';

import { LogoMark, Wordmark } from '@/components/brand/Logo';

type AuthShellProps = {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
};

const PROOF_POINTS = [
  'Body-aware size recommendations from height & weight',
  'Upload or scrape brand size charts in seconds',
  'Confidence scoring on every match — no more guesswork'
];

export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      {/* Brand panel */}
      <aside className="relative hidden overflow-hidden bg-[hsl(243_64%_18%)] text-white lg:flex lg:flex-col lg:justify-between">
        <div className="bg-grid absolute inset-0 opacity-70" />
        <div
          className="absolute -left-24 -top-24 h-96 w-96 rounded-full opacity-40 blur-3xl"
          style={{ background: 'radial-gradient(closest-side, hsl(243 80% 60%), transparent)' }}
        />
        <div
          className="absolute -bottom-24 right-0 h-80 w-80 rounded-full opacity-30 blur-3xl"
          style={{ background: 'radial-gradient(closest-side, hsl(199 82% 55%), transparent)' }}
        />

        <div className="relative z-10 p-10">
          <Link href="/" className="inline-flex items-center gap-2.5">
            <LogoMark className="h-9 w-9" />
            <span className="text-lg font-semibold tracking-tight">
              Fit<span className="text-[hsl(243_90%_84%)]">Vision</span>
            </span>
          </Link>
        </div>

        <div className="relative z-10 max-w-md p-10">
          <p className="text-2xl font-semibold leading-snug tracking-tight">
            Turn returns into confident purchases.
          </p>
          <ul className="mt-8 space-y-4">
            {PROOF_POINTS.map((point) => (
              <li key={point} className="flex items-start gap-3 text-sm text-white/80">
                <span className="mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/12 text-[hsl(243_90%_86%)]">
                  ✓
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>

        <div className="relative z-10 p-10 text-xs text-white/45">
          © {new Date().getFullYear()} FitVision · Multi-tenant size intelligence
        </div>
      </aside>

      {/* Form panel */}
      <main className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm animate-fade-in">
          <div className="lg:hidden">
            <Wordmark className="mb-8" />
          </div>

          <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-1.5 text-sm text-muted-foreground">{subtitle}</p>

          <div className="mt-7">{children}</div>

          <div className="mt-6 text-sm text-muted-foreground">{footer}</div>
        </div>
      </main>
    </div>
  );
}
