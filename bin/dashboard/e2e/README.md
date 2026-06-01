# FitVision Dashboard E2E Tests

Playwright tests run against a **real backend** and the Next.js dashboard.

## Prerequisites

1. **PostgreSQL** — same database the backend uses (e.g. Docker Compose or local Postgres).
2. **Backend API** on `http://localhost:8080`:
   ```bash
   # from repo root
   mvn spring-boot:run
   # or: docker compose up -d fitvision-backend
   ```
3. **Chromium** (one-time):
   ```bash
   npx playwright install chromium
   ```

The Playwright config starts `npm run dev` on port 3000 automatically (or reuses an existing dev server).

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `E2E_API_URL` | `http://localhost:8080` | Backend base URL for test helpers |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | Used by the dashboard when dev server starts |

## Run tests

```bash
cd dashboard
npm run test:e2e          # headless
npm run test:e2e:headed   # visible browser
npm run test:e2e:ui       # Playwright UI mode
```

## Artifacts

Failures write screenshots/videos to `e2e/results/`. HTML report: `playwright-report/` (both gitignored).

## Notes

- Each test creates its own store via the register API (`uniqueEmail()`).
- `deleteTestStore()` removes products/brands but cannot delete the store row (no public delete endpoint).
- Auth uses `localStorage` key `fitvision_access_token` and cookie `fitvision_token` for middleware.
- **Backend version matters:** plan-limit and billing tests auto-skip if the running API at `:8080` does not enforce limits or returns 500 on `/billing/status`. Rebuild/restart the backend from current source for full coverage.
- Login failures return HTTP 401 but the UI does not show an inline error — `lib/api.ts` `handleUnauthorized()` redirects to `/login` on every 401, wiping React state (spec expected visible `Invalid` text).
