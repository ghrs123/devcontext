# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FitVision is a multi-tenant SaaS platform for clothing size recommendations. It has four separate runtime components in this monorepo:

| Directory | Tech | Purpose |
|-----------|------|---------|
| `/` (root) | Spring Boot 3.x / Java 21 / Maven | Backend API |
| `/dashboard` | Next.js 14 / TypeScript / Tailwind | Store owner web app |
| `/widget` | Vanilla JS / Vite | Embeddable script tag for stores |
| `/shopify-app` | Node.js / Express | Shopify OAuth integration |

## Backend Commands (Spring Boot)

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Run all tests (unit + integration via Testcontainers)
mvn verify

# Run only unit tests
mvn test

# Run a single test class
mvn test -Dtest=BodyProfileCalculatorTest

# Run a single integration test class
mvn verify -Dit.test=WidgetRecommendationControllerIT

# Full Docker rebuild after code changes
docker compose down
mvn clean package -DskipTests
docker compose up --build -d
docker logs devcontext-fitvision-backend-1 --tail 30
```

Backend runs at `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Dashboard Commands (Next.js)

```bash
cd dashboard
npm install
npm run dev     # dev server at http://localhost:3000
npm run build
npm run lint
npm run test:e2e          # Playwright e2e (see dashboard/e2e/)
npm run test:e2e -- --grep "login"   # run a subset by title
```

## Widget Commands (Vite)

```bash
cd widget
npm install
npm run dev     # dev server
npm run build   # builds + validates gzip size < 50KB
```

Output: `/widget/dist/fitvision-widget.min.js` (single IIFE file, CSS inlined).

## Shopify App Commands (Node.js/Express)

```bash
cd shopify-app
npm install
npm run dev     # nodemon on port 3001
npm run tunnel  # ngrok tunnel for Shopify webhooks
```

## Environment Setup

Copy `.env.example` to `.env` before running Docker. The `.env` file is loaded by `docker-compose.yml`. Required vars: `POSTGRES_*`, `DB_*`, `FITVISION_JWT_SECRET`, `SHOPIFY_ENCRYPTION_KEY`, `FITVISION_SHOPIFY_SHARED_SECRET`.

The Shopify app has its own `.env` in `/shopify-app/` with `SHOPIFY_API_KEY`, `SHOPIFY_API_SECRET`, `HOST_NAME`, `FITVISION_API_URL`, and `FITVISION_SHOPIFY_SHARED_SECRET`.

Production: backend deploys on Railway (root `Dockerfile`, `railway.toml`, health check `/actuator/health`) against Neon PostgreSQL. Under `SPRING_PROFILES_ACTIVE=prod` the app rewrites Neon's `postgresql://` URL to JDBC automatically. Prod-only vars include `DATABASE_URL`, `JWT_SECRET`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*`. See `README.md` for the full list.

## Admin Account

The admin account can only be created via the seed script (never via `/auth/register`):
```bash
./scripts/create-admin.sh <email> <password> <bootstrap-token> [base-url]
```
This calls `POST /api/admin/seed` with an `X-Bootstrap-Token` header; it returns 409 if any admin already exists.

## Backend Architecture

### Layered Structure

```
Controller → Service → Repository → Database
Controller → Service → Engine       (recommendation logic)
```

- **`api/`** — HTTP controllers only (request/response mapping, no business logic)
  - `api/widget/` — public widget endpoint (API key auth)
  - `api/dashboard/` — store owner endpoints (JWT auth)
  - `api/admin/` — admin endpoints (JWT + ADMIN role)
  - `api/shopify/` — Shopify OAuth/webhook endpoints (shared secret)
  - `api/billing/` — Stripe webhook (`StripeWebhookController`); `api/dashboard/billing/` — store-owner subscription status/checkout
- **`domain/`** — JPA entities, domain services, and repositories interfaces by domain concept
- **`engine/recommendation/`** — stateless computation: `BodyProfileCalculator`, `SizeChartMatcher`, `RecommendationEngine` (no DB access in engine classes)
- **`infrastructure/`** — cross-cutting: security filters, persistence repositories, file parsers
- **`integration/scraper/`** — Playwright-based brand scrapers (`BrandScraper` interface, `BrandScraperRegistry`, `ScraperService`)
- **`domain/billing/`** — `StripeService`; subscription/plan fields live on the `Store` entity (V9 migration)
- **`shared/`** — `ApiResponse<T>` envelope, `GlobalExceptionHandler`, `ErrorCode` enum

### Multi-Tenancy

Every request to `/api/widget/**` or `/api/dashboard/**` sets a `UUID tenantId` in `TenantContext` (ThreadLocal). All repository queries on tenant-scoped entities **must** include `tenantId`. The pattern is always:

