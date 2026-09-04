'use client';

import { useState } from 'react';
import useSWR from 'swr';
import { FlaskConical } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { api, ApiError } from '@/lib/api';
import { useT } from '@/lib/i18n/I18nProvider';
import type { SimulateResponse } from '@/lib/types';
import { cn } from '@/lib/utils';

function fmtRange(r: { min: number | null; max: number | null }) {
  if (r.min == null && r.max == null) return '—';
  return `${r.min ?? '—'} – ${r.max ?? '—'}`;
}

function confVariant(label: string) {
  if (label === 'High') return 'success' as const;
  if (label === 'Medium') return 'warning' as const;
  return 'danger' as const;
}

export default function SimulatorPage() {
  const t = useT();
  const { data: products } = useSWR('products', api.getProducts);

  const [productId, setProductId] = useState('');
  const [height, setHeight] = useState('175');
  const [weight, setWeight] = useState('72');
  const [gender, setGender] = useState('MALE');
  const [age, setAge] = useState('30');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<SimulateResponse | null>(null);

  const hasProducts = (products?.length ?? 0) > 0;

  async function run(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await api.simulateRecommendation({
        productId,
        heightCm: Number(height),
        weightKg: Number(weight),
        gender,
        age: age ? Number(age) : undefined
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Simulation failed.');
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="flex items-center gap-2 text-xl font-semibold tracking-tight">
          <FlaskConical className="h-5 w-5 text-primary" />
          {t('sim.title')}
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">{t('sim.subtitle')}</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,360px)_1fr]">
        <Card>
          <CardContent className="pt-5 sm:pt-6">
            {!hasProducts ? (
              <p className="text-sm text-muted-foreground">{t('sim.noProducts')}</p>
            ) : (
              <form onSubmit={run} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="sim-product">{t('sim.product')}</Label>
                  <select
                    id="sim-product"
                    required
                    value={productId}
                    onChange={(ev) => setProductId(ev.target.value)}
                    className="h-10 w-full rounded-md border border-border-strong bg-card px-3 text-sm"
                  >
                    <option value="" disabled>
                      {t('sim.selectProduct')}
                    </option>
                    {products?.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="sim-h">{t('sim.height')}</Label>
                    <Input id="sim-h" type="number" min={50} max={250} value={height} onChange={(e) => setHeight(e.target.value)} required />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="sim-w">{t('sim.weight')}</Label>
                    <Input id="sim-w" type="number" min={20} max={300} value={weight} onChange={(e) => setWeight(e.target.value)} required />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label htmlFor="sim-g">{t('sim.gender')}</Label>
                    <select
                      id="sim-g"
                      value={gender}
                      onChange={(e) => setGender(e.target.value)}
                      className="h-10 w-full rounded-md border border-border-strong bg-card px-3 text-sm"
                    >
                      <option value="MALE">{t('sim.gender.male')}</option>
                      <option value="FEMALE">{t('sim.gender.female')}</option>
                      <option value="UNISEX">{t('sim.gender.unisex')}</option>
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="sim-a">
                      {t('sim.age')} <span className="text-muted-foreground">({t('common.optional')})</span>
                    </Label>
                    <Input id="sim-a" type="number" min={10} max={120} value={age} onChange={(e) => setAge(e.target.value)} />
                  </div>
                </div>

                {error ? (
                  <p className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
                ) : null}

                <Button type="submit" className="w-full" disabled={loading}>
                  {loading ? t('sim.running') : t('sim.run')}
                </Button>
              </form>
            )}
          </CardContent>
        </Card>

        {result ? (
          <div className="space-y-4">
            <Card>
              <CardContent className="pt-5 sm:pt-6">
                {result.recommendedSize ? (
                  <div className="flex flex-wrap items-center gap-4">
                    <div>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">{t('sim.result')}</p>
                      <p className="text-4xl font-semibold leading-tight">{result.recommendedSize}</p>
                    </div>
                    <div className="space-y-1">
                      <Badge variant={confVariant(result.confidenceLabel)}>
                        {result.confidenceLabel} · {(result.confidenceScore * 100).toFixed(0)}%
                      </Badge>
                      <p className="text-xs text-muted-foreground">{result.quality}</p>
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('sim.noChart')}</p>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{t('sim.estimated')}</CardTitle>
                <CardDescription>{t('sim.estimatedNote')}</CardDescription>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                  {[
                    [t('sim.bmi'), result.estimatedProfile.bmi.toFixed(1)],
                    [t('sizeChart.col.chest'), `${result.estimatedProfile.chestCm.toFixed(0)} cm`],
                    [t('sizeChart.col.waist'), `${result.estimatedProfile.waistCm.toFixed(0)} cm`],
                    [t('sizeChart.col.hip'), `${result.estimatedProfile.hipCm.toFixed(0)} cm`]
                  ].map(([k, v]) => (
                    <div key={k}>
                      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{k}</dt>
                      <dd className="mt-1 text-sm font-semibold tabular-nums">{v}</dd>
                    </div>
                  ))}
                </dl>
              </CardContent>
            </Card>

            {result.sizeChart.length > 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle>{t('sim.chestChart')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="overflow-x-auto rounded-md border border-border">
                    <table className="w-full min-w-[560px] text-sm">
                      <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 font-semibold">{t('sizeChart.col.size')}</th>
                          <th className="px-3 py-2 font-semibold">{t('sizeChart.col.chest')}</th>
                          <th className="px-3 py-2 font-semibold">{t('sizeChart.col.waist')}</th>
                          <th className="px-3 py-2 font-semibold">{t('sizeChart.col.hip')}</th>
                          <th className="px-3 py-2 font-semibold">{t('sizeChart.col.height')}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border tabular-nums">
                        {result.sizeChart.map((row) => (
                          <tr
                            key={row.size}
                            className={cn(row.recommended && 'bg-primary-soft/60 font-medium')}
                          >
                            <td className="px-3 py-2">
                              {row.size}
                              {row.recommended ? (
                                <span className="ml-2 text-xs text-primary">◄ {t('sim.recommendedRow')}</span>
                              ) : null}
                            </td>
                            <td className="px-3 py-2 text-muted-foreground">{fmtRange(row.chest)}</td>
                            <td className="px-3 py-2 text-muted-foreground">{fmtRange(row.waist)}</td>
                            <td className="px-3 py-2 text-muted-foreground">{fmtRange(row.hip)}</td>
                            <td className="px-3 py-2 text-muted-foreground">{fmtRange(row.height)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>
            ) : null}
          </div>
        ) : (
          <Card className="hidden lg:block">
            <CardContent className="flex h-full items-center justify-center py-16 text-center text-sm text-muted-foreground">
              {t('sim.subtitle')}
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
