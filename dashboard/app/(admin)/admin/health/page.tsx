'use client';

import { useT } from '@/lib/i18n/I18nProvider';

import { useEffect, useState } from 'react';
import useSWR from 'swr';

import { RecommendationStatsPanel } from '@/components/admin/RecommendationStatsPanel';
import { ScrapePipelineStatus } from '@/components/admin/ScrapePipelineStatus';
import { SystemHealthCards } from '@/components/admin/SystemHealthCards';
import { api } from '@/lib/api';
import { useAdminGuard } from '@/hooks/useAdminGuard';

const REFRESH_INTERVAL_MS = 30_000;

export default function AdminHealthPage() {
  const t = useT();
  const { isChecking } = useAdminGuard();
  const [secondsSinceUpdate, setSecondsSinceUpdate] = useState(0);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<number>(Date.now());

  const { data: health, isLoading: loadingHealth, mutate: mutateHealth } = useSWR(
    'admin-health',
    () => api.adminGetHealth(),
    { refreshInterval: REFRESH_INTERVAL_MS, revalidateOnFocus: true }
  );

  const { data: stats, isLoading: loadingStats, mutate: mutateStats } = useSWR(
    'admin-recommendation-stats',
    () => api.adminGetRecommendationStats(),
    { refreshInterval: REFRESH_INTERVAL_MS, revalidateOnFocus: true }
  );

  useEffect(() => {
    if (health || stats) {
      setLastUpdatedAt(Date.now());
      setSecondsSinceUpdate(0);
    }
  }, [health, stats]);

  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsSinceUpdate(Math.floor((Date.now() - lastUpdatedAt) / 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, [lastUpdatedAt]);

  function refreshAll() {
    void mutateHealth();
    void mutateStats();
  }

  if (isChecking || loadingHealth || loadingStats || !health || !stats) {
    return <div className="h-64 animate-pulse rounded-xl border border-border bg-card" />;
  }

  return (
    <main className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t('admin.health.title')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{t('admin.health.subtitle')}</p>
        </div>
        <p className="text-xs text-muted-foreground">
          Last updated: {secondsSinceUpdate}s ago · auto-refresh every 30s
        </p>
      </div>

      <SystemHealthCards health={health} />
      <RecommendationStatsPanel stats={stats} />
      <ScrapePipelineStatus brandScrapes={health.brandScrapes} onRefresh={refreshAll} />
    </main>
  );
}
