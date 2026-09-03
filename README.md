# FitVision

Multi-tenant SaaS for clothing size recommendations. See [CLAUDE.md](CLAUDE.md) for local development, architecture, and test commands.

## Backend production deployment (Railway + Neon)

### 1. Neon PostgreSQL

1. Create a project at [neon.tech](https://neon.tech).
2. Copy the connection string (format: `postgresql://user:password@host/dbname?sslmode=require`).
3. Flyway runs on startup; all migrations in `src/main/resources/db/migration/` apply to a fresh database.

The app converts Neon’s `postgresql://` (or `postgres://`) URL to `jdbc:postgresql://` automatically when `SPRING_PROFILES_ACTIVE=prod` (`NeonDatabaseUrlEnvironmentPostProcessor`), preserving the `?sslmode=require` (and `channel_binding`) query string. If you already have a `jdbc:`-prefixed URL it is passed through unchanged. The `prod` profile reads the `DATABASE_URL` env var (the dev profile uses `DB_URL`).

### 2. Build (optional for local Docker image)

Railway builds from the root `Dockerfile` (multi-stage Maven + Alpine runtime). For a local production image after code changes:

```bash
mvn clean package -DskipTests
docker build -t fitvision-backend .
```

Local full stack development still uses `docker compose` with `Dockerfile.dev`.

### 3. Railway service

1. Create a new Railway project and connect this repository.
2. Railway reads `railway.toml` (Dockerfile builder, health check on `/actuator/health`).
3. Set environment variables:

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | Neon connection string, full, including `?sslmode=require` (the `prod` profile name; the dev profile / `.env.example` use `DB_URL`) |
| `JWT_SECRET` | Random string, at least 32 bytes (the `prod` profile name; `.env.example` uses `FITVISION_JWT_SECRET`) |
| `SHOPIFY_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key (`openssl rand -base64 32`) — validated at boot |
| `SHOPIFY_SHARED_SECRET` | Shared secret with the Shopify app — must equal the Node service's `FITVISION_SHOPIFY_SHARED_SECRET` exactly |
| `STRIPE_SECRET_KEY` | Stripe secret key — `sk_test_...` for a test deploy, `sk_live_...` for production |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_...`) |
| `STRIPE_PRICE_STARTER` / `_PRO` / `_TEAM` | Stripe recurring Price IDs — must be **three distinct non-empty values** or the app fails to start |
| `SENTRY_DSN` | Required key; set to an empty string to disable Sentry |
| `SPRING_PROFILES_ACTIVE` | `prod` (also hardcoded in the Docker entrypoint) |
| `ADMIN_BOOTSTRAP_TOKEN` | Optional — required only to call `POST /api/admin/seed` |
| `DB_HEALTH_DOWN_MS` / `DB_HEALTH_SLOW_MS` | Optional — `/actuator/health` DB probe thresholds in ms (defaults 2000 / 100) |

Railway sets `PORT` automatically; the `prod` profile binds the server to `${PORT:8080}`.

The `prod` profile requires every variable above without a YAML default. A missing one fails context
startup with `Could not resolve placeholder`.

### 4. CORS (production)

With `prod` active, dashboard, admin, and Shopify API routes allow:

- `https://app.fitvision.io` (dashboard)
- `https://fitvision.io` (marketing site)
- `https://*.myshopify.com` (Shopify storefronts)

`/api/widget/**` remains open to any origin (embeddable widget).

### 5. Health check

- URL: `/actuator/health` (public, no auth). Actuator exposes only `health` and `info`; details are hidden.
- The custom `DatabaseHealthIndicator` runs `SELECT 1` and reports `DOWN` if it exceeds
  `DB_HEALTH_DOWN_MS` (default 2000 ms). Neon with autosuspend enabled can cold-start slower than
  that — keep Neon in the same region as Railway, disable autosuspend, or raise `DB_HEALTH_DOWN_MS`.
  `railway.toml` gives the health check a 120 s timeout to cover build + Flyway on a fresh database.

### 6. Verify deploy

```bash
curl https://<your-railway-domain>/actuator/health
```

Expect HTTP 200 with `"status":"UP"`.

## Shopify app (Node/Express) — Railway

The `shopify-app/` service is deployed separately. It has no Dockerfile; deploy it on Railway with
**Root Directory = `shopify-app`** (Nixpacks detects `package.json` and runs `node src/index.js`).

