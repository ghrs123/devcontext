export const LOCALES = ['pt', 'en'] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = 'pt';
export const LOCALE_COOKIE = 'fitvision_locale';

export const LOCALE_LABELS: Record<Locale, string> = {
  pt: 'Português',
  en: 'English'
};

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (LOCALES as readonly string[]).includes(value);
}
