'use client';

import { LogOut, Menu } from 'lucide-react';

import { Button } from '@/components/ui/button';

type TopBarProps = {
  storeName: string;
  onMenuToggle: () => void;
  onLogout: () => void;
};

export function TopBar({ storeName, onMenuToggle, onLogout }: TopBarProps) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/95 px-4 backdrop-blur">
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onMenuToggle}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border text-muted-foreground lg:hidden"
          aria-label="Open menu"
        >
          <Menu className="h-5 w-5" />
        </button>
        <div>
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Store</p>
          <p className="text-sm font-medium">{storeName}</p>
        </div>
      </div>

      <Button variant="outline" size="sm" onClick={onLogout}>
        <LogOut className="mr-2 h-4 w-4" />
        Logout
      </Button>
    </header>
  );
}
