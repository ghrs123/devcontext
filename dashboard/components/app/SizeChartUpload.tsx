'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Upload, Plus, Trash2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { api, ApiError } from '@/lib/api';
import type { Product, SizeEntryData, SizeChartUploadResult } from '@/lib/types';
import { SizeChartTable } from '@/components/app/SizeChartTable';

type SizeChartUploadProps = {
  product: Product | null;
  open: boolean;
  onClose: () => void;
  onUploaded: () => Promise<void>;
};

type ManualRow = {
  id: string;
  sizeLabel: string;
  chestMin: string;
  chestMax: string;
  waistMin: string;
  waistMax: string;
  hipMin: string;
  hipMax: string;
  heightMin: string;
  heightMax: string;
};

const makeRow = (): ManualRow => ({
  id: crypto.randomUUID(),
  sizeLabel: '',
  chestMin: '',
  chestMax: '',
  waistMin: '',
  waistMax: '',
  hipMin: '',
  hipMax: '',
  heightMin: '',
  heightMax: ''
});

function toNumberOrNull(value: string): number | null {
  if (!value.trim()) {
    return null;
  }
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

function toSizeEntry(row: ManualRow): SizeEntryData | null {
  const label = row.sizeLabel.trim().toUpperCase();
  if (!label) {
    return null;
  }

  return {
    sizeLabel: label,
    chestMin: toNumberOrNull(row.chestMin),
    chestMax: toNumberOrNull(row.chestMax),
    waistMin: toNumberOrNull(row.waistMin),
    waistMax: toNumberOrNull(row.waistMax),
    hipMin: toNumberOrNull(row.hipMin),
    hipMax: toNumberOrNull(row.hipMax),
    heightMin: toNumberOrNull(row.heightMin),
    heightMax: toNumberOrNull(row.heightMax)
  };
}

export function SizeChartUpload({ product, open, onClose, onUploaded }: Readonly<SizeChartUploadProps>) {
  const [activeTab, setActiveTab] = useState<'file' | 'manual'>('file');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [activeEntries, setActiveEntries] = useState<SizeEntryData[]>([]);
  const [rows, setRows] = useState<ManualRow[]>([makeRow()]);
  const [loadingActive, setLoadingActive] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<SizeChartUploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showWarnings, setShowWarnings] = useState(false);

  const refreshActive = useCallback(async () => {
    if (!product) {
      return;
    }
    setLoadingActive(true);
    try {
      const entries = await api.getActiveSizeChart(product.id);
      setActiveEntries(entries || []);
    } catch {
      setActiveEntries([]);
    } finally {
      setLoadingActive(false);
    }
  }, [product]);

  useEffect(() => {
    if (!open || !product) {
      return;
    }

    setError(null);
    setResult(null);
    setShowWarnings(false);
    setSelectedFile(null);
    refreshActive();
  }, [open, product, refreshActive]);

  const canUpload = useMemo(() => selectedFile && !uploading, [selectedFile, uploading]);
  const warnings = result?.warnings ?? [];
  const hasWarnings = warnings.length > 0;

  function validateFile(file: File): string | null {
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.csv') && !lower.endsWith('.xlsx')) {
      return 'Only .csv and .xlsx files are supported.';
    }
    return null;
  }

  function handleFile(file: File) {
    const validationError = validateFile(file);
    if (validationError) {
      setError(validationError);
      setSelectedFile(null);
      return;
    }

    setError(null);
    setSelectedFile(file);
  }

  async function handleFileUpload() {
    if (!product || !selectedFile) {
      return;
    }

    setUploading(true);
    setError(null);

    try {
      const uploadResult = await api.uploadSizeChart(product.id, selectedFile);
      setResult(uploadResult);
      await refreshActive();
      await onUploaded();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Upload failed. Please try again.';
      setError(message);
    } finally {
      setUploading(false);
    }
  }

  async function handleManualSubmit() {
    if (!product) {
      return;
    }

    const payload = rows
      .map(toSizeEntry)
      .filter((entry): entry is SizeEntryData => entry !== null);

    if (!payload.length) {
      setError('Add at least one row with a size label.');
      return;
    }

    setUploading(true);
    setError(null);

    try {
      const uploadResult = await api.uploadManualSizeChart(product.id, payload);
      setResult(uploadResult);
      await refreshActive();
      await onUploaded();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Manual entry failed. Please try again.';
      setError(message);
    } finally {
      setUploading(false);
    }
  }

  function updateRow(rowId: string, key: keyof ManualRow, value: string) {
    setRows((current) =>
      current.map((row) =>
        row.id === rowId
          ? {
              ...row,
              [key]: value
            }
          : row
      )
    );
  }

  function removeRow(rowId: string) {
    setRows((current) => current.filter((item) => item.id !== rowId));
  }

  if (!open || !product) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50">
      <button className="absolute inset-0 bg-slate-950/35" onClick={onClose} aria-label="Close upload panel" />

      <aside className="absolute right-0 top-0 h-full w-full max-w-3xl overflow-y-auto border-l border-border bg-background p-5 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-semibold">Upload Size Chart</h2>
            <p className="text-sm text-muted-foreground">{product.name}</p>
          </div>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
        </div>

        <div className="mb-4 inline-flex rounded-md border border-border bg-muted/40 p-1">
          <button
            className={`rounded px-3 py-1.5 text-sm ${activeTab === 'file' ? 'bg-card text-foreground' : 'text-muted-foreground'}`}
            onClick={() => setActiveTab('file')}
            type="button"
          >
            Upload File
          </button>
          <button
            className={`rounded px-3 py-1.5 text-sm ${activeTab === 'manual' ? 'bg-card text-foreground' : 'text-muted-foreground'}`}
            onClick={() => setActiveTab('manual')}
            type="button"
          >
            Enter Manually
          </button>
        </div>

        {activeTab === 'file' ? (
          <section className="space-y-4">
            <div
              className={`rounded-lg border-2 border-dashed p-6 text-center ${isDragOver ? 'border-primary bg-muted/50' : 'border-border'}`}
              role="region"
              aria-label="File drop zone"
              tabIndex={0}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragOver(true);
              }}
              onDragLeave={(event) => {
                event.preventDefault();
                setIsDragOver(false);
              }}
              onDrop={(event) => {
                event.preventDefault();
                setIsDragOver(false);
                const file = event.dataTransfer.files?.[0];
                if (file) {
                  handleFile(file);
                }
              }}
            >
              <Upload className="mx-auto mb-2 h-7 w-7 text-muted-foreground" />
              <p className="text-sm">Drag and drop a CSV or XLSX file here</p>
              <p className="mt-1 text-xs text-muted-foreground">or choose a file below</p>

              <Input
                className="mx-auto mt-4 max-w-sm"
                type="file"
                accept=".csv,.xlsx"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    handleFile(file);
                  }
                }}
              />
            </div>

            {selectedFile ? (
              <p className="text-sm text-muted-foreground">
                Selected: <span className="font-medium text-foreground">{selectedFile.name}</span> ({(selectedFile.size / 1024).toFixed(1)} KB)
              </p>
            ) : null}

            <div className="flex justify-end">
              <Button onClick={handleFileUpload} disabled={!canUpload}>
                {uploading ? 'Uploading...' : 'Upload file'}
              </Button>
            </div>
          </section>
        ) : (
          <section className="space-y-4">
            <div className="overflow-x-auto rounded-md border border-border">
              <table className="w-full min-w-[980px] text-sm">
                <thead className="bg-muted/60 text-left text-muted-foreground">
                  <tr>
                    <th className="px-2 py-2">Size</th>
                    <th className="px-2 py-2">Chest min</th>
                    <th className="px-2 py-2">Chest max</th>
                    <th className="px-2 py-2">Waist min</th>
                    <th className="px-2 py-2">Waist max</th>
                    <th className="px-2 py-2">Hip min</th>
                    <th className="px-2 py-2">Hip max</th>
                    <th className="px-2 py-2">Height min</th>
                    <th className="px-2 py-2">Height max</th>
                    <th className="px-2 py-2">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="border-t border-border/70">
                      <td className="px-2 py-2"><Input value={row.sizeLabel} onChange={(e) => updateRow(row.id, 'sizeLabel', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.chestMin} onChange={(e) => updateRow(row.id, 'chestMin', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.chestMax} onChange={(e) => updateRow(row.id, 'chestMax', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.waistMin} onChange={(e) => updateRow(row.id, 'waistMin', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.waistMax} onChange={(e) => updateRow(row.id, 'waistMax', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.hipMin} onChange={(e) => updateRow(row.id, 'hipMin', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.hipMax} onChange={(e) => updateRow(row.id, 'hipMax', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.heightMin} onChange={(e) => updateRow(row.id, 'heightMin', e.target.value)} /></td>
                      <td className="px-2 py-2"><Input value={row.heightMax} onChange={(e) => updateRow(row.id, 'heightMax', e.target.value)} /></td>
                      <td className="px-2 py-2">
                        <Button
                          type="button"
                          variant="ghost"
                          onClick={() => removeRow(row.id)}
                          disabled={rows.length === 1}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between">
              <Button type="button" variant="outline" onClick={() => setRows((current) => [...current, makeRow()])}>
                <Plus className="mr-2 h-4 w-4" />
                Add row
              </Button>
              <Button type="button" onClick={handleManualSubmit} disabled={uploading}>
                {uploading ? 'Submitting...' : 'Submit manual chart'}
              </Button>
            </div>
          </section>
        )}

        {error ? <p className="mt-4 text-sm text-rose-600">{error}</p> : null}

        {result ? (
          <div className="mt-4 rounded-md border border-border bg-card p-4">
            <p className="text-sm font-medium">Entries saved: {result.entriesSaved.toLocaleString()} (v{result.version})</p>
            {hasWarnings ? (
              <div className="mt-3">
                <button
                  className="text-sm text-primary underline-offset-4 hover:underline"
                  type="button"
                  onClick={() => setShowWarnings((current) => !current)}
                >
                  {showWarnings ? 'Hide warnings' : `Show warnings (${warnings.length})`}
                </button>
                {showWarnings ? (
                  <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-muted-foreground">
                    {warnings.map((warning, index) => (
                      <li key={`${warning}-${index}`}>{warning}</li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}

        <div className="mt-6 space-y-2">
          <h3 className="text-base font-semibold">Current active size chart</h3>
          {loadingActive ? (
            <div className="h-28 animate-pulse rounded-md border border-border bg-muted" />
          ) : (
            <SizeChartTable entries={activeEntries} />
          )}
        </div>
      </aside>
    </div>
  );
}
