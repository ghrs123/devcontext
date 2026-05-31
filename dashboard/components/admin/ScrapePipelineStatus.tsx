'use client';

import { useState } from 'react';
import { RefreshCw } from 'lucide-react';

import { ScrapeStatusBadge } from '@/components/admin/ScrapeStatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { api, ApiError } from '@/lib/api';
import type { BrandScrapeStatus, ScrapeJobStatus } from '@/lib/types';

type ScrapePipelineStatusProps = {
  brandScrapes: BrandScrapeStatus[];
  onRefresh: () => void;
};

export function ScrapePipelineStatus({ brandScrapes, onRefresh }: Readonly<ScrapePipelineStatusProps>) {
  const [triggeringAll, setTriggeringAll] = useState(false);
  const [triggeringBrandId, setTriggeringBrandId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleTriggerAll() {
    setTriggeringAll(true);
    setError(null);
    setMessage(null);
    try {
      const result = await api.adminTriggerAllScrapes();
      setMessage(`Triggered ${result.triggered} scrape(s), skipped ${result.skipped}.`);
      onRefresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to trigger scrapes.');
    } finally {
      setTriggeringAll(false);
    }
  }

  async function handleTriggerBrand(brandId: string) {
    setTriggeringBrandId(brandId);
    setError(null);
    setMessage(null);
    try {
      await api.adminTriggerScrape(brandId);
      setMessage('Scrape job queued.');
      onRefresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to trigger scrape.');
    } finally {
      setTriggeringBrandId(null);
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
        <div>
          <CardTitle>Scrape pipeline</CardTitle>
          <CardDescription>Last scrape per global brand</CardDescription>
        </div>
        <Button
          variant="outline"
          size="sm"
          disabled={triggeringAll}
          onClick={() => void handleTriggerAll()}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${triggeringAll ? 'animate-spin' : ''}`} />
          Force re-scrape all
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        {message ? <p className="text-sm text-emerald-600">{message}</p> : null}
        {error ? <p className="text-sm text-rose-600">{error}</p> : null}

        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-muted/50 text-left text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">Brand</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Timestamp</th>
                <th className="px-3 py-2 font-medium">Entries</th>
                <th className="px-3 py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {brandScrapes.length === 0 ? (
                <tr>
                  <td className="px-3 py-3 text-muted-foreground" colSpan={5}>
                    No global brands configured.
                  </td>
                </tr>
              ) : (
                brandScrapes.map((brand) => (
                  <tr key={brand.brandId} className="border-t border-border/70">
                    <td className="px-3 py-2">{brand.brandName}</td>
                    <td className="px-3 py-2">
                      {brand.status ? (
                        <ScrapeStatusBadge status={brand.status as ScrapeJobStatus} />
                      ) : (
                        <span className="text-muted-foreground">Never scraped</span>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {brand.timestamp ? new Date(brand.timestamp).toLocaleString() : '—'}
                    </td>
                    <td className="px-3 py-2">{brand.entriesFound ?? '—'}</td>
                    <td className="px-3 py-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={!brand.scraperAvailable || triggeringBrandId === brand.brandId}
                        onClick={() => void handleTriggerBrand(brand.brandId)}
                      >
                        {triggeringBrandId === brand.brandId ? 'Queuing…' : 'Force re-scrape'}
                      </Button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
