'use client';

import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import type { Brand, Product, ProductRequest } from '@/lib/types';

const schema = z.object({
  name: z.string().min(1, 'Name is required.'),
  externalProductId: z.string().min(1, 'External product ID is required.'),
  category: z.enum(['TOPS', 'BOTTOMS', 'DRESSES', 'OUTERWEAR', 'OTHER']),
  genderTarget: z.enum(['MALE', 'FEMALE', 'UNISEX'])
});

type ProductFormValues = z.infer<typeof schema>;

type ProductFormProps = {
  mode: 'create' | 'edit';
  initialValue?: Product | null;
  brands: Brand[];
  onCreateBrand: (name: string) => Promise<Brand>;
  onSubmit: (payload: ProductRequest) => Promise<void>;
  onCancel: () => void;
};

const defaultValues: ProductFormValues = {
  name: '',
  externalProductId: '',
  category: 'TOPS',
  genderTarget: 'UNISEX'
};

export function ProductForm({ mode, initialValue, brands, onCreateBrand, onSubmit, onCancel }: Readonly<ProductFormProps>) {
  const form = useForm<ProductFormValues>({
    resolver: zodResolver(schema),
    defaultValues
  });

  const [selectedBrandId, setSelectedBrandId] = useState<string>('');
  const [createBrandName, setCreateBrandName] = useState('');
  const [createBrandLoading, setCreateBrandLoading] = useState(false);
  const [createBrandError, setCreateBrandError] = useState<string | null>(null);

  const brandOptions = useMemo(() => {
    const sorted = [...brands].sort((a, b) => a.name.localeCompare(b.name));
    return sorted;
  }, [brands]);

  useEffect(() => {
    if (!initialValue) {
      form.reset(defaultValues);
      return;
    }

    form.reset({
      name: initialValue.name || '',
      externalProductId: initialValue.externalProductId || '',
      category: (initialValue.category || 'TOPS').toUpperCase() as ProductFormValues['category'],
      genderTarget: (initialValue.genderTarget || 'UNISEX').toUpperCase() as ProductFormValues['genderTarget']
    });
    setSelectedBrandId(initialValue.brandId || '');
  }, [form, initialValue]);

  useEffect(() => {
    if (!initialValue) {
      setSelectedBrandId('');
    }
  }, [initialValue]);

  async function handleSubmit(values: ProductFormValues) {
    await onSubmit({
      name: values.name,
      externalProductId: values.externalProductId,
      category: values.category,
      genderTarget: values.genderTarget,
      brandId: selectedBrandId && selectedBrandId !== '__create_new__' ? selectedBrandId : undefined
    });
  }

  async function handleCreateBrandInline() {
    const normalizedName = createBrandName.trim();
    if (!normalizedName) {
      setCreateBrandError('Brand name is required.');
      return;
    }

    try {
      setCreateBrandLoading(true);
      setCreateBrandError(null);
      const created = await onCreateBrand(normalizedName);
      setSelectedBrandId(created.id);
      setCreateBrandName('');
    } catch (error) {
      setCreateBrandError(error instanceof Error ? error.message : 'Unable to create brand.');
    } finally {
      setCreateBrandLoading(false);
    }
  }

  let submitLabel = 'Save changes';
  if (mode === 'create') {
    submitLabel = 'Create product';
  }
  if (form.formState.isSubmitting) {
    submitLabel = 'Saving...';
  }

  return (
    <Form {...form}>
      <form className="space-y-4" onSubmit={form.handleSubmit(handleSubmit)}>
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Name</FormLabel>
              <FormControl>
                <Input {...field} placeholder="Classic T-Shirt" />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="externalProductId"
          render={({ field }) => (
            <FormItem>
              <FormLabel>External Product ID</FormLabel>
              <FormControl>
                <Input {...field} placeholder="shopify-product-123" />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="category"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Category</FormLabel>
              <FormControl>
                <select
                  {...field}
                  className="flex h-10 w-full rounded-md border border-border bg-card px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="TOPS">Tops</option>
                  <option value="BOTTOMS">Bottoms</option>
                  <option value="DRESSES">Dresses</option>
                  <option value="OUTERWEAR">Outerwear</option>
                  <option value="OTHER">Other</option>
                </select>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="genderTarget"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Gender target</FormLabel>
              <FormControl>
                <select
                  {...field}
                  className="flex h-10 w-full rounded-md border border-border bg-card px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="UNISEX">Unisex</option>
                </select>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-2">
          <FormLabel>Brand (optional)</FormLabel>
          <select
            value={selectedBrandId || ''}
            onChange={(event) => {
              const value = event.target.value;
              setCreateBrandError(null);
              if (value === '__create_new__') {
                setSelectedBrandId('__create_new__');
                return;
              }
              setSelectedBrandId(value);
            }}
            className="flex h-10 w-full rounded-md border border-border bg-card px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <option value="">No brand</option>
            {brandOptions.map((brand) => (
              <option key={brand.id} value={brand.id}>
                {brand.name} {brand.isGlobal ? '(Global)' : ''}
              </option>
            ))}
            <option value="__create_new__">+ Create new brand</option>
          </select>

          {selectedBrandId === '__create_new__' ? (
            <div className="space-y-2 rounded-md border border-border bg-muted/40 p-3">
              <Input
                value={createBrandName}
                onChange={(event) => setCreateBrandName(event.target.value)}
                placeholder="New brand name"
              />
              {createBrandError ? <p className="text-xs text-rose-600">{createBrandError}</p> : null}
              <div className="flex gap-2">
                <Button type="button" size="sm" onClick={handleCreateBrandInline} disabled={createBrandLoading}>
                  {createBrandLoading ? 'Creating...' : 'Create brand'}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setSelectedBrandId('');
                    setCreateBrandName('');
                    setCreateBrandError(null);
                  }}
                >
                  Cancel
                </Button>
              </div>
            </div>
          ) : null}
        </div>

        <div className="flex items-center justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {submitLabel}
          </Button>
        </div>
      </form>
    </Form>
  );
}
