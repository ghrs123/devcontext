'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import type { Product, ProductRequest } from '@/lib/types';

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
  onSubmit: (payload: ProductRequest) => Promise<void>;
  onCancel: () => void;
};

const defaultValues: ProductFormValues = {
  name: '',
  externalProductId: '',
  category: 'TOPS',
  genderTarget: 'UNISEX'
};

export function ProductForm({ mode, initialValue, onSubmit, onCancel }: Readonly<ProductFormProps>) {
  const form = useForm<ProductFormValues>({
    resolver: zodResolver(schema),
    defaultValues
  });

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
  }, [form, initialValue]);

  async function handleSubmit(values: ProductFormValues) {
    await onSubmit({
      name: values.name,
      externalProductId: values.externalProductId,
      category: values.category,
      genderTarget: values.genderTarget
    });
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

        <div className="flex items-center justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? 'Saving...' : mode === 'create' ? 'Create product' : 'Save changes'}
          </Button>
        </div>
      </form>
    </Form>
  );
}
