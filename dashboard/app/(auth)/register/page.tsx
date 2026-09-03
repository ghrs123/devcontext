'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

import { api, ApiError } from '@/lib/api';
import { saveToken } from '@/lib/auth';
import { getRoleFromToken } from '@/lib/jwt';
import { AuthShell } from '@/components/auth/AuthShell';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { useT } from '@/lib/i18n/I18nProvider';
import { cn } from '@/lib/utils';

const schema = z.object({
  name: z.string().min(2, 'auth.validation.storeName'),
  email: z.string().email('auth.validation.email'),
  password: z.string().min(8, 'auth.validation.password'),
  platform: z.enum(['shopify', 'woocommerce', 'other'])
});

type RegisterFormValues = z.infer<typeof schema>;

const platformOptions = [
  { value: 'shopify', label: 'Shopify' },
  { value: 'woocommerce', label: 'WooCommerce' },
  { value: 'other', label: 'Outra / Other' }
] as const;

export default function RegisterPage() {
  const router = useRouter();
  const t = useT();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', email: '', password: '', platform: 'shopify' }
  });

  async function onSubmit(values: RegisterFormValues) {
    try {
      setSubmitError(null);
      const response = await api.register(values);
      saveToken(response.accessToken);
      const role = getRoleFromToken(response.accessToken);
      router.push(role === 'ADMIN' ? '/admin/dashboard' : '/dashboard');
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('auth.register.failed'));
    }
  }

  const platform = form.watch('platform');

  return (
    <AuthShell
      title={t('auth.register.title')}
      subtitle={t('auth.register.subtitle')}
      footer={
        <>
          {t('auth.register.hasAccount')}{' '}
          <Link href="/login" className="font-medium text-primary hover:underline">
            {t('auth.register.signIn')}
          </Link>
        </>
      }
    >
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.field.storeName')}</FormLabel>
                <FormControl>
                  <Input
                    placeholder={t('auth.placeholder.storeName')}
                    autoComplete="organization"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.field.email')}</FormLabel>
                <FormControl>
                  <Input
                    placeholder={t('auth.placeholder.email')}
                    type="email"
                    autoComplete="email"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="password"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.field.password')}</FormLabel>
                <FormControl>
                  <Input
                    placeholder={t('auth.placeholder.password')}
                    type="password"
                    autoComplete="new-password"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="platform"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('auth.field.platform')}</FormLabel>
                <FormControl>
                  <div className="grid grid-cols-3 gap-2">
                    {platformOptions.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        onClick={() => field.onChange(option.value)}
                        className={cn(
                          'rounded-md border px-3 py-2 text-sm font-medium transition-colors',
                          platform === option.value
                            ? 'border-primary bg-primary-soft text-primary-soft-foreground'
                            : 'border-border-strong bg-card text-muted-foreground hover:bg-muted'
                        )}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {submitError ? (
            <p className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{submitError}</p>
          ) : null}

          <Button type="submit" size="lg" className="w-full" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? t('auth.register.submitting') : t('auth.register.submit')}
          </Button>
        </form>
      </Form>
    </AuthShell>
  );
}
