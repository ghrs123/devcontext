# 09 — Área Admin

[← Dashboard store](./08-dashboard-store.md) | [Índice](./README.md) | [Próximo: Shopify →](./10-shopify-app.md)

---

## Visão geral

**Status:** Implementado  
Área operacional FitVision para utilizadores com JWT `role=ADMIN`. UI distinta (sidebar escura) separada da experiência store owner.

**Bootstrap admin:** `POST /api/admin/seed` ou `./scripts/create-admin.sh` — **nunca** via `/auth/register`.

---

## Rotas admin

| Rota | Ficheiro | Função |
|------|----------|--------|
| `/admin/dashboard` | `(admin)/admin/dashboard/page.tsx` | KPIs plataforma |
| `/admin/stores` | `(admin)/admin/stores/page.tsx` | Gestão lojas |
| `/admin/brands` | `(admin)/admin/brands/page.tsx` | Marcas globais + scrape |
| `/admin/recommendations` | `(admin)/admin/recommendations/page.tsx` | Log cross-tenant |
| `/admin/health` | `(admin)/admin/health/page.tsx` | Saúde sistema (Phase 12) |

**Layout:** `(admin)/layout.tsx` — `AdminSidebar`, header mobile

---

## Protecção de acesso

1. **middleware.ts** — non-ADMIN em `/admin/*` → `/login`; ADMIN em rotas store → `/admin/dashboard`
2. **Backend** — `AdminAuthFilter` exige claim `role=ADMIN`; else 403
3. **Client guard** — `hooks/useAdminGuard.ts` valida via `GET /api/admin/v1/metrics`

---

## Admin Sidebar

**Ficheiro:** `components/admin/AdminSidebar.tsx`

Navegação:
- Platform Overview
- Stores
- Global Brands
- Recommendations
- System Health

Tema: slate escuro, botão logout

---

## Platform Overview (`/admin/dashboard`)

**Componentes:**
- `PlatformMetrics.tsx` — métricas agregadas
- Tabela recomendações recentes

**API:** `GET /api/admin/v1/metrics`

---

## Stores (`/admin/stores`)

**Componente:** `StoreTable.tsx`

Funcionalidades:
- Paginação, filtro status, pesquisa
- Detalhe loja: plano, subscription, recomendações
- PATCH status e plano via API admin

**APIs:**
- `GET /api/admin/v1/stores?page&size&status&search`
- `GET /api/admin/v1/stores/{id}`
- `PATCH .../status`, `PATCH .../plan`

---

## Global Brands (`/admin/brands`)

**Componente:** `GlobalBrandManager.tsx`

- CRUD marcas (`tenant_id = null`)
- Upload size charts por marca
- Trigger scrape individual
- Histórico jobs — `ScrapeHistoryDrawer`, `ScrapeStatusBadge`

**Scrapers disponíveis:** zara, hm, mango, pull-and-bear

**Batch:** botão trigger-all → `POST /api/admin/v1/scrape-jobs/trigger-all`

---

## Recommendations (`/admin/recommendations`)

Log filtrável:
- `tenantId`, `productId`, `quality`
- Paginação Spring `page`, `size`

**API:** `GET /api/admin/v1/recommendations`

---

## System Health (`/admin/health`) — Phase 12

**Status:** Implementado

**Página:** `app/(admin)/admin/health/page.tsx`

**Componentes:**

| Componente | Dados |
|------------|-------|
| `SystemHealthCards.tsx` | DB status/latency, última recomendação, lojas activas 24h, scrapes falhados 7d |
| `RecommendationStatsPanel.tsx` | p50/p95 `duration_ms`, volume |
| `ScrapePipelineStatus.tsx` | Estado por marca, acções scrape |

**API:** `GET /api/admin/v1/health` → `AdminHealthResponse`  
**Backend:** `domain/admin/AdminHealthService.java`

```mermaid
flowchart LR
    UI[/admin/health] --> API[GET /api/admin/v1/health]
    API --> AHS[AdminHealthService]
    AHS --> DB[(SELECT 1 latency)]
    AHS --> REC[recommendation_requests stats]
    AHS --> SCR[scrape_jobs counts]
```

---

## APIs admin complementares

| Endpoint | Uso |
|----------|-----|
| `GET /recommendations/stats` | Painel stats motor |
| `GET /scrape-jobs?status=` | Lista jobs global |
| `GET /brands/{id}/scrape-jobs` | Histórico por marca |

---

## Divergências

| Item | Estado |
|------|--------|
| RBAC granular (roles intermédios) | **Não implementado** — só ADMIN |
| Audit log acções admin | **Não encontrado** |
| 2FA admin | **Não implementado** |
| Impersonate store | **Não implementado** |
