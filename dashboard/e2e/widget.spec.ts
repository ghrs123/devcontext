import fs from 'node:fs';
import path from 'node:path';

import { expect, test, type Page } from '@playwright/test';

import {
  createTestProduct,
  createTestStore,
  deleteTestStore,
  renderHtmlFixture,
  uploadTestSizeChart,
  type TestStore,
} from './helpers/api';

const API_BASE_URL = process.env.E2E_API_URL || process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function fixturePath(name: string): string {
  return path.join(__dirname, 'widget-test-pages', name);
}

function widgetBundlePath(): string {
  return path.resolve(__dirname, '../../widget/dist/fitvision-widget.min.js');
}

async function installWidgetRoutes(
  page: Page,
  options: {
    validProductId: string;
    noChartProductId: string;
    apiKey: string;
    invalidApiKey: string;
  }
): Promise<void> {
  const bundlePath = widgetBundlePath();
  if (!fs.existsSync(bundlePath)) {
    throw new Error('Widget bundle not found at widget/dist/fitvision-widget.min.js. Run npm run build in widget/.');
  }

  const bundleJs = fs.readFileSync(bundlePath, 'utf8');

  await page.route('**/fitvision-widget.min.js', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: bundleJs,
    });
  });

  await page.route('**/api/widget/v1/size-recommendation', async (route) => {
    const headers = route.request().headers();
    const apiKey = headers['x-fitvision-key'];
    const payload = route.request().postDataJSON() as {
      externalProductId?: string;
    };

    if (apiKey === options.invalidApiKey) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          data: null,
          error: {
            code: 'INVALID_API_KEY',
            message: 'Could not connect. Please try again.',
          },
        }),
      });
      return;
    }

    if (payload.externalProductId === options.noChartProductId) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            recommendedSize: null,
            confidenceScore: 0,
            quality: 'NO_MATCH',
            productName: 'Widget Product No Chart',
            hasSizeChart: false,
            confidenceLabel: 'Low',
            message: "We don't have size data for this product yet.",
          },
          error: null,
        }),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          recommendedSize: 'M',
          confidenceScore: 0.89,
          quality: 'EXACT',
          productName: 'Widget Product With Chart',
          hasSizeChart: true,
          confidenceLabel: 'High',
          message: 'Based on your measurements, we recommend size M.',
        },
        error: null,
      }),
    });
  });

  await page.route('**/e2e-widget-test.html', async (route) => {
    const body = renderHtmlFixture(fixturePath('e2e-widget-test.html'), {
      __PRODUCT_ID__: options.validProductId,
      __API_KEY__: options.apiKey,
      __API_BASE_URL__: API_BASE_URL,
    });

    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body,
    });
  });

  await page.route('**/e2e-widget-test-no-chart.html', async (route) => {
    const body = renderHtmlFixture(fixturePath('e2e-widget-test-no-chart.html'), {
      __PRODUCT_ID__: options.noChartProductId,
      __API_KEY__: options.apiKey,
      __API_BASE_URL__: API_BASE_URL,
    });

    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body,
    });
  });

  await page.route('**/e2e-widget-test-invalid-key.html', async (route) => {
    const body = renderHtmlFixture(fixturePath('e2e-widget-test-invalid-key.html'), {
      __PRODUCT_ID__: options.validProductId,
      __API_KEY__: options.invalidApiKey,
      __API_BASE_URL__: API_BASE_URL,
    });

    await route.fulfill({
      status: 200,
      contentType: 'text/html',
      body,
    });
  });
}

test.describe('Widget', () => {
  let store: TestStore;
  let validProductExternalId = '';
  let noChartProductExternalId = '';

  test.beforeEach(async ({ page }) => {
    store = await createTestStore({ name: 'Widget E2E Store' });

    const withChart = await createTestProduct(store.jwt, 'Widget Product With Chart');
    await uploadTestSizeChart(store.jwt, withChart.id);

    const noChart = await createTestProduct(store.jwt, 'Widget Product No Chart');

    validProductExternalId = withChart.externalProductId;
    noChartProductExternalId = noChart.externalProductId;

    await installWidgetRoutes(page, {
      validProductId: validProductExternalId,
      noChartProductId: noChartProductExternalId,
      apiKey: store.apiKeyPublic,
      invalidApiKey: 'fitvision-invalid-e2e-key',
    });
  });

  test.afterEach(async () => {
    if (store?.jwt) {
      await deleteTestStore(store.jwt);
    }
  });

  test('widget loads -> trigger button visible', async ({ page }) => {
    await page.goto('/e2e-widget-test.html');

    await expect(page.locator(`[data-fitvision-product-id="${validProductExternalId}"]`)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Find my size' })).toBeVisible();
  });

  test('widget -> fill form -> shows recommendation', async ({ page }) => {
    await page.goto('/e2e-widget-test.html');

    await page.getByRole('button', { name: 'Find my size' }).click();
    await page.getByLabel('Height (cm)').fill('175');
    await page.getByLabel('Weight (kg)').fill('75');
    await page.getByLabel('Gender').selectOption('MALE');
    await page.getByRole('button', { name: 'Get my size' }).click();

    await expect(page.locator('.fitvision-result')).toBeVisible();
    await expect(page.getByText('Your recommended size')).toBeVisible();
  });

  test('widget -> no size chart -> shows fallback message', async ({ page }) => {
    await page.goto('/e2e-widget-test-no-chart.html');

    await page.getByRole('button', { name: 'Find my size' }).click();
    await page.getByLabel('Height (cm)').fill('175');
    await page.getByLabel('Weight (kg)').fill('75');
    await page.getByLabel('Gender').selectOption('MALE');
    await page.getByRole('button', { name: 'Get my size' }).click();

    await expect(page.locator('.fitvision-result')).toBeVisible();
    await expect(page.getByText(/size guide/i)).toBeVisible();
  });

  test('widget -> invalid api key -> shows error', async ({ page }) => {
    await page.goto('/e2e-widget-test-invalid-key.html');

    await page.getByRole('button', { name: 'Find my size' }).click();
    await page.getByLabel('Height (cm)').fill('175');
    await page.getByLabel('Weight (kg)').fill('75');
    await page.getByLabel('Gender').selectOption('MALE');
    await page.getByRole('button', { name: 'Get my size' }).click();

    await expect(page.locator('.fitvision-error')).toBeVisible();
    await expect(page.locator('.fitvision-error')).toContainText(
      /could not connect|could not complete|invalid|unauthorized|not found/i
    );
  });
});
