# 04 — Fluxos da Aplicação

[← Arquitetura](./03-arquitetura-tecnica.md) | [Índice](./README.md) | [Próximo: Modelo de dados →](./05-modelo-de-dados.md)

---

## 1. Autenticação store (JWT)

```mermaid
sequenceDiagram
    participant U as Utilizador
    participant D as Dashboard
    participant API as Backend
    participant DB as PostgreSQL

    U->>D: POST /login (email, password)
    D->>API: POST /api/dashboard/v1/auth/login
    API->>DB: findByEmail + BCrypt verify
    API-->>D: AuthResponse { token, storeId, role }
    D->>D: saveToken(localStorage + cookie fitvision_token)
    D->>U: Redirect /dashboard ou /admin/dashboard
    Note over D: middleware.ts valida cookie em navegação
```

**Ficheiros:** `StoreAuthController`, `JwtService`, `dashboard/lib/auth.ts`, `middleware.ts`

---

## 2. Recomendação via widget

```mermaid
sequenceDiagram
    participant B as Comprador
    participant W as Widget JS
    participant F as ApiKeyAuthFilter
    participant E as RecommendationEngine
    participant DB as PostgreSQL

    B->>W: Preenche altura/peso/género
    W->>F: POST /api/widget/v1/size-recommendation<br/>X-FitVision-Key
    F->>F: Resolve store, TenantContext.set()
    F->>E: recommend(input)
    E->>E: PlanLimitsService.check
    E->>E: BodyProfileCalculator
    E->>DB: Product + SizeChart + Entries
    E->>E: SizeChartMatcher.match
    E->>DB: INSERT recommendation_requests
    E-->>W: SizeRecommendationResponse
    W-->>B: Exibe tamanho + mensagem
```

**GDPR:** Widget envia `storeBodyData: false` por defeito (`widget/src/main.js`).

**Limite de plano:** `PlanLimitException` capturada no controller → HTTP 200 + `planLimitFallback()`.

---

## 3. Upload de tabela de tamanhos

```mermaid
flowchart LR
    A[Store owner] --> B[Dashboard SizeChartUpload]
    B --> C{Modo}
    C -->|Ficheiro| D[POST multipart upload]
    C -->|Manual| E[POST JSON manual]
    D --> F[SizeChartService]
    E --> F
    F --> G[Parser CSV/Excel]
    F --> H[Desactiva chart anterior]
    F --> I[Persiste size_charts + size_entries]
```

**Limite ficheiro:** 2 MB. Erros: `UNSUPPORTED_FILE_FORMAT`, `SIZE_CHART_PARSE_ERROR`.

---

## 4. Billing Stripe

```mermaid
sequenceDiagram
    participant S as Store owner
    participant D as Dashboard
    participant API as BillingController
    participant ST as Stripe
    participant WH as StripeWebhookController

    S->>D: Clica Upgrade
    D->>API: POST /billing/checkout { plan: "PRO" }
    API->>ST: createCheckoutSession
    ST-->>S: Redirect Stripe Checkout
    S->>ST: Pagamento
    ST->>WH: customer.subscription.updated
    WH->>WH: Actualiza stores.plan, subscription_status
    S->>D: Redirect settings?billing=success
```

**Pendente:** URLs success/cancel hardcoded `http://localhost:3000` — ver [14-configuracao-env.md](./14-configuracao-env.md).

---

## 5. Scraping de marcas globais

```mermaid
sequenceDiagram
    participant Sch as ScrapeScheduler
    participant SS as ScraperService
    participant PW as Playwright
    participant DB as PostgreSQL

    Sch->>SS: Weekly MON 02:00
    SS->>DB: Marcas globais não scraped 30d
    SS->>SS: create ScrapeJob PENDING
    SS->>PW: BrandScraper.scrape(brand)
    PW-->>SS: ScrapePayload entries
    SS->>DB: uploadManual + update last_scraped_at
    SS->>DB: Job COMPLETED/FAILED
```

**Admin manual:** `POST /api/admin/v1/brands/{id}/scrape` ou `trigger-all`.

---

## 6. OAuth Shopify

```mermaid
sequenceDiagram
    participant M as Merchant
    participant SA as shopify-app
    participant SH as Shopify
    participant API as FitVision API

    M->>SA: GET /auth?shop=...
    SA->>SH: OAuth consent
    SH->>SA: GET /auth/callback + code
    SA->>API: POST /api/shopify/connect<br/>X-FitVision-Shopify-Secret
    API-->>SA: jwt + apiKeyPublic + storeId
    SA->>SA: registerWebhooks, injectWidget, syncAllProducts
    SA->>M: Redirect /app
```

---

## 7. Admin health check

```mermaid
flowchart TB
    A[Admin /admin/health] --> B[GET /api/admin/v1/health]
    B --> C[AdminHealthService]
    C --> D[DB latency SELECT 1]
    C --> E[Rec p50/p95 duration_ms]
    C --> F[Scrape jobs failed 7d]
    C --> G[Active stores 24h]
    C --> H[Brand scrape status]
```

**UI:** `SystemHealthCards`, `RecommendationStatsPanel`, `ScrapePipelineStatus`

---

## 8. Registo de loja

1. `POST /auth/register` — cria `Store` FREE/ACTIVE, gera API keys
2. Login → JWT 24h (configurável)
3. Settings → copiar snippet widget com `api_key_public`

---

## Divergências

| Fluxo | Nota |
|-------|------|
| Refresh token | **Não implementado** — re-login após expiração JWT |
| Webhook genérico FitVision | `WebhookController` vazio |
| 2FA admin | **Não encontrado no código atual** |
