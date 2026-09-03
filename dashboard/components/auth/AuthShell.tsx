'use client';

import Link from 'next/link';

import { LogoMark } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/app/LanguageSwitcher';
import { useT } from '@/lib/i18n/I18nProvider';

type AuthShellProps = {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
};

export function AuthShell({ title, subtitle, children, footer }: AuthShellProps) {
  const t = useT();
  const points = [t('auth.brand.point1'), t('auth.brand.point2'), t('auth.brand.point3')];

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      {/* Brand panel */}
      <aside className="relative hidden overflow-hidden bg-[hsl(222_68%_16%)] text-white lg:flex lg:flex-col lg:justify-between">
        <div className="bg-grid absolute inset-0 opacity-70" />
        <div
          className="absolute -left-24 -top-24 h-96 w-96 rounded-full opacity-45 blur-3xl"
          style={{ background: 'radial-gradient(closest-side, hsl(222 82% 58%), transparent)' }}
        />
        <div
          className="absolute -bottom-24 right-0 h-80 w-80 rounded-full opacity-30 blur-3xl"
          style={{ background: 'radial-gradient(closest-side, hsl(189 78% 52%), transparent)' }}
        />

        <div className="relative z-10 p-10">
          <Link href="/" className="inline-flex items-center gap-2.5">
            <LogoMark className="h-8 w-8" />
            <span className="text-lg font-semibold tracking-tight">FitVision</span>
          </Link>
        </div>

        <div className="relative z-10 max-w-md p-10">
          <p className="text-[1.7rem] font-semibold leading-tight tracking-tight">
            {t('auth.brand.tagline1')}
            <br />
            <span className="text-[hsl(199_90%_78%)]">{t('auth.brand.tagline2')}</span>
          </p>
          <ul className="mt-8 space-y-4">
            {points.map((point) => (
              <li key={point} className="flex items-start gap-3 text-sm text-white/80">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/12 text-[hsl(199_90%_82%)]">
                  ✓
                </span>
                {point}
              </li>
            ))}
          </ul>
        </div>

        <div className="relative z-10 p-10 text-xs text-white/45">© {new Date().getFullYear()} FitVision</div>
      </aside>

      {/* Form panel */}
      <main className="relative flex items-center justify-center px-6 py-12">
        <div className="absolute right-5 top-5">
          <LanguageSwitcher />
        </div>

        <div className="w-full max-w-sm animate-fade-in">
          <div className="mb-8 flex items-center gap-2 lg:hidden">
            <LogoMark className="h-6 w-6" />
            <span className="text-[0.975rem] font-semibold tracking-tight">FitVision</span>
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
