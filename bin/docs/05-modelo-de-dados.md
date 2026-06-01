# 05 — Modelo de Dados

[← Fluxos](./04-fluxos-da-aplicacao.md) | [Índice](./README.md) | [Próximo: API →](./06-api-reference.md)

---

## ERD (estado V10)

```mermaid
erDiagram
    stores ||--o{ products : "tenant_id"
    stores ||--o{ brands : "tenant_id nullable"
    stores ||--o{ recommendation_requests : "tenant_id"
    brands ||--o{ products : "brand_id"
    brands ||--o{ scrape_jobs : "brand_id"
    products ||--o{ size_charts : "product_id"
    products ||--o{ recommendation_requests : "product_id"
    size_charts ||--o{ size_entries : "size_chart_id"

    stores {
        uuid id PK
        varchar email UK
        varchar api_key_public UK
        varchar plan
        varchar status
        varchar role
        varchar stripe_customer_id UK
        int recommendations_count_current_month
    }

    brands {
        uuid id PK
        uuid tenant_id FK "NULL=global"
        varchar slug UK
        timestamp deleted_at
    }

    products {
        uuid id PK
        uuid tenant_id FK
        uuid brand_id FK "nullable V5"
        varchar external_product_id
        timestamp deleted_at
    }

    size_charts {
        uuid id PK
        uuid product_id FK
        boolean active
        varchar scrape_source_url
    }

    size_entries {
        uuid id PK
        uuid size_chart_id FK
        varchar size_label
        decimal chest_min chest_max
    }

    recommendation_requests {
        uuid id PK
        decimal height_cm weight_kg
        boolean body_measurements_stored
        int duration_ms "V10"
    }

    scrape_jobs {
        uuid id PK
        uuid brand_id FK
        varchar status
    }
```

---

## Migrações Flyway

Localização: `src/main/resources/db/migration/`

| Versão | Ficheiro | Alterações |
|--------|----------|------------|
| V1 | `V1__init_schema.sql` | Schema inicial: 6 tabelas + índices |
| V2 | `V2__add_store_password.sql` | `stores.password_hash` |
| V3 | `V3__add_product_soft_delete.sql` | `products.deleted_at` |
| V4 | `V4__add_admin_role.sql` | `stores.role` DEFAULT `'STORE'` |
| V5 | `V5__make_product_brand_optional.sql` | `products.brand_id` nullable |
| V6 | `V6__add_brand_soft_delete.sql` | `brands.deleted_at` |
| V7 | `V7__add_shopify_fields.sql` | `shopify_shop`, `shopify_access_token_encrypted` |
| V8 | `V8__add_scrape_jobs.sql` | `scrape_jobs`, `size_charts.scrape_source_url` |
| V9 | `V9__add_billing_fields.sql` | Campos Stripe + contadores mensais |
| V10 | `V10__add_recommendation_duration_ms.sql` | `recommendation_requests.duration_ms` |

**Próxima migração esperada:** V11 (não existe no código actual)

---

## Entidades JPA

Relacionamentos **lógicos** via UUID — sem `@ManyToOne` explícito.

### `Store` — `domain/store/Store.java`

| Campo | Tipo | Notas |
|-------|------|-------|
| id | UUID | PK |
| name, email | String | email unique |
| plan | String | FREE, STARTER, PRO, TEAM |
| status | String | ACTIVE, etc. |
| apiKeyPublic, apiKeySecret | String | public unique |
| passwordHash | String | BCrypt |
| platform | String | SHOPIFY, MANUAL, etc. |
| role | StoreRole | STORE / ADMIN |
| shopifyShop | String | unique |
| shopifyAccessTokenEncrypted | String | AES-256-GCM |
| stripeCustomerId, stripeSubscriptionId, stripePriceId | String | |
| subscriptionStatus, subscriptionCurrentPeriodEnd | | |
| recommendationsCountCurrentMonth, recommendationsCountResetAt | | billing V9 |

### `Product` — `domain/product/Product.java`

Unique: `(tenantId, externalProductId)`. Soft delete via `deletedAt`.

### `Brand` — `domain/brand/Brand.java`

`tenantId == null` → marca global FitVision. Soft delete.

### `SizeChart` / `SizeEntry`

Uma chart activa por produto (aplicação). Entries com dimensões nullable.

### `RecommendationRequest`

Analytics imutável (FK RESTRICT). `bodyMeasurementsStored` flag GDPR. `durationMs` desde V10.

### `ScrapeJob`

Status: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` (`ScrapeJobStatus`)

---

## Índices relevantes

```sql
-- V1
idx_stores_api_key_public
idx_products_tenant_id
idx_rec_requests_tenant_id
idx_rec_requests_product_id

-- V4
idx_stores_role

-- V7
idx_stores_shopify_shop

-- V8
idx_scrape_jobs_brand_created

-- V9
idx_stores_stripe_customer_id
idx_stores_stripe_subscription_id
```

---

## Invariantes de dados

| Invariante | Onde |
|------------|------|
| Um size chart activo por produto | `SizeChartService` |
| Labels uppercase | `SizeEntry` lifecycle |
| Tenant isolation | Todos os repos tenant-scoped |
| Admin store para template scrape | `ScraperService` produto `__global_brand__{brandId}` |

---

## Divergências

| Item | Spec | Código |
|------|------|--------|
| Constraint DB "one active chart" | Possível na spec | **Só camada aplicação** |
| Tabela audit_log | Roadmap | **Não encontrado** |
| Encriptação API keys at rest | Roadmap | Keys em texto na BD |
