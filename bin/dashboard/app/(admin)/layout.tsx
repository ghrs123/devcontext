'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Menu } from 'lucide-react';

import { AdminSidebar } from '@/components/admin/AdminSidebar';
import { Button } from '@/components/ui/button';
import { clearToken } from '@/lib/auth';

export default function AdminLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const [mobileOpen, setMobileOpen] = useState(false);

  function handleLogout() {
    clearToken();
    router.push('/login');
  }

  return (
    <div className="min-h-screen bg-slate-100 lg:grid lg:grid-cols-[18rem_1fr]">
      <AdminSidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} onLogout={handleLogout} />

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-slate-300 text-slate-600 lg:hidden"
              aria-label="Open admin menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div>
              <p className="text-xs uppercase tracking-[0.2em] text-slate-500">Control Panel</p>
              <p className="text-sm font-semibold text-slate-900">FitVision Admin</p>
            </div>
          </div>

          <Button type="button" variant="outline" size="sm" onClick={handleLogout}>
            Logout
          </Button>
        </header>

        <div className="px-4 py-6 sm:px-6">{children}</div>
      </div>
    </div>
  );
}
