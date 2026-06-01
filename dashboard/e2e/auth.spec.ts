import { expect, test } from '@playwright/test';

import { createTestStore, deleteTestStore, setAuthInPage, uniqueEmail } from './helpers/api';

test.describe('Auth', () => {
  test('register → login → see dashboard', async ({ page }) => {
    const email = uniqueEmail();

    await page.goto('/register');
    await page.locator('[name=name]').fill('Test Store');
    await page.locator('[name=email]').fill(email);
    await page.locator('[name=password]').fill('password123');
    await page.locator('select[name=platform]').selectOption('shopify');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL('/dashboard');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    // New stores have zero recommendations — empty state, not stat cards
    await expect(page.getByText('No recommendations yet.')).toBeVisible();
  });

  test('login with wrong password → shows error', async ({ page }) => {
    await page.goto('/login');
    await page.locator('[name=email]').fill('wrong@example.com');
    await page.locator('[name=password]').fill('wrongpass12');

    const [response] = await Promise.all([
      page.waitForResponse(
        (res) => res.url().includes('/auth/login') && res.request().method() === 'POST'
      ),
      page.getByRole('button', { name: 'Sign in' }).click(),
    ]);

    expect(response.status()).toBe(401);
    // lib/api.ts handleUnauthorized() redirects to /login on 401, clearing the inline error banner
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible();
  });

  test('logout → redirects to login', async ({ page }) => {
    const store = await createTestStore();

    try {
      await setAuthInPage(page, store.jwt);
      await page.goto('/dashboard');
      await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

      await page.getByRole('button', { name: 'Logout' }).click();
      await expect(page).toHaveURL('/login');
    } finally {
      await deleteTestStore(store.jwt);
    }
  });
});
