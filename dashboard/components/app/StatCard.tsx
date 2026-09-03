import type { LucideIcon } from 'lucide-react';
import { ArrowDownRight, ArrowUpRight } from 'lucide-react';

import { cn } from '@/lib/utils';

type StatTone = 'default' | 'good' | 'warn' | 'bad';

type StatCardProps = {
  title: string;
  value: string;
  icon: LucideIcon;
  subtitle?: string;
  tone?: StatTone;
  delta?: { value: number; label?: string } | null;
};

const valueTone: Record<StatTone, string> = {
  default: 'text-foreground',
  good: 'text-success',
  warn: 'text-warning',
  bad: 'text-danger'
};

export function StatCard({ title, value, icon: Icon, subtitle, tone = 'default', delta }: StatCardProps) {
  const positive = (delta?.value ?? 0) >= 0;

  return (
    <article className="group rounded-lg border border-border bg-card p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="flex items-start justify-between">
        <p className="text-sm font-medium text-muted-foreground">{title}</p>
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-primary-soft text-primary-soft-foreground">
          <Icon className="h-4 w-4" />
        </span>
      </div>

      <p className={cn('mt-3 text-[1.75rem] font-semibold leading-none tabular-nums tracking-tight', valueTone[tone])}>
        {value}
      </p>

      <div className="mt-2 flex items-center gap-2 text-xs">
        {delta ? (
          <span
            className={cn(
              'inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-medium',
              positive ? 'bg-success-soft text-success' : 'bg-danger-soft text-danger'
            )}
          >
            {positive ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
            {Math.abs(delta.value).toFixed(1)}%
          </span>
        ) : null}
        {subtitle ? <span className="text-muted-foreground">{subtitle}</span> : null}
      </div>
    </article>
  );
}