| Variable | Description |
|----------|-------------|
| `SHOPIFY_API_KEY` / `SHOPIFY_API_SECRET` | From the Shopify Partners app (a development app is enough for testing) |
| `HOST_NAME` | Public URL of this Railway service, e.g. `https://fitvision-shopify.up.railway.app` |
| `FITVISION_API_URL` | Public URL of the backend Railway service |
| `FITVISION_SHOPIFY_SHARED_SECRET` | Must equal the backend's `SHOPIFY_SHARED_SECRET` |
| `FITVISION_ADMIN_EMAIL` / `FITVISION_ADMIN_PASSWORD` | Seeded admin credentials (used on app uninstall) |
| `SESSION_SECRET` | Random string |
| `NODE_ENV` | `production` (enables the `secure` session cookie) |

In the Shopify Partners dashboard register **App URL** `https://<host>` and **Allowed redirection URL**
`https://<host>/auth/callback`; scopes `read_products`, `write_script_tags`.

> **Known limitation:** `shopify-app/src/store.js` keeps shop→JWT credentials in an in-memory `Map`.
> Run a single instance; the map is lost on every restart/redeploy and shops must re-authenticate.

## Widget CDN (Cloudflare R2)

The embeddable widget is built from `widget/` and published to Cloudflare R2. Stores load it from the CDN (not from the backend or Vercel).

| Item | Value |
|------|--------|
| R2 bucket | `fitvision-widget` |
| Object prefix | `widget/` |
| Public URL | `https://cdn.fitvision.io/widget/` |
| Latest script | `https://cdn.fitvision.io/widget/fitvision-widget.min.js` |
| Versioned script | `https://cdn.fitvision.io/widget/fitvision-widget.{version}.min.js` (from `widget/package.json`) |

### Cache-Control strategy

| Artifact | Cache-Control |
|----------|----------------|
| `fitvision-widget.min.js` (latest) | `public, max-age=300` (5 minutes) |
| `fitvision-widget.{version}.min.js` | `public, max-age=31536000, immutable` (1 year) |

Pin production embeds to the versioned URL when you need a stable, long-cached asset; use the latest URL when you want automatic updates after each deploy.

### CORS

Allow browser `GET` from any storefront origin (Shopify and custom domains). Example R2 bucket CORS policy:

```json
[
  {
    "AllowedOrigins": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "MaxAgeSeconds": 3600
  }
]
```

### Cloudflare dashboard setup (one-time)

1. **R2 bucket** — Create bucket `fitvision-widget` in the Cloudflare dashboard (R2 → Create bucket).
2. **Public access** — Enable public access for the bucket (or connect a custom domain) so objects are served over HTTPS.
3. **Custom domain** — Add `cdn.fitvision.io` as a custom domain for the bucket (or via Cloudflare CDN rules) with path prefix `/widget/` mapping to the `widget/` object prefix.
4. **CORS** — Paste the JSON policy above under the bucket’s CORS settings.
5. **API token** — Create a token with R2 read/write for CI uploads (used by GitHub Actions).

### GitHub Actions (widget)

Pushes to `main` that touch `widget/**` run [`.github/workflows/widget.yml`](.github/workflows/widget.yml): `npm ci`, `npm run build` (produces latest + versioned files in `dist/`), then `wrangler r2 object put` for both artifacts with the cache headers above.

### GitHub repository secrets (CI/CD)

Configure these under **Settings → Secrets and variables → Actions**:

| Secret | Used by |
|--------|---------|
| `RAILWAY_TOKEN` | `backend.yml` — Railway project deploy token |
| `VERCEL_TOKEN` | `dashboard.yml` |
| `VERCEL_ORG_ID` | `dashboard.yml` |
| `VERCEL_PROJECT_ID` | `dashboard.yml` |
| `NEXT_PUBLIC_API_URL` | `dashboard.yml` — e.g. `https://api.fitvision.io` |
| `CLOUDFLARE_API_TOKEN` | `widget.yml` — R2 object upload |

Backend and dashboard workflows are defined in [`.github/workflows/backend.yml`](.github/workflows/backend.yml) and [`.github/workflows/dashboard.yml`](.github/workflows/dashboard.yml).
