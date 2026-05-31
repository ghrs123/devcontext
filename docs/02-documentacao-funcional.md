# 02 — Documentação Funcional

[← Visão geral](./01-visao-geral.md) | [Índice](./README.md) | [Próximo: Arquitetura →](./03-arquitetura-tecnica.md)

---

## Casos de uso principais

### UC-01 — Registo e login de loja

**Ator:** Store owner  
**Fluxo:** `POST /api/dashboard/v1/auth/register` → JWT → dashboard  
**Regras:**
- Email único (`STORE_ALREADY_EXISTS` → 409)
- Password com BCrypt strength 12
- Plano inicial FREE, status ACTIVE
- Geração automática de `api_key_public` e `api_key_secret`

**Status:** Implementado — `StoreAuthController`, `StoreRegistrationRequest`

---

### UC-02 — Gestão de produtos

**Ator:** Store owner  
**Endpoints:** CRUD em `/api/dashboard/v1/products`  
**Regras:**
- Produto identificado por `(tenant_id, external_product_id)` único
- Soft delete (`deleted_at` — V3)
- `brand_id` opcional desde V5
- Limite de produtos por plano (`PlanLimitsService.checkProductLimit`) → 402 `PLAN_LIMIT_REACHED`

**Status:** Implementado

---

### UC-03 — Tabela de tamanhos

**Ator:** Store owner  
**Formas de entrada:**
1. Upload multipart CSV/XLSX — `POST .../size-charts/{productId}/upload`
2. JSON manual — `POST .../size-charts/{productId}/manual`

**Regras:**
- Apenas **uma** tabela ativa por produto (camada de aplicação)
- Labels normalizados para UPPERCASE (`SizeEntry` `@PreUpdate`)
- Ficheiro máx. 2 MB (`application.yml`)
- Formatos: CSV, Excel (parsers em `infrastructure/file/`)

**Status:** Implementado

---

### UC-04 — Recomendação de tamanho (widget)

**Ator:** Comprador  
**Endpoint:** `POST /api/widget/v1/size-recommendation`  
**Entrada:** altura, peso, género opcional, `externalProductId`, `storeBodyData` (GDPR)

**Motor (`RecommendationEngine`):**
1. Verifica limite mensal do plano
2. `BodyProfileCalculator` — BMI, body fat Deurenberg, estimativas
3. Resolve produto por `externalProductId` + tenant da API key
4. Carrega tabela ativa + entries
5. `SizeChartMatcher` — score por dimensões disponíveis
6. Persiste analytics (medidas zeradas se `storeBodyData=false`)
7. Incrementa contador mensal

**Qualidades de match:** `EXACT`, `PARTIAL`, `CLOSEST`, `NO_MATCH`

**Status:** Implementado

---

### UC-05 — Analytics da loja

**Ator:** Store owner  
**Endpoints:**
- `GET /api/dashboard/v1/analytics/summary`
- `GET /api/dashboard/v1/analytics/recommendations?page&size`

**Status:** Implementado

---

### UC-06 — Billing (Stripe)

**Ator:** Store owner  
**Fluxo:**
1. Ver status e uso — `GET /billing/status`
2. Checkout — `POST /billing/checkout` (plan STARTER/PRO/TEAM ou priceId)
3. Portal — `POST /billing/portal`
4. Webhooks Stripe atualizam `stores.plan`, `subscription_status`, etc.

**Regra especial:** Limite de recomendações no widget **não** expõe 402 ao comprador — retorna fallback amigável (`SizeRecommendationResponse.planLimitFallback()`)

**Status:** Implementado (URLs de redirect ainda localhost — ver divergências)

---

### UC-07 — Admin: marcas globais e scraping

**Ator:** Admin (`role=ADMIN`)  
**Funcionalidades:**
- CRUD marcas com `tenant_id = null` (globais)
- Upload tabelas por marca
- Trigger scrape manual ou batch (`POST /scrape-jobs/trigger-all`)
- Scheduler semanal segunda 02:00 (`ScrapeScheduler` cron `0 0 2 * * MON`)

**Scrapers registados:** `zara`, `hm`, `mango`, `pull-and-bear`

**Status:** Implementado

---

### UC-08 — Integração Shopify

**Ator:** Merchant Shopify  
**Fluxo:** OAuth app → `POST /api/shopify/connect` → sync produtos → inject ScriptTag widget CDN

**Status:** Implementado (`shopify-app/`)

---

### UC-09 — Seed admin (bootstrap)

**Endpoint:** `POST /api/admin/seed` (sem auth)  
**Regra:** 409 se admin já existir — **nunca** via register público

**Status:** Implementado — script `scripts/create-admin.sh`

---

## Regras de negócio transversais

| Regra | Implementação |
|-------|---------------|
| Isolamento multi-tenant | `TenantContext` ThreadLocal; queries com `tenantId` |
| Marcas globais | `brands.tenant_id IS NULL` |
| GDPR medidas | `storeBodyData=false` → height/weight = 0 na persistência |
| Admin não acede UI store | `middleware.ts` redireciona ADMIN para `/admin/dashboard` |
| Widget sempre HTTP 200 | Erros de plano → fallback no body, não 402 |

---

## Matriz funcional por módulo

| Funcionalidade | Backend | Dashboard | Widget | Shopify |
|----------------|---------|-----------|--------|---------|
| Recomendação | ✅ | — | ✅ | — |
| Produtos | ✅ | ✅ | — | ✅ sync |
| Size charts | ✅ | ✅ | — | — |
| Analytics | ✅ | ✅ | — | — |
| Billing | ✅ | ✅ | — | — |
| Admin ops | ✅ | ✅ | — | — |
| Scraping | ✅ | ✅ (UI) | — | — |

---

## Divergências

| Item | Spec / expectativa | Código |
|------|-------------------|--------|
| WooCommerce widget | Mencionado no DTO | Sem integração dedicada além de `externalProductId` genérico |
| Rate limiting API | Possível na spec | **Não encontrado no código atual** |
| Email transacional | Roadmap | **Não implementado** |
| i18n dashboard | Parcial widget (`en`/`pt`) | Dashboard majoritariamente inglês |
