import { defineConfig } from '@playwright/test';

/**
 * E2E tests hit the real FitVision backend at http://localhost:8080 (see E2E_API_URL).
 * Start the Spring Boot API before running tests — Playwright only starts the Next.js dev server.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 1,
  timeout: 30_000,
  workers: 1,
  outputDir: 'e2e/results',
  reporter: [
    ['line'],
    ['json', { outputFile: 'e2e/results/report.json' }],
    ['html', { outputFolder: 'playwright-report' }],
  ],
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
  },
});
