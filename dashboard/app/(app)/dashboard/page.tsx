'use client';

import Link from 'next/link';
import useSWR from 'swr';
import { Activity, CalendarClock, Layers, Sparkles } from 'lucide-react';

import { QualityChart } from '@/components/app/QualityChart';
import { StatCard } from '@/components/app/StatCard';
import { TopProductsTable } from '@/components/app/TopProductsTable';
import { Button } from '@/components/ui/button';
import { api } from '@/lib/api';

const REVALIDATE_MS = 60000;

function confidenceTone(score: number): 'good' | 'warn' | 'bad' {
  const percent = score * 100;
  if (percent >= 80) return 'good';
  if (percent >= 50) return 'warn';
  return 'bad';
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <div className="h-7 w-48 animate-pulse rounded-md bg-muted" />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-32 animate-pulse rounded-lg border border-border bg-card" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
        <div className="h-72 animate-pulse rounded-lg border border-border bg-card" />
        <div className="h-72 animate-pulse rounded-lg border border-border bg-card" />
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { data: summary, isLoading: loadingSummary } = useSWR('analytics-summary', api.getAnalyticsSummary, {
    refreshInterval: REVALIDATE_MS,
    revalidateOnFocus: true
  });
  const { data: products, isLoading: loadingProducts } = useSWR('products', api.getProducts, {
    refreshInterval: REVALIDATE_MS,
    revalidateOnFocus: true
  });

  if (loadingSummary || loadingProducts || !summary || !products) {
    return <DashboardSkeleton />;
  }

  const totalRecommendations = summary.totalRecommendations ?? 0;
  const last30Days = summary.recommendationsLast30Days ?? 0;
  const previous30Days = Math.max(totalRecommendations - last30Days, 0);
  const trend =
    previous30Days > 0 ? ((last30Days - previous30Days) / previous30Days) * 100 : null;

  const productsWithChart = products.filter((p) => p.hasSizeChart).length;
  const coverage = products.length > 0 ? (productsWithChart / products.length) * 100 : 0;
  let coverageTone: 'good' | 'warn' | 'bad' = 'bad';
  if (coverage >= 80) coverageTone = 'good';
  else if (coverage >= 50) coverageTone = 'warn';

  const avgConfidence = summary.averageConfidenceScore ?? 0;

  if (totalRecommendations === 0) {
    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Welcome to FitVision</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Two steps to your first size recommendation.
          </p>
        </div>

        <div className="rounded-lg border border-border bg-card p-8">
          <ol className="space-y-5">
            {[
              { n: 1, t: 'Add a product', d: 'Match it to a product in your store by its external ID.' },
              { n: 2, t: 'Upload a size chart', d: 'Chest / waist / hip / height ranges per size — CSV, Excel, or manual.' }
            ].map((step) => (
              <li key={step.n} className="flex gap-4">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-sm font-semibold text-primary-soft-foreground">
                  {step.n}
                </span>
                <div>
                  <p className="text-sm font-medium">{step.t}</p>
                  <p className="text-sm text-muted-foreground">{step.d}</p>
                </div>
              </li>
            ))}
          </ol>
          <div className="mt-7">
            <Button asChild>
              <Link href="/products">Add your first product</Link>
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">Overview</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Recommendation activity and size-chart coverage across your catalogue.
          </p>
        </div>
        <Button variant="secondary" size="sm" asChild>
          <Link href="/products">Manage products</Link>
        </Button>
      </div>

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Total recommendations"
          value={totalRecommendations.toLocaleString()}
          icon={Sparkles}
        />
        <StatCard
          title="Last 30 days"
          value={last30Days.toLocaleString()}
          icon={CalendarClock}
          delta={trend !== null ? { value: trend } : null}
          subtitle={trend === null ? 'no prior period' : 'vs previous 30 days'}
        />
        <StatCard
          title="Average confidence"
          value={`${(avgConfidence * 100).toFixed(1)}%`}
          icon={Activity}
          tone={confidenceTone(avgConfidence)}
        />
        <StatCard
          title="Size-chart coverage"
          value={`${coverage.toFixed(0)}%`}
          icon={Layers}
          tone={coverageTone}
          subtitle={`${productsWithChart} of ${products.length} products`}
        />
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_1fr]">
        <QualityChart distribution={summary.qualityDistribution || {}} />
        <TopProductsTable products={summary.topProducts || []} />
      </section>
    </div>
  );
}
