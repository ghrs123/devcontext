'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { AdminHealthResponse } from '@/lib/types';

function StatusBadge({ status }: Readonly<{ status: string }>) {
  const isUp = status === 'UP';
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${
        isUp ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
      }`}
    >
      {status}
    </span>
  );
}

export function SystemHealthCards({ health }: Readonly<{ health: AdminHealthResponse }>) {
  const failedScrapes = health.scrapeJobs.failedLast7Days;

  return (
    <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Card>
        <CardHeader className="pb-2">
          <CardDescription>Database</CardDescription>
          <CardTitle className="flex items-center gap-2 text-xl">
            <StatusBadge status={health.database.status} />
            <span className="text-base font-normal text-muted-foreground">
              {health.database.latencyMs} ms
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">SELECT 1 probe latency</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-2">
          <CardDescription>Last recommendation</CardDescription>
          <CardTitle className="text-lg">
            {health.storeActivity.lastRecommendationAt
              ? new Date(health.storeActivity.lastRecommendationAt).toLocaleString()
              : 'No activity yet'}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">
            {health.storeActivity.recommendationsLast24h.toLocaleString()} in last 24h
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-2">
          <CardDescription>Active stores (24h)</CardDescription>
          <CardTitle className="text-2xl">{health.storeActivity.activeStoresLast24h.toLocaleString()}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">Stores with at least one recommendation</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-2">
          <CardDescription>Failed scrapes (7d)</CardDescription>
          <CardTitle className={`text-2xl ${failedScrapes > 0 ? 'text-rose-600' : ''}`}>
            {failedScrapes.toLocaleString()}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">
            {health.scrapeJobs.running > 0
              ? `${health.scrapeJobs.running} job(s) currently running`
              : 'No scrape jobs running'}
          </p>
        </CardContent>
      </Card>
    </section>
  );
}
