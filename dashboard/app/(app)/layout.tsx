'use client';

import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import useSWR from 'swr';

import { Sidebar } from '@/components/app/Sidebar';
import { TopBar } from '@/components/app/TopBar';
import { clearToken } from '@/lib/auth';
import { api } from '@/lib/api';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);

  const { data: profile } = useSWR('store-profile', api.getProfile, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  const storeName = useMemo(() => {
    if (!profile?.name) {
      return 'Loading store...';
    }
    return profile.name;
  }, [profile?.name]);

  const handleLogout = () => {
    clearToken();
    router.push('/login');
  };

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-[16rem_1fr]">
      <Sidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />

      <div className="min-w-0">
        <TopBar storeName={storeName} onMenuToggle={() => setMobileOpen(true)} onLogout={handleLogout} />
        <div className="px-4 py-6 sm:px-6">{children}</div>
      </div>
    </div>
  );
}