```java
repository.findByIdAndTenantId(id, TenantContext.get());  // correct
repository.findById(id);                                   // forbidden on tenant data
```

Brands with `tenant_id = null` are FitVision-managed global brands accessible to all stores.

### Authentication Flow

Three security filters apply before `UsernamePasswordAuthenticationFilter`:

1. **`AdminAuthFilter`** — triggers on `/api/admin/**`; validates JWT + `role=ADMIN` claim
2. **`JwtAuthFilter`** — triggers on `/api/dashboard/**` (except `/auth/**`); validates JWT, sets `TenantContext`
3. **`ApiKeyAuthFilter`** — triggers on `/api/widget/**`; looks up store by `X-FitVision-Key` header, sets `TenantContext`

The `SecretKeyAuthFilter` (still present) is scoped via `shouldNotFilter()` to `/api/dashboard/v1/size-charts/` only.

### API Response Envelope

All responses use `ApiResponse<T>`:
```json
{ "success": true, "data": {...}, "error": null, "meta": { "requestId": "...", "timestamp": "..." } }
```
Use `ApiResponse.ok(data)` and `ApiResponse.error(ErrorCode.X, message)`.

### Recommendation Engine

`RecommendationEngine.recommend(RecommendationInput)` orchestrates:
1. `BodyProfileCalculator` — computes BMI, body fat (Deurenberg), chest/waist/hip estimates
2. `SizeChartMatcher` — matches `BodyProfile` against `List<SizeEntry>`, returns `MatchResult` with confidence score (0.0–1.0) and `MatchQuality` (EXACT/PARTIAL/CLOSEST/NO_MATCH)
3. Persists `RecommendationRequest` entity; if `storeBodyData=false`, body measurements are zeroed

### Database Migrations

Flyway runs automatically on startup. Migrations are in `src/main/resources/db/migration/`. Last applied is **V10** (`recommendation_duration_ms`); V8 = `scrape_jobs` table, V9 = billing fields. Next migration is **V11**.

### Scraping Pipeline

`BrandScraper` implementations are auto-discovered by `BrandScraperRegistry` via Spring DI. `ScraperService.executeScrape()` is `@Transactional` and never overwrites an existing active size chart on failure. `ScrapeScheduler` fires every Monday at 2am. Playwright browser must always be closed in `finally`.

## Dashboard Architecture

Next.js 14 App Router with three route groups:

- **`(auth)/`** — `/login`, `/register` — no sidebar
- **`(app)/`** — `/dashboard`, `/products`, `/settings` — store owner shell with sidebar
- **`(admin)/admin/`** — `/admin/dashboard`, `/admin/stores`, `/admin/brands`, `/admin/recommendations` — admin shell (dark sidebar, distinct from store UI)

Route protection is in `middleware.ts`: decodes JWT role from the `fitvision_token` cookie. `role=ADMIN` redirects to `/admin/dashboard`; `role=STORE` to `/dashboard`. Admin routes require `role=ADMIN`.

Token is stored in `localStorage` + mirrored to a cookie (`fitvision_token`) for middleware access. Data fetching uses SWR.

## Testing

Integration tests extend `AbstractIntegrationTest` which starts a shared singleton Testcontainers PostgreSQL 16 instance. Test class naming: `{ClassName}Test` for unit tests (plain JUnit 5 + Mockito, no Spring context), `{ClassName}IT` for integration tests.

The `TestDataBuilder` utility in `src/test/java/com/fitvision/testutil/` builds test fixtures.

Dashboard e2e tests live in `dashboard/e2e/` (Playwright, config in `dashboard/playwright.config.ts`).

## Additional Docs

`docs/` holds detailed design docs (Portuguese): architecture (`03`), data model (`05`), security/multi-tenancy (`11`), GDPR (`12`), testing (`15`), env config (`14`), roadmap (`18`). `docs/19-guia-para-novo-developer.md` is the new-developer onboarding guide. `docs-site/` is a static browser for these docs (`npm run docs` at repo root, serves on :4000).

The `fitvision-invariant-reviewer` subagent (`.claude/agents/`) checks a diff against the Key Invariants below — run it before committing changes touching `domain/`, `infrastructure/security/`, `api/widget/`, or `api/dashboard/`.

## Key Invariants

- **Widget bundle**: must stay under 50KB gzipped; `build-check.js` enforces this
- **GDPR**: never store body measurements with `storeBodyData=false`; never log height/weight at INFO level
- **Tenant isolation**: every tenant-scoped repository query must include `tenantId`
- **Size labels**: always normalised to uppercase
- **Only one active SizeChart per product** at a time (enforced at application layer)
- **Widget API contract** (`/api/widget/v1/`): versioned — breaking changes require a new version path
- **Shopify access tokens**: stored AES-256-GCM encrypted in `stores.shopify_access_token_encrypted`
