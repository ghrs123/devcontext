'use client';

import { LogOut, Menu } from 'lucide-react';

import { Button } from '@/components/ui/button';

type TopBarProps = {
  title: string;
  onMenuToggle: () => void;
  onLogout: () => void;
};

export function TopBar({ title, onMenuToggle, onLogout }: TopBarProps) {
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-md sm:px-6">
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onMenuToggle}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-border-strong text-muted-foreground hover:bg-muted lg:hidden"
          aria-label="Open menu"
        >
          <Menu className="h-5 w-5" />
        </button>
        <h1 className="text-[0.95rem] font-semibold tracking-tight">{title}</h1>
      </div>

      <Button variant="ghost" size="sm" onClick={onLogout}>
        <LogOut className="mr-2 h-4 w-4" />
        Sign out
      </Button>
    </header>
  );
}
