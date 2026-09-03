'use client';

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

type QualityChartProps = {
  distribution: Record<string, number>;
};

const SEGMENTS: Array<{ key: string; label: string; color: string; hint: string }> = [
  { key: 'EXACT', label: 'Exact', color: 'hsl(var(--chart-3))', hint: 'Every measurement inside the size range' },
  { key: 'PARTIAL', label: 'Partial', color: 'hsl(var(--chart-1))', hint: 'Most measurements inside the range' },
  { key: 'CLOSEST', label: 'Closest', color: 'hsl(var(--chart-4))', hint: 'Nearest size, lower confidence' },
  { key: 'NO_MATCH', label: 'No match', color: 'hsl(var(--border-strong))', hint: 'Nothing credibly fit' }
];

export function QualityChart({ distribution }: Readonly<QualityChartProps>) {
  const data = SEGMENTS.map((seg) => ({ ...seg, value: distribution[seg.key] ?? 0 }));
  const total = data.reduce((acc, item) => acc + item.value, 0);
  const matched = (distribution.EXACT ?? 0) + (distribution.PARTIAL ?? 0);
  const matchRate = total > 0 ? Math.round((matched / total) * 100) : 0;

  const pieData =
    total > 0 ? data : [{ key: 'empty', label: 'No data', color: 'hsl(var(--muted))', hint: '', value: 1 }];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Match quality</CardTitle>
        <CardDescription>How confidently sizes were matched across {total.toLocaleString()} recommendations.</CardDescription>
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
                  <Tooltip
                    formatter={(value, name) => [Number(value).toLocaleString(), String(name)]}
                  />
                ) : null}
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-2xl font-semibold tabular-nums">{matchRate}%</span>
              <span className="text-xs text-muted-foreground">strong match</span>
            </div>
          </div>

          <ul className="space-y-2.5">
            {data.map((item) => {
              const pct = total > 0 ? Math.round((item.value / total) * 100) : 0;
              return (
                <li key={item.key} className="flex items-center justify-between gap-3">
                  <span className="flex items-center gap-2.5 text-sm">
                    <span
                      className="h-2.5 w-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: item.color }}
                    />
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
