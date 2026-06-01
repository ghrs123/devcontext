import type { SizeEntryData } from '@/lib/types';

type SizeChartTableProps = {
  entries: SizeEntryData[];
};

function formatRange(min?: number | null, max?: number | null): string {
  if (min == null && max == null) {
    return '-';
  }

  const minText = min == null ? '-' : min.toLocaleString();
  const maxText = max == null ? '-' : max.toLocaleString();
  return `${minText} - ${maxText}`;
}

export function SizeChartTable({ entries }: Readonly<SizeChartTableProps>) {
  if (!entries.length) {
    return (
      <p className="rounded-md border border-dashed border-border p-4 text-sm text-muted-foreground">
        No size chart uploaded yet.
      </p>
    );
  }

  return (
    <div className="overflow-x-auto rounded-md border border-border">
      <table className="w-full min-w-[720px] text-sm">
        <thead className="bg-muted/60 text-left text-muted-foreground">
          <tr>
            <th className="px-3 py-2 font-medium">Size</th>
            <th className="px-3 py-2 font-medium">Chest</th>
            <th className="px-3 py-2 font-medium">Waist</th>
            <th className="px-3 py-2 font-medium">Hip</th>
            <th className="px-3 py-2 font-medium">Height</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry, index) => (
            <tr key={`${entry.sizeLabel}-${index}`} className="border-t border-border/70">
              <td className="px-3 py-2 font-medium">{entry.sizeLabel}</td>
              <td className="px-3 py-2">{formatRange(entry.chestMin, entry.chestMax)}</td>
              <td className="px-3 py-2">{formatRange(entry.waistMin, entry.waistMax)}</td>
              <td className="px-3 py-2">{formatRange(entry.hipMin, entry.hipMax)}</td>
              <td className="px-3 py-2">{formatRange(entry.heightMin, entry.heightMax)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
