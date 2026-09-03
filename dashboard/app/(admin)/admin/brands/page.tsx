'use client';

import { useT } from '@/lib/i18n/I18nProvider';

import useSWR from 'swr';

import { GlobalBrandManager } from '@/components/admin/GlobalBrandManager';
import { useAdminGuard } from '@/hooks/useAdminGuard';
import { api } from '@/lib/api';

export default function AdminBrandsPage() {
  const t = useT();
  useAdminGuard();

  const { data, isLoading, mutate } = useSWR('admin-brands', api.adminGetBrands, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  return (
    <main className="mx-auto max-w-7xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">{t('admin.brands.title')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('admin.brands.subtitle')}</p>
      </div>

      <GlobalBrandManager brands={data || []} loading={isLoading} onRefresh={async () => { await mutate(); }} />
    </main>
  );
}
