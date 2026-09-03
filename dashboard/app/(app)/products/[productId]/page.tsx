'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import useSWR from 'swr';
import { ArrowLeft, FileUp, Ruler, Sparkles } from 'lucide-react';

import { SizeChartTable } from '@/components/app/SizeChartTable';
import { SizeChartUpload } from '@/components/app/SizeChartUpload';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { api } from '@/lib/api';
import { useT } from '@/lib/i18n/I18nProvider';

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm font-medium">{value}</dd>
    </div>
  );
}

export default function ProductDetailPage() {
  const t = useT();
  const params = useParams<{ productId: string }>();
  const productId = params.productId;
  const [uploadOpen, setUploadOpen] = useState(false);

  const { data: products, isLoading } = useSWR('products', api.getProducts);
  const { data: entries, mutate: mutateChart } = useSWR(
    productId ? ['size-chart', productId] : null,
    () => api.getActiveSizeChart(productId)
  );
  const { data: summary } = useSWR('analytics-summary', api.getAnalyticsSummary);

  const product = products?.find((p) => p.id === productId);
  const stat = summary?.topProducts?.find((p) => p.productId === productId);

  if (isLoading) {
    return <div className="h-64 animate-pulse rounded-lg border border-border bg-card" />;
  }

  if (!product) {
    return (
      <div className="space-y-4">
        <Link href="/products" className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" /> {t('product.back')}
        </Link>
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm font-medium">{t('product.notFound')}</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('product.notFoundDesc')}
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/products"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" /> {t('product.back')}
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{product.name}</h2>
          <p className="mt-1 font-mono text-xs text-muted-foreground">{product.externalProductId}</p>
        </div>
        <Button variant="secondary" onClick={() => setUploadOpen(true)}>
          <FileUp className="mr-2 h-4 w-4" />
          {product.hasSizeChart ? t('product.replaceChart') : t('product.uploadChart')}
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{t('product.details')}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-4">
              <Meta label={t('products.col.category')} value={product.category || '—'} />
              <Meta label={t('products.col.gender')} value={product.genderTarget || '—'} />
              <Meta label={t('products.col.brand')} value={product.brandName || '—'} />
              <div>
                <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  {t('products.col.sizeChart')}
                </dt>
                <dd className="mt-1">
                  {product.hasSizeChart ? (
                    <Badge variant="success">{t('products.badge.ready')}</Badge>
                  ) : (
                    <Badge variant="warning">{t('products.badge.missing')}</Badge>
                  )}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-primary" /> {t('product.recommendations')}
            </CardTitle>
            <CardDescription>{t('product.recServed')}</CardDescription>
          </CardHeader>
          <CardContent>
            {stat ? (
              <div className="space-y-3">
                <div>
                  <p className="text-2xl font-semibold tabular-nums">
                    {stat.recommendationCount.toLocaleString()}
                  </p>
                  <p className="text-xs text-muted-foreground">{t('product.totalRec')}</p>
                </div>
                <div>
                  <p className="text-lg font-semibold tabular-nums">
                    {(stat.averageConfidence * 100).toFixed(0)}%
                  </p>
                  <p className="text-xs text-muted-foreground">{t('product.avgConf')}</p>
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t('product.noRec')}</p>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Ruler className="h-4 w-4 text-primary" /> {t('product.snippet')}
            </CardTitle>
            <CardDescription>{t('product.snippetDesc')}</CardDescription>
          </CardHeader>
          <CardContent>
            <pre className="overflow-x-auto rounded-md bg-subtle p-3 text-[0.7rem] leading-relaxed text-muted-foreground">
{`<div
  data-fitvision-product-id="${product.externalProductId}"
  data-fitvision-key="YOUR_PUBLIC_KEY">
</div>`}
            </pre>
            <p className="mt-2 text-xs text-muted-foreground">
              {t('product.findKey')}{' '}
              <Link href="/settings" className="text-primary hover:underline">
                {t('nav.settings')}
              </Link>
              .
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('product.sizeChart')}</CardTitle>
          <CardDescription>{t('product.sizeChartDesc')}</CardDescription>
        </CardHeader>
        <CardContent>
          <SizeChartTable entries={entries || []} />
        </CardContent>
      </Card>

      <SizeChartUpload
        product={uploadOpen ? product : null}
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onUploaded={async () => {
          await mutateChart();
        }}
      />
    </div>
  );
}
