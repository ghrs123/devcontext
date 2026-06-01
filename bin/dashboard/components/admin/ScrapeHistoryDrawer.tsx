'use client';

import { useEffect, useState } from 'react';

import { ScrapeStatusBadge } from '@/components/admin/ScrapeStatusBadge';
import { Button } from '@/components/ui/button';
import { api, ApiError } from '@/lib/api';
import type { ScrapeJobResponse } from '@/lib/types';

type Props = {
  brandId: string;
  brandName: string;
  onClose: () => void;
};

function durationSeconds(job: ScrapeJobResponse): number | null {
  if (!job.startedAt || !job.completedAt) return null;
  const ms = new Date(job.completedAt).getTime() - new Date(job.startedAt).getTime();
  return Math.round(ms / 1000);
}

export function ScrapeHistoryDrawer({ brandId, brandName, onClose }: Readonly<Props>) {
  const [jobs, setJobs] = useState<ScrapeJobResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [triggering, setTriggering] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      try {
        const data = await api.adminGetScrapeJobs(brandId);
        if (!cancelled) setJobs(data);
      } catch {
        if (!cancelled) setError('Could not load scrape history.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [brandId]);

  // Poll while any job is RUNNING or PENDING
  useEffect(() => {
    const hasActive = jobs.some((j) => j.status === 'RUNNING' || j.status === 'PENDING');
    if (!hasActive) return;

    const timer = setInterval(async () => {
      try {
        const data = await api.adminGetScrapeJobs(brandId);
        setJobs(data);
      } catch {
        // silent — polling failures are non-fatal
      }
    }, 5000);

    return () => clearInterval(timer);
  }, [brandId, jobs]);

  async function triggerScrape() {
    setTriggering(true);
    setError(null);
    try {
      const job = await api.adminTriggerScrape(brandId);
      setJobs((prev) => [job, ...prev]);
      setToast('Scrape started.');
      setTimeout(() => setToast(null), 3000);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setToast('Scrape already running.');
        setTimeout(() => setToast(null), 3000);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not trigger scrape.');
      }
    } finally {
      setTriggering(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50">
      <button
        className="absolute inset-0 bg-slate-950/35"
        onClick={onClose}
        aria-label="Close scrape history"
      />
      <aside className="absolute right-0 top-0 h-full w-full max-w-2xl overflow-y-auto border-l border-border bg-background p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h3 className="text-xl font-semibold">{brandName} — Scrape History</h3>
            <p className="text-sm text-muted-foreground">Past scrape jobs for this global brand.</p>
          </div>
          <Button type="button" variant="outline" onClick={onClose}>Close</Button>
        </div>

        <div className="mb-4 flex items-center gap-3">
          <Button type="button" onClick={triggerScrape} disabled={triggering}>
            {triggering ? 'Starting...' : 'Scrape now'}
          </Button>
          {toast ? <span className="text-sm text-muted-foreground">{toast}</span> : null}
        </div>

        {error ? <p className="mb-3 text-sm text-rose-600">{error}</p> : null}

        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table className="w-full text-sm">
            <thead className="bg-muted/50 text-left text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">Date</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Duration</th>
                <th className="px-3 py-2 font-medium">Entries</th>
                <th className="px-3 py-2 font-medium">Error</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td className="px-3 py-4 text-muted-foreground" colSpan={5}>Loading...</td>
                </tr>
              ) : jobs.length === 0 ? (
                <tr>
                  <td className="px-3 py-4 text-muted-foreground" colSpan={5}>No scrape history yet.</td>
                </tr>
              ) : (
                jobs.map((job) => {
                  const dur = durationSeconds(job);
                  return (
                    <tr key={job.id} className="border-t border-border/70">
                      <td className="px-3 py-2 tabular-nums">{new Date(job.createdAt).toLocaleString()}</td>
                      <td className="px-3 py-2"><ScrapeStatusBadge status={job.status} /></td>
                      <td className="px-3 py-2 tabular-nums">{dur !== null ? `${dur}s` : '—'}</td>
                      <td className="px-3 py-2 tabular-nums">{job.entriesFound ?? '—'}</td>
                      <td className="px-3 py-2 max-w-[200px] truncate text-rose-600" title={job.errorMessage ?? undefined}>
                        {job.errorMessage ?? '—'}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </aside>
    </div>
  );
}
