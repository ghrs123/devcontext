'use client';

import { useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import useSWR from 'swr';

import { Sidebar } from '@/components/app/Sidebar';
import { TopBar } from '@/components/app/TopBar';
import { clearToken } from '@/lib/auth';
import { api } from '@/lib/api';
import { useT } from '@/lib/i18n/I18nProvider';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

const TITLE_KEYS: Array<{ prefix: string; key: TranslationKey }> = [
  { prefix: '/dashboard', key: 'nav.overview' },
  { prefix: '/products', key: 'nav.products' },
  { prefix: '/settings', key: 'nav.settings' }
];

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const t = useT();
  const [mobileOpen, setMobileOpen] = useState(false);

  const { data: profile } = useSWR('store-profile', api.getProfile, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  const title = useMemo(() => {
    const match = TITLE_KEYS.find((entry) => pathname.startsWith(entry.prefix));
    return match ? t(match.key) : t('nav.overview');
  }, [pathname, t]);

  const handleLogout = () => {
    clearToken();
    router.push('/login');
  };

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-[16rem_1fr]">
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} profile={profile} />

      <div className="flex min-w-0 flex-col">
        <TopBar title={title} signOutLabel={t('nav.signOut')} onMenuToggle={() => setMobileOpen(true)} onLogout={handleLogout} />
        <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 lg:px-8">{children}</div>
      </div>
    </div>
  );
}
