'use client';

import { useMemo, useState } from 'react';
import useSWR from 'swr';
import { ChevronDown, ChevronUp, Edit3, FileUp, Plus, Search, Trash2 } from 'lucide-react';

import { ProductForm } from '@/components/app/ProductForm';
import { SizeChartUpload } from '@/components/app/SizeChartUpload';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useT } from '@/lib/i18n/I18nProvider';
import { api, ApiError } from '@/lib/api';
import type { Brand, Product, ProductRequest } from '@/lib/types';

function slugPreview(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9\s-]/g, '')
    .replaceAll(/\s+/g, '-')
    .replaceAll(/-+/g, '-')
    .replaceAll(/^-|-$/g, '');
}

export default function ProductsPage() {
  const t = useT();
  const [search, setSearch] = useState('');
  const [brandsOpen, setBrandsOpen] = useState(true);
  const [formOpen, setFormOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);
  const [deleteBrandTarget, setDeleteBrandTarget] = useState<Brand | null>(null);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [uploadProduct, setUploadProduct] = useState<Product | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteBrandLoading, setDeleteBrandLoading] = useState(false);
  const [newBrandName, setNewBrandName] = useState('');
  const [createBrandLoading, setCreateBrandLoading] = useState(false);
  const [createBrandError, setCreateBrandError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isPlanLimitError, setIsPlanLimitError] = useState(false);

  const {
    data: products,
    isLoading,
    mutate: mutateProducts
  } = useSWR('products', api.getProducts, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  const { data: brands, isLoading: brandsLoading, mutate: mutateBrands } = useSWR('brands', api.getBrands, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

  const tenantBrands = useMemo(() => (brands || []).filter((brand) => !brand.isGlobal), [brands]);
  const globalBrands = useMemo(() => (brands || []).filter((brand) => brand.isGlobal), [brands]);

  const linkedBrandCounts = useMemo(() => {
    const counts = new Map<string, number>();
    (products || []).forEach((product) => {
      if (!product.brandId) {
        return;
      }
      counts.set(product.brandId, (counts.get(product.brandId) || 0) + 1);
    });
    return counts;
  }, [products]);

  const filteredProducts = useMemo(() => {
    const list = products || [];
    const term = search.trim().toLowerCase();
    if (!term) {
      return list;
    }

    return list.filter((product) => product.name.toLowerCase().includes(term));
  }, [products, search]);

  let tableContent: React.ReactNode;
  if (isLoading) {
    tableContent = <div className="h-40 animate-pulse rounded-xl border border-border bg-card" />;
  } else if (filteredProducts.length === 0) {
    tableContent = (
      <section className="rounded-lg border border-dashed border-border bg-card p-8 text-center">
        <p className="text-sm font-medium text-muted-foreground">{t('products.empty')}</p>
      </section>
    );
  } else {
    tableContent = (
      <div className="overflow-x-auto rounded-lg border border-border bg-card shadow-sm">
        <table className="w-full min-w-[960px] text-sm">
          <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
            <tr>
              <th className="px-4 py-3 font-semibold">{t('products.col.product')}</th>
              <th className="px-4 py-3 font-semibold">{t('products.col.externalId')}</th>
              <th className="px-4 py-3 font-semibold">{t('products.col.category')}</th>
              <th className="px-4 py-3 font-semibold">{t('products.col.gender')}</th>
              <th className="px-4 py-3 font-semibold">{t('products.col.brand')}</th>
              <th className="px-4 py-3 font-semibold">{t('products.col.sizeChart')}</th>
              <th className="px-4 py-3 text-right font-semibold">{t('products.col.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {filteredProducts.map((product) => (
              <tr key={product.id} className="transition-colors hover:bg-muted/40">
                <td className="px-4 py-3 font-medium">{product.name}</td>
                <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{product.externalProductId}</td>
                <td className="px-4 py-3 text-muted-foreground">{product.category || '—'}</td>
                <td className="px-4 py-3 text-muted-foreground">{product.genderTarget || '—'}</td>
                <td className="px-4 py-3 text-muted-foreground">{product.brandName || '—'}</td>
                <td className="px-4 py-3">
                  {product.hasSizeChart ? (
                    <Badge variant="success">{t('products.badge.ready')}</Badge>
                  ) : (
                    <Badge variant="warning">{t('products.badge.missing')}</Badge>
                  )}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-end gap-1.5">
                    <Button type="button" size="sm" variant="ghost" onClick={() => openEdit(product)}>
                      <Edit3 className="mr-1 h-3.5 w-3.5" />
                      {t('common.edit')}
                    </Button>
                    <Button type="button" size="sm" variant="secondary" onClick={() => openUpload(product)}>
                      <FileUp className="mr-1 h-3.5 w-3.5" />
                      {t('products.action.sizeChart')}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="text-danger hover:bg-danger-soft"
                      onClick={() => setDeleteTarget(product)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  async function handleSaveProduct(payload: ProductRequest) {
    try {
      setError(null);
      setIsPlanLimitError(false);
      if (editingProduct) {
        await api.updateProduct(editingProduct.id, payload);
      } else {
        await api.createProduct(payload);
      }
      await mutateProducts();
      setFormOpen(false);
      setEditingProduct(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 402) {
        setIsPlanLimitError(true);
        setError(err.message);
      } else {
        setError(err instanceof ApiError ? err.message : 'Unable to save product.');
      }
    }
  }

  async function handleDeleteProduct() {
    if (!deleteTarget) {
      return;
    }

    try {
      setDeleteLoading(true);
      setError(null);
      await api.deleteProduct(deleteTarget.id);
      await mutateProducts();
      setDeleteTarget(null);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to delete product.';
      setError(message);
    } finally {
      setDeleteLoading(false);
    }
  }

  async function handleCreateBrand(name: string): Promise<Brand> {
    const normalizedName = name.trim();
    if (!normalizedName) {
      throw new Error('Brand name is required.');
    }

    const created = await api.createBrand(normalizedName);
    await mutateBrands();
    return created;
  }

  async function handleCreateBrandFromSection() {
    try {
      setCreateBrandLoading(true);
      setCreateBrandError(null);
      await handleCreateBrand(newBrandName);
      setNewBrandName('');
    } catch (err) {
      setCreateBrandError(err instanceof Error ? err.message : 'Unable to create brand.');
    } finally {
      setCreateBrandLoading(false);
    }
  }

  async function handleDeleteBrand() {
    if (!deleteBrandTarget) {
      return;
    }

    try {
      setDeleteBrandLoading(true);
      setError(null);
      await api.deleteBrand(deleteBrandTarget.id);
      await Promise.all([mutateBrands(), mutateProducts()]);
      setDeleteBrandTarget(null);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to delete brand.';
      setError(message);
    } finally {
      setDeleteBrandLoading(false);
    }
  }

  function openCreate() {
    setEditingProduct(null);
    setFormOpen(true);
  }

  function openEdit(product: Product) {
    setEditingProduct(product);
    setFormOpen(true);
  }

  function openUpload(product: Product) {
    setUploadProduct(product);
    setUploadOpen(true);
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{t('products.title')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('products.subtitle')}</p>
        </div>

        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          {t('products.add')}
        </Button>
      </div>

      <div className="relative max-w-md">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t('products.search')}
          className="pl-9"
        />
      </div>

      {error ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
          {isPlanLimitError && (
            <a href="/settings#billing" className="ml-2 font-medium underline">
              Upgrade your plan →
            </a>
          )}
        </div>
      ) : null}

      <section className="rounded-xl border border-border bg-card">
        <button
          type="button"
          onClick={() => setBrandsOpen((prev) => !prev)}
          className="flex w-full items-center justify-between px-4 py-3 text-left"
        >
          <div>
            <h2 className="text-base font-semibold">{t('products.brands.title')}</h2>
            <p className="text-sm text-muted-foreground">{t('products.brands.subtitle')}</p>
          </div>
          {brandsOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>

        {brandsOpen ? (
          <div className="space-y-4 border-t border-border px-4 py-4">
            <div className="space-y-2 rounded-lg border border-border bg-muted/30 p-3">
              <p className="text-sm font-medium">Add Brand</p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Input
                  value={newBrandName}
                  onChange={(event) => setNewBrandName(event.target.value)}
                  placeholder="Brand name"
                />
                <Button onClick={handleCreateBrandFromSection} disabled={createBrandLoading}>
                  {createBrandLoading ? 'Adding...' : 'Add Brand'}
                </Button>
              </div>
              {newBrandName.trim() ? (
                <p className="text-xs text-muted-foreground">Slug preview: {slugPreview(newBrandName) || '-'}</p>
              ) : null}
              {createBrandError ? <p className="text-xs text-rose-600">{createBrandError}</p> : null}
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <div className="rounded-lg border border-border">
                <div className="border-b border-border px-3 py-2 text-sm font-medium">Your Brands</div>
                <div className="divide-y divide-border">
                  {tenantBrands.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-muted-foreground">No tenant brands yet.</p>
                  ) : (
                    tenantBrands.map((brand) => {
                      const linkedCount = linkedBrandCounts.get(brand.id) || 0;
                      return (
                        <div key={brand.id} className="flex items-center justify-between gap-3 px-3 py-3">
                          <div>
                            <p className="text-sm font-medium">{brand.name}</p>
                            <p className="text-xs text-muted-foreground">{brand.slug}</p>
                            {linkedCount > 0 ? (
                              <p className="text-xs text-amber-700">
                                Deleting will make {linkedCount} linked product{linkedCount > 1 ? 's' : ''} brandless.
                              </p>
                            ) : null}
                          </div>
                          <Button type="button" size="sm" variant="outline" onClick={() => setDeleteBrandTarget(brand)}>
                            Delete
                          </Button>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              <div className="rounded-lg border border-border">
                <div className="border-b border-border px-3 py-2 text-sm font-medium">Global Brands</div>
                <div className="divide-y divide-border">
                  {globalBrands.length === 0 ? (
                    <p className="px-3 py-3 text-sm text-muted-foreground">No global brands available.</p>
                  ) : (
                    globalBrands.map((brand) => (
                      <div key={brand.id} className="flex items-center justify-between gap-3 px-3 py-3">
                        <div>
                          <p className="text-sm font-medium">{brand.name}</p>
                          <p className="text-xs text-muted-foreground">{brand.slug}</p>
                        </div>
                        <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">
                          Global
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            {brandsLoading ? <p className="text-sm text-muted-foreground">Loading brands...</p> : null}
          </div>
        ) : null}
      </section>

      {tableContent}

      {formOpen ? (
        <div className="fixed inset-0 z-50">
          <button className="absolute inset-0 bg-slate-950/35" onClick={() => setFormOpen(false)} aria-label="Close product form" />
          <aside className="absolute right-0 top-0 h-full w-full max-w-lg overflow-y-auto border-l border-border bg-background p-5 shadow-xl">
            <div className="mb-5 flex items-center justify-between">
              <h2 className="text-xl font-semibold">{editingProduct ? 'Edit Product' : 'Add Product'}</h2>
              <Button variant="outline" onClick={() => setFormOpen(false)}>
                Close
              </Button>
            </div>

            <ProductForm
              mode={editingProduct ? 'edit' : 'create'}
              initialValue={editingProduct}
              brands={brands || []}
              onCreateBrand={handleCreateBrand}
              onCancel={() => setFormOpen(false)}
              onSubmit={handleSaveProduct}
            />
          </aside>
        </div>
      ) : null}

      <SizeChartUpload
        product={uploadProduct}
        open={uploadOpen}
        onClose={() => {
          setUploadOpen(false);
          setUploadProduct(null);
        }}
        onUploaded={async () => {
          await mutateProducts();
        }}
      />

      {deleteTarget ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <button className="absolute inset-0 bg-slate-950/35" onClick={() => setDeleteTarget(null)} aria-label="Close delete dialog" />

          <div className="relative z-10 w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg">
            <h3 className="text-lg font-semibold">Delete product?</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              This will soft-delete <span className="font-medium text-foreground">{deleteTarget.name}</span> and hide it from your product list.
            </p>

            <div className="mt-5 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setDeleteTarget(null)}>
                Cancel
              </Button>
              <Button onClick={handleDeleteProduct} disabled={deleteLoading}>
                {deleteLoading ? 'Deleting...' : 'Delete'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {deleteBrandTarget ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <button
            className="absolute inset-0 bg-slate-950/35"
            onClick={() => setDeleteBrandTarget(null)}
            aria-label="Close delete brand dialog"
          />

          <div className="relative z-10 w-full max-w-md rounded-xl border border-border bg-card p-5 shadow-lg">
            <h3 className="text-lg font-semibold">Delete brand?</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              This will soft-delete <span className="font-medium text-foreground">{deleteBrandTarget.name}</span>. Any linked
              products will become brandless.
            </p>

            <div className="mt-5 flex justify-end gap-2">
              <Button variant="outline" onClick={() => setDeleteBrandTarget(null)}>
                Cancel
              </Button>
              <Button onClick={handleDeleteBrand} disabled={deleteBrandLoading}>
                {deleteBrandLoading ? 'Deleting...' : 'Delete'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
