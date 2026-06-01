import path from 'node:path';

import { expect, test, type Page } from '@playwright/test';

import {
  createTestProduct,
  createTestStore,
  deleteTestStore,
  setAuthInPage,
  type TestStore,
} from './helpers/api';

function productFormPanel(page: Page) {
  return page.locator('aside').filter({ has: page.getByRole('heading', { name: /Add Product|Edit Product/ }) });
}

function uploadPanel(page: Page) {
  return page.locator('aside').filter({ has: page.getByRole('heading', { name: 'Upload Size Chart' }) });
}

test.describe('Products', () => {
  let store: TestStore;

  test.beforeEach(async ({ page }) => {
    store = await createTestStore();
    await setAuthInPage(page, store.jwt);
    await page.goto('/products');
    await expect(page.getByRole('heading', { name: 'Products' })).toBeVisible();
  });

  test.afterEach(async () => {
    if (store?.jwt) {
      await deleteTestStore(store.jwt);
    }
  });

  test('create product → appears in list', async ({ page }) => {
    await page.getByRole('button', { name: 'Add Product' }).click();
    await expect(page.getByRole('heading', { name: 'Add Product' })).toBeVisible();

    const form = productFormPanel(page);
    await form.locator('[name=name]').fill('Test T-Shirt');
    await form.locator('[name=externalProductId]').fill(`ext-${Date.now()}`);
    await form.locator('select[name=category]').selectOption('TOPS');
    await form.getByRole('button', { name: 'Create product' }).click();

    await expect(page.getByRole('cell', { name: 'Test T-Shirt' })).toBeVisible();
  });

  test('upload size chart CSV → shows active chart', async ({ page }) => {
    await createTestProduct(store.jwt, 'Chart Upload Shirt');

    await page.reload();
    await expect(page.getByRole('cell', { name: 'Chart Upload Shirt' })).toBeVisible();

    const row = page.getByRole('row', { name: /Chart Upload Shirt/ });
    await row.getByRole('button', { name: 'Upload Size Chart' }).click();
    await expect(page.getByRole('heading', { name: 'Upload Size Chart' })).toBeVisible();

    const panel = uploadPanel(page);
    const csvPath = path.join(__dirname, 'fixtures', 'size-chart-tops.csv');
    await panel.locator('input[type="file"]').setInputFiles(csvPath);
    await panel.getByRole('button', { name: 'Upload file', exact: true }).click();

    await expect(panel.getByText(/Entries saved:/)).toBeVisible();
    await expect(panel.getByRole('heading', { name: 'Current active size chart' })).toBeVisible();

    await panel.getByRole('button', { name: 'Close' }).click();
    await expect(row.locator('span', { hasText: '✓' })).toBeVisible();
  });

  test('delete product → disappears from list', async ({ page }) => {
    await createTestProduct(store.jwt, 'Delete Me Shirt');

    await page.reload();
    await expect(page.getByRole('cell', { name: 'Delete Me Shirt' })).toBeVisible();

    const row = page.getByRole('row', { name: /Delete Me Shirt/ });
    await row.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByRole('heading', { name: 'Delete product?' })).toBeVisible();

    await page.getByRole('button', { name: 'Delete' }).last().click();
    await expect(page.getByRole('cell', { name: 'Delete Me Shirt' })).not.toBeVisible();
  });

  test('product limit reached → shows upgrade alert', async ({ page }) => {
    await page.route('**/api/dashboard/v1/products', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }

      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          data: null,
          error: {
            code: 'PLAN_LIMIT_REACHED',
            message: 'You reached the product limit on the Free plan.',
          },
        }),
      });
    });

    await createTestProduct(store.jwt, 'Limit Product One');
    await createTestProduct(store.jwt, 'Limit Product Two');

    await page.reload();
    await page.getByRole('button', { name: 'Add Product' }).click();

    const form = productFormPanel(page);
    await form.locator('[name=name]').fill('Limit Product Three');
    await form.locator('[name=externalProductId]').fill(`limit-3-${Date.now()}`);
    await form.getByRole('button', { name: 'Create product' }).click();

    await expect(page.locator('body')).toContainText(/product limit on the Free plan/i);
    await expect(page.getByRole('button', { name: /Manage Brands/i })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Limit Product Three' })).not.toBeVisible();
  });
});
