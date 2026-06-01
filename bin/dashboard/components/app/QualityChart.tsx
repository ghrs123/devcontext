'use client';

import { Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

type QualityChartProps = {
  distribution: Record<string, number>;
};

const ORDER = ['EXACT', 'PARTIAL', 'CLOSEST', 'NO_MATCH'];
const COLORS = [
  'hsl(var(--primary))',
  'hsl(var(--ring))',
  'hsl(var(--muted-foreground))',
  'hsl(var(--border))'
];

export function QualityChart({ distribution }: Readonly<QualityChartProps>) {
  const data = ORDER.map((key) => ({
    name: key,
    value: distribution[key] ?? 0,
    fill: COLORS[ORDER.indexOf(key) % COLORS.length]
  }));

  const total = data.reduce((acc, item) => acc + item.value, 0);

  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <h2 className="text-lg font-semibold">Match Quality Distribution</h2>

      <div className="mt-4 h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" innerRadius={68} outerRadius={96} paddingAngle={3} />
            <Tooltip
              formatter={(value) => {
                const safeValue = typeof value === 'number' ? value : Number(value || 0);
                return safeValue.toLocaleString();
              }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {data.map((item, index) => {
          const pct = total > 0 ? Math.round((item.value / total) * 100) : 0;
          return (
            <div key={item.name} className="rounded-md border border-border p-2">
              <p className="text-xs text-muted-foreground">
                <span className="mr-2 inline-block h-2.5 w-2.5 rounded-full align-middle" style={{ backgroundColor: COLORS[index % COLORS.length] }} />
                {item.name}
              </p>
              <p className="mt-1 text-sm font-medium">{item.value.toLocaleString()} ({pct}%)</p>
            </div>
          );
        })}
      </div>
    </section>
  );
}
