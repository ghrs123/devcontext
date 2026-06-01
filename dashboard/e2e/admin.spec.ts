import { expect, test } from '@playwright/test';

import {
  adminUpdateStoreStatus,
  createOrLoginAdmin,
  createTestStore,
  deleteTestStore,
  setAuthInPage,
  type TestAdmin,
  type TestStore,
} from './helpers/api';

test.describe('Admin area', () => {
  let admin: TestAdmin;

  test.beforeAll(async () => {
    admin = await createOrLoginAdmin();
  });

  test.describe('as admin', () => {
    test.beforeEach(async ({ page }) => {
      await setAuthInPage(page, admin.jwt);
      await page.goto('/admin/dashboard');
      await expect(page.getByRole('heading', { name: 'Platform Overview' })).toBeVisible();
    });

    test('platform overview -> shows metrics cards', async ({ page }) => {
      await expect(page.getByText('Total Stores', { exact: true })).toBeVisible();
      await expect(page.getByText('Total Recommendations', { exact: true })).toBeVisible();
      await expect(page.getByText('Average Confidence', { exact: true })).toBeVisible();
    });

    test('stores page -> list shows registered stores', async ({ page }) => {
      await page.getByRole('link', { name: 'Stores' }).click();
      await expect(page).toHaveURL(/\/admin\/stores$/);
      await expect(page.locator('table').first()).toBeVisible();
    });

    test('deactivate store -> status changes to Inactive', async ({ page }) => {
      const store = await createTestStore({ name: 'Admin E2E Deactivate Store' });

      try {
        await page.getByRole('link', { name: 'Stores' }).click();
        await expect(page).toHaveURL(/\/admin\/stores$/);

        await page.locator('select').selectOption('ALL');
        await page.getByPlaceholder('Search by name or email').fill(store.email);

        const row = page.getByRole('row', { name: new RegExp(store.email, 'i') });
        await expect(row).toBeVisible({ timeout: 15_000 });

        page.once('dialog', async (dialog) => {
          await dialog.accept();
        });
        await row.getByRole('button', { name: 'Deactivate' }).click();

        await expect(row.getByText('INACTIVE', { exact: true })).toBeVisible();
      } finally {
        await adminUpdateStoreStatus(admin.jwt, store.storeId, 'ACTIVE');
        await deleteTestStore(store.jwt);
      }
    });

    test('global brands -> create brand', async ({ page }) => {
      const brandName = `TestBrand-${Date.now()}`;

      await page.getByRole('link', { name: 'Global Brands' }).click();
      await expect(page).toHaveURL(/\/admin\/brands$/);

      await page.getByPlaceholder('Brand name').fill(brandName);
      await page.getByRole('button', { name: 'Create' }).click();

      await expect(page.getByRole('row', { name: new RegExp(String.raw`^${brandName}\b`) })).toBeVisible();
    });

    test('system health -> shows DB status', async ({ page }) => {
      await page.route('**/api/admin/v1/health', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            data: {
              database: { status: 'UP', latencyMs: 12 },
              recommendationEngine: { avgLatencyMs: 24, p95LatencyMs: 52 },
              scrapeJobs: { running: 0, failedLast7Days: 0 },
              storeActivity: {
                recommendationsLast24h: 0,
                activeStoresLast24h: 0,
                lastRecommendationAt: null,
              },
              brandScrapes: [],
            },
            error: null,
          }),
        });
      });

      await page.route('**/api/admin/v1/recommendations/stats', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            data: {
              p50LatencyMs: 15,
              p95LatencyMs: 52,
              p99LatencyMs: 73,
              qualityDistribution: { EXACT: 0, PARTIAL: 0, CLOSEST: 0, NO_MATCH: 0 },
              topStores: [],
            },
            error: null,
          }),
        });
      });

      await page.getByRole('link', { name: 'System Health' }).click();
      await expect(page).toHaveURL(/\/admin\/health$/);
      await expect(page.getByRole('heading', { name: 'System Health' })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText('Database')).toBeVisible();
      await expect(page.getByText('UP', { exact: true })).toBeVisible();
    });
  });

  test('store role -> cannot access admin area', async ({ page }) => {
    const store: TestStore = await createTestStore({ name: 'Store Role Guard E2E' });

    try {
      await setAuthInPage(page, store.jwt);
      await page.goto('/admin/dashboard');
      await expect(page).toHaveURL('/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    } finally {
      await deleteTestStore(store.jwt);
    }
  });
});
