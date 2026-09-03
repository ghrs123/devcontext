'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  BarChart3,
  Building2,
  HeartPulse,
  LayoutDashboard,
  LogOut,
  ShieldCheck,
  Store,
  Tags,
  X
} from 'lucide-react';

import { LogoMark } from '@/components/brand/Logo';
import { LanguageSwitcher } from '@/components/app/LanguageSwitcher';
import { useT } from '@/lib/i18n/I18nProvider';
import { cn } from '@/lib/utils';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

type AdminSidebarProps = {
  mobileOpen: boolean;
  onClose: () => void;
  onLogout: () => void;
};

const NAV: Array<{ href: string; key: TranslationKey; icon: typeof BarChart3 }> = [
  { href: '/admin/dashboard', key: 'admin.nav.overview', icon: LayoutDashboard },
  { href: '/admin/stores', key: 'admin.nav.stores', icon: Building2 },
  { href: '/admin/brands', key: 'admin.nav.brands', icon: Tags },
  { href: '/admin/recommendations', key: 'admin.nav.recommendations', icon: BarChart3 },
  { href: '/admin/health', key: 'admin.nav.health', icon: HeartPulse }
];

export function AdminSidebar({ mobileOpen, onClose, onLogout }: Readonly<AdminSidebarProps>) {
  const pathname = usePathname();
  const t = useT();

  return (
    <>
      {mobileOpen ? (
        <button
          className="fixed inset-0 z-40 bg-[hsl(222_50%_6%/0.7)] backdrop-blur-sm lg:hidden"
          onClick={onClose}
          aria-label="Close admin sidebar overlay"
        />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 flex-col bg-[hsl(222_44%_11%)] text-slate-200 transition-transform duration-200',
          'lg:static lg:z-auto lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <span className="inline-flex items-center gap-2">
            <LogoMark className="h-6 w-6" />
            <span className="text-[0.95rem] font-semibold tracking-tight text-white">FitVision</span>
          </span>
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-white/5 lg:hidden"
            onClick={onClose}
            aria-label="Close admin menu"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="mx-3 flex items-center gap-2 rounded-md bg-white/[0.06] px-3 py-2 text-xs font-medium text-[hsl(199_90%_78%)]">
          <ShieldCheck className="h-3.5 w-3.5" />
          {t('admin.mode')}
        </div>

        <nav className="flex-1 space-y-0.5 px-3 py-4">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onClose}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  active
                    ? 'bg-[hsl(222_82%_58%/0.18)] text-white'
                    : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
                )}
              >
                <Icon className={cn('h-4 w-4', active ? 'text-[hsl(199_90%_78%)]' : 'text-slate-500')} />
                {t(item.key)}
              </Link>
            );
          })}
        </nav>

        <div className="space-y-3 border-t border-white/10 p-3">
          <div className="flex items-center justify-between px-1 text-[0.7rem] font-semibold uppercase tracking-wider text-slate-500">
            {t('nav.language')}
            <LanguageSwitcher className="border-white/10 bg-white/5" />
          </div>

          <Link
            href="/dashboard"
            className="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-400 hover:bg-white/5 hover:text-slate-100"
          >
            <Store className="h-4 w-4" />
            {t('admin.backToStore')}
          </Link>

          <button
            type="button"
            onClick={onLogout}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-slate-400 hover:bg-white/5 hover:text-slate-100"
          >
            <LogOut className="h-4 w-4" />
            {t('nav.signOut')}
          </button>
        </div>
      </aside>
    </>
  );
}
