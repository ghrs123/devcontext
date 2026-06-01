import useSWR from 'swr';

import { api } from '@/lib/api';
import type { BillingStatusResponse } from '@/lib/types';

export function useBilling() {
  const { data, isLoading, mutate } = useSWR<BillingStatusResponse>(
    'billing-status',
    api.getBillingStatus,
    { refreshInterval: 60_000, revalidateOnFocus: true }
  );

  return { billing: data ?? null, isLoading, refresh: mutate };
}
