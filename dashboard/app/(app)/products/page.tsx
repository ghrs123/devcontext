'use client';

import { useMemo, useState } from 'react';
import useSWR from 'swr';
import { Edit3, FileUp, Plus, Search, Trash2 } from 'lucide-react';

import { ProductForm } from '@/components/app/ProductForm';
import { SizeChartUpload } from '@/components/app/SizeChartUpload';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { api, ApiError } from '@/lib/api';
import type { Product, ProductRequest } from '@/lib/types';

export default function ProductsPage() {
  const [search, setSearch] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Product | null>(null);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [uploadProduct, setUploadProduct] = useState<Product | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    data: products,
    isLoading,
    mutate
  } = useSWR('products', api.getProducts, {
    refreshInterval: 60000,
    revalidateOnFocus: true
  });

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
      <section className="rounded-xl border border-dashed border-border bg-card p-8 text-center">
        <p className="text-lg font-medium">No products yet. Add your first product.</p>
      </section>
    );
  } else {
    tableContent = (
      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[960px] text-sm">
          <thead className="bg-muted/50 text-left text-muted-foreground">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">External ID</th>
              <th className="px-4 py-3 font-medium">Category</th>
              <th className="px-4 py-3 font-medium">Gender</th>
              <th className="px-4 py-3 font-medium">Size Chart</th>
              <th className="px-4 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredProducts.map((product) => (
              <tr key={product.id} className="border-t border-border/70">
                <td className="px-4 py-3 font-medium">{product.name}</td>
                <td className="px-4 py-3">{product.externalProductId}</td>
                <td className="px-4 py-3">{product.category || '-'}</td>
                <td className="px-4 py-3">{product.genderTarget || '-'}</td>
                <td className="px-4 py-3">
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${
                      product.hasSizeChart ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                    }`}
                  >
                    {product.hasSizeChart ? '✓' : '✗'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <Button type="button" size="sm" variant="outline" onClick={() => openEdit(product)}>
                      <Edit3 className="mr-1 h-3.5 w-3.5" />
                      Edit
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={() => openUpload(product)}>
                      <FileUp className="mr-1 h-3.5 w-3.5" />
                      Upload Size Chart
                    </Button>
                    <Button type="button" size="sm" variant="outline" onClick={() => setDeleteTarget(product)}>
                      <Trash2 className="mr-1 h-3.5 w-3.5" />
                      Delete
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
      if (editingProduct) {
        await api.updateProduct(editingProduct.id, payload);
      } else {
        await api.createProduct(payload);
      }
      await mutate();
      setFormOpen(false);
      setEditingProduct(null);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to save product.';
      setError(message);
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
      await mutate();
      setDeleteTarget(null);
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to delete product.';
      setError(message);
    } finally {
      setDeleteLoading(false);
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
    <main className="mx-auto max-w-7xl space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Products</h1>
          <p className="mt-1 text-sm text-muted-foreground">Manage products and upload size charts.</p>
        </div>

        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          Add Product
        </Button>
      </div>

      <div className="relative max-w-md">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search by product name"
          className="pl-9"
        />
      </div>

      {error ? <p className="text-sm text-rose-600">{error}</p> : null}

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
          await mutate();
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
    </main>
  );
}
