# FitVision — System Overview

## What is FitVision
FitVision is a SaaS platform that reduces return rates in online fashion retail caused by incorrect sizing. It provides a smart sizing recommendation engine that online stores embed into their product pages as a lightweight widget.

## The Problem
Online clothing returns cost retailers 20–30% of revenue. The primary cause is size mismatch. The problem has two layers:
1. Customers do not know their measurements accurately
2. Sizing is inconsistent across brands and even across models within the same brand (a Zara M is not a C&A M)

## The Solution
FitVision uses customer-provided body data (weight, height) combined with mathematical body composition estimates to recommend the correct size for a specific product from a specific brand. The recommendation is always product-specific, not generic.

## Who Uses It

### Store owners (B2B customers — who pay)
- Shopify, WooCommerce, and other e-commerce platform operators
- They install the FitVision widget on their store with minimal technical effort
- They manage their product size charts via a dashboard or file upload
- They pay a monthly subscription based on usage

### End users (B2C — buyers on the store)
- Online shoppers on a store that has FitVision installed
- They interact with the widget on the product page
- They provide height, weight, and optionally gender and age
- They receive a size recommendation for that specific product
- They do not create accounts or pay anything

## Core Value Proposition
- For stores: reduce return rates without hiring developers or a dedicated team
- For buyers: confident size decisions without needing to know their exact measurements

## Key Constraint
Integration must be zero-effort for stores. No developer required, no team onboarding, no complex setup. A store owner must be able to install and go live within minutes.
# FitVision — Architecture Decisions

