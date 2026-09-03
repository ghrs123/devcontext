import type { Metadata } from 'next';
import { cookies } from 'next/headers';

import { Toaster } from '@/components/ui/toaster';
import { I18nProvider } from '@/lib/i18n/I18nProvider';
import { DEFAULT_LOCALE, LOCALE_COOKIE, isLocale } from '@/lib/i18n/config';
import './globals.css';

export const metadata: Metadata = {
  title: 'FitVision — O tamanho certo, antes da compra',
  description:
    'Plataforma de recomendação de tamanhos para lojas de moda online: produtos, tabelas de tamanhos e análise de recomendações.',
  icons: {
    icon: '/favicon.svg'
  }
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const cookieLocale = cookies().get(LOCALE_COOKIE)?.value;
  const locale = isLocale(cookieLocale) ? cookieLocale : DEFAULT_LOCALE;

  return (
    <html lang={locale}>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="min-h-screen bg-background font-sans text-foreground antialiased">
        <I18nProvider initialLocale={locale}>
          {children}
          <Toaster />
        </I18nProvider>
      </body>
    </html>
  );
}
