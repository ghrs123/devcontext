'use client';

import useSWR from 'swr';

import { QualityChart } from '@/components/app/QualityChart';
import { StatCard } from '@/components/app/StatCard';
import { TopProductsTable } from '@/components/app/TopProductsTable';
import { api } from '@/lib/api';

const REVALIDATE_MS = 60000;

function confidenceTone(score: number): 'good' | 'warn' | 'bad' {
  const percent = score * 100;
  if (percent >= 80) {
    return 'good';
  }
  if (percent >= 50) {
    return 'warn';
  }
  return 'bad';
}

function DashboardSkeleton() {
  return (
    <main className="mx-auto max-w-7xl space-y-6">
      <div className="h-8 w-56 animate-pulse rounded-md bg-muted" />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-32 animate-pulse rounded-xl border border-border bg-card" />
        ))}
      </div>
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.15fr_1fr]">
        <div className="h-[26rem] animate-pulse rounded-xl border border-border bg-card" />
        <div className="h-[26rem] animate-pulse rounded-xl border border-border bg-card" />
      </div>
    </main>
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
  const trend = previous30Days > 0 ? ((last30Days - previous30Days) / previous30Days) * 100 : null;
  let trendSubtitle = 'Trend unavailable for previous period';
  let trendTone: 'default' | 'good' | 'warn' = 'default';
  if (trend !== null) {
    const trendPrefix = trend >= 0 ? '+' : '';
    trendSubtitle = `${trendPrefix}${trend.toFixed(1)}% vs previous 30 days`;
    trendTone = trend >= 0 ? 'good' : 'warn';
  }

  const productsWithChart = products.filter((product) => product.hasSizeChart).length;
  const coverage = products.length > 0 ? (productsWithChart / products.length) * 100 : 0;
  let coverageTone: 'good' | 'warn' | 'bad' = 'bad';
  if (coverage >= 80) {
    coverageTone = 'good';
  } else if (coverage >= 50) {
    coverageTone = 'warn';
  }
  const avgConfidence = summary.averageConfidenceScore ?? 0;

  if (totalRecommendations === 0) {
    return (
      <main className="mx-auto max-w-7xl space-y-6">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <section className="rounded-xl border border-dashed border-border bg-card p-10 text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-muted text-2xl">📊</div>
          <h2 className="text-lg font-semibold">No recommendations yet.</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Add a product and upload a size chart to get started.
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-7xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="mt-1 text-sm text-muted-foreground">Overview of recommendations and product performance.</p>
      </div>

      <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Total Recommendations" value={totalRecommendations.toLocaleString()} />
        <StatCard
          title="Last 30 Days"
          value={last30Days.toLocaleString()}
          subtitle={trendSubtitle}
          tone={trendTone}
        />
        <StatCard
          title="Average Confidence"
          value={`${(avgConfidence * 100).toLocaleString(undefined, { maximumFractionDigits: 1 })}%`}
          tone={confidenceTone(avgConfidence)}
        />
        <StatCard
          title="Size Chart Coverage"
          value={`${coverage.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`}
          subtitle={`${productsWithChart.toLocaleString()} of ${products.length.toLocaleString()} products`}
          tone={coverageTone}
        />
      </section>

      <section className="grid grid-cols-1 gap-6 xl:grid-cols-[1.1fr_1fr]">
        <QualityChart distribution={summary.qualityDistribution || {}} />
        <TopProductsTable products={summary.topProducts || []} />
      </section>
    </main>
  );
}