## System Type
Multi-tenant SaaS with embeddable widget. Three distinct runtime contexts:
1. Store dashboard (web app — store owners manage their account)
2. Public widget (embedded JS — runs inside the store's product page)
3. Backend API (serves both dashboard and widget)

## ADR-001: Widget as Embeddable JavaScript Snippet
**Decision:** The store integration is a single `<script>` tag that the owner pastes into their store theme. No app install, no developer needed.
**Rationale:** This is the lowest-friction integration path. Works on Shopify, WooCommerce, Wix, and any HTML-based store without platform-specific development.
**Trade-off:** Widget must be lightweight (<50KB), load asynchronously, and never block the store's page render.

## ADR-002: Shopify App as Optional Acceleration Layer
**Decision:** Build a Shopify App in addition to the script tag, but not as a requirement.
**Rationale:** Shopify represents ~30% of e-commerce. A native app in the Shopify App Store provides discovery and one-click install. The script tag remains the universal fallback.
**Trade-off:** Requires maintaining two integration paths.

## ADR-003: Size Data via File Upload + Scraping Pipeline
**Decision:** Stores provide size charts via CSV/Excel upload. For major brands (Zara, H&M, etc.), FitVision maintains a scraped and curated database updated periodically.
**Rationale:** Covers both the long tail (small stores upload their own data) and major brands (FitVision owns the data quality). Stores never need to upload what FitVision already knows.
**Trade-off:** Scraping pipeline requires maintenance and monitoring. Must respect robots.txt and terms of service.

## ADR-004: Recommendation Engine is Deterministic, Not ML
**Decision:** Use validated anthropometric formulas (BMI, body fat estimation via Deurenberg formula, waist estimation via YMCA formula) to map body data to brand-specific size charts.
**Rationale:** ML requires large labelled datasets that do not exist at launch. Deterministic formulas are explainable, auditable, and immediately deployable. Can be replaced with ML later when data exists.
**Trade-off:** Formula-based estimation has accuracy limits, especially for edge body types. Must be communicated clearly to the user.

## ADR-005: Multi-tenant Isolation via Tenant ID
**Decision:** All store data is isolated by tenant_id at the database query level. No separate databases per tenant.
**Rationale:** Operational simplicity at early stage. Row-level isolation is sufficient for this data sensitivity level.
**Trade-off:** A bug in query isolation could leak data. Mitigated by strict repository pattern enforcing tenant_id on every query.

## ADR-006: Widget API is Stateless and Public
**Decision:** The widget recommendation endpoint requires only the store's public API key, product ID, and body measurements. No user authentication.
**Rationale:** Buyers do not create accounts. Removing friction is critical for conversion. Body measurements are not stored server-side by default.
**Trade-off:** No personalisation or history for returning buyers. Can be added later as an opt-in feature.

## ADR-007: Backend in Spring Boot
**Decision:** Spring Boot for all backend services.
**Rationale:** Existing team expertise. Strong typing, mature ecosystem, proven in production integrations.

## ADR-008: Frontend in Next.js
**Decision:** Next.js for the store dashboard. Vanilla JS for the embeddable widget (no framework dependency).
**Rationale:** Next.js for the dashboard provides SSR, routing, and Vercel deploy simplicity. The widget must have zero framework dependency to avoid conflicts with the store's own stack.
# FitVision — Stack and Technology Decisions

## Backend
- **Runtime:** Java 21
- **Framework:** Spring Boot 3.x
- **Database access:** Spring Data JPA + Hibernate
- **Migration:** Flyway
- **Validation:** Jakarta Bean Validation
- **Security:** Spring Security (API key auth for widget, JWT for dashboard)
- **Build:** Maven

## Frontend (Store Dashboard)
- **Framework:** Next.js 14 (App Router)
- **Language:** TypeScript
- **Styling:** Tailwind CSS
- **Components:** shadcn/ui
- **State:** React hooks + SWR for data fetching
- **Forms:** React Hook Form + Zod

## Widget (Embeddable)
- **Language:** Vanilla JavaScript (ES6+)
- **Bundler:** Vite (outputs single minified JS file)
- **No framework dependencies** — must run in any store environment
- **Max bundle size:** 50KB gzipped
- **Load strategy:** async, deferred, never blocking

## Database
- **Engine:** PostgreSQL 16
- **Hosting:** Neon (serverless, free tier to start)
- **Multi-tenancy:** Row-level isolation via tenant_id

## Infrastructure
- **Backend deploy:** Railway
- **Frontend deploy:** Vercel
- **Widget CDN:** Cloudflare CDN (widget JS served from edge)
- **File storage:** Cloudflare R2 (size chart file uploads)

## Third-party Services
- **Payments:** Stripe (subscriptions)
- **Email:** Resend
- **Auth (dashboard):** JWT issued by Spring Boot (Keycloak optional in future)
- **Scraping:** Custom Spring Boot scheduled service + Playwright

## Approved Libraries
- Apache Commons Math (anthropometric calculations)
- OpenCSV (CSV parsing for size chart uploads)
- Apache POI (Excel parsing for size chart uploads)
- Playwright Java (scraping pipeline)

## Explicitly Forbidden
- No frontend framework inside the widget (React, Vue, Angular, etc.)
- No jQuery
- No server-side sessions — stateless API only
- No storing raw body measurements in the database without explicit user consent flag
# FitVision — Domain Model

## Core Entities

### Store (Tenant)
The B2B customer. A store owner who integrates FitVision into their e-commerce platform.
- id, name, email, plan, status, api_key_public, api_key_secret
- platform (shopify / woocommerce / other)
- created_at, subscription_status

### Brand
A clothing brand. Can be created by the store (custom) or maintained by FitVision (global).
- id, name, slug, source (fitvision_managed / store_uploaded), tenant_id (null if global)
- last_scraped_at (for managed brands)

### Product
A specific clothing item within a brand. Size charts vary by product, not just by brand.
- id, brand_id, tenant_id, external_product_id (Shopify product ID, etc.)
- name, category (tops / bottoms / dresses / outerwear / etc.)
- gender_target (male / female / unisex)

### SizeChart
The size-to-measurement mapping for a specific product.
- id, product_id, version, source (uploaded / scraped / manual)
- created_at, active

### SizeEntry
A single row in a size chart. Maps a size label to measurement ranges.
- id, size_chart_id
- size_label (XS / S / M / L / XL / XXL or numeric: 36 / 38 / 40)
- chest_min, chest_max (cm)
- waist_min, waist_max (cm)
- hip_min, hip_max (cm)
- height_min, height_max (cm) — optional

### RecommendationRequest
A recommendation event triggered by a buyer on the widget. Stored for analytics.
- id, tenant_id, product_id
- height_cm, weight_kg, gender, age (all nullable)
- recommended_size, confidence_score
- created_at
- body_measurements_stored: boolean (GDPR consent flag)

### BodyProfile (derived, not persisted by default)
Computed on the fly from buyer input. Never stored unless consent given.
- height_cm, weight_kg, gender, age
- bmi (computed)
- estimated_body_fat_pct (Deurenberg formula)
- estimated_chest_cm (formula-based)
- estimated_waist_cm (YMCA formula)
- estimated_hip_cm (formula-based)
- body_type (ectomorph / mesomorph / endomorph — approximation)

## Key Business Rules

1. A recommendation is always product-specific. Never recommend a generic size.
2. If no size chart exists for the product, return a graceful fallback (link to brand's own size guide).
3. Confidence score is returned with every recommendation. Low confidence must be surfaced to the buyer.
4. Global brands (FitVision-managed) take precedence over store-uploaded data for the same brand, unless the store explicitly overrides.
5. A store's public API key is used by the widget. The secret key is only used for dashboard API calls.
6. Body measurements are never stored without an explicit consent flag set to true.
7. Size labels must always be normalised to uppercase (S not s, XL not xl).
8. A product can have multiple size chart versions but only one can be active at a time.

## Recommendation Algorithm (Deterministic v1)

Input: height_cm, weight_kg, gender, age (optional)

Step 1 — Compute BMI
  bmi = weight_kg / (height_cm / 100)²

Step 2 — Estimate body fat percentage (Deurenberg formula)
  body_fat = (1.20 × bmi) + (0.23 × age) - (10.8 × gender_factor) - 5.4
  gender_factor: male = 1, female = 0, unisex = 0.5

Step 3 — Estimate chest circumference
  lean_mass_kg = weight_kg × (1 - body_fat / 100)
  chest_cm = 85 + (lean_mass_kg × 0.4) + (bmi × 0.5)

Step 4 — Estimate waist circumference (YMCA formula approximation)
  waist_cm = (weight_kg × 0.74) + (height_cm × 0.18) - 28

Step 5 — Estimate hip circumference
  hip_cm = chest_cm × 1.05 (approximation, gender-adjusted)

Step 6 — Match against size chart
  Find SizeEntry where chest, waist, and hip all fall within min/max ranges.
  If multiple entries match: return the one with the highest overlap percentage.
  If no entry matches: return closest match with low confidence score.

Step 7 — Compute confidence score (0.0 to 1.0)
  1.0 = all three measurements within range
  0.7 = two measurements within range
  0.4 = one measurement within range
  0.2 = closest match only
# FitVision — Code Patterns and Conventions

## Package Structure (Backend)

```
com.fitvision
├── api
│   ├── widget          # Public widget endpoints (no auth)
│   ├── dashboard       # Store dashboard endpoints (JWT auth)
│   └── webhook         # Shopify and platform webhooks
├── domain
│   ├── store
│   ├── brand
│   ├── product
│   ├── sizechart
│   └── recommendation
├── engine
│   └── recommendation  # Anthropometric calculation engine
├── integration
│   ├── shopify
│   └── scraper
├── infrastructure
│   ├── persistence
│   ├── security
│   └── storage
└── shared
    ├── exception
    ├── validation
    └── response
```

## Naming Conventions

- Classes: PascalCase — `SizeChartService`, `RecommendationEngine`
- Methods: camelCase — `computeBodyProfile`, `findBestMatch`
- Constants: UPPER_SNAKE_CASE — `MAX_CONFIDENCE_SCORE`
- Database tables: snake_case — `size_chart`, `recommendation_request`
- API endpoints: kebab-case — `/api/widget/v1/size-recommendation`
- Environment variables: UPPER_SNAKE_CASE — `DATABASE_URL`, `STRIPE_SECRET_KEY`

## Layered Architecture (mandatory)

```
Controller → Service → Repository → Database
Controller → Service → Engine (for recommendation logic)
```

- Controllers handle HTTP only: request mapping, response wrapping, error handling
- Services contain business logic and orchestration
- Repositories handle data access only — no business logic
- Engine classes are stateless computation units — no database access

## API Response Envelope

All API responses use a standard envelope:

```json
{
  "success": true,
  "data": { },
  "error": null,
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

Error response:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "SIZE_CHART_NOT_FOUND",
    "message": "No size chart found for product ID xyz",
    "field": null
  },
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

## Tenant Isolation Pattern

Every repository method that accesses tenant data MUST include tenant_id as a parameter.
Never query without tenant_id on tenant-scoped entities.

```java
// Correct
sizeChartRepository.findByIdAndTenantId(id, tenantId);

// Forbidden
sizeChartRepository.findById(id);
```

## Error Handling

- Use custom exception classes per domain: `SizeChartNotFoundException`, `InvalidBodyMeasurementException`
- All exceptions extend `FitVisionException` with an error code enum
- Global exception handler in `GlobalExceptionHandler` translates to API envelope
- Never expose stack traces in API responses

## Widget JavaScript Conventions

- No external dependencies
- All DOM manipulation via vanilla JS
- Widget initialises from a data attribute: `data-fitvision-product-id`
- Widget namespace: `window.FitVision` — never pollute global scope otherwise
- All API calls include the public store API key as a header: `X-FitVision-Key`

## Logging

- Use SLF4J with structured logging
- Always log: tenant_id, product_id, request_id on recommendation requests
- Never log: raw body measurements, API keys, personal data
- Log level: INFO for business events, DEBUG for computation steps, ERROR for failures

## Testing

- Unit tests for all engine computation methods
- Integration tests for all repository methods
- Contract tests for all public API endpoints
- Test class naming: `{ClassName}Test` for unit, `{ClassName}IT` for integration
# FitVision — Boundaries and Hard Constraints

## What Must Never Change Without Explicit Decision

- The public widget API contract — stores depend on it. Any breaking change requires versioning (/v1, /v2)
- The recommendation algorithm input/output contract — changing it silently would break stored analytics
- The tenant isolation pattern — every data access must be scoped to tenant_id
- The API response envelope structure

## What Must Never Be Done

- Store raw body measurements without explicit GDPR consent flag set to true
- Return a recommendation without a confidence score
- Query tenant-scoped data without tenant_id filter
- Use a frontend framework inside the embeddable widget
- Block the store page render — widget script must always be async/deferred
- Expose internal error details or stack traces in API responses
- Log personal data (height, weight, measurements) at INFO level or above
- Use a single global size chart for a brand — recommendations are always product-specific

## GDPR Constraints (EU Market)

- Body measurements are personal data under GDPR
- Do not store them server-side unless the buyer explicitly consents
- When stored, must be deletable on request
- Widget must not set cookies without consent
- Data retention policy must be defined before storing any measurement data

## Integration Constraints

- Widget must work without any server-side changes on the store's side
- A store owner must reach first recommendation in under 10 minutes from sign-up
- Widget bundle must stay under 50KB gzipped
- Widget must not conflict with the store's existing JS libraries

## Scraping Constraints

- Only scrape public size chart pages
- Respect robots.txt
- Do not scrape at a rate that could be considered abusive
- Store the scrape timestamp and source URL for every scraped size chart
- Flag scraped data with staleness after 30 days — trigger re-scrape
# FitVision — Build Progress

## Current Status
Phase 9 — Scraping Pipeline. Prompts generated and ready to execute. Phases 1–8 fully complete.

## What Has Been Built

### Backend (Spring Boot 3.x, Java 21)
- 80+ production classes
- 77 tests passing (45 unit + 32 integration)
- 8 Flyway migrations (V1–V8)
- Running in Docker via docker-compose

### Widget (Vanilla JS + Vite)
- Single JS file: fitvision-widget.min.js (4.32KB gzipped)
- Full flow: trigger → form → loading → API → result/error
- Accessibility: ARIA roles, keyboard navigation, prefers-reduced-motion
- Mock mode for local testing without backend
- build-check.js validates gzip size < 50KB on every build

### Store Dashboard (Next.js 14)
- Auth: register, login, logout, JWT + cookie mirror for middleware
- Role-based redirect on login: ADMIN → /admin/dashboard, STORE → /dashboard
- Sidebar navigation: Dashboard, Products, Settings
- Analytics: summary cards, quality distribution chart (Recharts), top products table
- Products: full CRUD, size chart upload (CSV/Excel), manual entry, soft delete
- Brand management: create brands inline from ProductForm, list + delete in Products page
- Settings: store profile, API keys (reveal/copy/regenerate), Widget Integration Guide

### Admin Area (Next.js 14)
- Separate /admin route group with visually distinct dark sidebar
- Navigation: Platform Overview, Stores, Global Brands, Recommendations
- Platform Overview: summary cards + quality chart + recent activity
- Stores: search, status filter, pagination, activate/deactivate, detail drawer
- Global Brands: create, edit, delete, size chart upload, version history
- Recommendations Log: filters by store, quality, date range, pagination
- useAdminGuard hook: validates role, clears token + redirects on 401/403

### Shopify App (Node.js/Express)
- OAuth flow: install → consent → callback → FitVision account linked
- Product sync: Shopify catalogue → FitVision (idempotent, category mapping)
- Webhooks: products/create, products/update, products/delete, app/uninstalled (HMAC validated)
- Widget injection: ScriptTag API (idempotent)
- Admin JWT: cached in memory, refreshed every 20h
- Uninstall: deactivates store, data preserved, reinstall reactivates
- README with full ngrok local dev setup

## Completed Phases

### Phase 1 — Foundation ✅
- Spring Boot 3.x, Maven, Java 21
- V1__init_schema.sql — all tables with UUID PKs, indexes, tenant isolation
- JPA entities: Store, Brand, Product, SizeChart, SizeEntry, RecommendationRequest
- Repository layer with mandatory tenant_id on every tenant-scoped query
- ApiResponse<T> envelope, GlobalExceptionHandler, RequestIdFilter
- ApiKeyAuthFilter (widget), TenantContext (ThreadLocal, always cleared)
- Health check at /actuator/health

### Phase 2 — Recommendation Engine ✅
- Gender enum (MALE=1.0, FEMALE=0.0, UNISEX=0.5 genderFactor)
- BodyProfile value object (immutable, OUT_OF_RANGE when BMI < 15 or > 45)
- BodyProfileCalculator: BMI → Deurenberg body fat → chest/waist/hip estimates
- SizeChartMatcher: dimension matching, confidence score, MatchQuality enum
- RecommendationEngine: 7-step orchestrator, @Transactional, GDPR flag
- 30 unit tests

### Phase 3 — Widget API ✅
- POST /api/widget/v1/size-recommendation
- CORS: all origins for /api/widget/**, POST/OPTIONS
- Graceful fallback: HTTP 200 with hasSizeChart=false
- confidenceLabel (High/Medium/Low) and human-readable message
- 6 integration tests via MockMvc
- scripts/manual-test.sh

### Phase 4 — Size Chart Management ✅
- CsvSizeChartParser (OpenCSV, UTF-8/BOM, max 500 rows)
- ExcelSizeChartParser (Apache POI, .xlsx only, max 500 rows)
- SizeChartParserFactory: format detection by content type + filename
- SizeChartService: versioned uploads, manual entry
- POST /upload, POST /manual, GET /active, DELETE /active
- File size limit: 2MB

### Phase 5 — Store Dashboard API ✅
- JWT auth: register, login, BCrypt strength 12, 24h token
- V2__add_store_password.sql, V3__add_product_soft_delete.sql
- StoreController: GET/PATCH /profile, GET /api-keys, POST /api-keys/regenerate
- ProductController: full CRUD with soft delete
- AnalyticsController: summary + paginated list
- 32 integration tests

### Phase 6 — Embeddable Widget ✅
- Vite build, iife format, 4.32KB gzipped
- api.js, ui.js, main.js, styles.css
- Accessibility, prefers-reduced-motion, multi-container support
- build-check.js size validation

### Phase 7 — Store Dashboard ✅
- Next.js 14 + Tailwind + shadcn/ui
- Auth flow, middleware route protection, JWT + cookie mirror
- Analytics dashboard with Recharts
- Products CRUD + size chart upload/manual
- Settings: profile, API keys, Widget Integration Guide
- Running at localhost:3000

### Phase A — Critical Fixes + Admin Area ✅

**Migrations:**
- V4__add_admin_role.sql: role column on stores (STORE | ADMIN)
- V5__make_product_brand_optional.sql: brandId nullable on products
- V6__add_brand_soft_delete.sql: soft delete on brands

**A1a — SecretKeyAuthFilter scope**
- shouldNotFilter() scoped to /api/dashboard/v1/size-charts/ only

**A1b — brandId optional in ProductService**
- Products can be created without brand — BRAND_NOT_FOUND only when brandId explicitly provided

**A2 — Brand management UI**
- BrandController: GET /brands, POST /brands (tenant-scoped, slug auto-generated)
- Brand selector in ProductForm with inline create option
- Global brands shown as read-only with "Global" badge

**A3 — Admin area backend**
- AdminAuthFilter: JWT + role=ADMIN for /api/admin/**
- JwtService: role claim in token
- AdminController: /metrics, /stores, /brands, /recommendations
- AdminService: platform-wide aggregations (no tenant_id filter)
- POST /api/admin/seed + scripts/create-admin.sh

**A4 — Admin area frontend**
- /admin route group with dark sidebar
- Platform Overview, Stores, Global Brands, Recommendations pages
- useAdminGuard hook, role-based redirect in middleware

**A5 — Swagger/OpenAPI**
- springdoc-openapi-starter-webmvc-ui
- Accessible at http://localhost:8080/swagger-ui.html

### Phase 8 — Shopify App ✅

**8.1 — Setup + OAuth**
- V7__add_shopify_fields.sql: shopify_shop (unique) + shopify_access_token_encrypted
- ShopifyService: AES-256-GCM encrypt/decrypt
- ShopifyController: POST /api/shopify/connect + GET /api/shopify/status
- /shopify-app: Express + @shopify/shopify-api@^11, OAuth flow complete

**8.2 — Product Sync + Widget Injection**
- sync.js: Shopify → FitVision product sync (idempotent, category mapping)
- webhooks.js: products/create, update, delete (HMAC validated, async processing)
- inject.js: ScriptTag API injection (idempotent)
- app.html: embedded UI with status, sync button, integration guide

**8.3 — Uninstall + Local Dev**
- app/uninstalled: HMAC validated, deactivates store via admin API, data preserved
- Reinstall reactivates store in ShopifyController
- Admin JWT: cached in memory, refreshed every 20h
- README.md: full ngrok local dev setup with troubleshooting

## Current Phase
Phase 9 — Scraping Pipeline (prompts generated, ready to execute)

## Phase 9 Status

### 9.1 — Scraper Infrastructure + Zara 🔲 PENDING
### 9.2 — Admin Scrape Endpoints + UI 🔲 PENDING
### 9.3 — H&M, Pull&Bear, Mango Scrapers 🔲 PENDING

## Flyway Migration History
```
V1 — init_schema                  Phase 1
V2 — add_store_password           Phase 5
V3 — add_product_soft_delete      Phase 5
V4 — add_admin_role               Phase A3
V5 — make_product_brand_optional  Phase A1b
V6 — add_brand_soft_delete        Phase A2
V7 — add_shopify_fields           Phase 8.1
V8 — add_scrape_jobs              Phase 9.1 (next)
```

## Remaining Phases

### Phase 9 — Scraping Pipeline (next)
- V8 migration: scrape_jobs table, scrape_source_url + last_scraped_at on size_charts
- BrandScraper interface + BrandScraperRegistry (Spring DI auto-discovery)
- ZaraScraper: robots.txt check, rate limit 3s, garment→body conversion
- HMScraper, PullAndBearScraper, MangoScraper
- AbstractBrandScraper: shared robots.txt, rate limiting, Playwright setup
- ScraperService: executeScrape(), failed scrape never overwrites active chart
- ScrapeScheduler: every Monday 2am, sequential, skips brands scraped < 30 days
- Admin endpoints: POST /brands/{id}/scrape (async), GET /brands/{id}/scrape-jobs
- Admin UI: last scraped date, stale badge, scrape now button, history drawer

### Phase 10 — Billing & Subscriptions
- Stripe: Free (2 products), Starter €29/mo, Pro €79/mo, Team €149/mo
- Stripe webhook handlers: subscription created/updated/cancelled
- Plan enforcement: check limits before product creation and recommendations
- Billing UI in store settings
- Admin subscription view per store
- Level 2 admin: plan override, revenue metrics, store impersonation

### Phase 11 — Production Deployment
- Railway (backend) + Vercel (dashboard) + Cloudflare CDN (widget)
- Neon PostgreSQL (production, serverless)
- Cloudflare R2 (file storage for size chart uploads)
- Resend (transactional email: welcome, API key regenerated, plan upgraded)
- SSL + custom domains: fitvision.io, app.fitvision.io, api.fitvision.io
- CI/CD: GitHub Actions → build → test → deploy
- Environment variables audit

### Phase 12 — Observability & Operations
- Structured logging with correlation IDs
- Sentry for error alerting
- Performance monitoring: p95 recommendation latency
- Admin health panel (Level 3 admin)
- PostgreSQL backup strategy
- Error log viewer per tenant
- Force re-scrape per brand from admin

## Admin Area — Full Specification

### Level 1 — Operational ✅ DONE (Phase A)
- View all registered stores with metrics
- Activate / deactivate any store
- Platform-wide metrics
- Manage global brands and size charts

### Level 2 — Business (Phase 10)
- Subscription status per store
- Plan override manually
- Revenue metrics
- Store impersonation for support (read-only)

### Level 3 — Technical (Phase 12)
- Recommendation logs across all tenants
- Force re-scrape per brand
- Scraping pipeline status
- System health dashboard
- Error log viewer per tenant

### Admin Security Rules
- Admin JWT contains role=ADMIN claim
- AdminAuthFilter validates JWT + role before /api/admin/**
- Admin account created ONLY via seed script
- Seed endpoint returns 409 if any admin already exists
- All admin actions logged with adminStoreId and timestamp

## Docker Rebuild Workflow
```bash
docker compose down
mvn clean package -DskipTests
docker compose up --build -d
docker logs devcontext-fitvision-backend-1 --tail 30
```

## Real-World Testing Notes
- Zara T-Shirt Slim Fit Básica /01 size chart tested end-to-end
- CSV format: size_label, chest_min/max (waist/hip/height empty for tops)
- Peça → body measurements: subtract 4cm min, add 2cm max

## Decisions Made
- JWT chosen over Keycloak
- Singleton Testcontainers — static block, one container per JVM
- SecretKeyAuthFilter scoped to /size-charts/** only
- GDPR: body measurements zeroed when storeBodyData=false
- Widget CSS injected into JS bundle
- Token: localStorage + cookie mirror for middleware
- Docker Compose for local dev; Railway for production
- brandId optional — product can exist without brand
- Admin account only via seed script, never via public register
- Shopify access tokens encrypted AES-256-GCM in database
- Shared secret (X-FitVision-Shopify-Secret) validates Shopify App → Backend calls
- Uninstall deactivates store, never deletes — data always preserved
- Reinstall reactivates existing store in ShopifyController
- Scraping: robots.txt check mandatory, 3s rate limit, failed scrape never overwrites active chart
- Next migration: V9 (V8 used for scrape_jobs)

## Decisions Pending
- Pricing tiers finalisation
- Initial brand database (Zara confirmed; H&M, Pull&Bear, Mango planned for Phase 9.3)
- Stripe integration timing (Phase 10)
- Production database: Neon vs Railway PostgreSQL
- Store detail full product list endpoint (deferred — add in Phase 10 or standalone)
# FitVision — Phase 2 Prompts: Recommendation Engine

> Pre-condition: Phase 1 complete. Application starts, Flyway migrations run, all entities and repositories exist, ApiResponse envelope and GlobalExceptionHandler are working.

---

## Prompt 2.1 — BodyProfile Value Object

### CONTEXT
FitVision backend. Spring Boot 3.x, Java 21. Phase 1 is complete.

We are building the recommendation engine. The first step is a value object that represents the computed body profile of a buyer. This is derived from user input (height, weight, gender, age) and is never persisted directly — it is computed on the fly for each recommendation request.

The domain model for the computation is defined as follows:

**Input:** height_cm (double), weight_kg (double), gender (enum: MALE / FEMALE / UNISEX), age (Integer, nullable)

**Computed fields:**
- bmi = weight_kg / (height_cm / 100)²
- body_fat_pct → Deurenberg formula: (1.20 × bmi) + (0.23 × age) - (10.8 × gender_factor) - 5.4
  - gender_factor: MALE = 1.0, FEMALE = 0.0, UNISEX = 0.5
  - if age is null, use 30 as default
- estimated_chest_cm: lean_mass_kg = weight_kg × (1 - body_fat_pct / 100), then chest = 85 + (lean_mass_kg × 0.4) + (bmi × 0.5)
- estimated_waist_cm: (weight_kg × 0.74) + (height_cm × 0.18) - 28
- estimated_hip_cm: chest_cm × 1.05, adjusted by gender (FEMALE × 1.08, MALE × 1.0, UNISEX × 1.04)

### OBJECTIVE
Create the BodyProfile value object and the BodyProfileCalculator service.

**BodyProfile** (immutable value object, not a JPA entity)
- Package: com.fitvision.engine.recommendation
- All fields final
- No setters
- Include a confidence indicator: if bmi < 15 or bmi > 45, mark as OUT_OF_RANGE
- Include toString() for logging (must not expose raw measurements at INFO level — return only BMI and body type)

**Gender enum**
- Package: com.fitvision.domain.recommendation
- Values: MALE, FEMALE, UNISEX
- Field: double genderFactor

**BodyProfileCalculator** (@Service, stateless)
- Package: com.fitvision.engine.recommendation
- Single public method: BodyProfile calculate(double heightCm, double weightKg, Gender gender, Integer age)
- Validate inputs before computing: height must be between 50 and 250 cm, weight between 20 and 300 kg
- Throw InvalidBodyMeasurementException (already exists from Phase 1) with a clear message if validation fails
- Each formula step must be a private method with a descriptive name
- Add unit-level JavaDoc explaining each formula and its source

### CONSTRAINTS
- BodyProfile is a value object — no JPA annotations, no Spring annotations
- BodyProfileCalculator is a Spring @Service
- All formula constants must be named constants, not magic numbers
- Use double for all calculations — no BigDecimal at this stage
- Round all output measurements to 1 decimal place

### EXPECTED OUTPUT
- Gender.java (enum)
- BodyProfile.java (value object)
- BodyProfileCalculator.java (@Service)

### NEXT STEP
Prompt 2.2 will create the SizeChartMatcher that takes a BodyProfile and a list of SizeEntry and returns the best matching size with a confidence score.

---

## Prompt 2.2 — SizeChartMatcher

### CONTEXT
FitVision backend. BodyProfile value object and BodyProfileCalculator are complete. Gender enum exists at com.fitvision.domain.recommendation.Gender.

SizeEntry entity exists at com.fitvision.domain.sizechart.SizeEntry with fields:
- sizeLabel (String)
- chestMin, chestMax (Double, nullable)
- waistMin, waistMax (Double, nullable)
- hipMin, hipMax (Double, nullable)
- heightMin, heightMax (Double, nullable)

### OBJECTIVE
Create the SizeChartMatcher service that receives a BodyProfile and a list of SizeEntry and returns a MatchResult.

**MatchResult** (value object)
- Package: com.fitvision.engine.recommendation
- Fields: String recommendedSize, double confidenceScore (0.0 to 1.0), MatchQuality quality (enum: EXACT / PARTIAL / CLOSEST / NO_MATCH)
- Static factory methods: exact(String size), partial(String size, double score), closest(String size), noMatch()

**Matching algorithm:**
1. For each SizeEntry, compute how many measurement dimensions match the BodyProfile:
   - chest matches if BodyProfile.estimatedChestCm is within [chestMin, chestMax] (skip if both are null)
   - waist matches if BodyProfile.estimatedWaistCm is within [waistMin, waistMax] (skip if both are null)
   - hip matches if BodyProfile.estimatedHipCm is within [hipMin, hipMax] (skip if both are null)
   - height matches if BodyProfile.heightCm is within [heightMin, heightMax] (skip if both are null)
2. Count matched dimensions vs total available dimensions
3. Confidence score = matched / total available
4. Quality:
   - EXACT: all available dimensions match (score = 1.0)
   - PARTIAL: at least half match (score >= 0.5)
   - CLOSEST: best available match (score < 0.5)
   - NO_MATCH: empty size chart
5. If multiple entries have the same score, prefer the one where waist matches (most reliable predictor)
6. If BodyProfile is OUT_OF_RANGE, cap confidence score at 0.5 regardless of matches

**SizeChartMatcher** (@Service, stateless)
- Package: com.fitvision.engine.recommendation
- Single public method: MatchResult match(BodyProfile profile, List<SizeEntry> entries)
- Return MatchResult.noMatch() if entries is empty or null

### CONSTRAINTS
- Pure computation — no database access, no external calls
- All logic must be unit testable in isolation
- Null-safe for all SizeEntry measurement fields

### EXPECTED OUTPUT
- MatchResult.java (value object + MatchQuality enum)
- SizeChartMatcher.java (@Service)

### NEXT STEP
Prompt 2.3 will create the RecommendationEngine that orchestrates BodyProfileCalculator + SizeChartMatcher and produces the final recommendation.

---

## Prompt 2.3 — RecommendationEngine (Orchestrator)

### CONTEXT
FitVision backend. All components exist:
- BodyProfileCalculator (@Service) — computes BodyProfile from height, weight, gender, age
- SizeChartMatcher (@Service) — matches BodyProfile against List<SizeEntry>, returns MatchResult
- SizeChartRepository — findActiveByProductIdAndTenantId(UUID productId, UUID tenantId)
- SizeEntryRepository — findAllBySizeChartId(UUID sizeChartId)
- ProductRepository — findByIdAndTenantId(UUID id, UUID tenantId)
- RecommendationRequestRepository — save()
- GlobalExceptionHandler handles FitVisionException subclasses
- ErrorCode enum has: PRODUCT_NOT_FOUND, SIZE_CHART_NOT_FOUND

### OBJECTIVE
Create the RecommendationEngine service that orchestrates the full recommendation flow.

**RecommendationRequest DTO** (input, not the JPA entity)
- Package: com.fitvision.engine.recommendation
- Name: RecommendationInput
- Fields: UUID tenantId, UUID productId (or String externalProductId), double heightCm, double weightKg, Gender gender (nullable), Integer age (nullable), boolean storeBodyData

**RecommendationOutput DTO**
- Package: com.fitvision.engine.recommendation
- Fields: String recommendedSize, double confidenceScore, MatchQuality quality, String productName, String brandName, boolean hasSizeChart, String fallbackUrl (nullable — used when hasSizeChart = false)

**RecommendationEngine** (@Service)
- Package: com.fitvision.engine.recommendation
- Method: RecommendationOutput recommend(RecommendationInput input)

**Flow:**
1. Validate input (height, weight ranges) — delegate to BodyProfileCalculator which throws InvalidBodyMeasurementException
2. Find product by productId + tenantId — throw ProductNotFoundException if not found
3. Find active size chart for product + tenant — if not found, return graceful fallback (hasSizeChart=false, no exception)
4. Load all SizeEntry for the size chart
5. Compute BodyProfile via BodyProfileCalculator
6. Get MatchResult via SizeChartMatcher
7. Persist RecommendationRequest entity (the JPA one) with all data + confidenceScore + storeBodyData flag
8. Return RecommendationOutput

**Fallback behaviour (no size chart):**
- Return RecommendationOutput with hasSizeChart=false
- recommendedSize = null
- confidenceScore = 0.0
- quality = NO_MATCH
- fallbackUrl = null (will be populated in a future phase)

### CONSTRAINTS
- Gender defaults to UNISEX if null in input
- Age defaults to 30 if null (handled inside BodyProfileCalculator already)
- Log at INFO: tenantId, productId, recommendedSize, confidenceScore, quality
- Log at DEBUG: full BodyProfile details
- Never log raw height/weight at INFO level
- Transaction: the persist step (step 7) must be @Transactional

### EXPECTED OUTPUT
- RecommendationInput.java (DTO)
- RecommendationOutput.java (DTO)
- RecommendationEngine.java (@Service)

### NEXT STEP
Prompt 2.4 will create comprehensive unit tests for BodyProfileCalculator, SizeChartMatcher, and RecommendationEngine.

---

## Prompt 2.4 — Unit Tests for the Engine

### CONTEXT
FitVision backend. All engine components are complete:
- BodyProfileCalculator — computes BMI, body fat, chest, waist, hip estimates
- SizeChartMatcher — matches BodyProfile to SizeEntry list, returns MatchResult
- RecommendationEngine — orchestrates the full flow, uses repositories

Testing stack: JUnit 5, Mockito (via spring-boot-starter-test).

### OBJECTIVE
Create comprehensive unit tests for all three engine components.

**BodyProfileCalculatorTest**

Test cases to cover:
- Average male (175cm, 75kg) → assert BMI ~24.5, chest/waist/hip within expected range
- Average female (165cm, 60kg) → assert gender factor applied correctly
- Minimum valid input (50cm, 20kg) → should not throw
- Maximum valid input (250cm, 300kg) → should not throw
- Below minimum height (49cm) → throws InvalidBodyMeasurementException
- Below minimum weight (19kg) → throws InvalidBodyMeasurementException
- Null age → uses default 30, does not throw
- UNISEX gender → gender factor = 0.5

**SizeChartMatcherTest**

Test cases to cover:
- Empty entry list → returns NO_MATCH
- Null entry list → returns NO_MATCH
- Single entry, all dimensions match → EXACT, score = 1.0
- Single entry, 2 of 3 dimensions match → PARTIAL, score = 0.67
- Single entry, no dimensions match → CLOSEST (only option available)
- Multiple entries, pick best match
- Multiple entries with same score → prefer waist match
- OUT_OF_RANGE BodyProfile → confidence capped at 0.5
- Entry with all null measurements → skipped (treat as 0 available dimensions)

**RecommendationEngineTest** (use Mockito to mock repositories)

Test cases to cover:
- Happy path: product found, size chart found, entries exist → returns RecommendationOutput with size
- Product not found → throws ProductNotFoundException
- No active size chart → returns fallback output (hasSizeChart=false, no exception)
- Empty size chart (no entries) → returns NO_MATCH output
- storeBodyData=false → RecommendationRequest persisted with bodyMeasurementsStored=false
- storeBodyData=true → RecommendationRequest persisted with bodyMeasurementsStored=true

### CONSTRAINTS
- No Spring context in unit tests — plain JUnit 5 + Mockito only
- Each test method name must describe the scenario: given_averageMale_when_calculate_then_bmiIsCorrect
- Use @BeforeEach to set up common fixtures
- Assert specific values for formula outputs with delta tolerance of 0.5

### EXPECTED OUTPUT
- BodyProfileCalculatorTest.java
- SizeChartMatcherTest.java
- RecommendationEngineTest.java

### PHASE 2 COMPLETION CHECKLIST
Before moving to Phase 3, verify:
- [ ] All unit tests pass (mvn test)
- [ ] BodyProfileCalculator throws InvalidBodyMeasurementException for out-of-range inputs
- [ ] SizeChartMatcher returns NO_MATCH for empty/null entry list
- [ ] RecommendationEngine returns graceful fallback when no size chart exists
- [ ] RecommendationRequest is persisted after every successful recommendation
- [ ] No raw body measurements logged at INFO level
# FitVision — Phase 3 Prompts: Widget API

> Pre-condition: Phase 2 complete. RecommendationEngine is working and tested. BodyProfileCalculator, SizeChartMatcher, all repositories, ApiResponse envelope, and GlobalExceptionHandler exist.

---

## Prompt 3.1 — API Key Authentication Filter

### CONTEXT
FitVision backend. Spring Boot 3.x, Spring Security 6.x. The system has two API surfaces:
- Widget API (public — authenticated via store's public API key in header X-FitVision-Key)
- Dashboard API (authenticated via JWT — implemented in a future phase)

StoreRepository exists with method: findByApiKeyPublic(String apiKeyPublic): Optional<Store>

Store entity has fields: id (UUID), apiKeyPublic, status (String).

Spring Security is on the classpath. No SecurityFilterChain has been configured yet.

### OBJECTIVE
Create the API key authentication filter and Spring Security configuration for the widget API.

**ApiKeyAuthFilter** (extends OncePerRequestFilter)
- Package: com.fitvision.infrastructure.security
- Reads header X-FitVision-Key from every request to /api/widget/**
- Looks up the store by apiKeyPublic via StoreRepository
- If found and store status is ACTIVE: sets Authentication in SecurityContextHolder with the Store as principal
- If not found or store not ACTIVE: does NOT throw — lets the request continue (SecurityConfig will block it)
- Must not trigger on /api/dashboard/** or /actuator/**

**TenantContext** (ThreadLocal holder)
- Package: com.fitvision.infrastructure.security
- Static methods: set(UUID tenantId), get(): UUID, clear()
- ApiKeyAuthFilter sets TenantContext after successful auth
- Must be cleared after request completes (use finally block)

**SecurityConfig** (@Configuration @EnableWebSecurity)
- Package: com.fitvision.infrastructure.security
- Widget endpoints /api/widget/**: require authentication (API key filter applied)
- Actuator /actuator/health: permit all
- Dashboard /api/dashboard/**: permit all for now (JWT filter added in Phase 5)
- Disable CSRF (stateless API)
- Disable session management (stateless)
- Add ApiKeyAuthFilter before UsernamePasswordAuthenticationFilter

**InvalidApiKeyException** — already exists from Phase 1. Use it in the filter when key is missing entirely (header absent).

### CONSTRAINTS
- TenantContext must always be cleared — memory leak risk if not cleared after request
- StoreRepository call inside a filter requires @Transactional handling — inject StoreRepository directly, Spring handles the transaction
- Log at DEBUG: API key lookup result (never log the key value itself)
- Log at WARN: rejected request with reason (missing key / inactive store)

### EXPECTED OUTPUT
- TenantContext.java
- ApiKeyAuthFilter.java
- SecurityConfig.java

### NEXT STEP
Prompt 3.2 will create the widget recommendation endpoint that uses TenantContext to scope the recommendation to the authenticated store.

---

## Prompt 3.2 — Widget Recommendation Endpoint

### CONTEXT
FitVision backend. Security layer is complete:
- ApiKeyAuthFilter sets TenantContext.set(tenantId) on every authenticated widget request
- SecurityConfig protects /api/widget/** — only authenticated requests reach controllers
- RecommendationEngine.recommend(RecommendationInput) is the orchestrator
- ApiResponse<T> envelope is the standard response format
- GlobalExceptionHandler handles all FitVisionException subclasses

### OBJECTIVE
Create the widget recommendation endpoint.

**SizeRecommendationRequest** (request body DTO)
- Package: com.fitvision.api.widget
- Fields:
  - externalProductId (String, @NotBlank) — the Shopify/WooCommerce product ID
  - heightCm (double, @Min(50) @Max(250))
  - weightKg (double, @Min(20) @Max(300))
  - gender (String, nullable — "MALE" / "FEMALE" / "UNISEX")
  - age (Integer, nullable, @Min(10) @Max(120))
  - storeBodyData (boolean, default false)

**SizeRecommendationResponse** (response body DTO)
- Package: com.fitvision.api.widget
- Fields:
  - recommendedSize (String, nullable)
  - confidenceScore (double)
  - quality (String — EXACT / PARTIAL / CLOSEST / NO_MATCH)
  - productName (String)
  - hasSizeChart (boolean)
  - confidenceLabel (String — computed: "High" if score >= 0.8, "Medium" if >= 0.5, "Low" if < 0.5)
  - message (String — human-readable message for the widget to display)

**WidgetRecommendationController** (@RestController)
- Package: com.fitvision.api.widget
- Base path: /api/widget/v1
- Endpoint: POST /size-recommendation
- Reads tenantId from TenantContext.get()
- Maps SizeRecommendationRequest → RecommendationInput
- Calls RecommendationEngine.recommend()
- Maps RecommendationOutput → SizeRecommendationResponse
- Returns ApiResponse<SizeRecommendationResponse>

**Message logic:**
- EXACT + High confidence: "Based on your measurements, we recommend size {size}."
- PARTIAL: "We recommend size {size}, but please check the size guide for confirmation."
- CLOSEST / Low confidence: "Size {size} is the closest match. We recommend measuring yourself before ordering."
- NO_MATCH / no size chart: "We don't have size data for this product yet. Please consult the brand's size guide."

**CORS configuration:**
- Allow all origins (* ) for /api/widget/** — the widget runs on third-party store domains
- Allowed methods: POST, OPTIONS
- Allowed headers: X-FitVision-Key, Content-Type
- Add to SecurityConfig (not a separate @CrossOrigin annotation)

### CONSTRAINTS
- Gender string must be parsed to Gender enum safely — if invalid value, default to UNISEX (do not throw)
- TenantContext.get() returns the tenantId set by the filter — never trust a tenantId from the request body
- Endpoint must respond in under 500ms under normal conditions (no heavy computation)
- Return HTTP 200 even for NO_MATCH / no size chart — these are valid business outcomes, not errors

### EXPECTED OUTPUT
- SizeRecommendationRequest.java
- SizeRecommendationResponse.java
- WidgetRecommendationController.java
- Updated SecurityConfig.java (CORS added)

### NEXT STEP
Prompt 3.3 will create an integration test for the widget endpoint and a manual test script.

---

## Prompt 3.3 — Widget API Integration Test + Manual Test

### CONTEXT
FitVision backend. Widget endpoint is complete: POST /api/widget/v1/size-recommendation. ApiKeyAuthFilter protects it. TenantContext holds the authenticated store's tenantId.

Testing stack: JUnit 5, @SpringBootTest, MockMvc, @ActiveProfiles("test").

For integration tests, we need a test PostgreSQL. Use @DataJpaTest or @SpringBootTest with an in-memory approach — but since we use Flyway + PostgreSQL-specific SQL, use Testcontainers (PostgreSQL container) for integration tests.

Add to pom.xml test scope:
- org.testcontainers:testcontainers
- org.testcontainers:postgresql
- org.testcontainers:junit-jupiter

### OBJECTIVE
Create an integration test for the widget recommendation endpoint and a manual cURL test script.

**AbstractIntegrationTest** (base class)
- Package: com.fitvision (test directory)
- Starts a PostgreSQL Testcontainer
- Runs Flyway migrations on the container
- Shared across all future integration tests
- Annotated with @SpringBootTest(webEnvironment = RANDOM_PORT), @ActiveProfiles("test"), @Testcontainers

**WidgetRecommendationControllerIT**
- Extends AbstractIntegrationTest
- Uses TestRestTemplate or MockMvc

Test scenarios:
1. Missing API key header → HTTP 401, error code INVALID_API_KEY
2. Invalid API key → HTTP 401, error code INVALID_API_KEY
3. Valid API key, valid product, size chart exists → HTTP 200, recommendedSize not null, confidenceScore > 0
4. Valid API key, valid product, no size chart → HTTP 200, hasSizeChart=false, recommendedSize null
5. Valid API key, product not found → HTTP 404, error code PRODUCT_NOT_FOUND
6. Valid API key, invalid body (heightCm = 0) → HTTP 400, error code VALIDATION_ERROR

**Test data setup:**
- @BeforeEach inserts a test Store with a known apiKeyPublic
- Inserts a test Brand, Product, SizeChart, and SizeEntries for the happy path
- @AfterEach cleans up

**manual-test.sh** (bash script)
- Place in project root /scripts/manual-test.sh
- cURL commands for each scenario above
- Uses a placeholder API_KEY variable at the top
- Includes expected response comment for each call

### CONSTRAINTS
- Testcontainers must use a fixed PostgreSQL image version: postgres:16
- AbstractIntegrationTest must be reusable for all future integration tests
- Test data must be isolated — no test can depend on data from another test

### EXPECTED OUTPUT
- pom.xml (updated with Testcontainers dependencies)
- AbstractIntegrationTest.java
- WidgetRecommendationControllerIT.java
- scripts/manual-test.sh

### PHASE 3 COMPLETION CHECKLIST
Before moving to Phase 4, verify:
- [ ] POST /api/widget/v1/size-recommendation returns 401 without API key
- [ ] POST /api/widget/v1/size-recommendation returns 200 with valid key and product
- [ ] Response includes confidenceScore, quality, and human-readable message
- [ ] CORS headers present on response (Access-Control-Allow-Origin: *)
- [ ] TenantContext is always cleared after request (no ThreadLocal leak)
- [ ] All integration tests pass (mvn verify)
- [ ] manual-test.sh runs successfully against local instance
# FitVision — Phase 4 Prompts: Size Chart Management

> Pre-condition: Phase 3 complete. Widget API is working and tested. API key authentication, TenantContext, SecurityConfig, and CORS are all configured. 36 tests passing.

---

## Prompt 4.1 — File Parsing Infrastructure (CSV + Excel)

### CONTEXT
FitVision backend. Stores upload size charts as CSV or Excel files. The parsed result must produce a list of SizeEntryData records (not JPA entities yet — just parsed data). Parsing is decoupled from persistence.

Add to pom.xml (if not already present):
- com.opencsv:opencsv:5.9
- org.apache.poi:poi-ooxml:5.2.5

The expected file format (both CSV and Excel) has these columns in order:
size_label, chest_min, chest_max, waist_min, waist_max, hip_min, hip_max, height_min, height_max

Rules:
- First row is always a header — skip it
- size_label is required — skip rows where it is blank
- All measurement columns are optional — blank cells become null
- size_label must be trimmed and uppercased
- Measurement values must be parseable as decimal numbers — if not parseable, skip the row and log a warning with the row number

### OBJECTIVE
Create the file parsing infrastructure.

**SizeEntryData** (record, immutable)
- Package: com.fitvision.domain.sizechart
- Fields: String sizeLabel, Double chestMin, Double chestMax, Double waistMin, Double waistMax, Double hipMin, Double hipMax, Double heightMin, Double heightMax

**ParseResult** (value object)
- Package: com.fitvision.domain.sizechart
- Fields: List<SizeEntryData> entries, List<String> warnings, int skippedRows, boolean success
- Static factory: success(List<SizeEntryData> entries, List<String> warnings, int skippedRows)
- Static factory: failure(String reason)

**SizeChartFileParser** (interface)
- Package: com.fitvision.domain.sizechart
- Single method: ParseResult parse(InputStream inputStream)

**CsvSizeChartParser** (@Component, implements SizeChartFileParser)
- Package: com.fitvision.infrastructure.parsing
- Uses OpenCSV
- Handles UTF-8 and UTF-8-BOM encodings
- Max 500 rows — reject files larger than this with failure ParseResult

**ExcelSizeChartParser** (@Component, implements SizeChartFileParser)
- Package: com.fitvision.infrastructure.parsing
- Uses Apache POI (poi-ooxml) — supports .xlsx only, reject .xls
- Reads first sheet only
- Max 500 rows — same limit as CSV

**SizeChartParserFactory** (@Component)
- Package: com.fitvision.infrastructure.parsing
- Method: SizeChartFileParser getParser(String contentType, String filename)
- Detects CSV vs Excel by content type and filename extension
- Throws UnsupportedFileFormatException (new, extends FitVisionException) for unsupported types
- Add UNSUPPORTED_FILE_FORMAT to ErrorCode enum

### CONSTRAINTS
- Parsers must never throw unchecked exceptions on bad data — always return ParseResult.failure() or skip the row
- InputStream must be closed by the caller, not the parser
- No Spring annotations on SizeChartFileParser interface or SizeEntryData
- Log skipped rows at WARN with row number and reason

### EXPECTED OUTPUT
- SizeEntryData.java (record)
- ParseResult.java
- SizeChartFileParser.java (interface)
- CsvSizeChartParser.java
- ExcelSizeChartParser.java
- SizeChartParserFactory.java
- UnsupportedFileFormatException.java
- Updated ErrorCode enum (UNSUPPORTED_FILE_FORMAT added)
- Updated pom.xml (opencsv + poi-ooxml added if missing)

### NEXT STEP
Prompt 4.2 will create the SizeChartService that takes ParseResult and persists it as a versioned SizeChart with SizeEntry records.

---

## Prompt 4.2 — SizeChartService (Persistence + Versioning)

### CONTEXT
FitVision backend. File parsing is complete. ParseResult contains List<SizeEntryData> ready to persist.

Existing JPA entities and repositories:
- SizeChart: id, productId, version (Integer), source, active (boolean), createdAt
- SizeEntry: id, sizeChartId, sizeLabel, chestMin/Max, waistMin/Max, hipMin/Max, heightMin/Max
- SizeChartRepository: findActiveByProductIdAndTenantId(UUID productId, UUID tenantId)
- SizeEntryRepository: findAllBySizeChartId(UUID sizeChartId)
- ProductRepository: findByIdAndTenantId(UUID id, UUID tenantId)

Versioning rule: each upload creates a new SizeChart version. The new version is set to active=true and all previous versions for the same product are set to active=false. Only one version can be active per product at a time.

### OBJECTIVE
Create the SizeChartService that persists parsed size chart data.

**SizeChartUploadResult** (value object)
- Package: com.fitvision.domain.sizechart
- Fields: UUID sizeChartId, int version, int entriesSaved, List<String> warnings, boolean success

**SizeChartService** (@Service)
- Package: com.fitvision.domain.sizechart
- Method: SizeChartUploadResult uploadFromFile(UUID tenantId, UUID productId, ParseResult parseResult, String source)
- Method: SizeChartUploadResult uploadManual(UUID tenantId, UUID productId, List<SizeEntryData> entries)
- Method: Optional<SizeChart> getActiveSizeChart(UUID tenantId, UUID productId)
- Method: List<SizeEntryData> getActiveSizeChartEntries(UUID tenantId, UUID productId)

**uploadFromFile flow:**
1. Validate ParseResult.success — if false, throw SizeChartParseException (new)
2. Validate ParseResult.entries not empty — if empty, throw SizeChartParseException
3. Find product by productId + tenantId — throw ProductNotFoundException if not found
4. Deactivate all existing active SizeCharts for this product (set active=false)
5. Compute next version number (max existing version + 1, or 1 if none)
6. Create new SizeChart with active=true, source=source
7. Save all SizeEntryData as SizeEntry entities
8. Return SizeChartUploadResult with counts and warnings from ParseResult

**uploadManual flow:**
- Same as uploadFromFile but source is always "manual"
- Validates entries list is not empty

### CONSTRAINTS
- Steps 4 through 7 must be in a single @Transactional method — all or nothing
- Add SIZE_CHART_PARSE_ERROR to ErrorCode enum
- Add SizeChartParseException (new, extends FitVisionException)
- Deactivation query: use a @Modifying @Query in SizeChartRepository to bulk-update active=false for all versions of a product
- Log at INFO: tenantId, productId, new version number, entries saved count

### EXPECTED OUTPUT
- SizeChartUploadResult.java
- SizeChartService.java
- SizeChartParseException.java
- Updated SizeChartRepository.java (@Modifying deactivation query added)
- Updated ErrorCode enum

### NEXT STEP
Prompt 4.3 will create the dashboard API endpoints for size chart management (upload, manual entry, view active chart).

---

## Prompt 4.3 — Size Chart Dashboard Endpoints

### CONTEXT
FitVision backend. SizeChartService and file parsers are complete. SecurityConfig currently permits all /api/dashboard/** requests (JWT not implemented yet — Phase 5). For now, dashboard endpoints are temporarily protected by the same API key mechanism used by the widget, using the store's secret key (X-FitVision-Secret header) instead of the public key.

Add a second filter: SecretKeyAuthFilter (same pattern as ApiKeyAuthFilter) that:
- Triggers on /api/dashboard/**
- Reads X-FitVision-Secret header
- Looks up store by apiKeySecret via StoreRepository (add findByApiKeySecret method)
- Sets TenantContext on success

### OBJECTIVE
Create the size chart management endpoints.

**SizeChartController** (@RestController)
- Package: com.fitvision.api.dashboard
- Base path: /api/dashboard/v1/size-charts

**Endpoints:**

POST /api/dashboard/v1/size-charts/{productId}/upload
- Consumes: multipart/form-data
- File field name: file
- Detects format via SizeChartParserFactory
- Parses via appropriate parser
- Calls SizeChartService.uploadFromFile()
- Returns ApiResponse<SizeChartUploadResult>
- Max file size: 2MB (configure in application.yml)

POST /api/dashboard/v1/size-charts/{productId}/manual
- Consumes: application/json
- Body: List<SizeEntryData>
- Validates list not empty (@NotEmpty)
- Calls SizeChartService.uploadManual()
- Returns ApiResponse<SizeChartUploadResult>

GET /api/dashboard/v1/size-charts/{productId}/active
- Returns the active size chart entries for a product
- Returns ApiResponse<List<SizeEntryData>>
- Returns empty list (not 404) if no active chart exists

DELETE /api/dashboard/v1/size-charts/{productId}/active
- Deactivates the current active size chart (sets active=false)
- Does not delete data — soft deactivation only
- Returns ApiResponse<Void> with HTTP 204

**application.yml additions:**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2MB
      max-request-size: 2MB
```

### CONSTRAINTS
- productId in path must belong to the authenticated tenant — validate via ProductRepository
- SecretKeyAuthFilter follows exact same pattern as ApiKeyAuthFilter
- Add findByApiKeySecret to StoreRepository
- File upload endpoint must handle MultipartException gracefully (file too large → 400, not 500)
- Add INVALID_SECRET_KEY to ErrorCode enum

### EXPECTED OUTPUT
- SecretKeyAuthFilter.java
- SizeChartController.java
- Updated StoreRepository.java (findByApiKeySecret added)
- Updated SecurityConfig.java (SecretKeyAuthFilter added for /api/dashboard/**)
- Updated application.yml (multipart limits)
- Updated ErrorCode enum

### NEXT STEP
Prompt 4.4 will create unit tests for the parsers and integration tests for the size chart endpoints.

---

## Prompt 4.4 — Tests for Size Chart Management

### CONTEXT
FitVision backend. File parsers, SizeChartService, and dashboard endpoints are complete. AbstractIntegrationTest exists with Testcontainers PostgreSQL setup. 36 tests currently passing.

### OBJECTIVE
Create unit tests for parsers and integration tests for the size chart endpoints.

**CsvSizeChartParserTest** (unit test)
Test cases:
- Valid CSV with all columns → ParseResult.success, correct entry count
- CSV with blank size_label rows → rows skipped, skippedRows count correct
- CSV with non-numeric measurement → row skipped with warning
- CSV with only header row → success with empty entries list
- CSV exceeding 500 rows → ParseResult.failure
- UTF-8-BOM encoded CSV → parsed correctly

**ExcelSizeChartParserTest** (unit test)
Test cases:
- Valid .xlsx with all columns → ParseResult.success
- .xlsx with blank rows → skipped correctly
- Empty sheet (header only) → success with empty entries
- Rows exceeding 500 → ParseResult.failure

Use small in-memory test files built programmatically (Apache POI for Excel, plain strings for CSV).

**SizeChartServiceTest** (unit test with Mockito)
Test cases:
- uploadFromFile: happy path → new version created, previous deactivated, entries saved
- uploadFromFile: ParseResult.failure → throws SizeChartParseException
- uploadFromFile: product not found → throws ProductNotFoundException
- uploadManual: empty entries → throws SizeChartParseException
- getActiveSizeChart: no active chart → returns Optional.empty()

**SizeChartControllerIT** (integration test, extends AbstractIntegrationTest)
Test cases:
- POST /upload with valid CSV → 200, version=1, entriesSaved > 0
- POST /upload with second CSV → 200, version=2, previous version deactivated
- POST /upload with invalid secret key → 401
- POST /upload with file too large → 400
- POST /manual with valid entries → 200
- GET /active → 200, returns entries matching last upload
- DELETE /active → 204, subsequent GET /active returns empty list
- GET /active for product belonging to different tenant → 404

### CONSTRAINTS
- Build test CSV content as String in test code — no test resource files needed for CSV
- Build test Excel files programmatically with Apache POI in a TestDataBuilder utility class
- All integration tests must clean up after themselves

### EXPECTED OUTPUT
- CsvSizeChartParserTest.java
- ExcelSizeChartParserTest.java
- SizeChartServiceTest.java
- SizeChartControllerIT.java
- TestDataBuilder.java (test utility for building test fixtures)

### PHASE 4 COMPLETION CHECKLIST
Before moving to Phase 5, verify:
- [ ] All tests pass (mvn verify)
- [ ] CSV upload creates a new versioned SizeChart and deactivates previous
- [ ] Excel upload works identically to CSV
- [ ] Manual entry endpoint accepts JSON list of SizeEntryData
- [ ] File size limit enforced (2MB)
- [ ] GET /active returns the correct active entries
- [ ] DELETE /active soft-deactivates without deleting data
- [ ] Tenant isolation: a store cannot access another store's products
- [ ] After upload, a widget recommendation request returns a valid size recommendation
# FitVision — Phase 5 Prompts: Store Dashboard API

> Pre-condition: Phase 4 complete. 61 tests passing. File parsers, SizeChartService, size chart endpoints, and full end-to-end widget flow are working.

---

## Prompt 5.1 — JWT Authentication (Registration + Login)

### CONTEXT
FitVision backend. Spring Security 6.x is configured. Currently /api/dashboard/** is protected by SecretKeyAuthFilter (temporary). In this phase we replace that with proper JWT authentication.

Add to pom.xml:
- io.jsonwebtoken:jjwt-api:0.12.6
- io.jsonwebtoken:jjwt-impl:0.12.6 (runtime)
- io.jsonwebtoken:jjwt-jackson:0.12.6 (runtime)
- org.springframework.security:spring-security-crypto (already transitive — for BCrypt)

Store entity already has: id, name, email, apiKeyPublic, apiKeySecret, plan, status, platform, subscriptionStatus.

Add to Store entity:
- passwordHash (String) — BCrypt hashed password
- Add to V1 migration or create V2__add_store_password.sql

### OBJECTIVE
Create JWT-based registration and login for store owners.

**StoreRegistrationRequest** (DTO)
- Package: com.fitvision.api.dashboard.auth
- Fields: name (@NotBlank), email (@NotBlank @Email), password (@NotBlank @Size(min=8)), platform (String, nullable)

**StoreLoginRequest** (DTO)
- Package: com.fitvision.api.dashboard.auth
- Fields: email (@NotBlank @Email), password (@NotBlank)

**AuthResponse** (DTO)
- Package: com.fitvision.api.dashboard.auth
- Fields: String accessToken, String tokenType ("Bearer"), long expiresIn (seconds), String apiKeyPublic

**JwtService** (@Service)
- Package: com.fitvision.infrastructure.security
- generateToken(UUID storeId, String email): String
- validateToken(String token): boolean
- extractStoreId(String token): UUID
- extractEmail(String token): String
- Token expiry: 24 hours
- Secret key loaded from application.yml: fitvision.jwt.secret (minimum 256-bit key)
- Add fitvision.jwt.secret and fitvision.jwt.expiration-hours to application.yml

**StoreAuthService** (@Service)
- Package: com.fitvision.domain.store
- Method: AuthResponse register(StoreRegistrationRequest request)
  1. Check email not already registered — throw STORE_ALREADY_EXISTS error if duplicate (add to ErrorCode)
  2. Generate apiKeyPublic (UUID.randomUUID().toString().replace("-",""))
  3. Generate apiKeySecret (same pattern, different value)
  4. Hash password with BCryptPasswordEncoder
  5. Save Store with status=ACTIVE, plan=FREE
  6. Generate and return JWT
- Method: AuthResponse login(StoreLoginRequest request)
  1. Find store by email — throw INVALID_CREDENTIALS if not found (add to ErrorCode, HTTP 401)
  2. Verify password with BCryptPasswordEncoder — throw INVALID_CREDENTIALS if mismatch
  3. Generate and return JWT

**StoreAuthController** (@RestController)
- Package: com.fitvision.api.dashboard.auth
- Base path: /api/dashboard/v1/auth
- POST /register → calls StoreAuthService.register()
- POST /login → calls StoreAuthService.login()
- Both endpoints: permit all (no auth required)

**JwtAuthFilter** (OncePerRequestFilter)
- Package: com.fitvision.infrastructure.security
- Reads Authorization: Bearer {token} header
- Validates token via JwtService
- Extracts storeId, loads Store from repository, sets TenantContext
- Triggers on /api/dashboard/** EXCEPT /api/dashboard/v1/auth/**
- Replaces SecretKeyAuthFilter on /api/dashboard/** (keep SecretKeyAuthFilter removed or disabled)

**Updated SecurityConfig:**
- /api/dashboard/v1/auth/**: permit all
- /api/dashboard/**: authenticated via JwtAuthFilter
- Remove SecretKeyAuthFilter from the chain
- Add JwtAuthFilter before UsernamePasswordAuthenticationFilter

**Flyway migration V2__add_store_password.sql:**
- ALTER TABLE stores ADD COLUMN password_hash VARCHAR(255);

### CONSTRAINTS
- Never log passwords or JWT secrets
- BCryptPasswordEncoder strength: 12
- INVALID_CREDENTIALS must return HTTP 401 — never reveal whether email exists or password is wrong
- JWT secret in application.yml must be clearly marked as: "change this in production"
- apiKeyPublic and apiKeySecret must be different values always

### EXPECTED OUTPUT
- V2__add_store_password.sql
- StoreRegistrationRequest.java
- StoreLoginRequest.java
- AuthResponse.java
- JwtService.java
- StoreAuthService.java
- StoreAuthController.java
- JwtAuthFilter.java
- Updated SecurityConfig.java
- Updated application.yml (jwt config added)
- Updated ErrorCode enum (STORE_ALREADY_EXISTS, INVALID_CREDENTIALS)

### NEXT STEP
Prompt 5.2 will create the store profile and product management endpoints (authenticated dashboard operations).

---

## Prompt 5.2 — Store Profile + Product Management Endpoints

### CONTEXT
FitVision backend. JWT authentication is complete. JwtAuthFilter sets TenantContext for all /api/dashboard/** requests except /auth/**. StoreAuthService handles registration and login.

Existing entities and repositories:
- Store: id, name, email, plan, status, platform, apiKeyPublic, subscriptionStatus
- Product: id, brandId, tenantId, externalProductId, name, category, genderTarget
- Brand: id, tenantId, name, slug, source
- ProductRepository: findByIdAndTenantId, findAllByTenantId, findByExternalProductIdAndTenantId
- BrandRepository: findAllByTenantIdOrTenantIdIsNull, findByIdAndTenantIdOrTenantIdIsNull

### OBJECTIVE
Create store profile and product management endpoints.

**StoreProfileResponse** (DTO)
- Package: com.fitvision.api.dashboard.store
- Fields: UUID id, String name, String email, String plan, String platform, String apiKeyPublic, String subscriptionStatus

**UpdateStoreProfileRequest** (DTO)
- Fields: name (nullable — only update if provided), platform (nullable)
- Do NOT allow email or API keys to be changed via this endpoint

**ProductRequest** (DTO — used for both create and update)
- Package: com.fitvision.api.dashboard.product
- Fields: externalProductId (@NotBlank), name (@NotBlank), category (String), genderTarget (String), brandId (UUID, nullable)

**ProductResponse** (DTO)
- Fields: UUID id, String externalProductId, String name, String category, String genderTarget, UUID brandId, String brandName, boolean hasSizeChart

**StoreController** (@RestController)
- Package: com.fitvision.api.dashboard.store
- Base path: /api/dashboard/v1/store
- GET /profile → returns StoreProfileResponse for authenticated store
- PATCH /profile → updates name and/or platform, returns updated StoreProfileResponse
- GET /api-keys → returns apiKeyPublic and apiKeySecret (only endpoint that exposes secret key)
- POST /api-keys/regenerate → generates new apiKeyPublic and apiKeySecret, invalidates old ones, returns new keys

**ProductController** (@RestController)
- Package: com.fitvision.api.dashboard.product
- Base path: /api/dashboard/v1/products

Endpoints:
- GET / → returns List<ProductResponse> for authenticated store, includes hasSizeChart flag
- POST / → creates product, returns ProductResponse (HTTP 201)
- GET /{productId} → returns single ProductResponse
- PUT /{productId} → full update, returns ProductResponse
- DELETE /{productId} → soft delete (add deleted_at column via V3 migration), returns HTTP 204

**ProductService** (@Service)
- Package: com.fitvision.domain.product
- Handles CRUD with tenant isolation
- hasSizeChart computed by checking if active SizeChart exists for product
- On create: if brandId provided, validate it belongs to tenant or is global
- On delete: if product has active size chart, deactivate it first

**V3__add_product_soft_delete.sql:**
- ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP NULL;
- Update ProductRepository queries to exclude deleted_at IS NOT NULL

### CONSTRAINTS
- All endpoints read tenantId from TenantContext — never from request body
- Regenerating API keys must invalidate the old public key immediately (widget calls with old key must start returning 401)
- hasSizeChart must not trigger N+1 — use a single query or batch check
- Soft deleted products must not appear in any list or be accessible by productId

### EXPECTED OUTPUT
- V3__add_product_soft_delete.sql
- StoreProfileResponse.java
- UpdateStoreProfileRequest.java
- ProductRequest.java
- ProductResponse.java
- StoreController.java
- ProductController.java
- ProductService.java
- Updated ProductRepository.java (exclude soft-deleted)

### NEXT STEP
Prompt 5.3 will create the analytics endpoints and integration tests for all Phase 5 endpoints.

---

## Prompt 5.3 — Analytics Endpoints + Phase 5 Tests

### CONTEXT
FitVision backend. JWT auth, store profile, and product management are complete. RecommendationRequestRepository has: countByTenantIdAndCreatedAtAfter and findAllByTenantId(pageable).

### OBJECTIVE
Create analytics endpoints and full test coverage for Phase 5.

**AnalyticsResponse** (DTO)
- Package: com.fitvision.api.dashboard.analytics
- Fields:
  - totalRecommendations (long)
  - recommendationsLast30Days (long)
  - averageConfidenceScore (double)
  - qualityDistribution (Map<String, Long> — EXACT/PARTIAL/CLOSEST/NO_MATCH counts)
  - topProducts (List<ProductRecommendationStat> — top 5 products by recommendation count)

**ProductRecommendationStat** (DTO)
- Fields: UUID productId, String productName, long recommendationCount, double averageConfidence

**AnalyticsController** (@RestController)
- Package: com.fitvision.api.dashboard.analytics
- Base path: /api/dashboard/v1/analytics
- GET /summary → returns AnalyticsResponse for authenticated store
- GET /recommendations → paginated list of RecommendationRequest records (page, size params)

**AnalyticsService** (@Service)
- Package: com.fitvision.domain.recommendation
- Aggregates data from RecommendationRequestRepository
- Add necessary @Query methods to RecommendationRequestRepository:
  - findAverageConfidenceByTenantId(UUID tenantId): Double
  - countByTenantIdAndQuality(UUID tenantId, String quality): long
  - findTopProductsByTenantId(UUID tenantId, Pageable pageable): List<Object[]>

**Tests:**

StoreAuthControllerIT (integration):
- POST /register → 200, returns JWT and apiKeyPublic
- POST /register duplicate email → 409 STORE_ALREADY_EXISTS
- POST /login valid credentials → 200, returns JWT
- POST /login wrong password → 401 INVALID_CREDENTIALS
- POST /login unknown email → 401 INVALID_CREDENTIALS (same message as wrong password)

StoreControllerIT (integration):
- GET /profile without JWT → 401
- GET /profile with valid JWT → 200, correct store data
- PATCH /profile → 200, name updated
- GET /api-keys → 200, returns both keys
- POST /api-keys/regenerate → 200, old widget calls with old key return 401

ProductControllerIT (integration):
- Full CRUD cycle: create → get → update → delete → get returns 404
- GET / returns hasSizeChart=true after upload, false before
- DELETE soft-deleted product disappears from list
- Cross-tenant: cannot access other store's product → 404

AnalyticsControllerIT (integration):
- GET /summary after several recommendation requests → counts correct
- GET /recommendations paginated → correct page size and total

### CONSTRAINTS
- findTopProductsByTenantId must use JPQL, not native SQL
- Analytics queries must not load full entities — use projections or Object[] results
- All integration tests extend AbstractIntegrationTest

### EXPECTED OUTPUT
- AnalyticsResponse.java
- ProductRecommendationStat.java
- AnalyticsController.java
- AnalyticsService.java
- Updated RecommendationRequestRepository.java
- StoreAuthControllerIT.java
- StoreControllerIT.java
- ProductControllerIT.java
- AnalyticsControllerIT.java

### PHASE 5 COMPLETION CHECKLIST
Before moving to Phase 6, verify:
- [ ] All tests pass (mvn verify) — target: 90+ tests
- [ ] Store can register, login, and receive JWT
- [ ] JWT protects all /api/dashboard/** except /auth/**
- [ ] Store can create and manage products
- [ ] Soft delete works — deleted products invisible in all queries
- [ ] API key regeneration immediately invalidates old widget calls
- [ ] Analytics summary returns correct counts after recommendations
- [ ] Full end-to-end: register → login → create product → upload size chart → widget recommendation → analytics shows the event

# FitVision — Phase 6 Prompts: Embeddable Widget (Vanilla JS)

> Pre-condition: Phase 5 complete. Backend API fully operational. POST /api/widget/v1/size-recommendation returns recommendation with confidenceScore, quality, confidenceLabel, message, hasSizeChart. API key authentication via X-FitVision-Key header.

---

## Prompt 6.1 — Project Setup (Vite + Vanilla JS)

### CONTEXT
We are building the FitVision embeddable widget — a lightweight Vanilla JavaScript file that store owners paste into their store via a single `<script>` tag. It must work on Shopify, WooCommerce, Wix, and any HTML-based store without conflicts with the store's own libraries.

The widget:
- Initialises from a data attribute on a `<div>` element: `data-fitvision-product-id`
- Calls POST /api/widget/v1/size-recommendation on the FitVision backend
- Displays the recommendation inline on the product page
- Must never block the store's page render (async/deferred)
- Must stay under 50KB gzipped
- Must not use any frontend framework (no React, Vue, Angular)
- Must not pollute the global scope beyond `window.FitVision`
- Must not set cookies

The widget lives in a separate directory from the Spring Boot backend:
```
/widget
  ├── src/
  │   ├── main.js          # entry point
  │   ├── api.js           # API call logic
  │   ├── ui.js            # DOM rendering
  │   ├── styles.css       # scoped styles
  │   └── config.js        # constants and defaults
  ├── index.html           # local dev test page
  ├── vite.config.js
  └── package.json
```

### OBJECTIVE
Set up the Vite project for the widget with correct build configuration.

**package.json**
- name: fitvision-widget
- scripts: dev (vite), build (vite build), preview (vite preview)
- devDependencies: vite (latest stable)
- No runtime dependencies

**vite.config.js**
- Build mode: library
- Entry: src/main.js
- Output format: iife (Immediately Invoked Function Expression — required for script tag embed)
- Output filename: fitvision-widget.min.js
- Global name: FitVision
- Minify: true
- CSS: inline into the JS bundle (cssCodeSplit: false) — single file output
- Target: es2017 (broad browser support)
- No external dependencies

**config.js**
```javascript
// All configurable constants
export const API_BASE_URL = 'https://api.fitvision.io'; // overridable via data attribute
export const DEFAULT_TIMEOUT_MS = 8000;
export const WIDGET_VERSION = '1.0.0';
export const NAMESPACE = 'fitvision';
```

**index.html** (local dev test page only — not part of build output)
- Loads the widget script
- Has a div with data-fitvision-product-id="test-product-123" and data-fitvision-key="test-api-key"
- Has a mock server note explaining how to test locally

### CONSTRAINTS
- Output must be a single .js file — no separate CSS file
- iife format ensures the widget works via plain `<script src="...">` tag
- No TypeScript — Vanilla JS only for this phase
- vite.config.js must explicitly set rollupOptions.output.manualChunks to undefined to prevent code splitting

### EXPECTED OUTPUT
- /widget/package.json
- /widget/vite.config.js
- /widget/src/config.js
- /widget/index.html (dev test page)

### NEXT STEP
Prompt 6.2 will implement the widget initialisation logic, DOM injection, and API integration.

---

## Prompt 6.2 — Widget Core (Init + API + UI)

### CONTEXT
FitVision widget project is set up with Vite, iife build, single file output. The widget must:

1. Find all `<div data-fitvision-product-id="...">` elements on the page
2. For each, inject a "Find my size" button
3. When clicked, show a form asking for height, weight, gender (optional), age (optional)
4. On submit, call POST /api/widget/v1/size-recommendation
5. Display the recommendation result inline

API endpoint: POST /api/widget/v1/size-recommendation
Request headers: X-FitVision-Key: {apiKey}, Content-Type: application/json
Request body:
```json
{
  "externalProductId": "string",
  "heightCm": 175,
  "weightKg": 70,
  "gender": "MALE",
  "age": 30,
  "storeBodyData": false
}
```
Response envelope:
```json
{
  "success": true,
  "data": {
    "recommendedSize": "M",
    "confidenceScore": 0.95,
    "quality": "EXACT",
    "productName": "Classic T-Shirt",
    "hasSizeChart": true,
    "confidenceLabel": "High",
    "message": "Based on your measurements, we recommend size M."
  }
}
```

Data attributes the store owner sets on the container div:
- data-fitvision-product-id (required) — the externalProductId
- data-fitvision-key (required) — the store's public API key
- data-fitvision-api-url (optional) — override for API base URL (for testing)
- data-fitvision-locale (optional) — "en" or "pt" (default: "en")

### OBJECTIVE
Implement the three core modules.

**api.js**
- Single exported function: `async function getRecommendation(apiKey, payload, apiBaseUrl)`
- Uses fetch() with timeout (AbortController, DEFAULT_TIMEOUT_MS)
- Returns the parsed response data object on success
- Throws a typed error on failure:
  - NetworkError: fetch failed or timed out
  - ApiError: API returned success=false (include error.code and error.message)
- Never throws unhandled exceptions — always returns or throws a typed error

**ui.js**
Exported functions:

`renderTrigger(container)` — injects the "Find my size" button into the container
- Button text: "Find my size" (en) / "Encontrar o meu tamanho" (pt)
- Styled with inline CSS scoped to the fitvision namespace
- Clicking the button calls renderForm()

`renderForm(container, onSubmit)` — replaces trigger with measurement input form
- Fields: Height (cm), Weight (kg), Gender (select: Male/Female/Prefer not to say), Age (optional)
- Submit button: "Get my size" / "Obter o meu tamanho"
- Back link to return to trigger
- Client-side validation: height 50-250, weight 20-300, required fields
- On valid submit: calls renderLoading(), then calls onSubmit(formData)

`renderLoading(container)` — shows a loading state while API call is in progress
- Simple spinner or text: "Finding your size..." / "A encontrar o seu tamanho..."

`renderResult(container, data)` — displays the recommendation
- If hasSizeChart=false: shows fallback message, no size displayed
- If quality=NO_MATCH: shows low-confidence fallback message
- Otherwise: shows recommended size prominently, confidence label, and message from API
- "Try again" link to restart the form

`renderError(container, error)` — displays error state
- NetworkError: "Could not connect. Please try again." / PT equivalent
- ApiError: shows the error message from API
- "Try again" link

**main.js** (entry point)
- Auto-initialises on DOMContentLoaded
- Finds all elements with [data-fitvision-product-id]
- For each element:
  - Reads data attributes (productId, apiKey, apiBaseUrl, locale)
  - Validates required attributes — logs warning and skips if missing
  - Calls renderTrigger(element)
  - Sets up the full flow: trigger → form → loading → api call → result/error
- Exposes window.FitVision.init() for manual reinitialisation (useful for SPAs)

### CONSTRAINTS
- Zero external dependencies — fetch, AbortController, and DOM APIs only
- All CSS must be scoped with .fitvision- prefix to avoid conflicts with store styles
- No cookies, no localStorage, no sessionStorage
- Gender "Prefer not to say" maps to "UNISEX" in the API payload
- storeBodyData always false (GDPR — buyers do not consent in this flow)
- Widget must work if multiple product divs exist on the same page
- Must not throw errors that bubble to window.onerror and pollute store's error tracking

### EXPECTED OUTPUT
- /widget/src/api.js
- /widget/src/ui.js
- /widget/src/main.js

### NEXT STEP
Prompt 6.3 will add styling, accessibility, and the build + manual test validation.

---

## Prompt 6.3 — Styles + Accessibility + Build Validation

### CONTEXT
FitVision widget. Core logic is complete (api.js, ui.js, main.js). The widget renders a trigger button, form, loading state, result, and error state. CSS is inlined into the JS bundle via Vite.

### OBJECTIVE
Add professional styling, accessibility attributes, and validate the build output.

**styles.css**
Design requirements:
- All selectors prefixed with .fitvision- to avoid conflicts
- Clean, minimal design that works on any store background (white/light default)
- Trigger button: clean bordered button, neutral colours, hover state
- Form: label above input, clear spacing, visible focus states
- Loading: subtle animation (CSS keyframe, no JS animation library)
- Result: size label displayed large and prominent (48px+), confidence label as a badge
- Error: red-tinted background, clear message
- Responsive: works on mobile (min-width: 320px) and desktop
- No fixed widths — adapts to container width
- Font: inherit from store (font-family: inherit)

Accessibility requirements (add to ui.js):
- All form inputs have associated `<label>` with for/id
- Submit button has type="submit"
- Loading state has role="status" and aria-live="polite"
- Result container has role="region" and aria-label="Size recommendation"
- Error container has role="alert"
- Form has aria-describedby pointing to any validation error messages
- All interactive elements are keyboard navigable

**Build validation**
After `npm run build`:
- Output file exists: /widget/dist/fitvision-widget.min.js
- File size must be under 50KB gzipped — add a build check script
- Verify the output is valid IIFE format (starts with `(function(` or equivalent)

**build-check.js** (Node.js script, run after build)
```
node build-check.js
```
- Reads /widget/dist/fitvision-widget.min.js
- Checks gzipped size < 50KB
- Logs: "✓ Build OK — {size}KB gzipped" or "✗ Build too large — {size}KB gzipped"

**Updated package.json scripts:**
```json
{
  "build": "vite build && node build-check.js",
  "build:watch": "vite build --watch"
}
```

**Local integration test (index.html update)**
Update index.html to:
- Load the built widget from /dist/fitvision-widget.min.js (not dev server)
- Include a mock fetch interceptor that returns a realistic API response without hitting the real backend
- Document at the top: "To test against real backend, replace MOCK_MODE=true with real API key and product ID"

### CONSTRAINTS
- No CSS framework (no Tailwind, no Bootstrap) — plain CSS only
- CSS animations must respect prefers-reduced-motion media query
- Widget must render correctly when the store uses a CSS reset
- Build check must exit with code 1 if size exceeds 50KB so CI can catch it

### EXPECTED OUTPUT
- /widget/src/styles.css
- Updated /widget/src/ui.js (accessibility attributes added)
- /widget/build-check.js
- Updated /widget/package.json (build script updated)
- Updated /widget/index.html (mock mode + built file)

### PHASE 6 COMPLETION CHECKLIST
Before moving to Phase 7, verify:
- [ ] npm run build completes without errors
- [ ] build-check.js reports under 50KB gzipped
- [ ] Output is a single .js file in /widget/dist/
- [ ] index.html loads the widget in mock mode and the full flow works: trigger → form → loading → result
- [ ] Widget initialises correctly on a div with data attributes
- [ ] Form validates height and weight ranges before submitting
- [ ] Result displays recommendedSize prominently with confidenceLabel
- [ ] Fallback message shown when hasSizeChart=false
- [ ] Error state shown when API call fails
- [ ] All form inputs are keyboard navigable
- [ ] No JS errors in browser console during the full flow
- [ ] Widget does not conflict when two product divs exist on the same page
# FitVision — Phase 7 Prompts: Store Dashboard (Next.js)

> Pre-condition: Phase 6 complete. Backend fully operational (77 tests passing). Widget built and validated (4.32KB gzipped). API endpoints available: auth, store profile, products, size charts, analytics.

---

## Prompt 7.1 — Next.js Project Setup + Authentication Flow

### CONTEXT
We are building the FitVision store dashboard — a web application where store owners manage their account, products, size charts, and view analytics. It connects to the existing Spring Boot backend API.

Backend base URL (dev): http://localhost:8080
Auth endpoints:
- POST /api/dashboard/v1/auth/register → { accessToken, apiKeyPublic }
- POST /api/dashboard/v1/auth/login → { accessToken, apiKeyPublic }
All dashboard endpoints require: Authorization: Bearer {accessToken}

The dashboard lives in a separate directory:
```
/dashboard
  ├── app/                    # Next.js App Router
  │   ├── (auth)/             # Auth route group (no sidebar)
  │   │   ├── login/
  │   │   └── register/
  │   ├── (app)/              # Authenticated route group (with sidebar)
  │   │   ├── dashboard/      # Analytics overview
  │   │   ├── products/       # Product management
  │   │   └── settings/       # Store profile + API keys
  │   ├── layout.tsx
  │   └── globals.css
  ├── components/
  │   ├── ui/                 # shadcn/ui components
  │   └── app/                # App-specific components
  ├── lib/
  │   ├── api.ts              # API client
  │   ├── auth.ts             # Auth helpers
  │   └── types.ts            # Shared TypeScript types
  ├── hooks/
  ├── middleware.ts            # Route protection
  ├── next.config.js
  ├── tailwind.config.ts
  └── package.json
```

### OBJECTIVE
Set up the Next.js project with auth flow.

**Project setup**
- Next.js 14 (App Router)
- TypeScript
- Tailwind CSS
- shadcn/ui (init with default theme, slate base colour)
- Install shadcn components needed for auth: Button, Input, Label, Card, Form
- next.config.js: set NEXT_PUBLIC_API_URL from env

**.env.local**
```
NEXT_PUBLIC_API_URL=http://localhost:8080
```

**lib/types.ts** — shared TypeScript types
```typescript
// Auth
interface AuthResponse { accessToken: string; tokenType: string; expiresIn: number; apiKeyPublic: string; }
interface RegisterRequest { name: string; email: string; password: string; platform?: string; }
interface LoginRequest { email: string; password: string; }

// Store
interface StoreProfile { id: string; name: string; email: string; plan: string; platform: string; apiKeyPublic: string; subscriptionStatus: string; }

// Product
interface Product { id: string; externalProductId: string; name: string; category: string; genderTarget: string; brandId?: string; brandName?: string; hasSizeChart: boolean; }

// Analytics
interface AnalyticsSummary { totalRecommendations: number; recommendationsLast30Days: number; averageConfidenceScore: number; qualityDistribution: Record<string, number>; topProducts: ProductRecommendationStat[]; }
interface ProductRecommendationStat { productId: string; productName: string; recommendationCount: number; averageConfidence: number; }
```

**lib/api.ts** — typed API client
- Base fetch wrapper: reads token from localStorage, adds Authorization header
- Methods: register, login, getProfile, updateProfile, getApiKeys, regenerateApiKeys, getProducts, createProduct, updateProduct, deleteProduct, uploadSizeChart, getActiveSizeChart, getAnalyticsSummary
- All methods return typed responses or throw ApiError
- Handle 401 → clear token + redirect to /login

**lib/auth.ts**
- saveToken(token: string): void — saves to localStorage
- getToken(): string | null
- clearToken(): void
- isAuthenticated(): boolean

**middleware.ts**
- Protect all /dashboard/**, /products/**, /settings/** routes
- Redirect unauthenticated users to /login
- Redirect authenticated users away from /login and /register to /dashboard

**Login page** (app/(auth)/login/page.tsx)
- Email + password form
- Calls api.login(), saves token, redirects to /dashboard
- Link to register page
- Show error message on failed login

**Register page** (app/(auth)/register/page.tsx)
- Name + email + password + platform (select: Shopify / WooCommerce / Other) form
- Calls api.register(), saves token, redirects to /dashboard
- Link to login page

### CONSTRAINTS
- Token stored in localStorage (simple for MVP — not httpOnly cookie)
- All forms use React Hook Form + Zod validation
- shadcn/ui Form component for consistent field validation display
- Password: min 8 characters (Zod)
- Email: valid format (Zod)

### EXPECTED OUTPUT
- /dashboard/package.json
- /dashboard/next.config.js
- /dashboard/tailwind.config.ts
- /dashboard/.env.local
- /dashboard/lib/types.ts
- /dashboard/lib/api.ts
- /dashboard/lib/auth.ts
- /dashboard/middleware.ts
- /dashboard/app/(auth)/login/page.tsx
- /dashboard/app/(auth)/register/page.tsx
- /dashboard/app/layout.tsx
- /dashboard/app/globals.css

### NEXT STEP
Prompt 7.2 will build the authenticated app shell (sidebar, layout) and the analytics dashboard page.

---

## Prompt 7.2 — App Shell + Analytics Dashboard

### CONTEXT
FitVision dashboard. Auth flow is complete. Authenticated routes are under app/(app)/. The API client (lib/api.ts) is fully typed. AnalyticsSummary type is defined.

Backend analytics endpoints:
- GET /api/dashboard/v1/analytics/summary → AnalyticsSummary
- GET /api/dashboard/v1/analytics/recommendations?page=0&size=10 → paginated list

### OBJECTIVE
Build the authenticated app shell and analytics overview page.

**App shell** (app/(app)/layout.tsx)
- Sidebar navigation with links: Dashboard, Products, Settings
- Top bar with store name and logout button
- Sidebar collapses to hamburger on mobile
- Active link highlighted
- Logout: clears token, redirects to /login

**Sidebar items:**
- Dashboard (icon: LayoutDashboard) → /dashboard
- Products (icon: Package) → /products
- Settings (icon: Settings) → /settings

**Analytics dashboard** (app/(app)/dashboard/page.tsx)

Summary cards row:
- Total Recommendations (number, large)
- Last 30 Days (number with trend vs previous period if available)
- Average Confidence (percentage, colour coded: green ≥ 80%, amber 50–79%, red < 50%)
- Size Chart Coverage (% of products with active size chart — derived from product list)

Quality distribution chart:
- Bar chart or donut chart showing EXACT / PARTIAL / CLOSEST / NO_MATCH counts
- Use recharts (install as dependency)

Top products table:
- Columns: Product name, Recommendations, Avg confidence
- Max 5 rows
- Link to product detail

**Loading and empty states:**
- Skeleton loaders while data fetches
- Empty state illustration when totalRecommendations = 0: "No recommendations yet. Add a product and upload a size chart to get started."

**Data fetching:**
- Use SWR for data fetching (install as dependency)
- Revalidate every 60 seconds

### CONSTRAINTS
- No server components for authenticated pages — use client components with SWR
- recharts for charts — no other chart library
- Colour system from Tailwind + shadcn tokens — no hardcoded hex colours
- All numbers formatted with locale (toLocaleString())

### EXPECTED OUTPUT
- /dashboard/app/(app)/layout.tsx (app shell with sidebar)
- /dashboard/app/(app)/dashboard/page.tsx
- /dashboard/components/app/Sidebar.tsx
- /dashboard/components/app/TopBar.tsx
- /dashboard/components/app/StatCard.tsx
- /dashboard/components/app/QualityChart.tsx
- /dashboard/components/app/TopProductsTable.tsx

### NEXT STEP
Prompt 7.3 will build the product management pages and size chart upload flow.

---

## Prompt 7.3 — Product Management + Size Chart Upload

### CONTEXT
FitVision dashboard. App shell and analytics page are complete. API client has all product and size chart methods typed.

Backend endpoints:
- GET /api/dashboard/v1/products → Product[]
- POST /api/dashboard/v1/products → Product (201)
- PUT /api/dashboard/v1/products/{id} → Product
- DELETE /api/dashboard/v1/products/{id} → 204
- POST /api/dashboard/v1/size-charts/{productId}/upload → multipart/form-data
- POST /api/dashboard/v1/size-charts/{productId}/manual → SizeEntryData[]
- GET /api/dashboard/v1/size-charts/{productId}/active → SizeEntryData[]
- DELETE /api/dashboard/v1/size-charts/{productId}/active → 204

### OBJECTIVE
Build the product management pages and size chart upload flow.

**Products list page** (app/(app)/products/page.tsx)
- Table with columns: Name, External ID, Category, Gender, Size Chart (badge: ✓ or ✗), Actions
- Actions: Edit, Upload Size Chart, Delete
- "Add Product" button opens a modal/drawer
- Empty state: "No products yet. Add your first product."
- Search/filter by name (client-side)

**Add/Edit Product** (components/app/ProductForm.tsx)
- Fields: Name (@NotBlank), External Product ID (@NotBlank), Category (select: Tops/Bottoms/Dresses/Outerwear/Other), Gender Target (select: Male/Female/Unisex)
- Used in a Sheet (shadcn slide-over drawer) for both add and edit
- On save: calls api.createProduct() or api.updateProduct(), refreshes list

**Delete Product**
- Confirmation dialog (shadcn AlertDialog) before deleting
- On confirm: calls api.deleteProduct(), refreshes list

**Size Chart Upload flow** (components/app/SizeChartUpload.tsx)
- Opens in a Sheet when "Upload Size Chart" clicked
- Two tabs: "Upload File" and "Enter Manually"

Upload File tab:
- Drag-and-drop file zone (CSV or Excel)
- Shows file name and size after selection
- Upload button calls api.uploadSizeChart()
- Shows result: entries saved, warnings list
- Shows current active size chart entries in a table after upload

Manual Entry tab:
- Table editor: add rows with size_label, chest_min/max, waist_min/max, hip_min/max, height_min/max
- "Add row" button
- Delete row button per row
- Submit calls api.uploadManualSizeChart()

**Size chart entries table** (components/app/SizeChartTable.tsx)
- Columns: Size, Chest (min-max), Waist (min-max), Hip (min-max), Height (min-max)
- Used in the upload sheet to show current active chart
- Shows "No size chart uploaded yet" when empty

### CONSTRAINTS
- All mutations invalidate SWR cache for the products list
- File drag-and-drop: use native HTML5 drag events — no external library
- Sheet (drawer) pattern from shadcn/ui
- File upload: accept only .csv and .xlsx (validate client-side before upload)
- Show upload warnings (skipped rows) in a collapsible section after upload

### EXPECTED OUTPUT
- /dashboard/app/(app)/products/page.tsx
- /dashboard/components/app/ProductForm.tsx
- /dashboard/components/app/SizeChartUpload.tsx
- /dashboard/components/app/SizeChartTable.tsx

### NEXT STEP
Prompt 7.4 will build the settings page (store profile + API keys + script tag integration guide).

---

## Prompt 7.4 — Settings Page + Integration Guide

### CONTEXT
FitVision dashboard. Products and size chart upload are complete. The settings page is where store owners manage their profile, API keys, and get the script tag to install the widget.

Backend endpoints:
- GET /api/dashboard/v1/store/profile → StoreProfile
- PATCH /api/dashboard/v1/store/profile → StoreProfile
- GET /api/dashboard/v1/store/api-keys → { apiKeyPublic, apiKeySecret }
- POST /api/dashboard/v1/store/api-keys/regenerate → { apiKeyPublic, apiKeySecret }

Widget script tag template:
```html
<div
  data-fitvision-product-id="YOUR_PRODUCT_ID"
  data-fitvision-key="YOUR_API_KEY">
</div>
<script src="https://cdn.fitvision.io/widget/fitvision-widget.min.js" async defer></script>
```

### OBJECTIVE
Build the settings page with three sections.

**Settings page** (app/(app)/settings/page.tsx)
Three card sections on the page:

**Section 1 — Store Profile**
- Fields: Name (editable), Email (read-only), Platform (editable select)
- Save button — calls api.updateProfile()
- Success toast on save (shadcn useToast)

**Section 2 — API Keys**
- Shows apiKeyPublic (always visible, copyable)
- Shows apiKeySecret (hidden by default, reveal on click, copyable)
- "Regenerate Keys" button with confirmation dialog
- Warning text: "Regenerating keys will immediately invalidate the current widget installation."
- Copy button for each key (copies to clipboard, shows "Copied!" feedback)

**Section 3 — Widget Integration Guide**
- Step-by-step instructions:
  1. Copy your API key (shown inline)
  2. Add the container div to your product page template
  3. Add the script tag before </body>
- Code block showing the complete script tag (syntax highlighted, copyable)
- Product ID placeholder explained: "Replace YOUR_PRODUCT_ID with the External Product ID you set in FitVision for each product"
- Platform-specific tips (if platform = shopify: link to Shopify theme editor docs)

**Reusable components:**
- CopyButton.tsx — button that copies text to clipboard and shows feedback
- CodeBlock.tsx — syntax-highlighted read-only code display (no external syntax highlighter — use a <pre> with Tailwind prose styling)

### CONSTRAINTS
- API keys loaded fresh on page load — do not cache in SWR (security)
- apiKeySecret hidden by default — toggle with eye icon (lucide-react)
- Regenerate confirmation uses shadcn AlertDialog
- Toast notifications use shadcn Toaster (add to root layout)

### EXPECTED OUTPUT
- /dashboard/app/(app)/settings/page.tsx
- /dashboard/components/app/CopyButton.tsx
- /dashboard/components/app/CodeBlock.tsx
- Updated /dashboard/app/layout.tsx (Toaster added)

### PHASE 7 COMPLETION CHECKLIST
Before moving to Phase 8, verify:
- [ ] npm run dev starts without errors
- [ ] Register flow: fill form → submit → redirect to /dashboard
- [ ] Login flow: fill form → submit → redirect to /dashboard
- [ ] Logout: clears token → redirect to /login
- [ ] Unauthenticated access to /dashboard → redirect to /login
- [ ] Analytics page loads with summary cards and quality chart
- [ ] Products page: create → appears in list with hasSizeChart=false
- [ ] Upload CSV size chart → hasSizeChart badge turns green
- [ ] Delete product → disappears from list
- [ ] Settings: update profile name → saved successfully
- [ ] Settings: copy API key → clipboard feedback shown
- [ ] Settings: regenerate keys → confirmation dialog → new keys shown
- [ ] Integration guide shows correct script tag with store's API key
- [ ] Dashboard is usable on mobile (responsive sidebar)
# FitVision — Phase A Prompts: Critical Fixes + Admin Area

> Pre-condition: Phase 7 complete. Dashboard running at localhost:3000. Backend running in Docker. Known issues documented in progress.md.

---

## Prompt A1 — Backend Critical Fixes

### CONTEXT
FitVision backend. Several issues discovered during real-world testing that must be fixed before Phase 8.

Current state:
- SecretKeyAuthFilter applies to all /api/dashboard/** instead of only /api/dashboard/v1/size-charts/**
- ProductService throws BRAND_NOT_FOUND when brandId is null — brand should be optional
- No Swagger/OpenAPI configured
- No admin role or admin account mechanism
- Store entity has no role field

### OBJECTIVE
Fix all four issues in a single backend update.

**Fix 1 — SecretKeyAuthFilter scope**

In SecretKeyAuthFilter.java, add:
```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/dashboard/v1/size-charts/");
}
```

**Fix 2 — brandId optional in ProductService**

In ProductService, when handling product creation/update:
- If brandId is null or not provided: create product without brand association
- If brandId is provided: validate it belongs to tenant or is global (existing behaviour)
- Never throw BRAND_NOT_FOUND when brandId was not provided at all
- Update ProductResponse to return brandId and brandName as nullable

**Fix 3 — Swagger/OpenAPI**

Add to pom.xml:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

In SecurityConfig, add to permit list:
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
```

Add @Tag annotations to existing controllers grouping them by area (Widget, Dashboard, Admin).

**Fix 4 — Admin role migration**

Create V4__add_admin_role.sql:
```sql
ALTER TABLE stores ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'STORE';
CREATE INDEX idx_stores_role ON stores(role);
```

Update Store entity with role field (String, default 'STORE').
Add StoreRole enum: STORE, ADMIN.

**Fix 5 — Admin seed script**

Create scripts/create-admin.sh:
```bash
#!/bin/bash
# Creates the first admin account
# Usage: ./scripts/create-admin.sh <email> <password>
# Requires backend running at localhost:8080
curl -X POST http://localhost:8080/api/admin/seed \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"$1\", \"password\": \"$2\", \"name\": \"FitVision Admin\"}"
```

Create POST /api/admin/seed endpoint (permit all, but only works if zero admin accounts exist):
- Checks if any ADMIN role store exists — if yes, returns 409 CONFLICT
- Creates store with role=ADMIN, plan=ADMIN, status=ACTIVE
- Returns JWT for immediate use
- After first admin created, endpoint returns 409 on all subsequent calls

### CONSTRAINTS
- SecretKeyAuthFilter fix must not break existing SizeChartControllerIT tests
- brandId null must be allowed in both create and update product
- Swagger must not expose admin endpoints without authentication
- Seed endpoint is the ONLY way to create an admin — not via /auth/register

### EXPECTED OUTPUT
- Updated SecretKeyAuthFilter.java
- Updated ProductService.java
- Updated pom.xml (springdoc added)
- Updated SecurityConfig.java (Swagger + seed endpoint permitted)
- V4__add_admin_role.sql
- Updated Store.java (role field)
- AdminSeedController.java
- scripts/create-admin.sh
- Updated docker-compose.yml rebuild and test

### NEXT STEP
Prompt A2 will add brand management to the store dashboard.

---

## Prompt A2 — Brand Management (Backend + Frontend)

### CONTEXT
FitVision. brandId is now optional for products (fixed in A1). Stores need to be able to create and manage their own brands. Global brands (tenant_id = null) are managed by admin.

Backend existing:
- BrandRepository: findAllByTenantIdOrTenantIdIsNull, findByIdAndTenantIdOrTenantIdIsNull
- Brand entity: id, name, slug, source (fitvision_managed/store_uploaded), tenant_id (null = global)

### OBJECTIVE
Add brand management endpoints and UI.

**Backend — BrandController** (@RestController, /api/dashboard/v1/brands)
- GET / → returns tenant's own brands + all global brands (source differentiated)
- POST / → creates a new brand for the tenant (source=store_uploaded)
  - Auto-generates slug from name (lowercase, spaces to hyphens, trim special chars)
  - Validates slug uniqueness within tenant scope
- DELETE /{id} → soft-delete tenant's own brand (cannot delete global brands)

**BrandResponse** (DTO):
- id, name, slug, source (store_uploaded / fitvision_managed), isGlobal (boolean)

**Frontend — Brand selector in ProductForm**

Replace the current brandId UUID input with:
- Dropdown showing all available brands (tenant + global)
- "Create new brand" option at the bottom of the dropdown
- When selected: shows inline input for brand name, calls POST /brands, adds to list, auto-selects
- Brand is optional — "No brand" is a valid selection

**Frontend — Brands section in Products page**

Add a collapsible "Manage Brands" section above the products table:
- Lists tenant's own brands with delete option
- "Add Brand" button → inline form (name only, slug auto-generated)
- Global brands shown as read-only with "Global" badge

### CONSTRAINTS
- Slug generation: lowercase, trim, replace spaces with hyphens, remove non-alphanumeric except hyphens
- Cannot create a brand with the same slug as an existing global brand
- Cannot delete global brands from store dashboard
- Brand delete: if products are associated, show warning but allow (products become brandless)

### EXPECTED OUTPUT
- BrandController.java
- BrandResponse.java (DTO)
- Updated BrandRepository.java (if new queries needed)
- Updated ProductForm.tsx (brand selector with inline create)
- Updated products/page.tsx (brands management section)

### NEXT STEP
Prompt A3 will implement the admin area backend.

---

## Prompt A3 — Admin Area Backend

### CONTEXT
FitVision. Admin role exists (V4 migration). Seed endpoint created. Admin JWT contains role=ADMIN claim. JwtService already generates tokens — needs to include role claim.

All admin endpoints are under /api/admin/**. AdminAuthFilter validates JWT + role=ADMIN.

### OBJECTIVE
Implement the complete admin backend API.

**AdminAuthFilter** (OncePerRequestFilter)
- Triggers on /api/admin/** (except /api/admin/seed)
- Reads Authorization: Bearer token
- Validates via JwtService
- Extracts role from token — if not ADMIN, returns 403 FORBIDDEN
- Sets TenantContext with admin store ID (admins operate globally, not tenant-scoped)

**Updated JwtService**
- Include role in JWT claims: role=STORE or role=ADMIN
- Add extractRole(String token): String method

**AdminMetricsResponse** (DTO)
- totalStores, activeStores, totalRecommendations, recommendationsLast30Days
- averageConfidenceScore, qualityDistribution (Map<String, Long>)
- topBrands (List<BrandRecommendationStat> — top 5 brands by recommendation count)

**StoreAdminView** (DTO)
- id, name, email, plan, role, status, platform, createdAt
- totalProducts, totalRecommendations, lastRecommendationAt

**AdminController** (@RestController, /api/admin/v1)

Endpoints:

GET /metrics → AdminMetricsResponse
- Platform-wide aggregation across all tenants
- No tenant_id filter — queries all data

GET /stores?page=0&size=20&status=ACTIVE&search=
- Paginated list of all stores (StoreAdminView)
- Filter by status (ACTIVE/INACTIVE/ALL)
- Search by name or email

PATCH /stores/{storeId}/status
- Body: { "status": "ACTIVE" | "INACTIVE" }
- Activates or deactivates a store
- Deactivated stores: widget calls return 401 (ApiKeyAuthFilter checks status=ACTIVE)

GET /stores/{storeId} → StoreAdminView with full detail

GET /brands → all brands (global + tenant-scoped)
POST /brands → create global brand (tenant_id = null, source = fitvision_managed)
PUT /brands/{id} → update global brand name/slug
DELETE /brands/{id} → soft-delete global brand

POST /brands/{brandId}/size-charts/upload
- Multipart file upload
- Creates global size chart for brand (available to all stores)
- Same parser infrastructure as Phase 4

GET /brands/{brandId}/size-charts → list versions
DELETE /brands/{brandId}/size-charts/active → deactivate active chart

GET /recommendations?page=0&size=20&tenantId=&productId=&quality=
- Platform-wide recommendation log with filters
- Returns RecommendationRequest records with store and product context

**AdminService** (@Service)
- Aggregates metrics across all tenants (no tenant_id filter)
- Manages global brand operations

### CONSTRAINTS
- Admin endpoints NEVER use TenantContext for data filtering — they query all tenants
- AdminAuthFilter must be added to SecurityConfig BEFORE JwtAuthFilter in the chain
- /api/admin/seed remains permitted without auth (handled separately in A1)
- All admin actions logged at INFO with adminStoreId and action performed
- Deactivating a store does not delete data — only prevents new API key lookups

### EXPECTED OUTPUT
- AdminAuthFilter.java
- Updated JwtService.java (role claim added)
- AdminMetricsResponse.java, StoreAdminView.java, BrandRecommendationStat.java
- AdminController.java
- AdminService.java
- Updated SecurityConfig.java (AdminAuthFilter added)
- Updated ApiKeyAuthFilter.java (check store status=ACTIVE before setting TenantContext)

### NEXT STEP
Prompt A4 will build the admin frontend.

---

## Prompt A4 — Admin Area Frontend

### CONTEXT
FitVision dashboard (Next.js 14). Admin backend API complete at /api/admin/v1/**. Admin JWT is same format as store JWT but with role=ADMIN claim.

Admin frontend is a separate route group within the same Next.js app: /admin/**

Admin users log in via the same /login page — after login, the JWT is inspected client-side. If role=ADMIN, redirect to /admin/dashboard instead of /dashboard.

### OBJECTIVE
Build the admin area frontend.

**Route structure**
```
app/(admin)/
  layout.tsx          # Admin shell (different sidebar from store dashboard)
  admin/
    dashboard/        # Platform metrics
    stores/           # Store management
    brands/           # Global brand + size chart management
    recommendations/  # Platform-wide recommendation log
```

**Admin layout** (app/(admin)/layout.tsx)
- Dark sidebar or visually distinct from store dashboard
- Navigation: Platform Overview, Stores, Global Brands, Recommendations
- Top bar: "FitVision Admin" label, logout
- No store name shown (admin operates globally)

**Platform Overview** (admin/dashboard/page.tsx)
Summary cards:
- Total Stores (active / total)
- Total Recommendations (all time + last 30 days)
- Average Confidence Score (colour coded)
- Quality distribution chart (Recharts bar chart across all tenants)

Recent activity:
- Last 10 recommendations across all tenants (store name, product, size, confidence, timestamp)

**Stores** (admin/stores/page.tsx)
Table with columns: Store name, Email, Plan, Status (badge), Registered, Products, Recommendations, Actions
- Actions: View detail, Activate/Deactivate (confirmation dialog)
- Search by name or email
- Filter by status (All / Active / Inactive)
- Pagination

Store detail drawer:
- Full store info
- List of their products with size chart status
- Recommendation history for that store

**Global Brands** (admin/brands/page.tsx)
Table: Brand name, Slug, Source, Size Chart status (active/none), Last updated, Actions
- Actions: Upload size chart, Edit name, Delete

Upload size chart drawer:
- Same drag-and-drop as store dashboard
- Shows current active chart entries after upload
- Version history

Create brand form:
- Name (slug auto-generated preview shown)
- Submit creates global brand

**Recommendations Log** (admin/recommendations/page.tsx)
Table: Store, Product, Size recommended, Confidence, Quality, Date
- Filters: store, quality, date range
- Paginated

**Middleware update**
- After login, decode JWT client-side to extract role
- role=ADMIN → redirect to /admin/dashboard
- role=STORE → redirect to /dashboard
- /admin/** routes: check role=ADMIN, redirect to /login if not

**lib/api.ts additions**
- adminGetMetrics()
- adminGetStores(page, size, status, search)
- adminGetStore(storeId)
- adminUpdateStoreStatus(storeId, status)
- adminGetBrands()
- adminCreateBrand(name)
- adminUploadGlobalSizeChart(brandId, file)
- adminGetRecommendations(page, size, filters)

### CONSTRAINTS
- Admin routes use same JWT storage (localStorage + cookie) as store routes
- Role check happens both in middleware (cookie) and in each admin page (SWR fetch to /api/admin/v1/metrics — if 403, redirect to /login)
- Admin area must be visually distinct from store dashboard to avoid confusion
- No store-specific features in admin area (no widget guide, no API keys management)

### EXPECTED OUTPUT
- app/(admin)/layout.tsx
- app/(admin)/admin/dashboard/page.tsx
- app/(admin)/admin/stores/page.tsx
- app/(admin)/admin/brands/page.tsx
- app/(admin)/admin/recommendations/page.tsx
- components/admin/AdminSidebar.tsx
- components/admin/StoreTable.tsx
- components/admin/GlobalBrandManager.tsx
- components/admin/PlatformMetrics.tsx
- Updated middleware.ts (role-based redirect)
- Updated lib/api.ts (admin methods added)

### PHASE A COMPLETION CHECKLIST
Before moving to Phase 8, verify:
- [ ] SecretKeyAuthFilter no longer blocks /api/dashboard/v1/products or /api/dashboard/v1/analytics/**
- [ ] Product can be created without selecting a brand
- [ ] Brand can be created inline from the ProductForm
- [ ] Swagger accessible at http://localhost:8080/swagger-ui.html
- [ ] Admin seed: ./scripts/create-admin.sh admin@fitvision.io password creates admin account
- [ ] Admin login redirects to /admin/dashboard
- [ ] Admin can see list of all stores
- [ ] Admin can activate/deactivate a store (deactivated store widget returns 401)
- [ ] Admin can create a global brand and upload a size chart
- [ ] Global brand size chart appears as recommendation option for all stores
- [ ] Admin can view platform-wide recommendation log

---

# FitVision — Phase 8 Prompts: Shopify App

> Pre-condition: Phase A complete. Admin area operational. Brand management working. Backend stable with all fixes applied.

---

## Prompt 8.1 — Shopify App Setup + OAuth Flow

### CONTEXT
FitVision. The Shopify App allows store owners to install FitVision with one click instead of manually copying a script tag. The app handles authentication via Shopify OAuth and automatically injects the widget into product pages.

Shopify App requirements:
- Built with Shopify CLI and @shopify/shopify-api Node.js library
- Hosted separately from the dashboard (new /shopify-app directory)
- Uses Shopify OAuth to authenticate store owners
- On install: creates a FitVision account for the Shopify store (or links to existing)
- Registers webhooks: products/create, products/update, products/delete

The Shopify App is a separate Node.js/Express server (not Next.js — Shopify tooling works better with Express).

### OBJECTIVE
Set up the Shopify App project with OAuth flow.

**Project structure**
```
/shopify-app
  ├── src/
  │   ├── index.js          # Express server entry
  │   ├── auth.js           # Shopify OAuth handlers
  │   ├── webhooks.js       # Webhook registration + handlers
  │   ├── fitvision.js      # FitVision API client
  │   └── config.js         # Environment config
  ├── .env
  └── package.json
```

**OAuth flow**
1. Store owner visits app install URL
2. Shopify redirects to /auth/shopify with shop parameter
3. App redirects to Shopify OAuth consent page
4. Shopify redirects back to /auth/callback with code
5. App exchanges code for permanent access token
6. App calls FitVision backend POST /api/shopify/connect:
   - shop domain, access token (encrypted), shop name
   - FitVision creates or links a Store account
   - Returns FitVision JWT + apiKeyPublic
7. App stores FitVision credentials, redirects to app embedded UI

**FitVision backend additions** (new endpoints):
POST /api/shopify/connect
- Validates Shopify HMAC signature
- Creates or finds Store by shop domain
- Returns { jwt, apiKeyPublic, apiKeySecret }

GET /api/shopify/status?shop=
- Returns whether shop is connected and active

### CONSTRAINTS
- Shopify access tokens stored encrypted (AES-256) in FitVision database
- New column: stores.shopify_shop (VARCHAR, nullable, unique)
- New column: stores.shopify_access_token_encrypted (TEXT, nullable)
- V5__add_shopify_fields.sql migration
- HMAC validation mandatory on all Shopify webhook calls

### EXPECTED OUTPUT
- /shopify-app/package.json
- /shopify-app/src/index.js
- /shopify-app/src/auth.js
- /shopify-app/src/config.js
- /shopify-app/src/fitvision.js
- V5__add_shopify_fields.sql
- New ShopifyController.java in backend (/api/shopify/**)

### NEXT STEP
Prompt 8.2 will implement automatic product sync and widget injection.

---

## Prompt 8.2 — Product Sync + Widget Injection

### CONTEXT
FitVision Shopify App. OAuth complete. FitVision account linked to Shopify store. Access token available.

### OBJECTIVE
Implement automatic product sync and widget injection into Shopify theme.

**Product sync on install**
- On successful OAuth: fetch all products from Shopify API
- For each product: call FitVision POST /api/dashboard/v1/products with externalProductId = Shopify product ID
- Store mapping in FitVision (externalProductId is already designed for this)
- Show sync progress in embedded app UI

**Webhook handlers**
- products/create → create product in FitVision
- products/update → update product name/category in FitVision
- products/delete → soft-delete product in FitVision

**Widget injection**
- Use Shopify ScriptTag API or Theme App Extension to inject widget
- Inject into product page template automatically
- Widget reads product ID from Shopify's liquid context: {{ product.id }}
- Container div generated dynamically with correct data attributes

**Embedded App UI** (simple React page within Shopify Admin)
- Connection status (connected / not connected)
- Products synced count
- Link to FitVision dashboard for full management
- "Sync all products" button
- "Remove app" button (deregisters webhooks, removes widget)

### EXPECTED OUTPUT
- /shopify-app/src/webhooks.js
- /shopify-app/src/sync.js
- /shopify-app/src/ui/ (embedded app pages)
- Updated ShopifyController.java (webhook endpoints)

---

# FitVision — Phase 9 Prompts: Scraping Pipeline

> Pre-condition: Phase 8 complete. Admin area operational. Global brands can be created via admin dashboard. Backend running in Docker. Next migration is V8 (V7 was used for Shopify fields).

---

## Prompt 9.1 — Scraper Infrastructure + Zara Scraper

### CONTEXT
FitVision backend (Spring Boot 3.x, Java 21). The scraping pipeline fetches size charts from brand websites and stores them as global size charts (tenant_id = null), available to all stores automatically.

Scraping constraints (mandatory):
- Only scrape public size chart pages
- Always check robots.txt before scraping a new domain
- Max 1 request per 3 seconds per domain
- Store scrape_source_url for every scraped size chart
- Failed scrape must NOT overwrite existing active size chart
- Flag scraped data as stale after 30 days

Stack: Spring Scheduler + Playwright Java.

### OBJECTIVE
Build the scraping infrastructure, entities, scheduler, and first scraper (Zara men's tops).

**V8__add_scrape_jobs.sql**
```sql
ALTER TABLE size_charts
    ADD COLUMN scrape_source_url VARCHAR(500),
    ADD COLUMN last_scraped_at TIMESTAMP;

CREATE TABLE scrape_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id UUID NOT NULL REFERENCES brands(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    pages_scraped INTEGER NOT NULL DEFAULT 0,
    entries_found INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scrape_jobs_brand_id ON scrape_jobs(brand_id);
CREATE INDEX idx_scrape_jobs_status ON scrape_jobs(status);
CREATE INDEX idx_size_charts_last_scraped ON size_charts(last_scraped_at);
```

**ScrapeJob entity** (com.fitvision.domain.scraping)
```java
@Entity @Table(name = "scrape_jobs")
public class ScrapeJob {
    @Id UUID id;
    @ManyToOne Brand brand;
    @Enumerated(EnumType.STRING) ScrapeStatus status; // PENDING, RUNNING, COMPLETED, FAILED
    Instant startedAt;
    Instant completedAt;
    int pagesScraped;
    int entriesFound;
    String errorMessage;
    Instant createdAt;
}
```

**ScrapeJobRepository** (@Repository)
- findByBrandIdOrderByCreatedAtDesc(UUID brandId, Pageable pageable)
- findByStatus(ScrapeStatus status)
- countByBrandIdAndStatus(UUID brandId, ScrapeStatus status)

**BrandScraper interface**
```java
public interface BrandScraper {
    String getBrandSlug();           // e.g. "zara"
    boolean supportsCategory(String category); // "tops", "bottoms", "dresses"
    List<ScrapeResult> scrape(String category) throws ScraperException;
}
```

**ScrapeResult** (value object)
```java
public record ScrapeResult(
    String sizeLabel,       // "S", "M", "L", "XL", "XXL"
    BigDecimal chestMin, BigDecimal chestMax,
    BigDecimal waistMin, BigDecimal waistMax,
    BigDecimal hipMin, BigDecimal hipMax,
    BigDecimal heightMin, BigDecimal heightMax,
    String sourceUrl        // page URL where data was scraped from
) {}
```

**ScraperException** (RuntimeException)
- message, cause, String sourceUrl (page where it failed)

**ZaraScraper** (@Component, implements BrandScraper)

getBrandSlug(): "zara"
supportsCategory: "tops" only (Phase 9.1 scope)

scrape("tops") method:
1. Check robots.txt at https://www.zara.com/robots.txt — if /pt/ disallowed, log warning and throw ScraperException
2. Navigate to Zara men's size guide page: https://www.zara.com/pt/pt/guia-de-tamanhos.html
3. Use Playwright to:
   - Wait for size table to be visible (timeout 10s)
   - Find the men's tops/t-shirts section
   - Extract rows: size label + chest measurement in cm
   - Handle both CM and inches modes — always read CM values
4. Convert garment measurements to body measurements:
   - chestMin = garmentChest - 4
   - chestMax = garmentChest + 2
5. Rate limit: Thread.sleep(3000) between page navigations
6. Return List<ScrapeResult> with sourceUrl set to the page URL
7. On any error: throw ScraperException with the page URL where it failed

Playwright setup:
- Use Playwright.create() with headless=true, no-sandbox args
- Always close browser in finally block
- User-agent: "Mozilla/5.0 (compatible; FitVision-Scraper/1.0; +https://fitvision.io/bot)"

**BrandScraperRegistry** (@Component)
```java
@Component
public class BrandScraperRegistry {
    private final Map<String, BrandScraper> scrapers;

    public BrandScraperRegistry(List<BrandScraper> scraperList) {
        this.scrapers = scraperList.stream()
            .collect(Collectors.toMap(BrandScraper::getBrandSlug, Function.identity()));
    }

    public Optional<BrandScraper> findBySlug(String slug) {
        return Optional.ofNullable(scrapers.get(slug));
    }
}
```

**ScraperService** (@Service, @Transactional)

Method: ScrapeJob executeScrape(UUID brandId, String category)
1. Load brand — throw if not found or not global (tenant_id must be null)
2. Check if a RUNNING job exists for this brand — throw if yes (no concurrent scrapes)
3. Create ScrapeJob with status=PENDING, save
4. Find scraper via BrandScraperRegistry — if none, mark job FAILED with "No scraper available for brand slug: {slug}"
5. Mark job RUNNING, set startedAt
6. Call scraper.scrape(category)
7. On success:
   - Create new SizeChart for brand (globalBrand=true, source=FITVISION_MANAGED)
   - Deactivate previous active size chart for this brand+category
   - Create SizeEntry records from ScrapeResult list
   - Set scrape_source_url and last_scraped_at on new SizeChart
   - Mark job COMPLETED, set entriesFound, completedAt
8. On ScraperException:
   - Mark job FAILED, set errorMessage
   - Do NOT modify existing active size chart
   - Log at ERROR level with brandId, category, sourceUrl
9. Always save job at the end

**ScrapeScheduler** (@Component)
```java
@Scheduled(cron = "0 0 2 * * MON") // Every Monday at 2am
public void scheduledScrape() {
    // Find all global brands with last_scraped_at older than 30 days OR never scraped
    // For each: call scraperService.executeScrape(brand.getId(), "tops")
    // Run sequentially, never parallel
    // Log start/end with count
}
```

Add to application.yml:
```yaml
spring:
  task:
    scheduling:
      enabled: true
```

**pom.xml additions**
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.42.0</version>
</dependency>
```

Add Playwright browser install to Dockerfile:
```dockerfile
RUN mvn dependency:resolve
RUN java -cp target/dependency/* com.microsoft.playwright.CLI install chromium
```

### CONSTRAINTS
- robots.txt check is mandatory — ScraperException if disallowed
- Rate limit: Thread.sleep(3000) between page requests — no exceptions
- Failed scrape never touches existing size chart data
- No concurrent scrapes for the same brand — check before starting
- Playwright browser always closed in finally block — no resource leaks
- User-agent must identify the bot
- All scraping code in com.fitvision.infrastructure.scraping package

### EXPECTED OUTPUT
- V8__add_scrape_jobs.sql
- ScrapeJob.java (entity)
- ScrapeJobRepository.java
- ScrapeStatus.java (enum: PENDING, RUNNING, COMPLETED, FAILED)
- BrandScraper.java (interface)
- ScrapeResult.java (record)
- ScraperException.java
- ZaraScraper.java
- BrandScraperRegistry.java
- ScraperService.java
- ScrapeScheduler.java
- Updated pom.xml (Playwright dependency)
- Updated Dockerfile (Playwright browser install)
- Updated application.yml (scheduling enabled)

After implementing: docker compose down → mvn clean package -DskipTests → docker compose up --build -d. Confirm Flyway V8 runs on startup.

### NEXT STEP
Prompt 9.2 adds admin trigger endpoints and admin UI for scrape management.

---

## Prompt 9.2 — Admin Scrape Endpoints + Admin UI

### CONTEXT
FitVision. Scraper infrastructure complete (Prompt 9.1). ZaraScraper implemented. ScraperService.executeScrape() available. Admin backend at /api/admin/v1/**. Admin frontend at /admin/brands.

### OBJECTIVE
Expose scrape trigger and history via admin API. Add scrape management UI to admin brands page.

**AdminController additions** (new endpoints under /api/admin/v1)

POST /brands/{brandId}/scrape
- Body: { "category": "tops" } (optional, defaults to "tops")
- Calls scraperService.executeScrape(brandId, category) in a new thread (async)
- Returns immediately: { "jobId": UUID, "status": "RUNNING", "message": "Scrape started" }
- If scraper already running for this brand: returns 409 CONFLICT

GET /brands/{brandId}/scrape-jobs?page=0&size=10
- Returns paginated ScrapeJob history for brand
- ScrapeJobResponse: id, brandId, brandName, status, startedAt, completedAt, pagesScraped, entriesFound, errorMessage, createdAt

GET /brands/{brandId}/scrape-jobs/{jobId}
- Returns single ScrapeJob detail

GET /scrape-jobs?status=&page=0&size=20
- Platform-wide scrape job list with optional status filter
- Used by admin overview

**ScrapeJobResponse** (DTO)
- All ScrapeJob fields
- durationSeconds: long (completedAt - startedAt, null if not completed)
- isStale: boolean (last_scraped_at older than 30 days or null)

**AdminService additions**
- triggerScrapeAsync(UUID brandId, String category): CompletableFuture<ScrapeJob>
- getScrapeJobs(UUID brandId, Pageable): Page<ScrapeJobResponse>
- getAllScrapeJobs(ScrapeStatus filter, Pageable): Page<ScrapeJobResponse>

**Frontend — admin/brands/page.tsx updates**

For each global brand in the table, add a scraping section:

Status column additions:
- Last scraped: formatted date or "Never"
- isStale badge: yellow "Stale" badge if older than 30 days
- Size chart status: "Active" (green) or "None" (grey)

Actions column additions:
- "Scrape now" button:
  - POST /api/admin/v1/brands/{id}/scrape
  - Shows loading spinner while running
  - On success: shows toast "Scrape started"
  - On 409: shows toast "Scrape already running"
  - Polling: every 5 seconds check scrape job status, update badge when COMPLETED or FAILED

Scrape history drawer (new component: ScrapeHistoryDrawer.tsx):
- Opens on "View history" link next to each brand
- Lists last 10 scrape jobs in a table:
  - Date, Status badge (colour-coded), Duration, Entries found, Error (if failed)
- "Scrape now" button also available inside drawer

ScrapeStatusBadge component:
- PENDING → grey
- RUNNING → blue + spinner
- COMPLETED → green
- FAILED → red

**lib/api.ts additions**
```typescript
adminTriggerScrape(brandId: string, category?: string): Promise<{ jobId: string, status: string }>
adminGetScrapeJobs(brandId: string, page?: number): Promise<PagedResponse<ScrapeJobResponse>>
adminGetScrapeJob(brandId: string, jobId: string): Promise<ScrapeJobResponse>
```

**types.ts additions**
```typescript
interface ScrapeJobResponse {
  id: string
  brandId: string
  brandName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  startedAt: string | null
  completedAt: string | null
  pagesScraped: number
  entriesFound: number
  errorMessage: string | null
  durationSeconds: number | null
  isStale: boolean
  createdAt: string
}
```

### CONSTRAINTS
- Scrape trigger is async — admin does not wait for completion
- Polling interval 5s — stop polling when status is COMPLETED or FAILED
- 409 response when scrape already running must show clear message in UI
- ScrapeHistoryDrawer must handle empty state: "No scrape history yet"
- Admin can trigger scrape even if brand has no scraper — backend returns FAILED job gracefully

### EXPECTED OUTPUT
Backend:
- Updated AdminController.java (3 new endpoints)
- Updated AdminService.java (3 new methods)
- ScrapeJobResponse.java (DTO)

Frontend:
- Updated app/(admin)/admin/brands/page.tsx
- components/admin/ScrapeHistoryDrawer.tsx
- components/admin/ScrapeStatusBadge.tsx
- Updated lib/api.ts
- Updated types.ts

### NEXT STEP
Prompt 9.3 adds scrapers for H&M, Pull&Bear, and Mango.

---

## Prompt 9.3 — Additional Brand Scrapers (H&M, Pull&Bear, Mango)

### CONTEXT
FitVision. Scraper infrastructure complete. ZaraScraper implemented and working. BrandScraperRegistry auto-discovers scrapers via Spring DI. Admin can trigger scrapes and view history.

### OBJECTIVE
Add scrapers for H&M, Pull&Bear, and Mango. All follow the same BrandScraper interface.

**HMScraper** (@Component, implements BrandScraper)
- getBrandSlug(): "hm"
- supportsCategory: "tops", "bottoms"
- Target: https://www2.hm.com/pt_pt/customerservice/sizeguide/ladies.html (women) and /mens.html (men)
- Extract size table rows: size label + chest/waist/hip in cm
- Convert garment → body: chestMin = garment - 4, chestMax = garment + 2, waistMin = garment - 3, waistMax = garment + 2

**PullAndBearScraper** (@Component, implements BrandScraper)
- getBrandSlug(): "pull-and-bear"
- supportsCategory: "tops", "bottoms"
- Target: https://www.pullandbear.com/pt/guia-de-tamanhos.html
- Same extraction pattern as Zara (Inditex group — similar HTML structure)

**MangoScraper** (@Component, implements BrandScraper)
- getBrandSlug(): "mango"
- supportsCategory: "tops", "bottoms", "dresses"
- Target: https://shop.mango.com/pt/pt/guia-de-tamanhos
- Extract multi-category tables

**Common scraping rules for all scrapers:**
- robots.txt check before first request — ScraperException if disallowed
- Thread.sleep(3000) between page requests
- Playwright headless browser, always closed in finally
- Same user-agent as ZaraScraper
- chestMin = garmentChest - 4, chestMax = garmentChest + 2 (standard conversion)
- waistMin = garmentWaist - 3, waistMax = garmentWaist + 2 (standard conversion)
- hipMin = garmentHip - 4, hipMax = garmentHip + 2 (standard conversion)
- If a measurement column is absent for a category, leave it null (not zero)

**AbstractBrandScraper** (abstract class, optional but recommended)
- Shared logic: robots.txt check, rate limiting, Playwright setup/teardown, measurement conversion
- Subclasses implement: getBaseUrl(), getSizeTableSelector(), parseRow(row) → ScrapeResult

**ScrapeScheduler update**
- scheduledScrape() now triggers scrapes for ALL global brands with registered scrapers
- Brands without a registered scraper are skipped with a log warning (not an error)
- Order: Zara → H&M → Pull&Bear → Mango (sequential, never parallel)

### CONSTRAINTS
- Each scraper must handle layout changes gracefully — wrap each Playwright step in try-catch
- Null measurement fields are valid — do not default to 0
- AbstractBrandScraper is optional — use it only if it reduces duplication meaningfully
- All scrapers in com.fitvision.infrastructure.scraping package
- BrandScraperRegistry auto-discovers all scrapers via Spring DI — no manual registration needed

### EXPECTED OUTPUT
- HMScraper.java
- PullAndBearScraper.java
- MangoScraper.java
- AbstractBrandScraper.java (if used)
- Updated ScrapeScheduler.java (all brands)

### PHASE 9 COMPLETION CHECKLIST
Before moving to Phase 10, verify:
- [ ] V8 migration runs on Docker startup without errors
- [ ] ScrapeJob entity persists to database
- [ ] POST /api/admin/v1/brands/{id}/scrape returns jobId immediately
- [ ] ScrapeJob status transitions: PENDING → RUNNING → COMPLETED or FAILED
- [ ] ZaraScraper creates global size chart entries in database
- [ ] Failed scrape does not overwrite existing active size chart
- [ ] Admin brands page shows "Last scraped" date and stale badge
- [ ] Scrape history drawer lists past jobs with status and duration
- [ ] ScrapeScheduler runs on Monday at 2am (verify via log on startup)
- [ ] BrandScraperRegistry auto-discovers all 4 scrapers (log on startup)
- [ ] H&M, Pull&Bear, Mango scrapers registered and triggerable via admin
- [ ] robots.txt check prevents scraping if disallowed (unit test)
- [ ] Rate limit enforced: 3s between requests (verify in scraper logs)

# FitVision — Phase 10 Prompts: Billing & Subscriptions

> Pre-condition: Phase 9 complete. Admin area operational. Next migration is V9. Stripe account created at dashboard.stripe.com.

---

## Prompt 10.1 — Stripe Integration + Plan Enforcement (Backend)

### CONTEXT
FitVision backend (Spring Boot 3.x, Java 21). Stores pay a monthly subscription to use FitVision. Plans determine how many products a store can create and how many recommendations per month they can serve.

Plans:
- FREE: 2 products max, 100 recommendations/month, no export
- STARTER €29/mo: 10 products, 5000 recommendations/month
- PRO €79/mo: 50 products, 25000 recommendations/month
- TEAM €149/mo: unlimited products, unlimited recommendations

Current stores table has a `plan` column (VARCHAR, default 'FREE').

### OBJECTIVE
Integrate Stripe subscriptions and enforce plan limits on product creation and widget recommendations.

**V9__add_billing_fields.sql**
```sql
ALTER TABLE stores
    ADD COLUMN stripe_customer_id VARCHAR(255) UNIQUE,
    ADD COLUMN stripe_subscription_id VARCHAR(255) UNIQUE,
    ADD COLUMN stripe_price_id VARCHAR(255),
    ADD COLUMN subscription_status VARCHAR(50) DEFAULT 'inactive',
    ADD COLUMN subscription_current_period_end TIMESTAMP,
    ADD COLUMN recommendations_count_current_month INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN recommendations_count_reset_at TIMESTAMP;

CREATE INDEX idx_stores_stripe_customer ON stores(stripe_customer_id);
CREATE INDEX idx_stores_stripe_subscription ON stores(stripe_subscription_id);
```

**Plan enum** (com.fitvision.domain.billing)
```java
public enum Plan {
    FREE(2, 100),
    STARTER(10, 5000),
    PRO(50, 25000),
    TEAM(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int maxProducts;
    private final int maxRecommendationsPerMonth;
}
```

**PlanLimitsService** (@Service)
```java
// checkProductLimit(UUID tenantId): throws PlanLimitException if at max products
// checkRecommendationLimit(UUID tenantId): throws PlanLimitException if at monthly limit
// incrementRecommendationCount(UUID tenantId): increments counter, resets if new month
// getPlanForStore(UUID tenantId): returns Plan enum
```

**PlanLimitException** → mapped to 402 PAYMENT_REQUIRED in GlobalExceptionHandler
- code: PLAN_LIMIT_REACHED
- message: human-readable ("You've reached the 2 product limit on the Free plan. Upgrade to add more products.")

**Integration points:**
- ProductService.createProduct(): call planLimitsService.checkProductLimit() before creating
- RecommendationEngine: call planLimitsService.checkRecommendationLimit() then incrementRecommendationCount() after successful recommendation
- Widget API: graceful response when 402 — return hasSizeChart=false with message "Store plan limit reached"

**StripeService** (@Service)
```java
// createCustomer(String email, String name): String customerId
// createSubscription(String customerId, String priceId): Subscription
// cancelSubscription(String subscriptionId): void
// getSubscription(String subscriptionId): Subscription
// createBillingPortalSession(String customerId, String returnUrl): String portalUrl
```

**Stripe webhook controller** (POST /api/billing/webhooks — no auth, raw body)

Handle these events:
- customer.subscription.created → set plan, subscription_status=active, stripe IDs
- customer.subscription.updated → update plan and subscription_status
- customer.subscription.deleted → set plan=FREE, subscription_status=inactive
- invoice.payment_failed → set subscription_status=past_due, log warning

Validate Stripe-Signature header on every webhook — reject with 400 if invalid.

**BillingController** (/api/dashboard/v1/billing — JWT auth)
- GET /status → returns { plan, subscriptionStatus, currentPeriodEnd, productsUsed, productsLimit, recommendationsUsed, recommendationsLimit }
- POST /checkout → creates Stripe Checkout Session for plan upgrade, returns { checkoutUrl }
  - Body: { priceId: string }
- POST /portal → creates Stripe Billing Portal session, returns { portalUrl }
  - Allows customer to manage/cancel subscription

**pom.xml addition**
```xml
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>24.3.0</version>
</dependency>
```

**application.yml additions**
```yaml
stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  prices:
    starter: ${STRIPE_PRICE_STARTER}
    pro: ${STRIPE_PRICE_PRO}
    team: ${STRIPE_PRICE_TEAM}
```

**SecurityConfig update**
- Add /api/billing/webhooks to permitAll (raw body needed — before JSON parsing)
- Add /api/dashboard/v1/billing/** to JWT-protected routes

### CONSTRAINTS
- Stripe webhook validation is mandatory — reject any request without valid signature
- Plan limits checked BEFORE creating product or serving recommendation — fail fast
- FREE plan stores are never blocked from using the widget — only limited by count
- Recommendation counter resets on the 1st of each month (check recommendations_count_reset_at)
- stripe_customer_id created lazily — only when store first accesses billing
- TEAM plan: Integer.MAX_VALUE effectively means unlimited — no limit check needed
- All Stripe API calls wrapped in try-catch — StripeException mapped to 502 BAD_GATEWAY

### EXPECTED OUTPUT
- V9__add_billing_fields.sql
- Plan.java (enum)
- PlanLimitsService.java
- PlanLimitException.java
- StripeService.java
- StripeWebhookController.java
- BillingController.java
- BillingStatusResponse.java (DTO)
- Updated ProductService.java (plan limit check)
- Updated RecommendationEngine.java (plan limit check + counter increment)
- Updated GlobalExceptionHandler.java (PlanLimitException → 402)
- Updated SecurityConfig.java
- Updated application.yml
- Updated pom.xml

### NEXT STEP
Prompt 10.2 builds the billing UI in the store dashboard and admin subscription view.

---

## Prompt 10.2 — Billing UI (Store Dashboard + Admin)

### CONTEXT
FitVision. Billing backend complete. BillingController returns plan status and creates Checkout/Portal sessions. Stripe Checkout handles payment — store owner is redirected to Stripe-hosted page and back to dashboard after completing.

### OBJECTIVE
Add billing UI to store dashboard settings and subscription view to admin.

**Store Dashboard — Settings page updates** (app/(dashboard)/settings/page.tsx)

Add new "Plan & Billing" section below API Keys:

Current plan card:
- Plan name badge (FREE / STARTER / PRO / TEAM)
- Usage bars: Products used / limit, Recommendations used / limit (current month)
- Subscription status badge: Active (green) / Past Due (yellow) / Inactive (grey)
- Current period end date (if active subscription)

Upgrade section (shown when plan < TEAM):
- Plan comparison table: FREE / STARTER / PRO / TEAM
  - Columns: Products, Recommendations/month, Price
- "Upgrade to Starter" / "Upgrade to Pro" / "Upgrade to Team" buttons
- Click → POST /api/dashboard/v1/billing/checkout → redirect to checkoutUrl
- After Stripe success redirect back to /settings?billing=success → show success toast

Manage subscription (shown when plan > FREE):
- "Manage billing" button → POST /api/dashboard/v1/billing/portal → redirect to portalUrl
- Allows downgrade, cancellation, invoice download

**useBilling hook** (hooks/useBilling.ts)
```typescript
// Fetches GET /api/dashboard/v1/billing/status via SWR
// Returns { plan, subscriptionStatus, productsUsed, productsLimit, recommendationsUsed, recommendationsLimit, currentPeriodEnd }
// refreshInterval: 60000 (1 min)
```

**Plan limit feedback in Products page**
- When POST /products returns 402: show inline alert "Product limit reached — upgrade your plan" with link to Settings → Plan & Billing
- Do not block the form — show error after submission attempt

**Admin — Stores page update** (admin/stores/page.tsx)

Add columns to stores table:
- Plan badge (FREE/STARTER/PRO/TEAM)
- Subscription status badge
- Products used / limit

Store detail drawer additions:
- Billing section: plan, subscription status, Stripe customer ID (masked), current period end
- "Override plan" button → opens modal with plan selector → calls PATCH /api/admin/v1/stores/{id}/plan

**Admin billing endpoint** (AdminController addition)
```
PATCH /api/admin/v1/stores/{storeId}/plan
Body: { plan: "FREE" | "STARTER" | "PRO" | "TEAM" }
Sets plan directly without Stripe — for manual overrides (support, trials, partners)
```

**lib/api.ts additions**
```typescript
getBillingStatus(): Promise<BillingStatusResponse>
createCheckoutSession(priceId: string): Promise<{ checkoutUrl: string }>
createPortalSession(): Promise<{ portalUrl: string }>
adminOverrideStorePlan(storeId: string, plan: string): Promise<void>
```

**types.ts additions**
```typescript
interface BillingStatusResponse {
  plan: 'FREE' | 'STARTER' | 'PRO' | 'TEAM'
  subscriptionStatus: 'active' | 'inactive' | 'past_due' | 'canceled'
  currentPeriodEnd: string | null
  productsUsed: number
  productsLimit: number
  recommendationsUsed: number
  recommendationsLimit: number
}
```

### CONSTRAINTS
- Stripe Checkout redirect must open in same tab (not new tab)
- On return from Stripe (?billing=success), refresh billing status before showing toast
- Plan comparison table must always be visible — even for TEAM plan (shows current plan highlighted)
- Admin plan override does not create/modify Stripe subscription — backend sets column directly
- Usage bars show 100% filled and red when at limit

### EXPECTED OUTPUT
- Updated app/(dashboard)/settings/page.tsx
- hooks/useBilling.ts
- components/dashboard/BillingSection.tsx
- components/dashboard/PlanComparisonTable.tsx
- components/dashboard/UsageBar.tsx
- Updated app/(admin)/admin/stores/page.tsx
- Updated components/admin/StoreTable.tsx
- Updated lib/api.ts
- Updated types.ts
- Updated AdminController.java (plan override endpoint)

### PHASE 10 COMPLETION CHECKLIST
- [ ] V9 migration runs on Docker startup
- [ ] FREE store blocked from creating 3rd product (402 returned)
- [ ] Widget returns graceful response when recommendation limit reached
- [ ] POST /api/dashboard/v1/billing/checkout returns valid Stripe Checkout URL
- [ ] After Stripe payment, webhook updates store plan to STARTER/PRO/TEAM
- [ ] POST /api/dashboard/v1/billing/portal returns valid Stripe Portal URL
- [ ] Settings page shows current plan, usage bars, upgrade options
- [ ] Admin can override store plan without Stripe
- [ ] customer.subscription.deleted sets plan back to FREE
- [ ] Recommendation counter resets on 1st of month

---

# FitVision — Phase 11 Prompts: Production Deployment

> Pre-condition: Phase 10 complete. All features working locally. Stripe live keys configured. Domain fitvision.io registered.

---

## Prompt 11.1 — Backend Production Deployment (Railway + Neon)

### CONTEXT
FitVision backend (Spring Boot 3.x). Currently runs in Docker locally. Production target: Railway (backend) + Neon PostgreSQL (database).

### OBJECTIVE
Configure backend for production deployment on Railway with Neon PostgreSQL.

**Neon PostgreSQL setup**
- Create project at neon.tech
- Connection string format: postgresql://user:password@host/dbname?sslmode=require
- Flyway runs automatically on startup — all 9 migrations apply to fresh Neon database

**application-prod.yml** (new profile)
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

server:
  port: ${PORT:8080}

logging:
  level:
    com.fitvision: INFO
    root: WARN

fitvision:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
  shopify:
    encryption-key: ${SHOPIFY_ENCRYPTION_KEY}
    shared-secret: ${SHOPIFY_SHARED_SECRET}

stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}
  prices:
    starter: ${STRIPE_PRICE_STARTER}
    pro: ${STRIPE_PRICE_PRO}
    team: ${STRIPE_PRICE_TEAM}
```

**Dockerfile (production-ready)**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/fitvision-backend-*.jar app.jar

# Install Playwright browsers
RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV CHROMIUM_PATH=/usr/bin/chromium-browser

EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
```

**railway.toml**
```toml
[build]
builder = "dockerfile"

[deploy]
startCommand = "java -Dspring.profiles.active=prod -jar app.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 30
restartPolicyType = "on_failure"
```

**Railway environment variables to set:**
```
DATABASE_URL=           (Neon connection string)
JWT_SECRET=             (random 64-char string)
SHOPIFY_ENCRYPTION_KEY= (32-char string)
SHOPIFY_SHARED_SECRET=  (shared with Shopify App)
STRIPE_SECRET_KEY=      (sk_live_...)
STRIPE_WEBHOOK_SECRET=  (whsec_...)
STRIPE_PRICE_STARTER=   (price_...)
STRIPE_PRICE_PRO=       (price_...)
STRIPE_PRICE_TEAM=      (price_...)
SPRING_PROFILES_ACTIVE= prod
```

**CORS update for production**
In SecurityConfig or CorsConfig, allow:
- https://app.fitvision.io (dashboard)
- https://fitvision.io (landing page)
- https://*.myshopify.com (Shopify stores using widget)
- Keep * for /api/widget/** (public widget endpoint)

**Actuator hardening**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

### EXPECTED OUTPUT
- application-prod.yml
- Dockerfile (production-ready)
- railway.toml
- Updated SecurityConfig.java (production CORS)
- Updated application.yml (actuator hardening)
- README section: Railway deployment steps

---

## Prompt 11.2 — Dashboard Production Deployment (Vercel)

### CONTEXT
FitVision dashboard (Next.js 14). Production target: Vercel. API calls must point to Railway backend URL.

### OBJECTIVE
Configure Next.js dashboard for Vercel deployment.

**.env.production**
```
NEXT_PUBLIC_API_URL=https://api.fitvision.io
NEXT_PUBLIC_WIDGET_CDN=https://cdn.fitvision.io/widget/fitvision-widget.min.js
```

**next.config.js update**
```javascript
const nextConfig = {
  output: 'standalone',
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'SAMEORIGIN' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        ],
      },
    ]
  },
}
```

**lib/api.ts update**
- Replace hardcoded localhost:8080 with process.env.NEXT_PUBLIC_API_URL
- Fallback: http://localhost:8080 for local dev

**vercel.json**
```json
{
  "framework": "nextjs",
  "buildCommand": "npm run build",
  "outputDirectory": ".next",
  "rewrites": [
    { "source": "/api/:path*", "destination": "https://api.fitvision.io/api/:path*" }
  ]
}
```

**Vercel environment variables to set:**
```
NEXT_PUBLIC_API_URL=https://api.fitvision.io
NEXT_PUBLIC_WIDGET_CDN=https://cdn.fitvision.io/widget/fitvision-widget.min.js
```

**middleware.ts update**
- Ensure cookie settings work in production (secure: true, sameSite: strict)

### EXPECTED OUTPUT
- .env.production
- Updated next.config.js
- vercel.json
- Updated lib/api.ts (env-based URL)
- Updated middleware.ts (production cookie settings)

---

## Prompt 11.3 — Widget CDN + CI/CD Pipeline

### CONTEXT
FitVision. Backend on Railway, dashboard on Vercel. Widget (fitvision-widget.min.js) needs to be served from a CDN at https://cdn.fitvision.io/widget/.

Target: Cloudflare R2 + Cloudflare CDN (free tier covers this scale).

CI/CD: GitHub Actions — one workflow per service.

### OBJECTIVE
Configure Cloudflare R2 for widget CDN and GitHub Actions workflows for all services.

**Cloudflare R2 setup**
- Bucket: fitvision-widget
- Public URL: cdn.fitvision.io/widget/
- CORS: allow all origins for GET requests
- Cache: Cache-Control: public, max-age=31536000, immutable (versioned files)
- Latest: Cache-Control: public, max-age=300 (5 min for fitvision-widget.min.js)

**Widget versioning strategy**
- fitvision-widget.min.js — always latest (short cache)
- fitvision-widget.{version}.min.js — versioned (long cache, immutable)
- Version read from package.json version field

**GitHub Actions — Backend (.github/workflows/backend.yml)**
```yaml
name: Backend CI/CD
on:
  push:
    branches: [main]
    paths: ['src/**', 'pom.xml', 'Dockerfile']

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: fitvision_test
          POSTGRES_USER: fitvision
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: mvn verify
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/fitvision_test

  deploy:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: mvn package -DskipTests
      - uses: railway/deploy@v1
        with:
          service: fitvision-backend
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
```

**GitHub Actions — Dashboard (.github/workflows/dashboard.yml)**
```yaml
name: Dashboard CI/CD
on:
  push:
    branches: [main]
    paths: ['dashboard/**']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci
        working-directory: dashboard
      - run: npm run build
        working-directory: dashboard
        env:
          NEXT_PUBLIC_API_URL: ${{ secrets.NEXT_PUBLIC_API_URL }}
      - uses: amondnet/vercel-action@v25
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
          working-directory: dashboard
```

**GitHub Actions — Widget (.github/workflows/widget.yml)**
```yaml
name: Widget CI/CD
on:
  push:
    branches: [main]
    paths: ['widget/**']

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci
        working-directory: widget
      - run: npm run build
        working-directory: widget
      - name: Upload to Cloudflare R2
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          command: r2 object put fitvision-widget/widget/fitvision-widget.min.js --file=widget/dist/fitvision-widget.min.js
```

**GitHub Secrets to configure:**
```
RAILWAY_TOKEN
VERCEL_TOKEN
VERCEL_ORG_ID
VERCEL_PROJECT_ID
CLOUDFLARE_API_TOKEN
NEXT_PUBLIC_API_URL
```

### PHASE 11 COMPLETION CHECKLIST
- [ ] Railway backend responds at https://api.fitvision.io/actuator/health
- [ ] Neon database has all 9 migrations applied
- [ ] Vercel dashboard accessible at https://app.fitvision.io
- [ ] Widget loads from https://cdn.fitvision.io/widget/fitvision-widget.min.js
- [ ] Store can register, login, create product, upload size chart via production URLs
- [ ] Widget recommendation works end-to-end on production
- [ ] Stripe webhook receives events from live Stripe (configure endpoint in Stripe dashboard)
- [ ] GitHub Actions: push to main triggers deploy for changed service only
- [ ] CORS: widget works from any Shopify store domain

---

# FitVision — Phase 12 Prompts: Observability & Operations

> Pre-condition: Phase 11 complete. All services running in production.

---

## Prompt 12.1 — Structured Logging + Sentry

### CONTEXT
FitVision backend (Spring Boot 3.x) running on Railway. Need structured logging with correlation IDs, error alerting via Sentry, and performance monitoring for the recommendation endpoint.

### OBJECTIVE
Add production-grade observability to the backend.

**Structured logging**

Add logstash-logback-encoder to pom.xml:
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

logback-spring.xml (src/main/resources):
```xml
<configuration>
  <springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON"/>
    </root>
  </springProfile>
  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder><pattern>%d{HH:mm:ss} %-5level %logger{36} - %msg%n</pattern></encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>
</configuration>
```

**RequestIdFilter update**
- Add X-Request-ID to MDC on every request: MDC.put("requestId", requestId)
- Add tenantId to MDC after TenantContext is set: MDC.put("tenantId", tenantId)
- Clear MDC in finally block

**Sentry integration**
```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
    <version>7.6.0</version>
</dependency>
```

application-prod.yml additions:
```yaml
sentry:
  dsn: ${SENTRY_DSN}
  traces-sample-rate: 0.2
  environment: production
  release: ${APP_VERSION:unknown}
```

GlobalExceptionHandler update:
- Capture unexpected exceptions to Sentry: Sentry.captureException(ex)
- Do NOT capture expected business exceptions (PlanLimitException, FitVisionException with known codes)
- Add tenantId and requestId to Sentry scope before capturing

**Performance monitoring — recommendation endpoint**

In RecommendationEngine, add timing:
```java
long start = System.currentTimeMillis();
// ... existing logic ...
long duration = System.currentTimeMillis() - start;
log.info("recommendation_completed tenantId={} productId={} durationMs={} quality={}",
    tenantId, productId, duration, result.getMatchQuality());
if (duration > 500) {
    log.warn("slow_recommendation tenantId={} productId={} durationMs={}", tenantId, productId, duration);
}
```

**Health check enhancement**

Custom HealthIndicator for database connectivity:
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    // Check if a simple query runs in < 100ms
    // Down if query fails or exceeds 200ms
}
```

### EXPECTED OUTPUT
- logback-spring.xml
- Updated RequestIdFilter.java (MDC enrichment)
- Updated pom.xml (logstash-logback-encoder + sentry)
- Updated application-prod.yml (Sentry config)
- Updated GlobalExceptionHandler.java (Sentry capture)
- Updated RecommendationEngine.java (timing logs)
- DatabaseHealthIndicator.java

---

## Prompt 12.2 — Admin Health Panel + Operations

### CONTEXT
FitVision. Structured logging and Sentry active. Admin area exists at /admin. Need an admin health panel showing system status, and operational tools for the platform operator.

### OBJECTIVE
Add system health panel to admin area and operational utilities.

**Backend — Admin health endpoints**

GET /api/admin/v1/health
Returns:
```json
{
  "database": { "status": "UP", "latencyMs": 12 },
  "recommendationEngine": { "avgLatencyMs": 87, "p95LatencyMs": 234 },
  "scrapeJobs": { "running": 0, "failedLast7Days": 2 },
  "storeActivity": { "recommendationsLast24h": 1247, "activeStoresLast24h": 8 }
}
```

GET /api/admin/v1/recommendations/stats
- p50, p95, p99 latency for last 24h (computed from recommendation logs)
- Quality distribution last 24h
- Top 5 stores by recommendation count last 24h

**AdminHealthService** (@Service)
- getDatabaseLatency(): runs SELECT 1, measures ms
- getRecommendationStats(Duration window): aggregates from RecommendationRequest table
- getScrapeJobStats(): counts from ScrapeJob table

**Frontend — Admin health page** (admin/health/page.tsx)

Add to admin sidebar: "System Health" link

Health page sections:

System status cards:
- Database: UP/DOWN badge + latency ms
- Last recommendation: timestamp
- Active stores (last 24h): count
- Failed scrapes (last 7 days): count (red if > 0)

Recommendation performance:
- p50 / p95 / p99 latency (colour-coded: green < 200ms, yellow < 500ms, red > 500ms)
- Quality distribution bar chart (last 24h)
- Top stores by recommendation volume

Scrape pipeline:
- Last scrape per brand: brand name, status, timestamp, entries
- "Force re-scrape all" button (triggers scrape for all brands)
- "Force re-scrape {brand}" button per row

**Force re-scrape all** (AdminController)
POST /api/admin/v1/scrape-jobs/trigger-all
- Triggers scrape for all global brands with registered scrapers
- Returns { triggered: number, skipped: number }

**Auto-refresh**
- Health page auto-refreshes every 30 seconds
- Show "Last updated: X seconds ago" counter

### EXPECTED OUTPUT
Backend:
- AdminHealthService.java
- Updated AdminController.java (health + stats + trigger-all endpoints)
- AdminHealthResponse.java (DTO)
- RecommendationStatsResponse.java (DTO)

Frontend:
- app/(admin)/admin/health/page.tsx
- components/admin/SystemHealthCards.tsx
- components/admin/RecommendationStatsPanel.tsx
- components/admin/ScrapePipelineStatus.tsx
- Updated components/admin/AdminSidebar.tsx (Health link added)
- Updated lib/api.ts (health + stats methods)
- Updated types.ts (health response types)

### PHASE 12 COMPLETION CHECKLIST
- [ ] JSON logs appear in Railway log viewer with requestId and tenantId fields
- [ ] Sentry receives errors from production (test by triggering a 500)
- [ ] Slow recommendations (> 500ms) appear as warnings in logs
- [ ] /actuator/health returns database status
- [ ] Admin health page loads and shows real metrics
- [ ] p95 recommendation latency visible in admin
- [ ] "Force re-scrape all" triggers scrape jobs for all brands
- [ ] Health page auto-refreshes every 30 seconds
- [ ] Failed scrapes last 7 days visible with count

# FitVision — Phase T1 Prompts: Backend Integration Tests

> Pre-condition: All phases 1–12 complete. Existing tests: 77 passing (45 unit + 32 integration). AbstractIntegrationTest uses singleton Testcontainers pattern. Next migration is V11.

---

## Prompt T1.1 — Core Flow Integration Tests

### CONTEXT
FitVision backend. Existing integration tests cover basic CRUD and widget API. Missing coverage: full end-to-end business flows, billing enforcement, admin operations, and Shopify webhook handling.

All integration tests extend AbstractIntegrationTest which starts a singleton PostgreSQL container and runs all Flyway migrations.

### OBJECTIVE
Add integration tests for the critical business flows not currently covered.

**Test class: StoreRegistrationFlowIT** (extends AbstractIntegrationTest)

Test: full registration and login flow
```java
@Test
void register_login_getProfile_returnsCorrectData() {
    // POST /api/dashboard/v1/auth/register
    // POST /api/dashboard/v1/auth/login → extract JWT
    // GET /api/dashboard/v1/stores/profile → assert name, email, plan=FREE
}

@Test
void register_duplicateEmail_returns409() {
    // Register same email twice → second returns 409
}

@Test
void login_wrongPassword_returns401() {}

@Test
void login_unknownEmail_returns401() {}
```

**Test class: ProductFlowIT** (extends AbstractIntegrationTest)

```java
@Test
void createProduct_withoutBrand_succeeds() {
    // Register + login
    // POST /api/dashboard/v1/products with no brandId
    // Assert 201, product returned with brandId=null
}

@Test
void createProduct_withBrand_succeeds() {
    // Create brand first
    // Create product with brandId
    // Assert brand association
}

@Test
void createProduct_exceedsFreePlanLimit_returns402() {
    // Create 2 products (FREE plan limit)
    // Create 3rd product → assert 402, code=PLAN_LIMIT_REACHED
}

@Test
void softDeleteProduct_disappearsFromList() {
    // Create → DELETE → GET list → assert not present
    // GET by id → assert 404
}

@Test
void updateProduct_partialUpdate_onlyChangesSpecifiedFields() {}
```

**Test class: SizeChartFlowIT** (extends AbstractIntegrationTest)

```java
@Test
void uploadCsv_validFile_createsActiveSizeChart() {
    // Create product
    // POST /api/dashboard/v1/size-charts/{productId}/upload with valid CSV
    // GET /api/dashboard/v1/size-charts/{productId}/active
    // Assert entries present
}

@Test
void uploadCsv_secondUpload_replacesActiveChart() {
    // Upload twice → assert only second is active
    // Assert version incremented
}

@Test
void uploadCsv_invalidFormat_returns400() {
    // Upload CSV missing required columns → assert 400
}

@Test
void uploadCsv_exceedsFileSize_returns400() {
    // Upload file > 2MB → assert 400
}

@Test
void manualEntry_createsActiveSizeChart() {
    // POST /api/dashboard/v1/size-charts/{productId}/manual
    // Assert entries match input
}
```

**Test class: RecommendationFlowIT** (extends AbstractIntegrationTest)

```java
@Test
void recommendation_withSizeChart_returnsCorrectSize() {
    // Setup: store → product → size chart (S:38.5-44.5, M:42.5-48.5, L:46.5-52.5)
    // POST /api/widget/v1/size-recommendation
    //   { apiKey, productId, heightCm:175, weightKg:75, gender:MALE }
    // Assert: recommended size present, hasSizeChart=true, confidenceLabel in [High,Medium,Low]
}

@Test
void recommendation_withoutSizeChart_returnsFallback() {
    // Product with no size chart
    // Assert: hasSizeChart=false, no size recommended
}

@Test
void recommendation_invalidApiKey_returns401() {}

@Test
void recommendation_inactiveStore_returns401() {
    // Deactivate store via admin
    // Widget call → assert 401
}

@Test
void recommendation_exceedsMonthlyLimit_returnsPlanLimitFallback() {
    // Set recommendations_count_current_month to limit for FREE plan (100)
    // Next recommendation → assert planLimitFallback response (200, not 402)
}

@Test
void recommendation_outOfRangeBmi_returnsNoMatch() {
    // BMI < 15 or > 45 → OUT_OF_RANGE quality
}
```

**Test class: AnalyticsFlowIT** (extends AbstractIntegrationTest)

```java
@Test
void analyticsSummary_afterRecommendations_returnsCorrectCounts() {
    // Make 5 recommendations with known qualities
    // GET /api/dashboard/v1/analytics/summary
    // Assert totalRecommendations=5, quality distribution matches
}

@Test
void analyticsList_pagination_returnsCorrectPage() {
    // Make 15 recommendations
    // GET ?page=0&size=10 → assert 10 results
    // GET ?page=1&size=10 → assert 5 results
}
```

### CONSTRAINTS
- All tests must be independent — no shared state between test methods
- Use @BeforeEach to register a fresh store and get JWT for each test
- Helper methods: registerAndLogin(), createProduct(), uploadTestCsv()
- Test CSV loaded from src/test/resources/test-data/size-chart-tops.csv
- Never hardcode UUIDs — always use IDs returned from API calls
- Each test class reuses the singleton Testcontainers instance

### EXPECTED OUTPUT
- StoreRegistrationFlowIT.java
- ProductFlowIT.java
- SizeChartFlowIT.java
- RecommendationFlowIT.java
- AnalyticsFlowIT.java
- src/test/resources/test-data/size-chart-tops.csv (valid test fixture)
- Updated AbstractIntegrationTest.java (shared helper methods if not already present)

### NEXT STEP
Prompt T1.2 adds billing, admin, and Shopify webhook integration tests.

---

## Prompt T1.2 — Billing, Admin, and Shopify Integration Tests

### CONTEXT
FitVision backend. Core flow tests complete (T1.1). Now covering billing enforcement, admin operations, and Shopify webhook handling.

Stripe is NOT called in tests — StripeService must be mocked. Use @MockBean StripeService in billing tests.

### OBJECTIVE
Integration tests for billing, admin, and Shopify flows.

**Test class: BillingFlowIT** (extends AbstractIntegrationTest)

```java
@MockBean
private StripeService stripeService;

@Test
void billingStatus_freePlan_returnsCorrectLimits() {
    // Register store
    // GET /api/dashboard/v1/billing/status
    // Assert plan=FREE, productsLimit=2, recommendationsLimit=100
}

@Test
void checkout_validPlan_returnsCheckoutUrl() {
    // Mock stripeService.createCheckoutSession() → return "https://checkout.stripe.com/test"
    // POST /api/dashboard/v1/billing/checkout { priceId: "price_starter" }
    // Assert checkoutUrl returned
}

@Test
void stripeWebhook_subscriptionCreated_upgradesPlan() {
    // POST /api/billing/webhooks with valid Stripe signature
    // Event: customer.subscription.created, plan=STARTER
    // Assert store plan updated to STARTER in database
    // Assert productsLimit=10 in billing status
}

@Test
void stripeWebhook_subscriptionDeleted_downgradeToFree() {
    // Setup store with STARTER plan
    // POST webhook: customer.subscription.deleted
    // Assert plan=FREE, subscription_status=inactive
}

@Test
void stripeWebhook_invalidSignature_returns400() {
    // POST /api/billing/webhooks without valid Stripe-Signature header
    // Assert 400
}

@Test
void planLimitReset_newMonth_resetsCounter() {
    // Set recommendations_count_current_month=100, reset_at=last month
    // Make recommendation → assert counter reset to 1, new reset_at = this month
}
```

**Test class: AdminFlowIT** (extends AbstractIntegrationTest)

```java
@Test
void adminSeed_firstCall_createsAdmin() {
    // POST /api/admin/seed
    // Assert 200, JWT returned with role=ADMIN
}

@Test
void adminSeed_secondCall_returns409() {
    // Seed twice → second returns 409
}

@Test
void adminMetrics_afterStoresAndRecommendations_returnsCorrectData() {
    // Create 2 stores, make 5 recommendations
    // GET /api/admin/v1/metrics (admin JWT)
    // Assert totalStores>=2, totalRecommendations>=5
}

@Test
void adminStores_list_returnsAllStores() {
    // Create 3 stores
    // GET /api/admin/v1/stores
    // Assert all 3 present
}

@Test
void adminDeactivateStore_widgetReturns401() {
    // Create store, setup product + size chart
    // PATCH /api/admin/v1/stores/{id}/status { status: INACTIVE }
    // Widget call → assert 401
}

@Test
void adminReactivateStore_widgetWorksAgain() {
    // Deactivate then reactivate
    // Widget call → assert 200
}

@Test
void adminOverridePlan_changesPlanDirectly() {
    // PATCH /api/admin/v1/stores/{id}/plan { plan: PRO }
    // GET /api/dashboard/v1/billing/status → assert plan=PRO, productsLimit=50
}

@Test
void adminGlobalBrand_createAndUploadSizeChart_availableToAllStores() {
    // POST /api/admin/v1/brands { name: "Zara Global", slug: "zara-global" }
    // POST /api/admin/v1/brands/{id}/size-charts/upload (CSV)
    // From store: list products → assert global brand visible
}

@Test
void adminEndpoints_storeJwt_returns403() {
    // GET /api/admin/v1/metrics with store JWT (not admin)
    // Assert 403
}
```

**Test class: ShopifyWebhookIT** (extends AbstractIntegrationTest)

```java
@Test
void shopifyConnect_newStore_createsAccountAndReturnsJwt() {
    // POST /api/shopify/connect
    //   Header: X-FitVision-Shopify-Secret
    //   Body: { shop, accessToken, shopName }
    // Assert 200, jwt and apiKeyPublic returned
    // Assert store created with shopify_shop set
}

@Test
void shopifyConnect_existingStore_updatesTokenAndReturns200() {
    // Connect twice with same shop domain
    // Assert same storeId returned, token updated
}

@Test
void shopifyConnect_wrongSecret_returns401() {
    // POST with wrong X-FitVision-Shopify-Secret → 401
}

@Test
void shopifyStatus_connectedStore_returnsConnectedTrue() {
    // Connect store
    // GET /api/shopify/status?shop=test.myshopify.com
    // Assert connected=true, apiKeyPublic present
}

@Test
void shopifyStatus_unknownStore_returnsConnectedFalse() {
    // GET /api/shopify/status?shop=unknown.myshopify.com
    // Assert connected=false
}

@Test
void shopifyConnect_inactiveStore_reactivatesOnReconnect() {
    // Connect → deactivate via admin → connect again
    // Assert status=ACTIVE
}
```

### CONSTRAINTS
- StripeService mocked with @MockBean — never call real Stripe in tests
- Shopify HMAC validation: use test secret configured in application-test.yml
- Admin seed called in @BeforeEach for admin tests, cleanup in @AfterEach
- Webhook tests must construct valid Stripe event payloads (use Stripe test fixtures)
- Test Shopify shared secret: add fitvision.shopify.shared-secret=test-secret to application-test.yml

### EXPECTED OUTPUT
- BillingFlowIT.java
- AdminFlowIT.java
- ShopifyWebhookIT.java
- src/test/resources/application-test.yml (test-specific overrides)
- Updated pom.xml if any test dependencies missing

### PHASE T1 COMPLETION CHECKLIST
- [ ] mvn test runs all tests without errors
- [ ] Total test count > 120 (was 77)
- [ ] StoreRegistrationFlowIT: 4 tests passing
- [ ] ProductFlowIT: 5 tests passing including plan limit 402
- [ ] SizeChartFlowIT: 5 tests passing
- [ ] RecommendationFlowIT: 6 tests passing including plan limit fallback
- [ ] AnalyticsFlowIT: 2 tests passing
- [ ] BillingFlowIT: 6 tests passing (Stripe mocked)
- [ ] AdminFlowIT: 9 tests passing
- [ ] ShopifyWebhookIT: 6 tests passing

---

# FitVision — Phase T2 Prompts: E2E Tests (Playwright)

> Pre-condition: T1 complete. Dashboard running at localhost:3000. Backend running at localhost:8080.

---

## Prompt T2.1 — Playwright Setup + Store Dashboard E2E

### CONTEXT
FitVision dashboard (Next.js 14). No E2E tests exist. Adding Playwright for browser-based end-to-end testing of the store dashboard and widget.

Playwright runs against the real backend (localhost:8080) and dashboard (localhost:3000). Tests create real data via the API in beforeEach and clean up after.

### OBJECTIVE
Set up Playwright and implement E2E tests for the store dashboard.

**Setup**

In /dashboard directory:
```bash
npm install -D @playwright/test
npx playwright install chromium
```

playwright.config.ts:
```typescript
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false, // sequential to avoid DB conflicts
  retries: 1,
  timeout: 30000,
  use: {
    baseURL: 'http://localhost:3000',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'npm run dev',
      url: 'http://localhost:3000',
      reuseExistingServer: true,
    }
  ],
})
```

**e2e/helpers/api.ts** — test helper that creates data via API
```typescript
// createTestStore(): Promise<{ email, password, jwt, apiKeyPublic }>
// createTestProduct(jwt, name?): Promise<{ id, externalProductId }>
// uploadTestSizeChart(jwt, productId): Promise<void>
// deleteTestStore(jwt): Promise<void>
// Loads test CSV from e2e/fixtures/size-chart-tops.csv
```

**e2e/fixtures/** — test data files
- size-chart-tops.csv (valid CSV for upload tests)

**e2e/auth.spec.ts**
```typescript
test('register → login → see dashboard', async ({ page }) => {
  await page.goto('/register')
  await page.fill('[name=name]', 'Test Store')
  await page.fill('[name=email]', uniqueEmail())
  await page.fill('[name=password]', 'password123')
  await page.selectOption('[name=platform]', 'shopify')
  await page.click('button[type=submit]')
  await expect(page).toHaveURL('/dashboard')
  await expect(page.locator('text=Total Recommendations')).toBeVisible()
})

test('login with wrong password → shows error', async ({ page }) => {
  await page.goto('/login')
  await page.fill('[name=email]', 'wrong@example.com')
  await page.fill('[name=password]', 'wrongpass')
  await page.click('button[type=submit]')
  await expect(page.locator('text=Invalid')).toBeVisible()
})

test('logout → redirects to login', async ({ page }) => {
  // Login first via API, set token in localStorage
  // Click logout
  await expect(page).toHaveURL('/login')
})
```

**e2e/products.spec.ts**
```typescript
test.beforeEach(async ({ page }) => {
  // Create store via API, set JWT in localStorage, navigate to /products
})

test('create product → appears in list', async ({ page }) => {
  await page.click('text=Add product')
  await page.fill('[name=name]', 'Test T-Shirt')
  await page.selectOption('[name=category]', 'tops')
  await page.click('button[type=submit]')
  await expect(page.locator('text=Test T-Shirt')).toBeVisible()
})

test('upload size chart CSV → shows active chart', async ({ page }) => {
  // Create product via API
  // Click Upload Size Chart
  // Drop file into dropzone
  // Assert "Active" badge appears
})

test('delete product → disappears from list', async ({ page }) => {
  // Create product via API
  // Click delete → confirm
  // Assert product no longer in list
})

test('product limit reached → shows upgrade alert', async ({ page }) => {
  // Create 2 products via API (FREE plan limit)
  // Try to create 3rd via UI
  // Assert upgrade alert visible
})
```

**e2e/settings.spec.ts**
```typescript
test('api keys → reveal → copy public key', async ({ page }) => {
  // Navigate to /settings
  await expect(page.locator('text=Public key')).toBeVisible()
  await page.click('text=Reveal')
  await expect(page.locator('text=Copy secret key')).toBeVisible()
})

test('regenerate keys → new keys shown', async ({ page }) => {
  // Get current public key text
  // Click Regenerate keys → confirm
  // Assert public key changed
})

test('billing section → shows FREE plan', async ({ page }) => {
  await expect(page.locator('text=FREE')).toBeVisible()
  await expect(page.locator('text=Products')).toBeVisible()
})
```

**package.json additions**
```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui",
    "test:e2e:headed": "playwright test --headed"
  }
}
```

### CONSTRAINTS
- Tests must be independent — each test creates its own store via API helper
- Use uniqueEmail() helper to avoid conflicts: `test-${Date.now()}@fitvision-test.io`
- Never depend on data created by another test
- Screenshots and videos saved to e2e/results/ (add to .gitignore)
- Tests run against real backend — backend must be running before E2E tests

### EXPECTED OUTPUT
- dashboard/playwright.config.ts
- dashboard/e2e/helpers/api.ts
- dashboard/e2e/fixtures/size-chart-tops.csv
- dashboard/e2e/auth.spec.ts
- dashboard/e2e/products.spec.ts
- dashboard/e2e/settings.spec.ts
- Updated dashboard/package.json (playwright scripts)
- Updated dashboard/.gitignore (e2e/results/)

### NEXT STEP
Prompt T2.2 adds E2E tests for the admin area and widget.

---

## Prompt T2.2 — Admin E2E + Widget E2E Tests

### CONTEXT
FitVision. Playwright setup complete. Store dashboard E2E tests passing. Now covering admin area and widget.

Admin tests require an admin account. Create via API before tests using POST /api/admin/seed.

### OBJECTIVE
E2E tests for admin area and widget behaviour.

**e2e/admin.spec.ts**

```typescript
test.describe('Admin area', () => {
  let adminJwt: string

  test.beforeAll(async ({ request }) => {
    // POST /api/admin/seed (or login if already exists)
    // Store adminJwt
  })

  test.beforeEach(async ({ page }) => {
    // Set adminJwt in localStorage
    // Navigate to /admin/dashboard
  })

  test('platform overview → shows metrics cards', async ({ page }) => {
    await expect(page.locator('text=Total Stores')).toBeVisible()
    await expect(page.locator('text=Total Recommendations')).toBeVisible()
    await expect(page.locator('text=Average Confidence')).toBeVisible()
  })

  test('stores page → list shows registered stores', async ({ page }) => {
    await page.click('text=Stores')
    await expect(page).toHaveURL('/admin/stores')
    await expect(page.locator('table')).toBeVisible()
  })

  test('deactivate store → status changes to Inactive', async ({ page }) => {
    // Create test store via API
    // Navigate to stores page
    // Find store row → click Deactivate → confirm
    // Assert status badge shows Inactive
  })

  test('global brands → create brand', async ({ page }) => {
    await page.click('text=Global Brands')
    await page.click('text=Add Brand')
    await page.fill('[name=name]', `TestBrand-${Date.now()}`)
    await page.click('button[type=submit]')
    await expect(page.locator('text=TestBrand')).toBeVisible()
  })

  test('system health → shows DB status', async ({ page }) => {
    await page.click('text=System Health')
    await expect(page).toHaveURL('/admin/health')
    await expect(page.locator('text=Database')).toBeVisible()
    await expect(page.locator('text=UP')).toBeVisible()
  })

  test('store role → cannot access admin area', async ({ page }) => {
    // Login as store user
    // Navigate to /admin/dashboard
    // Assert redirected to /login
  })
})
```

**e2e/widget.spec.ts**

Widget tests load a minimal HTML page that embeds the FitVision widget.

```typescript
test.beforeEach(async ({ page }) => {
  // Create store + product + size chart via API
  // Serve a minimal HTML page with widget embedded
  // page.route() to intercept and serve local widget file
})

test('widget loads → trigger button visible', async ({ page }) => {
  await page.goto('/e2e-widget-test.html')
  await expect(page.locator('[data-fitvision]')).toBeVisible()
  // Or check for the trigger button text
  await expect(page.locator('text=Find my size')).toBeVisible()
})

test('widget → fill form → shows recommendation', async ({ page }) => {
  await page.goto('/e2e-widget-test.html')
  await page.click('text=Find my size')
  await page.fill('[name=height]', '175')
  await page.fill('[name=weight]', '75')
  await page.selectOption('[name=gender]', 'MALE')
  await page.click('button[type=submit]')
  // Assert recommendation shown
  await expect(page.locator('.fv-result')).toBeVisible()
  await expect(page.locator('text=Recommended size')).toBeVisible()
})

test('widget → no size chart → shows fallback message', async ({ page }) => {
  // Product without size chart
  await page.goto('/e2e-widget-test-no-chart.html')
  await page.click('text=Find my size')
  await page.fill('[name=height]', '175')
  await page.fill('[name=weight]', '75')
  await page.selectOption('[name=gender]', 'MALE')
  await page.click('button[type=submit]')
  await expect(page.locator('text=size guide not available')).toBeVisible()
})

test('widget → invalid api key → shows error', async ({ page }) => {
  // Widget with wrong api key
  await expect(page.locator('text=could not connect')).toBeVisible()
})
```

**e2e/widget-test-pages/** — static HTML fixtures for widget tests
- e2e-widget-test.html: page with valid widget embed
- e2e-widget-test-no-chart.html: page with product that has no size chart
- e2e-widget-test-invalid-key.html: page with wrong API key

### PHASE T2 COMPLETION CHECKLIST
- [ ] npx playwright test runs without errors
- [ ] auth.spec.ts: 3 tests passing
- [ ] products.spec.ts: 4 tests passing including plan limit
- [ ] settings.spec.ts: 3 tests passing
- [ ] admin.spec.ts: 6 tests passing
- [ ] widget.spec.ts: 4 tests passing
- [ ] Screenshots saved on failure in e2e/results/
- [ ] CI workflow updated: npm run test:e2e runs after build

---

# FitVision — Phase T3 Prompts: Smoke Tests (Production)

> Pre-condition: T1 and T2 complete. Production deployment live at api.fitvision.io and app.fitvision.io.

---

## Prompt T3.1 — Production Smoke Test Script

### CONTEXT
FitVision. All features complete and deployed. Need an automated smoke test that runs after every production deployment to verify the critical paths are working.

Smoke tests are NOT unit tests or E2E tests. They are fast, read-only checks that confirm the production environment is alive and responding correctly.

### OBJECTIVE
Create a smoke test script that runs in CI after every production deploy.

**scripts/smoke-test.sh**

```bash
#!/bin/bash
# FitVision Production Smoke Test
# Usage: ./scripts/smoke-test.sh https://api.fitvision.io
# Exit 0: all checks passed
# Exit 1: one or more checks failed

BASE_URL=${1:-https://api.fitvision.io}
DASHBOARD_URL=${2:-https://app.fitvision.io}
WIDGET_CDN=${3:-https://cdn.fitvision.io/widget/fitvision-widget.min.js}

PASS=0
FAIL=0

check() {
  local name=$1
  local result=$2
  local expected=$3
  if echo "$result" | grep -q "$expected"; then
    echo "✅ $name"
    PASS=$((PASS + 1))
  else
    echo "❌ $name (expected: $expected, got: $result)"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== FitVision Smoke Test ==="
echo "Backend: $BASE_URL"
echo "Dashboard: $DASHBOARD_URL"
echo ""

# 1. Backend health
HEALTH=$(curl -sf "$BASE_URL/actuator/health" | jq -r '.status' 2>/dev/null)
check "Backend health" "$HEALTH" "UP"

# 2. Widget CDN
WIDGET_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$WIDGET_CDN")
check "Widget CDN accessible" "$WIDGET_STATUS" "200"

# 3. Auth endpoint responds
AUTH_STATUS=$(curl -o /dev/null -sw "%{http_code}" -X POST "$BASE_URL/api/dashboard/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"wrong"}')
check "Auth endpoint responds" "$AUTH_STATUS" "401"

# 4. Widget API CORS headers present
CORS=$(curl -sf -I -X OPTIONS "$BASE_URL/api/widget/v1/size-recommendation" \
  -H "Origin: https://test.myshopify.com" | grep -i "access-control-allow-origin")
check "Widget CORS headers" "$CORS" "access-control"

# 5. Swagger accessible
SWAGGER_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$BASE_URL/swagger-ui.html")
check "Swagger accessible" "$SWAGGER_STATUS" "200"

# 6. Admin seed endpoint responds (should be 409 — admin exists)
SEED_STATUS=$(curl -o /dev/null -sw "%{http_code}" -X POST "$BASE_URL/api/admin/seed" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@test.com","password":"test","name":"Smoke"}')
check "Admin seed returns 409 (admin exists)" "$SEED_STATUS" "409"

# 7. Dashboard loads (HTML response)
DASHBOARD_STATUS=$(curl -o /dev/null -sw "%{http_code}" "$DASHBOARD_URL/login")
check "Dashboard login page" "$DASHBOARD_STATUS" "200"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="

if [ $FAIL -gt 0 ]; then
  exit 1
fi
exit 0
```

**GitHub Actions — smoke test job** (add to backend.yml and dashboard.yml)

```yaml
smoke-test:
  needs: deploy
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Wait for deployment
      run: sleep 30
    - name: Run smoke tests
      run: |
        chmod +x scripts/smoke-test.sh
        ./scripts/smoke-test.sh \
          https://api.fitvision.io \
          https://app.fitvision.io \
          https://cdn.fitvision.io/widget/fitvision-widget.min.js
```

**scripts/smoke-test-local.sh** — same checks but against localhost

```bash
#!/bin/bash
# Local smoke test — verifies dev environment is running correctly
./scripts/smoke-test.sh \
  http://localhost:8080 \
  http://localhost:3000 \
  http://localhost:5173/fitvision-widget.min.js
```

### CONSTRAINTS
- Smoke tests must be read-only — never create or modify data in production
- Must complete in under 60 seconds
- Exit code 1 on any failure — CI fails and alerts the team
- jq required for JSON parsing (add to CI runner install step if missing)
- Never include real credentials in the script

### EXPECTED OUTPUT
- scripts/smoke-test.sh
- scripts/smoke-test-local.sh
- Updated .github/workflows/backend.yml (smoke-test job)
- Updated .github/workflows/dashboard.yml (smoke-test job)

### PHASE T3 COMPLETION CHECKLIST
- [ ] ./scripts/smoke-test.sh https://api.fitvision.io exits 0
- [ ] All 7 checks pass against production
- [ ] smoke-test-local.sh exits 0 against localhost
- [ ] GitHub Actions smoke test job runs after every deploy to main
- [ ] CI fails and shows which check failed when a service is down
