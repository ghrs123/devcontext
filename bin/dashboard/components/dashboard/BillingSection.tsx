'use client';

import { useState } from 'react';

import { PlanComparisonTable } from '@/components/dashboard/PlanComparisonTable';
import { UsageBar } from '@/components/dashboard/UsageBar';
import { Button } from '@/components/ui/button';
import { api, ApiError } from '@/lib/api';
import { useBilling } from '@/hooks/useBilling';
import type { BillingStatusResponse } from '@/lib/types';

type Plan = BillingStatusResponse['plan'];

const STATUS_BADGE: Record<string, string> = {
  active:   'bg-emerald-100 text-emerald-700',
  inactive: 'bg-slate-100 text-slate-600',
  past_due: 'bg-amber-100 text-amber-700',
  canceled: 'bg-rose-100 text-rose-700',
};

export function BillingSection() {
  const { billing, isLoading, refresh } = useBilling();
  const [upgrading, setUpgrading] = useState<Plan | null>(null);
  const [portaling, setPortaling] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleUpgrade(plan: Plan) {
    setUpgrading(plan);
    setError(null);
    try {
      const { checkoutUrl } = await api.createCheckoutSession(plan);
      window.location.href = checkoutUrl;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start checkout. Please try again.');
      setUpgrading(null);
    }
  }

  async function handlePortal() {
    setPortaling(true);
    setError(null);
    try {
      const { portalUrl } = await api.createPortalSession();
      window.location.href = portalUrl;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open billing portal. Please try again.');
      setPortaling(false);
    }
  }

  if (isLoading || !billing) {
    return (
      <div className="animate-pulse space-y-3 rounded-xl border border-border bg-card p-5">
        <div className="h-5 w-32 rounded bg-muted" />
        <div className="h-3 w-full rounded bg-muted" />
        <div className="h-3 w-3/4 rounded bg-muted" />
      </div>
    );
  }

  const statusClass = STATUS_BADGE[billing.subscriptionStatus] ?? 'bg-slate-100 text-slate-600';
  const isPaid = billing.plan !== 'FREE';

  return (
    <div className="space-y-5 rounded-xl border border-border bg-card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">Plan &amp; Billing</h3>
          <p className="text-sm text-muted-foreground">Manage your subscription and monitor usage.</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-flex rounded-full bg-foreground px-3 py-1 text-xs font-semibold text-background">
            {billing.plan}
          </span>
          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium capitalize ${statusClass}`}>
            {billing.subscriptionStatus.replace('_', ' ')}
          </span>
        </div>
      </div>

      {billing.currentPeriodEnd ? (
        <p className="text-sm text-muted-foreground">
          Current period ends{' '}
          <span className="font-medium text-foreground">
            {new Date(billing.currentPeriodEnd).toLocaleDateString()}
          </span>
        </p>
      ) : null}

      <div className="space-y-3">
        <UsageBar label="Products" used={billing.productsUsed} limit={billing.productsLimit} />
        <UsageBar label="Recommendations (this month)" used={billing.recommendationsUsed} limit={billing.recommendationsLimit} />
      </div>

      {error ? <p className="text-sm text-rose-600">{error}</p> : null}

      <div className="space-y-3 border-t border-border pt-4">
        <h4 className="text-sm font-semibold">Plans</h4>
        <PlanComparisonTable
          currentPlan={billing.plan}
          onUpgrade={handleUpgrade}
          upgrading={upgrading}
        />
      </div>

      {isPaid && (
        <div className="border-t border-border pt-4">
          <Button type="button" variant="outline" disabled={portaling} onClick={handlePortal}>
            {portaling ? 'Opening portal...' : 'Manage billing'}
          </Button>
          <p className="mt-1 text-xs text-muted-foreground">
            Download invoices, change payment method, or cancel your subscription.
          </p>
        </div>
      )}

      <button type="button" className="text-xs text-muted-foreground underline" onClick={() => { void refresh(); }}>
        Refresh status
      </button>
    </div>
  );
}
