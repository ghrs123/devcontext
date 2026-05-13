'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { BarChart3, Building2, LayoutDashboard, LogOut, Tags, X } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type AdminSidebarProps = {
  mobileOpen: boolean;
  onClose: () => void;
  onLogout: () => void;
};

const items = [
  { href: '/admin/dashboard', label: 'Platform Overview', icon: LayoutDashboard },
  { href: '/admin/stores', label: 'Stores', icon: Building2 },
  { href: '/admin/brands', label: 'Global Brands', icon: Tags },
  { href: '/admin/recommendations', label: 'Recommendations', icon: BarChart3 }
];

export function AdminSidebar({ mobileOpen, onClose, onLogout }: Readonly<AdminSidebarProps>) {
  const pathname = usePathname();

  return (
    <>
      {mobileOpen ? (
        <button className="fixed inset-0 z-40 bg-slate-950/70 lg:hidden" onClick={onClose} aria-label="Close admin sidebar overlay" />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 w-72 border-r border-slate-800 bg-slate-950 p-4 text-slate-100 transition-transform duration-200 lg:static lg:z-auto lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="mb-8 flex items-center justify-between lg:justify-start">
          <div>
            <p className="text-xs uppercase tracking-[0.2em] text-slate-400">FitVision</p>
            <p className="text-lg font-semibold">Admin</p>
          </div>
          <button
            type="button"
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-700 text-slate-300 lg:hidden"
            onClick={onClose}
            aria-label="Close admin menu"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="space-y-1.5">
          {items.map((item) => {
            const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors',
                  isActive ? 'bg-slate-800 text-white' : 'text-slate-300 hover:bg-slate-900 hover:text-white'
                )}
                onClick={onClose}
              >
                <Icon className="h-4 w-4" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="mt-8 border-t border-slate-800 pt-4">
          <Button variant="outline" className="w-full border-slate-700 bg-transparent text-slate-100 hover:bg-slate-900" onClick={onLogout}>
            <LogOut className="mr-2 h-4 w-4" />
            Logout
          </Button>
        </div>
      </aside>
    </>
  );
}
