import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { ProductRecommendationStat } from '@/lib/types';

type TopProductsTableProps = {
  products: ProductRecommendationStat[];
};

function confidenceColor(score: number) {
  if (score >= 0.8) return 'hsl(var(--chart-3))';
  if (score >= 0.5) return 'hsl(var(--chart-4))';
  return 'hsl(var(--danger))';
}

export function TopProductsTable({ products }: Readonly<TopProductsTableProps>) {
  const top = products.slice(0, 5);
  const max = Math.max(1, ...top.map((p) => p.recommendationCount));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Top products</CardTitle>
        <CardDescription>Ranked by recommendations served.</CardDescription>
      </CardHeader>
      <CardContent>
        {top.length === 0 ? (
          <p className="rounded-md border border-dashed border-border py-8 text-center text-sm text-muted-foreground">
            No product activity yet.
          </p>
        ) : (
          <ul className="divide-y divide-border">
            {top.map((product) => (
              <li key={product.productId} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0 flex-1">
                  <Link
                    href={`/products/${product.productId}`}
                    className="flex items-center gap-1 truncate text-sm font-medium hover:text-primary"
                  >
                    {product.productName}
                    <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  </Link>
                  <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-primary/70"
                      style={{ width: `${(product.recommendationCount / max) * 100}%` }}
                    />
                  </div>
                </div>
                <div className="shrink-0 text-right">
                  <p className="text-sm font-semibold tabular-nums">
                    {product.recommendationCount.toLocaleString()}
                  </p>
                  <p
                    className="text-xs font-medium tabular-nums"
                    style={{ color: confidenceColor(product.averageConfidence) }}
                  >
                    {(product.averageConfidence * 100).toFixed(0)}% conf.
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
