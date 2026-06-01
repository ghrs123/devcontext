'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import useSWR from 'swr';

import { api, ApiError } from '@/lib/api';
import { clearToken } from '@/lib/auth';

export function useAdminGuard() {
  const router = useRouter();
  const state = useSWR('admin-guard-metrics', api.adminGetMetrics, {
    refreshInterval: 60000,
    revalidateOnFocus: true,
    shouldRetryOnError: false
  });

  useEffect(() => {
    if (!(state.error instanceof ApiError)) {
      return;
    }

    if (state.error.status === 401 || state.error.status === 403) {
      clearToken();
      router.replace('/login');
    }
  }, [router, state.error]);

  return {
    metrics: state.data,
    isChecking: state.isLoading && !state.data,
    guardError: state.error
  };
}
