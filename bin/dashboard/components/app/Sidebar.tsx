'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LayoutDashboard, Package, Settings, X } from 'lucide-react';

import { cn } from '@/lib/utils';

type SidebarProps = {
  mobileOpen: boolean;
  onClose: () => void;
};

const items = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/products', label: 'Products', icon: Package },
  { href: '/settings', label: 'Settings', icon: Settings }
];

export function Sidebar({ mobileOpen, onClose }: Readonly<SidebarProps>) {
  const pathname = usePathname();

  return (
    <>
      {mobileOpen ? <button className="fixed inset-0 z-40 bg-slate-900/35 lg:hidden" onClick={onClose} aria-label="Close sidebar overlay" /> : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 w-64 border-r border-border bg-card p-4 transition-transform duration-200 lg:static lg:z-auto lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="mb-6 flex items-center justify-between lg:justify-start">
          <p className="text-lg font-semibold">FitVision</p>
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border text-muted-foreground lg:hidden"
            onClick={onClose}
            aria-label="Close menu"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="space-y-1">
          {items.map((item) => {
            const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                )}
                onClick={onClose}
              >
                <Icon className="h-4 w-4" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>
      </aside>
    </>
  );
}
