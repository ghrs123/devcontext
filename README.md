# FitVision

Multi-tenant SaaS for clothing size recommendations. See [CLAUDE.md](CLAUDE.md) for local development, architecture, and test commands.

## Backend production deployment (Railway + Neon)

### 1. Neon PostgreSQL

1. Create a project at [neon.tech](https://neon.tech).
2. Copy the connection string (format: `postgresql://user:password@host/dbname?sslmode=require`).
3. Flyway runs on startup; all migrations in `src/main/resources/db/migration/` apply to a fresh database.

The app converts Neon’s `postgresql://` URL to `jdbc:postgresql://` automatically when `SPRING_PROFILES_ACTIVE=prod`. You can also set `JDBC_DATABASE_URL` manually if you prefer.

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
| `DATABASE_URL` | Neon connection string (`postgresql://...`) |
| `JWT_SECRET` | Random string, at least 32 bytes |
| `SHOPIFY_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key |
| `SHOPIFY_SHARED_SECRET` | Shared secret with the Shopify app |
| `STRIPE_SECRET_KEY` | Stripe live secret key (`sk_live_...`) |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_...`) |
| `STRIPE_PRICE_STARTER` | Stripe Price ID for Starter |
| `STRIPE_PRICE_PRO` | Stripe Price ID for Pro |
| `STRIPE_PRICE_TEAM` | Stripe Price ID for Team |
| `SPRING_PROFILES_ACTIVE` | `prod` |

Railway sets `PORT` automatically; the `prod` profile binds the server to `${PORT:8080}`.

### 4. CORS (production)

With `prod` active, dashboard, admin, and Shopify API routes allow:

- `https://app.fitvision.io` (dashboard)
- `https://fitvision.io` (marketing site)
- `https://*.myshopify.com` (Shopify storefronts)

`/api/widget/**` remains open to any origin (embeddable widget).

### 5. Health check

- URL: `/actuator/health`
- Actuator exposes only `health` and `info`; health details are hidden.

### 6. Verify deploy

```bash
curl https://<your-railway-domain>/actuator/health
```

Expect HTTP 200 with `"status":"UP"`.

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
