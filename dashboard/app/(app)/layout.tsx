'use client';

import { useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import useSWR from 'swr';

import { Sidebar } from '@/components/app/Sidebar';
import { TopBar } from '@/components/app/TopBar';
import { clearToken } from '@/lib/auth';
import { api } from '@/lib/api';

const TITLES: Record<string, string> = {
  '/dashboard': 'Overview',
  '/products': 'Products',
  '/settings': 'Settings'
};

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  const { data: profile } = useSWR('store-profile', api.getProfile, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  const title = useMemo(() => {
    const key = Object.keys(TITLES).find((k) => pathname.startsWith(k));
    return key ? TITLES[key] : 'Dashboard';
  }, [pathname]);

  const handleLogout = () => {
    clearToken();
    router.push('/login');
  };

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-[16rem_1fr]">
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} profile={profile} />

      <div className="flex min-w-0 flex-col">
        <TopBar title={title} onMenuToggle={() => setMobileOpen(true)} onLogout={handleLogout} />
        <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 lg:px-8">{children}</div>
      </div>
    </div>
  );
}
