import type { ScrapeJobStatus } from '@/lib/types';

const config: Record<ScrapeJobStatus, { label: string; className: string }> = {
  PENDING:   { label: 'Pending',   className: 'bg-slate-100 text-slate-600' },
  RUNNING:   { label: 'Running',   className: 'bg-blue-100 text-blue-700 animate-pulse' },
  COMPLETED: { label: 'Completed', className: 'bg-emerald-100 text-emerald-700' },
  FAILED:    { label: 'Failed',    className: 'bg-rose-100 text-rose-700' },
};

export function ScrapeStatusBadge({ status }: Readonly<{ status: ScrapeJobStatus }>) {
  const { label, className } = config[status] ?? config.FAILED;
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
