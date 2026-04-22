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
