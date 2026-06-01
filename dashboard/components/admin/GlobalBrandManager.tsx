'use client';

import { useEffect, useMemo, useState } from 'react';

import { ScrapeHistoryDrawer } from '@/components/admin/ScrapeHistoryDrawer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { api, ApiError } from '@/lib/api';
import type { Brand, GlobalBrandSizeChartVersion, SizeChartUploadResult } from '@/lib/types';

function slugPreview(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9\s-]/g, '')
    .replaceAll(/\s+/g, '-')
    .replaceAll(/-+/g, '-')
    .replaceAll(/^-|-$/g, '');
}

type GlobalBrandManagerProps = {
  brands: Brand[];
  loading: boolean;
  onRefresh: () => Promise<void>;
};

async function loadBrandChartMeta(brandId: string): Promise<{ hasActive: boolean; lastUpdated: string | null }> {
  try {
    const versionsForBrand = await api.adminGetGlobalBrandSizeCharts(brandId);
    const active = versionsForBrand.some((item) => item.active);
    const lastUpdated = versionsForBrand.length > 0 ? versionsForBrand[0].createdAt : null;
    return { hasActive: active, lastUpdated };
  } catch {
    return { hasActive: false, lastUpdated: null };
  }
}

export function GlobalBrandManager({ brands, loading, onRefresh }: Readonly<GlobalBrandManagerProps>) {
  const [newBrandName, setNewBrandName] = useState('');
  const [editingBrandId, setEditingBrandId] = useState<string | null>(null);
  const [editingBrandName, setEditingBrandName] = useState('');
  const [uploadBrand, setUploadBrand] = useState<Brand | null>(null);
  const [historyBrand, setHistoryBrand] = useState<Brand | null>(null);
  const [scrapingBrandId, setScrapingBrandId] = useState<string | null>(null);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadResult, setUploadResult] = useState<SizeChartUploadResult | null>(null);
  const [versions, setVersions] = useState<GlobalBrandSizeChartVersion[]>([]);
  const [chartMetaByBrand, setChartMetaByBrand] = useState<Record<string, { hasActive: boolean; lastUpdated: string | null }>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const globalBrands = useMemo(() => brands.filter((brand) => brand.isGlobal), [brands]);
  const rows = loading ? [] : globalBrands;

  useEffect(() => {
    let cancelled = false;

    async function loadChartMeta() {
      const entries = await Promise.all(
        globalBrands.map(async (brand) => [brand.id, await loadBrandChartMeta(brand.id)] as const)
      );

      if (!cancelled) {
        setChartMetaByBrand(Object.fromEntries(entries));
      }
    }

    if (globalBrands.length > 0) {
      void loadChartMeta();
    } else {
      setChartMetaByBrand({});
    }

    return () => {
      cancelled = true;
    };
  }, [globalBrands]);

  async function createBrand() {
    const name = newBrandName.trim();
    if (!name) {
      setError('Brand name is required.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await api.adminCreateBrand(name);
      setNewBrandName('');
      await onRefresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to create global brand.');
    } finally {
      setSubmitting(false);
    }
  }

  async function saveBrand(brandId: string) {
    const name = editingBrandName.trim();
    if (!name) {
      setError('Brand name is required.');
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await api.adminUpdateBrand(brandId, name);
      setEditingBrandId(null);
      setEditingBrandName('');
      await onRefresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to update global brand.');
    } finally {
      setSubmitting(false);
    }
  }

  async function deleteBrand(brandId: string) {
    if (!confirm('Delete this global brand? Existing product links will be removed.')) {
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await api.adminDeleteBrand(brandId);
      await onRefresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to delete global brand.');
    } finally {
      setSubmitting(false);
    }
  }

  async function openUpload(brand: Brand) {
    setUploadBrand(brand);
    setUploadFile(null);
    setUploadResult(null);
    try {
      const data = await api.adminGetGlobalBrandSizeCharts(brand.id);
      setVersions(data);
    } catch {
      setVersions([]);
    }
  }

  async function uploadGlobalChart() {
    if (!uploadBrand || !uploadFile) {
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      const result = await api.adminUploadGlobalSizeChart(uploadBrand.id, uploadFile);
      setUploadResult(result);
      const data = await api.adminGetGlobalBrandSizeCharts(uploadBrand.id);
      setVersions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to upload size chart.');
    } finally {
      setSubmitting(false);
    }
  }

  async function deactivateActiveChart() {
    if (!uploadBrand) {
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      await api.adminDeactivateGlobalBrandActiveSizeChart(uploadBrand.id);
      const data = await api.adminGetGlobalBrandSizeCharts(uploadBrand.id);
      setVersions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to deactivate active chart.');
    } finally {
      setSubmitting(false);
    }
  }

  async function triggerScrape(brand: Brand) {
    setScrapingBrandId(brand.id);
    setError(null);
    try {
      await api.adminTriggerScrape(brand.id);
      await onRefresh();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(`Scrape already running for ${brand.name}.`);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not trigger scrape.');
      }
    } finally {
      setScrapingBrandId(null);
    }
  }

  function isStaleScrape(lastScrapedAt: string | null | undefined): boolean {
    if (!lastScrapedAt) return true;
    const thirtyDaysAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;
    return new Date(lastScrapedAt).getTime() < thirtyDaysAgo;
  }

  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-border bg-card p-4">
        <h2 className="text-lg font-semibold">Create Global Brand</h2>
        <p className="mt-1 text-sm text-muted-foreground">Brand will be available to all stores.</p>
        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <Input value={newBrandName} onChange={(event) => setNewBrandName(event.target.value)} placeholder="Brand name" />
          <Button type="button" onClick={createBrand} disabled={submitting}>
            {submitting ? 'Saving...' : 'Create'}
          </Button>
        </div>
        {newBrandName.trim() ? (
          <p className="mt-2 text-xs text-muted-foreground">Slug preview: {slugPreview(newBrandName) || '-'}</p>
        ) : null}
      </section>

      {error ? <p className="text-sm text-rose-600">{error}</p> : null}

      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[900px] text-sm">
          <thead className="bg-muted/50 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-3 font-medium">Brand</th>
              <th className="px-4 py-3 font-medium">Slug</th>
              <th className="px-4 py-3 font-medium">Source</th>
              <th className="px-4 py-3 font-medium">Size Chart</th>
              <th className="px-4 py-3 font-medium">Last updated</th>
              <th className="px-4 py-3 font-medium">Last scraped</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td className="px-4 py-6 text-muted-foreground" colSpan={7}>Loading global brands...</td>
              </tr>
            ) : null}
            {!loading && globalBrands.length === 0 ? (
              <tr>
                <td className="px-4 py-6 text-muted-foreground" colSpan={7}>No global brands yet.</td>
              </tr>
            ) : null}
            {rows.map((brand) => (
              <tr key={brand.id} className="border-t border-border/70">
                {/** Render the latest chart metadata preloaded for each brand */}
                <td className="px-4 py-3">
                  {editingBrandId === brand.id ? (
                    <Input value={editingBrandName} onChange={(event) => setEditingBrandName(event.target.value)} />
                  ) : (
                    <span className="font-medium">{brand.name}</span>
                  )}
                </td>
                <td className="px-4 py-3">{brand.slug}</td>
                <td className="px-4 py-3">{brand.source}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${
                      chartMetaByBrand[brand.id]?.hasActive ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {chartMetaByBrand[brand.id]?.hasActive ? 'Active' : 'None'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {(() => {
                    const lastUpdated = chartMetaByBrand[brand.id]?.lastUpdated;
                    return lastUpdated ? new Date(lastUpdated).toLocaleString() : '-';
                  })()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {brand.lastScrapedAt ? (
                      <span className="text-xs tabular-nums">{new Date(brand.lastScrapedAt).toLocaleDateString()}</span>
                    ) : (
                      <span className="text-xs text-muted-foreground">Never</span>
                    )}
                    {isStaleScrape(brand.lastScrapedAt) ? (
                      <span className="inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                        Stale
                      </span>
                    ) : null}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    {editingBrandId === brand.id ? (
                      <>
                        <Button type="button" size="sm" variant="outline" onClick={() => saveBrand(brand.id)} disabled={submitting}>
                          Save
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setEditingBrandId(null);
                            setEditingBrandName('');
                          }}
                        >
                          Cancel
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setEditingBrandId(brand.id);
                            setEditingBrandName(brand.name);
                          }}
                        >
                          Edit name
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={() => openUpload(brand)}>
                          Upload chart
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => triggerScrape(brand)}
                          disabled={scrapingBrandId === brand.id}
                        >
                          {scrapingBrandId === brand.id ? 'Starting...' : 'Scrape now'}
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={() => setHistoryBrand(brand)}>
                          History
                        </Button>
                        <Button type="button" size="sm" variant="outline" onClick={() => deleteBrand(brand.id)} disabled={submitting}>
                          Delete
                        </Button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {historyBrand ? (
        <ScrapeHistoryDrawer
          brandId={historyBrand.id}
          brandName={historyBrand.name}
          onClose={() => setHistoryBrand(null)}
        />
      ) : null}

      {uploadBrand ? (
        <div className="fixed inset-0 z-50">
          <button className="absolute inset-0 bg-slate-950/35" onClick={() => setUploadBrand(null)} aria-label="Close global brand upload" />
          <aside className="absolute right-0 top-0 h-full w-full max-w-2xl overflow-y-auto border-l border-border bg-background p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <div>
                <h3 className="text-xl font-semibold">{uploadBrand.name}</h3>
                <p className="text-sm text-muted-foreground">Upload a global size chart version</p>
              </div>
              <Button type="button" variant="outline" onClick={() => setUploadBrand(null)}>
                Close
              </Button>
            </div>

            <div className="rounded-lg border border-border bg-card p-4">
              <Input
                type="file"
                accept=".csv,.xlsx"
                onChange={(event) => {
                  const file = event.target.files?.[0] || null;
                  setUploadFile(file);
                }}
              />
              <div className="mt-3 flex flex-wrap gap-2">
                <Button type="button" onClick={uploadGlobalChart} disabled={!uploadFile || submitting}>
                  {submitting ? 'Uploading...' : 'Upload'}
                </Button>
                <Button type="button" variant="outline" onClick={deactivateActiveChart} disabled={submitting}>
                  Deactivate active chart
                </Button>
              </div>
              {uploadResult ? (
                <p className="mt-2 text-sm text-muted-foreground">
                  Uploaded version {uploadResult.version.toLocaleString()} with {uploadResult.entriesSaved.toLocaleString()} entries.
                </p>
              ) : null}
            </div>

            <div className="mt-5">
              <h4 className="mb-2 text-base font-semibold">Version history</h4>
              <div className="overflow-x-auto rounded-lg border border-border bg-card">
                <table className="w-full text-sm">
                  <thead className="bg-muted/50 text-left text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2 font-medium">Version</th>
                      <th className="px-3 py-2 font-medium">Status</th>
                      <th className="px-3 py-2 font-medium">Source</th>
                      <th className="px-3 py-2 font-medium">Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {versions.length === 0 ? (
                      <tr>
                        <td className="px-3 py-3 text-muted-foreground" colSpan={4}>No versions available.</td>
                      </tr>
                    ) : (
                      versions.map((version) => (
                        <tr key={version.id} className="border-t border-border/70">
                          <td className="px-3 py-2">v{version.version.toLocaleString()}</td>
                          <td className="px-3 py-2">
                            <span
                              className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${
                                version.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-700'
                              }`}
                            >
                              {version.active ? 'Active' : 'Inactive'}
                            </span>
                          </td>
                          <td className="px-3 py-2">{version.source}</td>
                          <td className="px-3 py-2">{new Date(version.createdAt).toLocaleString()}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </aside>
        </div>
      ) : null}
    </div>
  );
}
