'use client';

import { useEffect, useMemo, useState } from 'react';
import useSWR from 'swr';

import { StoreTable } from '@/components/admin/StoreTable';
import { Input } from '@/components/ui/input';
import { api, ApiError } from '@/lib/api';
import type { AdminRecommendation, SpringPage, StoreAdminView } from '@/lib/types';
import { useAdminGuard } from '@/hooks/useAdminGuard';

const EMPTY_PAGE: SpringPage<StoreAdminView> = {
  content: [],
  number: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true
};

export default function AdminStoresPage() {
  useAdminGuard();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [error, setError] = useState<string | null>(null);
  const [storeRecommendationsById, setStoreRecommendationsById] = useState<Record<string, AdminRecommendation[]>>({});

  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const key = useMemo(() => ['admin-stores', page, status, searchTerm] as const, [page, searchTerm, status]);

  const { data, isLoading, mutate } = useSWR(key, () => api.adminGetStores(page, 20, status, searchTerm), {
    revalidateOnFocus: true
  });

  async function preloadStoreRecommendations(stores: StoreAdminView[]) {
    const updates: Record<string, AdminRecommendation[]> = {};
    await Promise.all(
      stores.map(async (store) => {
        try {
          const recPage = await api.adminGetRecommendations(0, 10, { tenantId: store.id });
          updates[store.id] = recPage.content;
        } catch {
          updates[store.id] = [];
        }
      })
    );

    setStoreRecommendationsById((current) => ({ ...current, ...updates }));
  }

  useEffect(() => {
    if (data?.content?.length) {
      void preloadStoreRecommendations(data.content);
    }
  }, [data?.content]);

  async function handleOverridePlan(storeId: string, plan: string) {
    await api.adminOverrideStorePlan(storeId, plan);
    await mutate();
  }

  async function handleToggleStatus(store: StoreAdminView) {
    const nextStatus = store.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';

    const confirmed = confirm(
      nextStatus === 'INACTIVE'
        ? 'Deactivate this store? Widget requests will return 401 until reactivated.'
        : 'Activate this store?'
    );

    if (!confirmed) {
      return;
    }

    try {
      setError(null);
      await api.adminUpdateStoreStatus(store.id, nextStatus);
      await mutate();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to update store status.');
    }
  }

  return (
    <main className="mx-auto max-w-7xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">Stores</h1>
        <p className="mt-1 text-sm text-muted-foreground">Manage tenant access and inspect store-level health.</p>
      </div>

      <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center">
        <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by name or email" />
        <select
          className="h-10 rounded-md border border-border bg-background px-3 text-sm"
          value={status}
          onChange={(event) => {
            setPage(0);
            setStatus(event.target.value);
          }}
        >
          <option value="ALL">All</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {error ? <p className="text-sm text-rose-600">{error}</p> : null}

      <StoreTable
        page={data || EMPTY_PAGE}
        loading={isLoading}
        onToggleStatus={handleToggleStatus}
        onPlanOverride={handleOverridePlan}
        onPageChange={(next) => setPage(Math.max(next, 0))}
        storeRecommendationsById={storeRecommendationsById}
      />
    </main>
  );
}
