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
Phase 0 — Project not yet started. Context and roadmap defined.

## What Has Been Built
Nothing yet.

## Current Phase
Phase 1 — Foundation (next to execute)

## Roadmap

### Phase 1 — Foundation
- Spring Boot project setup
- PostgreSQL schema with Flyway migrations
- Core domain entities (Store, Brand, Product, SizeChart, SizeEntry)
- Base repository layer with tenant isolation pattern
- API response envelope and global exception handler
- Spring Security with API key filter (widget) and JWT filter (dashboard)
- Health check endpoint

### Phase 2 — Recommendation Engine
- BodyProfile computation (BMI, Deurenberg, YMCA formulas)
- SizeChart matching algorithm
- Confidence score calculation
- RecommendationEngine service (stateless)
- Unit tests for all formula methods

### Phase 3 — Widget API
- POST /api/widget/v1/size-recommendation endpoint
- API key validation
- RecommendationRequest persistence (with consent flag)
- Graceful fallback when no size chart exists
- CORS configuration for cross-origin widget calls

### Phase 4 — Size Chart Management
- CSV upload and parsing (OpenCSV)
- Excel upload and parsing (Apache POI)
- Size chart versioning and activation
- Manual size entry via API
- Validation rules for size data

### Phase 5 — Store Dashboard API
- Store registration and authentication (JWT)
- Brand and product management endpoints
- Size chart management endpoints
- Analytics endpoints (recommendation counts, confidence distribution)
- Stripe subscription integration

### Phase 6 — Embeddable Widget (Frontend)
- Vanilla JS widget build (Vite)
- Body measurement input form
- Recommendation display with confidence indicator
- Fallback state (no size chart available)
- CDN deployment via Cloudflare

### Phase 7 — Store Dashboard (Frontend)
- Next.js dashboard
- Store onboarding flow
- Product and size chart management UI
- Analytics dashboard
- Stripe billing UI

### Phase 8 — Shopify App
- Shopify App setup
- Automatic product sync
- One-click widget installation
- App Store listing

### Phase 9 — Scraping Pipeline
- Playwright scraper for major brands
- Scheduled re-scrape every 30 days
- Scrape monitoring and alerting
- Manual override for scraped data

## Decisions Pending
- Whether to use Keycloak or custom JWT for dashboard auth
- Pricing tiers (per recommendation vs flat monthly)
- Which brands to include in the initial FitVision-managed database
# FitVision — Phase 1 Prompts

---

## Prompt 1.1 — Spring Boot Project Setup

### CONTEXT
We are building FitVision, a multi-tenant SaaS that provides size recommendation widgets for online clothing stores. The backend is Spring Boot 3.x with Java 21. The system has two API surfaces: a public widget API (API key auth) and a store dashboard API (JWT auth). All data is tenant-scoped.

Stack: Java 21, Spring Boot 3.x, PostgreSQL, Flyway, Maven, Spring Security, Spring Data JPA.

### OBJECTIVE
Generate the complete Maven project structure for the FitVision backend with the following:
- pom.xml with all required dependencies (Spring Boot Web, Data JPA, Security, Flyway, PostgreSQL driver, Validation, Lombok)
- Main application class
- Application properties for dev profile (database connection to localhost PostgreSQL, Flyway enabled, JPA DDL auto = validate)
- Package structure exactly as defined below

