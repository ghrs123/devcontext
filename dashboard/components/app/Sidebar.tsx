'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { BarChart3, Package, Settings, X } from 'lucide-react';

import { Wordmark } from '@/components/brand/Logo';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { StoreProfile } from '@/lib/types';

type SidebarProps = {
  mobileOpen: boolean;
  onClose: () => void;
  profile?: StoreProfile;
};

const NAV = [
  { href: '/dashboard', label: 'Overview', icon: BarChart3 },
  { href: '/products', label: 'Products', icon: Package },
  { href: '/settings', label: 'Settings', icon: Settings }
];

function planVariant(plan?: string) {
  switch ((plan || '').toUpperCase()) {
    case 'FREE':
      return 'neutral' as const;
    case 'STARTER':
      return 'primary' as const;
    default:
      return 'success' as const;
  }
}

export function Sidebar({ mobileOpen, onClose, profile }: Readonly<SidebarProps>) {
  const pathname = usePathname();

  return (
    <>
      {mobileOpen ? (
        <button
          className="fixed inset-0 z-40 bg-[hsl(224_40%_10%/0.45)] backdrop-blur-sm lg:hidden"
          onClick={onClose}
          aria-label="Close sidebar overlay"
        />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border bg-surface transition-transform duration-200',
          'lg:static lg:z-auto lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <Wordmark />
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted lg:hidden"
            onClick={onClose}
            aria-label="Close menu"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="flex-1 space-y-0.5 px-3 py-4">
          <p className="px-3 pb-2 text-[0.7rem] font-semibold uppercase tracking-wider text-muted-foreground">
            Store
          </p>
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onClose}
                className={cn(
                  'group flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  active
                    ? 'bg-primary-soft text-primary-soft-foreground'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                )}
              >
                <Icon
                  className={cn('h-4 w-4', active ? 'text-primary' : 'text-muted-foreground/80')}
                />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-border p-3">
          <div className="rounded-lg border border-border bg-card p-3">
            <p className="truncate text-sm font-medium">{profile?.name || 'Your store'}</p>
            <p className="mt-0.5 truncate text-xs text-muted-foreground">{profile?.email || ''}</p>
            <div className="mt-2 flex items-center gap-2">
              <Badge variant={planVariant(profile?.plan)} className="capitalize">
                {(profile?.plan || 'free').toLowerCase()} plan
              </Badge>
              <Link
                href="/settings#billing"
                className="text-xs font-medium text-primary hover:underline"
              >
                Manage
              </Link>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
