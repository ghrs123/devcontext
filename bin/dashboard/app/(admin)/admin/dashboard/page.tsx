'use client';

import useSWR from 'swr';

import { PlatformMetrics } from '@/components/admin/PlatformMetrics';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { api } from '@/lib/api';
import { useAdminGuard } from '@/hooks/useAdminGuard';

export default function AdminDashboardPage() {
  const { metrics, isChecking } = useAdminGuard();

  const { data: recommendations, isLoading: loadingRecommendations } = useSWR(
    ['admin-recent-recommendations', 0, 10],
    () => api.adminGetRecommendations(0, 10),
    {
      refreshInterval: 60000,
      revalidateOnFocus: true
    }
  );

  if (isChecking || !metrics) {
    return <div className="h-64 animate-pulse rounded-xl border border-border bg-card" />;
  }

  return (
    <main className="mx-auto max-w-7xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Platform Overview</h1>
        <p className="mt-1 text-sm text-muted-foreground">Cross-tenant metrics and latest activity.</p>
      </div>

      <PlatformMetrics metrics={metrics} />

      <Card>
        <CardHeader>
          <CardTitle>Recent activity</CardTitle>
          <CardDescription>Last 10 recommendations across all stores</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="bg-muted/50 text-left text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">Store</th>
                  <th className="px-3 py-2 font-medium">Product</th>
                  <th className="px-3 py-2 font-medium">Size</th>
                  <th className="px-3 py-2 font-medium">Confidence</th>
                  <th className="px-3 py-2 font-medium">Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {loadingRecommendations ? (
                  <tr>
                    <td className="px-3 py-3 text-muted-foreground" colSpan={5}>Loading recent activity...</td>
                  </tr>
                ) : null}
                {!loadingRecommendations && (recommendations?.content?.length || 0) === 0 ? (
                  <tr>
                    <td className="px-3 py-3 text-muted-foreground" colSpan={5}>No recommendations yet.</td>
                  </tr>
                ) : null}
                {!loadingRecommendations
                  ? (recommendations?.content || []).map((item) => (
                      <tr key={item.id} className="border-t border-border/70">
                        <td className="px-3 py-2">{item.storeName}</td>
                        <td className="px-3 py-2">{item.productName}</td>
                        <td className="px-3 py-2">{item.recommendedSize || '-'}</td>
                        <td className="px-3 py-2">
                          {(item.confidenceScore * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%
                        </td>
                        <td className="px-3 py-2">{new Date(item.createdAt).toLocaleString()}</td>
                      </tr>
                    ))
                  : null}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
