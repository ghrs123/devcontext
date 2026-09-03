'use client';

import { Globe } from 'lucide-react';

import { LOCALES, LOCALE_LABELS } from '@/lib/i18n/config';
import { useI18n } from '@/lib/i18n/I18nProvider';
import { cn } from '@/lib/utils';

export function LanguageSwitcher({ className }: { className?: string }) {
  const { locale, setLocale } = useI18n();

  return (
    <div
      className={cn(
        'inline-flex items-center gap-1 rounded-md border border-border bg-card p-0.5 text-xs',
        className
      )}
    >
      <Globe className="ml-1 h-3.5 w-3.5 text-muted-foreground" />
      {LOCALES.map((l) => (
        <button
          key={l}
          type="button"
          onClick={() => setLocale(l)}
          aria-pressed={locale === l}
          title={LOCALE_LABELS[l]}
          className={cn(
            'rounded px-1.5 py-0.5 font-medium uppercase transition-colors',
            locale === l
              ? 'bg-primary-soft text-primary-soft-foreground'
              : 'text-muted-foreground hover:text-foreground'
          )}
        >
          {l}
        </button>
      ))}
    </div>
  );
}
