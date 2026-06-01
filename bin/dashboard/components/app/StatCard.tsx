import { cn } from '@/lib/utils';

type StatTone = 'default' | 'good' | 'warn' | 'bad';

type StatCardProps = {
  title: string;
  value: string;
  subtitle?: string;
  tone?: StatTone;
};

const toneMap: Record<StatTone, string> = {
  default: 'text-foreground',
  good: 'text-emerald-600',
  warn: 'text-amber-600',
  bad: 'text-rose-600'
};

export function StatCard({ title, value, subtitle, tone = 'default' }: StatCardProps) {
  return (
    <article className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <p className="text-sm text-muted-foreground">{title}</p>
      <p className={cn('mt-2 text-3xl font-semibold tracking-tight', toneMap[tone])}>{value}</p>
      {subtitle ? <p className="mt-2 text-xs text-muted-foreground">{subtitle}</p> : null}
    </article>
  );
}
