'use client';

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { RecommendationStatsResponse } from '@/lib/types';

function latencyTone(ms: number | null): string {
  if (ms == null) return 'text-muted-foreground';
  if (ms < 200) return 'text-emerald-600';
  if (ms < 500) return 'text-amber-600';
  return 'text-rose-600';
}

function formatLatency(ms: number | null): string {
  if (ms == null) return '—';
  return `${Math.round(ms)} ms`;
}

export function RecommendationStatsPanel({ stats }: Readonly<{ stats: RecommendationStatsResponse }>) {
  const distribution = ['EXACT', 'PARTIAL', 'CLOSEST', 'NO_MATCH'].map((quality) => ({
    quality,
    value: stats.qualityDistribution?.[quality] || 0
  }));

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Recommendation performance</CardTitle>
          <CardDescription>Latency percentiles (last 24h)</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: 'p50', value: stats.p50LatencyMs },
              { label: 'p95', value: stats.p95LatencyMs },
              { label: 'p99', value: stats.p99LatencyMs }
            ].map((item) => (
              <div key={item.label} className="rounded-lg border border-border p-4 text-center">
                <p className="text-xs uppercase tracking-wide text-muted-foreground">{item.label}</p>
                <p className={`mt-1 text-2xl font-semibold ${latencyTone(item.value)}`}>
                  {formatLatency(item.value)}
                </p>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Quality distribution</CardTitle>
          <CardDescription>Recommendations in the last 24 hours</CardDescription>
        </CardHeader>
        <CardContent className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={distribution} margin={{ top: 8, right: 12, left: 0, bottom: 6 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="quality" stroke="hsl(var(--muted-foreground))" fontSize={12} />
              <YAxis stroke="hsl(var(--muted-foreground))" fontSize={12} allowDecimals={false} />
              <Tooltip cursor={{ fill: 'hsl(var(--muted))' }} />
              <Bar dataKey="value" fill="hsl(var(--primary))" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      <Card className="xl:col-span-2">
        <CardHeader>
          <CardTitle>Top stores by volume</CardTitle>
          <CardDescription>Last 24 hours</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full min-w-[480px] text-sm">
              <thead className="bg-muted/50 text-left text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">Store</th>
                  <th className="px-3 py-2 font-medium">Recommendations</th>
                </tr>
              </thead>
              <tbody>
                {(stats.topStores?.length || 0) === 0 ? (
                  <tr>
                    <td className="px-3 py-3 text-muted-foreground" colSpan={2}>
                      No recommendations in the last 24 hours.
                    </td>
                  </tr>
                ) : (
                  stats.topStores.map((store) => (
                    <tr key={store.storeId} className="border-t border-border/70">
                      <td className="px-3 py-2">{store.storeName}</td>
                      <td className="px-3 py-2">{store.recommendationCount.toLocaleString()}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
