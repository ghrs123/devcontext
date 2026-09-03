'use client';

import Link from 'next/link';
import { Suspense, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';

import { BillingSection } from '@/components/dashboard/BillingSection';
import { Eye, EyeOff, Loader2, RefreshCcw } from 'lucide-react';

import { CodeBlock } from '@/components/app/CodeBlock';
import { CopyButton } from '@/components/app/CopyButton';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { toast } from '@/hooks/use-toast';
import { api, ApiError } from '@/lib/api';
import type { ApiKeys, StoreProfile } from '@/lib/types';

const PLATFORM_OPTIONS = [
  { value: 'shopify', label: 'Shopify' },
  { value: 'woocommerce', label: 'WooCommerce' },
  { value: 'other', label: 'Other' }
];

export default function SettingsPage() {
  return (
      <Suspense fallback={<SettingsPageLoading />}>
        <SettingsPageContent />
      </Suspense>
  );
}

function SettingsPageLoading() {
  return (
      <main className="mx-auto max-w-7xl">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading settings...
        </div>
      </main>
  );
}

function SettingsPageContent() {
  const searchParams = useSearchParams();

  const [profile, setProfile] = useState<StoreProfile | null>(null);
  const [apiKeys, setApiKeys] = useState<ApiKeys | null>(null);
  const [name, setName] = useState('');
  const [platform, setPlatform] = useState('other');
  const [isLoading, setIsLoading] = useState(true);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [isRegenerating, setIsRegenerating] = useState(false);
  const [showSecret, setShowSecret] = useState(false);

  useEffect(() => {
    if (searchParams.get('billing') === 'success') {
      toast({
        title: 'Plan upgraded',
        description: 'Your subscription is now active.'
      });
    }
  }, [searchParams]);

  useEffect(() => {
    void loadSettings();
  }, []);

  async function loadSettings() {
    setIsLoading(true);

    try {
      const [profileData, keysData] = await Promise.all([
        api.getProfile(),
        api.getApiKeys()
      ]);

      setProfile(profileData);
      setApiKeys(keysData);
      setName(profileData.name || '');
      setPlatform((profileData.platform || 'other').toLowerCase());
    } catch (error) {
      const message =
          error instanceof ApiError
              ? error.message
              : 'Unexpected error while loading settings.';

      toast({
        title: 'Failed to load settings',
        description: message,
        variant: 'destructive'
      });
    } finally {
      setIsLoading(false);
    }
  }

  async function handleSaveProfile(
      event: React.FormEvent<HTMLFormElement>
  ) {
    event.preventDefault();
    setIsSavingProfile(true);

    try {
      const updated = await api.updateProfile({
        name: name.trim(),
        platform
      });

      setProfile(updated);
      setName(updated.name || '');
      setPlatform((updated.platform || 'other').toLowerCase());

      toast({
        title: 'Profile updated',
        description: 'Store profile saved successfully.'
      });
    } catch (error) {
      const message =
          error instanceof ApiError
              ? error.message
              : 'Unexpected error while saving profile.';

      toast({
        title: 'Could not save profile',
        description: message,
        variant: 'destructive'
      });
    } finally {
      setIsSavingProfile(false);
    }
  }

  async function handleRegenerateKeys() {
    setIsRegenerating(true);

    try {
      const refreshedKeys = await api.regenerateApiKeys();

      setApiKeys(refreshedKeys);
      setShowSecret(false);

      toast({
        title: 'Keys regenerated',
        description: 'Your previous widget key was invalidated immediately.'
      });
    } catch (error) {
      const message =
          error instanceof ApiError
              ? error.message
              : 'Unexpected error while regenerating keys.';

      toast({
        title: 'Could not regenerate keys',
        description: message,
        variant: 'destructive'
      });
    } finally {
      setIsRegenerating(false);
    }
  }

  const widgetSnippet = useMemo(() => {
    const key = apiKeys?.apiKeyPublic || 'YOUR_API_KEY';

    return `<div
  data-fitvision-product-id="YOUR_PRODUCT_ID"
  data-fitvision-key="${key}">
</div>
<script src="https://cdn.fitvision.io/widget/fitvision-widget.min.js" async defer></script>`;
  }, [apiKeys?.apiKeyPublic]);

  const isShopify = platform === 'shopify';

  if (isLoading) {
    return <SettingsPageLoading />;
  }

  return (
      <div>
        <h2 className="text-xl font-semibold tracking-tight">Settings</h2>

        <p className="mt-1 text-sm text-muted-foreground">
          Manage your profile, API credentials, billing, and widget integration.
        </p>

        <section className="mt-6 grid gap-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-xl">Store Profile</CardTitle>
              <CardDescription>
                Keep your store information up to date.
              </CardDescription>
            </CardHeader>

            <CardContent>
              <form className="grid gap-4" onSubmit={handleSaveProfile}>
                <div className="grid gap-2">
                  <Label htmlFor="store-name">Name</Label>
                  <Input
                      id="store-name"
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      required
                  />
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="store-email">Email</Label>
                  <Input
                      id="store-email"
                      value={profile?.email || ''}
                      readOnly
                      disabled
                  />
                </div>

                <div className="grid gap-2">
                  <Label htmlFor="store-platform">Platform</Label>

                  <select
                      id="store-platform"
                      className="h-10 rounded-md border border-border bg-card px-3 text-sm"
                      value={platform}
                      onChange={(event) => setPlatform(event.target.value)}
                  >
                    {PLATFORM_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                    ))}
                  </select>
                </div>

                <div>
                  <Button type="submit" disabled={isSavingProfile}>
                    {isSavingProfile ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : null}
                    Save profile
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-xl">API Keys</CardTitle>
              <CardDescription>
                Regenerating keys will immediately invalidate the current
                widget installation.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="api-key-public">Public key</Label>

                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                  <Input
                      id="api-key-public"
                      value={apiKeys?.apiKeyPublic || ''}
                      readOnly
                      className="font-mono text-xs"
                  />

                  <CopyButton
                      text={apiKeys?.apiKeyPublic || ''}
                      label="Copy public key"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="api-key-secret">Secret key</Label>

                <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                  <Input
                      id="api-key-secret"
                      type={showSecret ? 'text' : 'password'}
                      value={apiKeys?.apiKeySecret || ''}
                      readOnly
                      className="font-mono text-xs"
                  />

                  <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setShowSecret((value) => !value)}
                  >
                    {showSecret ? (
                        <EyeOff className="mr-2 h-4 w-4" />
                    ) : (
                        <Eye className="mr-2 h-4 w-4" />
                    )}

                    {showSecret ? 'Hide' : 'Reveal'}
                  </Button>

                  <CopyButton
                      text={apiKeys?.apiKeySecret || ''}
                      label="Copy secret key"
                  />
                </div>
              </div>

              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                      type="button"
                      variant="outline"
                      disabled={isRegenerating}
                  >
                    {isRegenerating ? (
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                        <RefreshCcw className="mr-2 h-4 w-4" />
                    )}

                    Regenerate keys
                  </Button>
                </AlertDialogTrigger>

                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>
                      Regenerate API keys?
                    </AlertDialogTitle>

                    <AlertDialogDescription>
                      Existing widget installations will stop working
                      immediately until you update them with the new public key.
                    </AlertDialogDescription>
                  </AlertDialogHeader>

                  <AlertDialogFooter>
                    <AlertDialogCancel>Cancel</AlertDialogCancel>

                    <AlertDialogAction onClick={handleRegenerateKeys}>
                      Confirm regeneration
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </CardContent>
          </Card>

          <div id="billing" className="scroll-mt-20">
            <BillingSection />
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-xl">
                Widget Integration Guide
              </CardTitle>

              <CardDescription>
                Install FitVision in three quick steps.
              </CardDescription>
            </CardHeader>

            <CardContent className="space-y-4">
              <ol className="list-decimal space-y-2 pl-5 text-sm text-foreground">
                <li className="space-y-1">
                  <p>Copy your API key:</p>

                  <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-3 py-2 font-mono text-xs">
                  <span>
                    {apiKeys?.apiKeyPublic || 'Loading key...'}
                  </span>

                    <CopyButton
                        text={apiKeys?.apiKeyPublic || ''}
                        label="Copy"
                    />
                  </div>
                </li>

                <li>
                  Add the container div to your product page template.
                </li>

                <li>
                  Add the script tag before {'</body>'}.
                </li>
              </ol>

              <CodeBlock code={widgetSnippet} />

              <p className="text-sm text-muted-foreground">
                Replace{' '}
                <span className="font-mono text-foreground">
                YOUR_PRODUCT_ID
              </span>{' '}
                with the External Product ID you set in FitVision for each
                product.
              </p>

              {isShopify ? (
                  <p className="text-sm text-muted-foreground">
                    Shopify tip: add this snippet in your theme product
                    template. See the{' '}
                    <Link
                        href="https://help.shopify.com/en/manual/online-store/themes/theme-structure/extend/edit-theme-code"
                        target="_blank"
                        rel="noreferrer"
                        className="font-medium text-foreground underline"
                    >
                      Shopify theme code editor documentation
                    </Link>
                    .
                  </p>
              ) : null}
            </CardContent>
          </Card>
        </section>
      </div>
  );
}