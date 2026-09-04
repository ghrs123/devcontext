'use client';

import Link from 'next/link';
import useSWR from 'swr';
import { AlertTriangle } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api } from '@/lib/api';
import { useT } from '@/lib/i18n/I18nProvider';
import type { ProductHealthRow } from '@/lib/types';
import type { TranslationKey } from '@/lib/i18n/dictionaries';

function reasonLabel(
  t: (k: TranslationKey, v?: Record<string, string | number>) => string,
  row: ProductHealthRow,
  reason: ProductHealthRow['reasons'][number]
) {
  if (reason === 'HIGH_NO_MATCH') {
    return t('attention.reason.HIGH_NO_MATCH', { rate: Math.round(row.noMatchRate * 100) });
  }
  if (reason === 'LOW_CONFIDENCE') {
    return t('attention.reason.LOW_CONFIDENCE', { conf: Math.round(row.averageConfidence * 100) });
  }
  return t('attention.reason.NO_SIZE_CHART');
}

function scoreVariant(score: number) {
  if (score >= 60) return 'danger' as const;
  if (score >= 30) return 'warning' as const;
  return 'neutral' as const;
}

export function AttentionList({ limit = 5 }: { limit?: number }) {
  const t = useT();
  const { data, isLoading } = useSWR('product-health', api.getProductHealth, {
    refreshInterval: 60000
  });

  if (isLoading) {
    return <div className="h-56 animate-pulse rounded-lg border border-border bg-card" />;
  }

  const rows = (data ?? []).slice(0, limit);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 text-warning" />
          {t('attention.title')}
        </CardTitle>
        <CardDescription>{t('attention.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="rounded-md border border-dashed border-border py-8 text-center text-sm text-muted-foreground">
            {t('attention.empty')}
          </p>
        ) : (
          <ul className="divide-y divide-border">
            {rows.map((row) => (
              <li key={row.productId} className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0">
                  <Link
                    href={`/products/${row.productId}`}
                    className="truncate text-sm font-medium hover:text-primary"
                  >
                    {row.productName}
                  </Link>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {row.reasons.map((reason) => (
                      <span key={reason} className="text-xs text-muted-foreground">
                        {reasonLabel(t, row, reason)}
                      </span>
                    ))}
                  </div>
                  {row.totalRecommendations > 0 ? (
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {t('attention.recs', { count: row.totalRecommendations })}
                    </p>
                  ) : null}
                </div>
                <Badge variant={scoreVariant(row.attentionScore)} className="shrink-0">
                  {t('attention.score', { score: row.attentionScore })}
                </Badge>
              </li>
            ))}
          </ul>
        )}
        {(data?.length ?? 0) > limit ? (
          <div className="mt-3 border-t border-border pt-3">
            <Link href="/products" className="text-sm font-medium text-primary hover:underline">
              {t('attention.viewAll')} →
            </Link>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
