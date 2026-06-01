'use client';

import { useMemo, useState } from 'react';
import useSWR from 'swr';

import { Input } from '@/components/ui/input';
import { useAdminGuard } from '@/hooks/useAdminGuard';
import { api } from '@/lib/api';

export default function AdminRecommendationsPage() {
  useAdminGuard();

  const [page, setPage] = useState(0);
  const [quality, setQuality] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const key = useMemo(() => ['admin-recommendations', page, quality, tenantId] as const, [page, quality, tenantId]);

  const { data, isLoading } = useSWR(
    key,
    () =>
      api.adminGetRecommendations(page, 20, {
        quality: quality || undefined,
        tenantId: tenantId || undefined
      }),
    {
      revalidateOnFocus: true
    }
  );

  const filtered = useMemo(() => {
    const items = data?.content || [];

    return items.filter((item) => {
      const value = new Date(item.createdAt).getTime();
      const fromOk = dateFrom ? value >= new Date(`${dateFrom}T00:00:00`).getTime() : true;
      const toOk = dateTo ? value <= new Date(`${dateTo}T23:59:59`).getTime() : true;
      return fromOk && toOk;
    });
  }, [data?.content, dateFrom, dateTo]);

  return (
    <main className="mx-auto max-w-7xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Recommendations Log</h1>
        <p className="mt-1 text-sm text-muted-foreground">Platform-wide recommendation events with filters.</p>
      </div>

      <div className="grid grid-cols-1 gap-3 rounded-xl border border-border bg-card p-4 md:grid-cols-2 xl:grid-cols-4">
        <Input value={tenantId} onChange={(event) => setTenantId(event.target.value)} placeholder="Store UUID" />
        <select
          className="h-10 rounded-md border border-border bg-background px-3 text-sm"
          value={quality}
          onChange={(event) => {
            setPage(0);
            setQuality(event.target.value);
          }}
        >
          <option value="">All qualities</option>
          <option value="EXACT">EXACT</option>
          <option value="PARTIAL">PARTIAL</option>
          <option value="CLOSEST">CLOSEST</option>
          <option value="NO_MATCH">NO_MATCH</option>
        </select>
        <Input type="date" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} />
        <Input type="date" value={dateTo} onChange={(event) => setDateTo(event.target.value)} />
      </div>

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[980px] text-sm">
          <thead className="bg-muted/50 text-left text-muted-foreground">
            <tr>
              <th className="px-3 py-2 font-medium">Store</th>
              <th className="px-3 py-2 font-medium">Product</th>
              <th className="px-3 py-2 font-medium">Recommended</th>
              <th className="px-3 py-2 font-medium">Confidence</th>
              <th className="px-3 py-2 font-medium">Quality</th>
              <th className="px-3 py-2 font-medium">Date</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td className="px-3 py-3 text-muted-foreground" colSpan={6}>Loading recommendations...</td>
              </tr>
            ) : null}
            {!isLoading && filtered.length === 0 ? (
              <tr>
                <td className="px-3 py-3 text-muted-foreground" colSpan={6}>No recommendations for current filters.</td>
              </tr>
            ) : null}
            {!isLoading
              ? filtered.map((item) => (
                  <tr key={item.id} className="border-t border-border/70">
                    <td className="px-3 py-2">{item.storeName}</td>
                    <td className="px-3 py-2">{item.productName}</td>
                    <td className="px-3 py-2">{item.recommendedSize || '-'}</td>
                    <td className="px-3 py-2">
                      {(item.confidenceScore * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%
                    </td>
                    <td className="px-3 py-2">{item.quality}</td>
                    <td className="px-3 py-2">{new Date(item.createdAt).toLocaleString()}</td>
                  </tr>
                ))
              : null}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Page {((data?.number || 0) + 1).toLocaleString()} of {Math.max(data?.totalPages || 1, 1).toLocaleString()}
        </p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="h-9 rounded-md border border-border px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            disabled={Boolean(data?.first ?? true)}
            onClick={() => setPage((current) => Math.max(current - 1, 0))}
          >
            Previous
          </button>
          <button
            type="button"
            className="h-9 rounded-md border border-border px-3 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            disabled={Boolean(data?.last ?? true)}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </button>
        </div>
      </div>
    </main>
  );
}
