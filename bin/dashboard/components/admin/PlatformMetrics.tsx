'use client';

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { AdminMetrics } from '@/lib/types';

function confidenceTone(score: number): string {
  const pct = score * 100;
  if (pct >= 80) {
    return 'text-emerald-600';
  }
  if (pct >= 50) {
    return 'text-amber-600';
  }
  return 'text-rose-600';
}

export function PlatformMetrics({ metrics }: Readonly<{ metrics: AdminMetrics }>) {
  const distribution = ['EXACT', 'PARTIAL', 'CLOSEST', 'NO_MATCH'].map((quality) => ({
    quality,
    value: metrics.qualityDistribution?.[quality] || 0
  }));

  return (
    <div className="space-y-6">
      <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total Stores</CardDescription>
            <CardTitle className="text-2xl">{metrics.totalStores.toLocaleString()}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">{metrics.activeStores.toLocaleString()} active stores</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Total Recommendations</CardDescription>
            <CardTitle className="text-2xl">{metrics.totalRecommendations.toLocaleString()}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">
              {metrics.recommendationsLast30Days.toLocaleString()} in the last 30 days
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Average Confidence</CardDescription>
            <CardTitle className={`text-2xl ${confidenceTone(metrics.averageConfidenceScore)}`}>
              {(metrics.averageConfidenceScore * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">Across all tenants</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardDescription>Top Brand</CardDescription>
            <CardTitle className="text-xl">
              {metrics.topBrands?.[0]?.brandName || 'No data'}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">
              {(metrics.topBrands?.[0]?.recommendationCount || 0).toLocaleString()} recommendations
            </p>
          </CardContent>
        </Card>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Quality Distribution</CardTitle>
          <CardDescription>Platform-wide recommendation quality</CardDescription>
        </CardHeader>
        <CardContent className="h-80">
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
    </div>
  );
}
