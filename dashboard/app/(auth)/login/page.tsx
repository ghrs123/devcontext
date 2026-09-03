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

const schema = z.object({
  email: z.string().email('auth.validation.email'),
  password: z.string().min(8, 'auth.validation.password')
});

type LoginFormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const t = useT();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' }
  });

  async function onSubmit(values: LoginFormValues) {
    try {
      setSubmitError(null);
      const response = await api.login(values);
      saveToken(response.accessToken);
      const role = getRoleFromToken(response.accessToken);
      router.push(role === 'ADMIN' ? '/admin/dashboard' : '/dashboard');
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : t('auth.login.failed'));
    }
  }

  return (
    <AuthShell
      title={t('auth.login.title')}
      subtitle={t('auth.login.subtitle')}
      footer={
        <>
          {t('auth.login.noAccount')}{' '}
          <Link href="/register" className="font-medium text-primary hover:underline">
            {t('auth.login.createAccount')}
          </Link>
        </>
      }
    >
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
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
                    placeholder="••••••••"
                    type="password"
                    autoComplete="current-password"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {submitError ? (
            <p className="rounded-md bg-danger-soft px-3 py-2 text-sm text-danger">{submitError}</p>
          ) : null}

          <Button type="submit" size="lg" className="w-full" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? t('auth.login.submitting') : t('auth.login.submit')}
          </Button>
        </form>
      </Form>
    </AuthShell>
  );
}
