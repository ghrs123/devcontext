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

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm font-medium">{value}</dd>
    </div>
  );
}

export default function ProductDetailPage() {
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
          <ArrowLeft className="h-4 w-4" /> Back to products
        </Link>
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm font-medium">Product not found</p>
            <p className="mt-1 text-sm text-muted-foreground">
              It may have been deleted, or the link is out of date.
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
        <ArrowLeft className="h-4 w-4" /> Back to products
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{product.name}</h2>
          <p className="mt-1 font-mono text-xs text-muted-foreground">{product.externalProductId}</p>
        </div>
        <Button variant="secondary" onClick={() => setUploadOpen(true)}>
          <FileUp className="mr-2 h-4 w-4" />
          {product.hasSizeChart ? 'Replace size chart' : 'Upload size chart'}
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-4">
              <Meta label="Category" value={product.category || '—'} />
              <Meta label="Gender" value={product.genderTarget || '—'} />
              <Meta label="Brand" value={product.brandName || '—'} />
              <div>
                <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Size chart
                </dt>
                <dd className="mt-1">
                  {product.hasSizeChart ? (
                    <Badge variant="success">Ready</Badge>
                  ) : (
                    <Badge variant="warning">Missing</Badge>
                  )}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-primary" /> Recommendations
            </CardTitle>
            <CardDescription>Served for this product.</CardDescription>
          </CardHeader>
          <CardContent>
            {stat ? (
              <div className="space-y-3">
                <div>
                  <p className="text-2xl font-semibold tabular-nums">
                    {stat.recommendationCount.toLocaleString()}
                  </p>
                  <p className="text-xs text-muted-foreground">total recommendations</p>
                </div>
                <div>
                  <p className="text-lg font-semibold tabular-nums">
                    {(stat.averageConfidence * 100).toFixed(0)}%
                  </p>
                  <p className="text-xs text-muted-foreground">average confidence</p>
                </div>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No recommendations yet.</p>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Ruler className="h-4 w-4 text-primary" /> Widget snippet
            </CardTitle>
            <CardDescription>Mount point for this product.</CardDescription>
          </CardHeader>
          <CardContent>
            <pre className="overflow-x-auto rounded-md bg-subtle p-3 text-[0.7rem] leading-relaxed text-muted-foreground">
{`<div
  data-fitvision-product-id="${product.externalProductId}"
  data-fitvision-key="YOUR_PUBLIC_KEY">
</div>`}
            </pre>
            <p className="mt-2 text-xs text-muted-foreground">
              Find your public key in{' '}
              <Link href="/settings" className="text-primary hover:underline">
                Settings
              </Link>
              .
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Size chart</CardTitle>
          <CardDescription>Active measurement ranges (cm) used to match buyers.</CardDescription>
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