### PACKAGE STRUCTURE
```
com.fitvision
├── api
│   ├── widget
│   ├── dashboard
│   └── webhook
├── domain
│   ├── store
│   ├── brand
│   ├── product
│   ├── sizechart
│   └── recommendation
├── engine
│   └── recommendation
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

### CONSTRAINTS
- Java 21
- Spring Boot 3.x (latest stable)
- No Spring Boot DevTools in production profile
- Lombok for boilerplate reduction
- No test dependencies beyond Spring Boot Test and JUnit 5

### EXPECTED OUTPUT
- Complete pom.xml
- FitVisionApplication.java
- application.yml (dev profile)
- One placeholder class per package to establish structure (can be empty with a comment)

### NEXT STEP
Prompt 1.2 will create the database schema via Flyway migrations. The application.yml from this prompt will be used directly.

---

## Prompt 1.2 — Database Schema (Flyway Migrations)

### CONTEXT
FitVision backend is set up with Spring Boot 3.x, Java 21, PostgreSQL, and Flyway. The project structure and pom.xml are complete.

The system is multi-tenant. Every tenant-scoped table has a tenant_id column. Recommendations are stored for analytics with a GDPR consent flag.

### OBJECTIVE
Create Flyway migration file V1__init_schema.sql with the complete initial database schema.

### ENTITIES TO CREATE

**stores** — tenant table
- id (UUID, PK), name, email, plan (VARCHAR), status (VARCHAR)
- api_key_public (VARCHAR UNIQUE), api_key_secret (VARCHAR)
- platform (VARCHAR — shopify/woocommerce/other)
- subscription_status (VARCHAR), created_at, updated_at

**brands**
- id (UUID, PK), tenant_id (UUID, nullable — null means FitVision-managed global brand)
- name (VARCHAR), slug (VARCHAR UNIQUE), source (VARCHAR — fitvision_managed/store_uploaded)
- last_scraped_at (TIMESTAMP, nullable), created_at

**products**
- id (UUID, PK), brand_id (UUID FK), tenant_id (UUID FK → stores)
- external_product_id (VARCHAR — Shopify product ID etc.)
- name (VARCHAR), category (VARCHAR), gender_target (VARCHAR)
- created_at, updated_at
- UNIQUE(tenant_id, external_product_id)

**size_charts**
- id (UUID, PK), product_id (UUID FK), version (INTEGER)
- source (VARCHAR — uploaded/scraped/manual), active (BOOLEAN DEFAULT false)
- created_at

**size_entries**
- id (UUID, PK), size_chart_id (UUID FK)
- size_label (VARCHAR — S/M/L/XL or numeric)
- chest_min, chest_max (DECIMAL 5,1, nullable)
- waist_min, waist_max (DECIMAL 5,1, nullable)
- hip_min, hip_max (DECIMAL 5,1, nullable)
- height_min, height_max (DECIMAL 5,1, nullable)

**recommendation_requests**
- id (UUID, PK), tenant_id (UUID FK), product_id (UUID FK)
- height_cm (DECIMAL 5,1), weight_kg (DECIMAL 5,1)
- gender (VARCHAR, nullable), age (INTEGER, nullable)
- recommended_size (VARCHAR), confidence_score (DECIMAL 3,2)
- body_measurements_stored (BOOLEAN DEFAULT false)
- created_at

### CONSTRAINTS
- All PKs are UUID generated by the application (not serial/auto-increment)
- All timestamps default to NOW()
- Add indexes on: stores.api_key_public, products.tenant_id, recommendation_requests.tenant_id, recommendation_requests.product_id
- File location: src/main/resources/db/migration/V1__init_schema.sql

### EXPECTED OUTPUT
Complete V1__init_schema.sql ready to run via Flyway.

### NEXT STEP
Prompt 1.3 will create the JPA entity classes mapped to this schema.

---

## Prompt 1.3 — JPA Entity Classes

### CONTEXT
FitVision backend with Spring Boot 3.x, Java 21. Database schema is created (V1__init_schema.sql). Tables: stores, brands, products, size_charts, size_entries, recommendation_requests. All PKs are UUID. Multi-tenant system — tenant_id on every tenant-scoped entity.

Lombok is available. Use @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor where appropriate.

### OBJECTIVE
Create all JPA entity classes mapped to the existing schema.

### CONSTRAINTS
- Package: com.fitvision.domain.{entityname}
- All entities use UUID as ID type
- No Lombok on entities that have JPA relationships — use explicit getters/setters to avoid Lombok/JPA conflicts
- Use @Column(name = "...") explicitly on every field — do not rely on naming convention
- No cascading deletes on recommendation_requests — analytical data must be preserved
- size_label must always be stored in uppercase — enforce via @PrePersist and @PreUpdate

### EXPECTED OUTPUT
- Store.java
- Brand.java
- Product.java
- SizeChart.java
- SizeEntry.java
- RecommendationRequest.java

Each in their respective domain package. All mapped to the exact schema from V1__init_schema.sql.

### NEXT STEP
Prompt 1.4 will create the repository interfaces and the base tenant-scoped query pattern.

---

## Prompt 1.4 — Repository Layer with Tenant Isolation

### CONTEXT
FitVision backend. JPA entities are complete: Store, Brand, Product, SizeChart, SizeEntry, RecommendationRequest. The system is multi-tenant — every query on tenant-scoped data must include tenant_id.

### OBJECTIVE
Create Spring Data JPA repository interfaces for all entities, enforcing the tenant isolation pattern.

### TENANT ISOLATION RULE
Every method that accesses tenant-scoped data (Product, SizeChart, SizeEntry, RecommendationRequest) MUST include tenantId as a parameter. There must be no method that retrieves tenant-scoped data without the tenantId filter.

### REPOSITORIES TO CREATE

**StoreRepository** (JpaRepository<Store, UUID>)
- findByApiKeyPublic(String apiKeyPublic): Optional<Store>
- findByEmail(String email): Optional<Store>

**BrandRepository** (JpaRepository<Brand, UUID>)
- findBySlug(String slug): Optional<Brand>
- findAllByTenantIdOrTenantIdIsNull(UUID tenantId): List<Brand> — returns store's brands + global FitVision brands
- findByIdAndTenantIdOrTenantIdIsNull(UUID id, UUID tenantId): Optional<Brand>

**ProductRepository** (JpaRepository<Product, UUID>)
- findByIdAndTenantId(UUID id, UUID tenantId): Optional<Product>
- findAllByTenantId(UUID tenantId): List<Product>
- findByExternalProductIdAndTenantId(String externalProductId, UUID tenantId): Optional<Product>

**SizeChartRepository** (JpaRepository<SizeChart, UUID>)
- findActiveByProductIdAndTenantId(UUID productId, UUID tenantId): Optional<SizeChart> — only active=true

**SizeEntryRepository** (JpaRepository<SizeEntry, UUID>)
- findAllBySizeChartId(UUID sizeChartId): List<SizeEntry>

**RecommendationRequestRepository** (JpaRepository<RecommendationRequest, UUID>)
- countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after): long
- findAllByTenantId(UUID tenantId, Pageable pageable): Page<RecommendationRequest>

### CONSTRAINTS
- Package: com.fitvision.infrastructure.persistence
- Use @Query with JPQL where Spring Data method naming is not sufficient
- No native SQL queries at this stage

### EXPECTED OUTPUT
All 6 repository interfaces in com.fitvision.infrastructure.persistence.

### NEXT STEP
Prompt 1.5 will create the API response envelope, base exception classes, and global exception handler.

---

## Prompt 1.5 — Shared Infrastructure (Response Envelope + Exception Handling)

### CONTEXT
FitVision backend. Project structure, schema, entities, and repositories are complete. Before building any API endpoint, we need the shared response envelope and exception handling that all endpoints will use.

### OBJECTIVE
Create the complete shared infrastructure for API responses and error handling.

### COMPONENTS TO CREATE

**1. ApiResponse<T> (generic response envelope)**
```json
{
  "success": true/false,
  "data": { } or null,
  "error": null or { "code": "...", "message": "...", "field": null },
  "meta": { "requestId": "uuid", "timestamp": "ISO-8601" }
}
```
- Static factory methods: ApiResponse.ok(T data), ApiResponse.error(ErrorCode code, String message)
- Package: com.fitvision.shared.response

**2. ErrorCode enum**
Initial values: SIZE_CHART_NOT_FOUND, PRODUCT_NOT_FOUND, STORE_NOT_FOUND, INVALID_API_KEY, INVALID_BODY_MEASUREMENTS, BRAND_NOT_FOUND, UNAUTHORIZED, VALIDATION_ERROR, INTERNAL_ERROR
- Package: com.fitvision.shared.exception

**3. FitVisionException (base runtime exception)**
- Fields: ErrorCode errorCode, String message
- Package: com.fitvision.shared.exception

**4. Specific exceptions extending FitVisionException**
- SizeChartNotFoundException
- ProductNotFoundException
- StoreNotFoundException
- InvalidApiKeyException
- InvalidBodyMeasurementException

**5. GlobalExceptionHandler (@RestControllerAdvice)**
- Handles FitVisionException → correct ErrorCode and HTTP status
- Handles MethodArgumentNotValidException → VALIDATION_ERROR with field name
- Handles generic Exception → INTERNAL_ERROR, never expose stack trace
- All responses use ApiResponse envelope
- Log all exceptions with request ID

**6. RequestIdFilter (OncePerRequestFilter)**
- Generates UUID request ID per request
- Stores in MDC for logging
- Adds X-Request-Id response header

### CONSTRAINTS
- Never expose stack traces in responses
- HTTP status mapping: NOT_FOUND exceptions → 404, UNAUTHORIZED → 401, VALIDATION → 400, INTERNAL → 500
- Package: com.fitvision.shared.*

### EXPECTED OUTPUT
All classes listed above, fully implemented and ready to use by any controller.

### NEXT STEP
Phase 1 is complete. Phase 2 will implement the RecommendationEngine — the body composition formulas and size chart matching logic. The shared infrastructure from this prompt will be used by all subsequent API endpoints.

### PHASE 1 COMPLETION CHECKLIST
Before moving to Phase 2, verify:
- [ ] Application starts without errors
- [ ] Flyway migrations run successfully on a local PostgreSQL database
- [ ] All entities are mapped correctly (spring.jpa.ddl-auto=validate passes)
- [ ] A simple test endpoint returns the ApiResponse envelope correctly
- [ ] GlobalExceptionHandler returns the correct format for a thrown FitVisionException

---

## Prompt 0 — Infraestrutura Local (pré-requisito)

> Executar ANTES do Prompt 1.1. Não requer IA — são comandos directos.

### OBJECTIVO
Subir o PostgreSQL localmente via Docker para que o Spring Boot consiga ligar e o Flyway possa executar as migrations.

### COMANDOS

**1. Subir o container PostgreSQL:**
```bash
docker run --name fitvision-db \
  -e POSTGRES_DB=fitvision \
  -e POSTGRES_USER=fitvision \
  -e POSTGRES_PASSWORD=fitvision \
  -p 5432:5432 \
  -d postgres:16
```

**2. Verificar que está a correr:**
```bash
docker ps
```
Deves ver `fitvision-db` com status `Up`.

**3. Para parar e retomar nas próximas sessões:**
```bash
# Parar
docker stop fitvision-db

# Retomar (não precisas de criar novamente)
docker start fitvision-db
```

### CHECKLIST
- [ ] `docker ps` mostra `fitvision-db` com status `Up`
- [ ] Porta 5432 disponível (não tens outro PostgreSQL a correr)
- [ ] Só depois disto arrancar o Spring Boot

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
