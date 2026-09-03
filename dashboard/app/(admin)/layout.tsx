'use client';

import { useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Menu } from 'lucide-react';

import { AdminSidebar } from '@/components/admin/AdminSidebar';
import { Button } from '@/components/ui/button';
import { clearToken } from '@/lib/auth';
import { useT } from '@/lib/i18n/I18nProvider';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

const TITLE_KEYS: Array<{ prefix: string; key: TranslationKey }> = [
  { prefix: '/admin/dashboard', key: 'admin.nav.overview' },
  { prefix: '/admin/stores', key: 'admin.nav.stores' },
  { prefix: '/admin/brands', key: 'admin.nav.brands' },
  { prefix: '/admin/recommendations', key: 'admin.nav.recommendations' },
  { prefix: '/admin/health', key: 'admin.nav.health' }
];

export default function AdminLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const pathname = usePathname();
  const t = useT();
  const [mobileOpen, setMobileOpen] = useState(false);

  function handleLogout() {
    clearToken();
    router.push('/login');
  }

  const title = useMemo(() => {
    const match = TITLE_KEYS.find((entry) => pathname.startsWith(entry.prefix));
    return match ? t(match.key) : t('admin.title');
  }, [pathname, t]);

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-[18rem_1fr]">
      <AdminSidebar mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} onLogout={handleLogout} />

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-md sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border-strong text-muted-foreground hover:bg-muted lg:hidden"
              aria-label="Open admin menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="leading-tight">
              <p className="text-[0.65rem] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                {t('admin.controlPanel')}
              </p>
              <p className="text-[0.95rem] font-semibold tracking-tight">{title}</p>
            </div>
          </div>

          <Button type="button" variant="ghost" size="sm" onClick={handleLogout}>
            {t('nav.signOut')}
          </Button>
        </header>

        <div className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 lg:px-8">{children}</div>
      </div>
    </div>
  );
}
