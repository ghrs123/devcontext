'use client';

import { useT } from '@/lib/i18n/I18nProvider';
import type { SizeEntryData } from '@/lib/types';

type SizeChartTableProps = {
  entries: SizeEntryData[];
};

function formatRange(min?: number | null, max?: number | null): string {
  if (min == null && max == null) return '—';
  const minText = min == null ? '—' : min.toLocaleString();
  const maxText = max == null ? '—' : max.toLocaleString();
  return `${minText} – ${maxText}`;
}

export function SizeChartTable({ entries }: Readonly<SizeChartTableProps>) {
  const t = useT();

  if (!entries.length) {
    return (
      <p className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground">
        {t('sizeChart.empty')}
      </p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full min-w-[640px] text-sm">
        <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-3 py-2 font-semibold">{t('sizeChart.col.size')}</th>
            <th className="px-3 py-2 font-semibold">{t('sizeChart.col.chest')}</th>
            <th className="px-3 py-2 font-semibold">{t('sizeChart.col.waist')}</th>
            <th className="px-3 py-2 font-semibold">{t('sizeChart.col.hip')}</th>
            <th className="px-3 py-2 font-semibold">{t('sizeChart.col.height')}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border tabular-nums">
          {entries.map((entry, index) => (
            <tr key={`${entry.sizeLabel}-${index}`}>
              <td className="px-3 py-2 font-medium">{entry.sizeLabel}</td>
              <td className="px-3 py-2 text-muted-foreground">{formatRange(entry.chestMin, entry.chestMax)}</td>
              <td className="px-3 py-2 text-muted-foreground">{formatRange(entry.waistMin, entry.waistMax)}</td>
              <td className="px-3 py-2 text-muted-foreground">{formatRange(entry.hipMin, entry.hipMax)}</td>
              <td className="px-3 py-2 text-muted-foreground">{formatRange(entry.heightMin, entry.heightMax)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
