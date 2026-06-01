import Link from 'next/link';

import type { ProductRecommendationStat } from '@/lib/types';

type TopProductsTableProps = {
  products: ProductRecommendationStat[];
};

export function TopProductsTable({ products }: Readonly<TopProductsTableProps>) {
  return (
    <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-semibold">Top Products</h2>
        <span className="text-xs text-muted-foreground">Top 5 by recommendations</span>
      </div>

      {products.length === 0 ? (
        <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
          No top products yet.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[420px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-3 font-medium">Product name</th>
                <th className="py-2 pr-3 font-medium">Recommendations</th>
                <th className="py-2 pr-3 font-medium">Avg confidence</th>
                <th className="py-2 font-medium">Details</th>
              </tr>
            </thead>
            <tbody>
              {products.slice(0, 5).map((product) => (
                <tr key={product.productId} className="border-b border-border/70">
                  <td className="py-3 pr-3 font-medium">{product.productName}</td>
                  <td className="py-3 pr-3">{product.recommendationCount.toLocaleString()}</td>
                  <td className="py-3 pr-3">{(product.averageConfidence * 100).toFixed(1)}%</td>
                  <td className="py-3">
                    <Link href={`/products/${product.productId}`} className="text-primary underline-offset-4 hover:underline">
                      Open
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
