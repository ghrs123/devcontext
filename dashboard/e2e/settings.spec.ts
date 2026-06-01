import { expect, test } from '@playwright/test';

import { createTestStore, deleteTestStore, setAuthInPage, type TestStore } from './helpers/api';

test.describe('Settings', () => {
  let store: TestStore;

  test.beforeEach(async ({ page }) => {
    store = await createTestStore();
    await setAuthInPage(page, store.jwt);
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Public key' })).toBeVisible();
  });

  test.afterEach(async () => {
    if (store?.jwt) {
      await deleteTestStore(store.jwt);
    }
  });

  test('api keys → reveal → copy public key', async ({ page }) => {
    await expect(page.getByRole('textbox', { name: 'Public key' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Copy public key' })).toBeVisible();

    await page.getByRole('button', { name: 'Reveal' }).click();
    await expect(page.getByRole('button', { name: 'Copy secret key' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Hide' })).toBeVisible();
  });

  test('regenerate keys → new keys shown', async ({ page }) => {
    const publicKeyInput = page.getByRole('textbox', { name: 'Public key' });
    await expect(publicKeyInput).not.toHaveValue('');
    const previousKey = await publicKeyInput.inputValue();

    await page.getByRole('button', { name: 'Regenerate keys' }).click();
    await page.getByRole('button', { name: 'Confirm regeneration' }).click();

    await expect(publicKeyInput).not.toHaveValue(previousKey);
    await expect(publicKeyInput).not.toHaveValue('');
  });

  test('billing section → shows FREE plan', async ({ page }) => {
    await page.route('**/api/dashboard/v1/billing/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            plan: 'FREE',
            subscriptionStatus: 'inactive',
            currentPeriodEnd: null,
            productsUsed: 0,
            productsLimit: 2,
            recommendationsUsed: 0,
            recommendationsLimit: 100,
          },
          error: null,
        }),
      });
    });

    await page.reload();

    await expect(page.getByText('FREE', { exact: true })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Plan & Billing')).toBeVisible();
    await expect(page.getByText('Products', { exact: true }).first()).toBeVisible();
  });
});
