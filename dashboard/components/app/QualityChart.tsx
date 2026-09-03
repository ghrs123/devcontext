'use client';

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useT } from '@/lib/i18n/I18nProvider';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

type QualityChartProps = {
  distribution: Record<string, number>;
};

const SEGMENTS: Array<{ key: string; labelKey: TranslationKey; hintKey: TranslationKey; color: string }> = [
  { key: 'EXACT', labelKey: 'chart.exact', hintKey: 'chart.exactHint', color: 'hsl(var(--chart-3))' },
  { key: 'PARTIAL', labelKey: 'chart.partial', hintKey: 'chart.partialHint', color: 'hsl(var(--chart-1))' },
  { key: 'CLOSEST', labelKey: 'chart.closest', hintKey: 'chart.closestHint', color: 'hsl(var(--chart-4))' },
  { key: 'NO_MATCH', labelKey: 'chart.noMatch', hintKey: 'chart.noMatchHint', color: 'hsl(var(--border-strong))' }
];

export function QualityChart({ distribution }: Readonly<QualityChartProps>) {
  const t = useT();

  const data = SEGMENTS.map((seg) => ({
    key: seg.key,
    label: t(seg.labelKey),
    hint: t(seg.hintKey),
    color: seg.color,
    value: distribution[seg.key] ?? 0
  }));
  const total = data.reduce((acc, item) => acc + item.value, 0);
  const matched = (distribution.EXACT ?? 0) + (distribution.PARTIAL ?? 0);
  const matchRate = total > 0 ? Math.round((matched / total) * 100) : 0;

  const pieData =
    total > 0
      ? data
      : [{ key: 'empty', label: '—', hint: '', color: 'hsl(var(--muted))', value: 1 }];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('chart.title')}</CardTitle>
        <CardDescription>{t('chart.subtitle', { total: total.toLocaleString() })}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid gap-6 sm:grid-cols-[minmax(0,180px)_1fr] sm:items-center">
          <div className="relative mx-auto h-44 w-44">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={pieData}
                  dataKey="value"
                  nameKey="label"
                  innerRadius={58}
                  outerRadius={82}
                  paddingAngle={total > 0 ? 2 : 0}
                  stroke="none"
                  isAnimationActive={false}
                >
                  {pieData.map((entry) => (
                    <Cell key={entry.key} fill={entry.color} />
                  ))}
                </Pie>
                {total > 0 ? (
                  <Tooltip formatter={(value, name) => [Number(value).toLocaleString(), String(name)]} />
                ) : null}
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-2xl font-semibold tabular-nums">{matchRate}%</span>
              <span className="text-xs text-muted-foreground">{t('chart.center')}</span>
            </div>
          </div>

          <ul className="space-y-2.5">
            {data.map((item) => {
              const pct = total > 0 ? Math.round((item.value / total) * 100) : 0;
              return (
                <li key={item.key} className="flex items-center justify-between gap-3">
                  <span className="flex items-center gap-2.5 text-sm">
                    <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: item.color }} />
                    <span className="font-medium">{item.label}</span>
                    <span className="hidden text-xs text-muted-foreground md:inline">{item.hint}</span>
                  </span>
                  <span className="shrink-0 text-sm tabular-nums text-muted-foreground">
                    {item.value.toLocaleString()}
                    <span className="ml-1.5 text-xs">({pct}%)</span>
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      </CardContent>
    </Card>
  );
}
