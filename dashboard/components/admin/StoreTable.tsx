'use client';

import { useMemo, useState } from 'react';

import { Button } from '@/components/ui/button';
import type { AdminRecommendation, SpringPage, StoreAdminView } from '@/lib/types';

type StoreTableProps = {
  page: SpringPage<StoreAdminView>;
  loading: boolean;
  onToggleStatus: (store: StoreAdminView) => Promise<void>;
  onPageChange: (next: number) => void;
  storeRecommendationsById: Record<string, AdminRecommendation[]>;
};

function statusBadge(status: string) {
  if (status === 'ACTIVE') {
    return 'bg-emerald-100 text-emerald-700';
  }
  return 'bg-rose-100 text-rose-700';
}

export function StoreTable({ page, loading, onToggleStatus, onPageChange, storeRecommendationsById }: Readonly<StoreTableProps>) {
  const [selectedStore, setSelectedStore] = useState<StoreAdminView | null>(null);
  const [submitting, setSubmitting] = useState<string | null>(null);

  const recommendations = useMemo(() => {
    if (!selectedStore) {
      return [];
    }
    return storeRecommendationsById[selectedStore.id] || [];
  }, [selectedStore, storeRecommendationsById]);

  const productNames = useMemo(() => {
    const set = new Set(recommendations.map((item) => item.productName).filter(Boolean));
    return Array.from(set);
  }, [recommendations]);

  async function handleToggle(store: StoreAdminView) {
    try {
      setSubmitting(store.id);
      await onToggleStatus(store);
    } finally {
      setSubmitting(null);
    }
  }

  function actionLabelFor(store: StoreAdminView): string {
    if (store.status === 'ACTIVE') {
      return 'Deactivate';
    }
    return 'Activate';
  }

  const showLoadingRow = loading;
  const showEmptyRow = !loading && page.content.length === 0;
  const rows = loading ? [] : page.content;

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[980px] text-sm">
          <thead className="bg-muted/50 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-3 font-medium">Store</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Plan</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Registered</th>
              <th className="px-4 py-3 font-medium">Products</th>
              <th className="px-4 py-3 font-medium">Recommendations</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {showLoadingRow ? (
              <tr>
                <td className="px-4 py-6 text-muted-foreground" colSpan={8}>Loading stores...</td>
              </tr>
            ) : null}
            {showEmptyRow ? (
              <tr>
                <td className="px-4 py-6 text-muted-foreground" colSpan={8}>No stores found for this filter.</td>
              </tr>
            ) : null}
            {rows.map((store) => (
              <tr key={store.id} className="border-t border-border/70">
                <td className="px-4 py-3 font-medium">{store.name}</td>
                <td className="px-4 py-3">{store.email}</td>
                <td className="px-4 py-3">{store.plan}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusBadge(store.status)}`}>
                    {store.status}
                  </span>
                </td>
                <td className="px-4 py-3">{new Date(store.createdAt).toLocaleDateString()}</td>
                <td className="px-4 py-3">{store.totalProducts.toLocaleString()}</td>
                <td className="px-4 py-3">{store.totalRecommendations.toLocaleString()}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <Button type="button" size="sm" variant="outline" onClick={() => setSelectedStore(store)}>
                      View detail
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={submitting === store.id}
                      onClick={() => handleToggle(store)}
                    >
                      {submitting === store.id ? 'Updating...' : actionLabelFor(store)}
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          Page {(page.number + 1).toLocaleString()} of {Math.max(page.totalPages, 1).toLocaleString()} ({page.totalElements.toLocaleString()} stores)
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="sm" disabled={page.first} onClick={() => onPageChange(page.number - 1)}>
            Previous
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={page.last} onClick={() => onPageChange(page.number + 1)}>
            Next
          </Button>
        </div>
      </div>

      {selectedStore ? (
        <div className="fixed inset-0 z-50">
          <button className="absolute inset-0 bg-slate-950/35" onClick={() => setSelectedStore(null)} aria-label="Close store details" />
          <aside className="absolute right-0 top-0 h-full w-full max-w-2xl overflow-y-auto border-l border-border bg-background p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-xl font-semibold">{selectedStore.name}</h2>
              <Button type="button" variant="outline" onClick={() => setSelectedStore(null)}>
                Close
              </Button>
            </div>

            <div className="grid grid-cols-1 gap-3 rounded-lg border border-border bg-card p-4 sm:grid-cols-2">
              <p className="text-sm"><span className="font-medium">Email:</span> {selectedStore.email}</p>
              <p className="text-sm"><span className="font-medium">Plan:</span> {selectedStore.plan}</p>
              <p className="text-sm"><span className="font-medium">Status:</span> {selectedStore.status}</p>
              <p className="text-sm"><span className="font-medium">Role:</span> {selectedStore.role}</p>
              <p className="text-sm"><span className="font-medium">Platform:</span> {selectedStore.platform}</p>
              <p className="text-sm"><span className="font-medium">Registered:</span> {new Date(selectedStore.createdAt).toLocaleString()}</p>
              <p className="text-sm"><span className="font-medium">Products:</span> {selectedStore.totalProducts.toLocaleString()}</p>
              <p className="text-sm"><span className="font-medium">Recommendations:</span> {selectedStore.totalRecommendations.toLocaleString()}</p>
            </div>

            <div className="mt-5 space-y-2">
              <h3 className="text-base font-semibold">Products (observed in recent recommendations)</h3>
              <div className="overflow-x-auto rounded-lg border border-border bg-card">
                <table className="w-full min-w-[560px] text-sm">
                  <thead className="bg-muted/50 text-left text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 font-medium">Product</th>
                      <th className="px-3 py-2 font-medium">Size chart status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {productNames.length === 0 ? (
                      <tr>
                        <td className="px-3 py-3 text-muted-foreground" colSpan={2}>No product data available yet.</td>
                      </tr>
                    ) : (
                      productNames.map((name) => (
                        <tr key={name} className="border-t border-border/70">
                          <td className="px-3 py-2">{name}</td>
                          <td className="px-3 py-2">Unknown</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="mt-5 space-y-2">
              <h3 className="text-base font-semibold">Recommendation history (recent)</h3>
              <div className="overflow-x-auto rounded-lg border border-border bg-card">
                <table className="w-full min-w-[720px] text-sm">
                  <thead className="bg-muted/50 text-left text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 font-medium">Product</th>
                      <th className="px-3 py-2 font-medium">Size</th>
                      <th className="px-3 py-2 font-medium">Confidence</th>
                      <th className="px-3 py-2 font-medium">Quality</th>
                      <th className="px-3 py-2 font-medium">Date</th>
                    </tr>
                  </thead>
                  <tbody>
                    {recommendations.length === 0 ? (
                      <tr>
                        <td className="px-3 py-3 text-muted-foreground" colSpan={5}>No recommendations loaded for this store.</td>
                      </tr>
                    ) : (
                      recommendations.map((item) => (
                        <tr key={item.id} className="border-t border-border/70">
                          <td className="px-3 py-2">{item.productName}</td>
                          <td className="px-3 py-2">{item.recommendedSize || '-'}</td>
                          <td className="px-3 py-2">
                            {(item.confidenceScore * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%
                          </td>
                          <td className="px-3 py-2">{item.quality}</td>
                          <td className="px-3 py-2">{new Date(item.createdAt).toLocaleString()}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
