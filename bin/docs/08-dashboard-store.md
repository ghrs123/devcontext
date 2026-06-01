# 08 — Dashboard (Loja)

[← Widget](./07-widget-integration.md) | [Índice](./README.md) | [Próximo: Admin →](./09-admin-area.md)

---

## Visão geral

**Stack:** Next.js 14 App Router, TypeScript, Tailwind, SWR  
**Diretório:** `dashboard/`  
**Status:** Implementado

---

## Rotas store owner

| Rota | Ficheiro | Função |
|------|----------|--------|
| `/` | `app/page.tsx` | Redirect → `/login` |
| `/login` | `(auth)/login/page.tsx` | Login |
| `/register` | `(auth)/register/page.tsx` | Registo loja |
| `/dashboard` | `(app)/dashboard/page.tsx` | Analytics resumo |
| `/products` | `(app)/products/page.tsx` | CRUD produtos + size charts |
| `/settings` | `(app)/settings/page.tsx` | Perfil, API keys, billing, snippet widget |

**Layout store:** `(app)/layout.tsx` — `Sidebar`, `TopBar`, SWR profile

---

## Autenticação e middleware

```mermaid
flowchart TD
    A[Request] --> B{Cookie fitvision_token?}
    B -->|Não| C[/login]
    B -->|Sim| D{Decode role JWT}
    D -->|ADMIN em /dashboard| E[/admin/dashboard]
    D -->|STORE| F[Permite rota app]
    D -->|Non-ADMIN em /admin| C
```

**Ficheiros:**
- `middleware.ts` — matcher: `/dashboard/*`, `/products/*`, `/settings/*`, `/admin/*`, auth routes
- `lib/auth.ts` — `fitvision_access_token` (localStorage) + cookie `fitvision_token`
- `lib/jwt.ts` — decode payload **sem verificar assinatura** (middleware client-side)

**Nota de segurança:** Verificação real de JWT ocorre no **backend**; middleware é routing UX.

---

## Cliente API

**Ficheiro:** `lib/api.ts`

- Base: `NEXT_PUBLIC_API_URL` || `http://localhost:8080`
- 401 → clear token + redirect login
- Envelope `ApiResponse` parseado em `parseEnvelope()`

Principais métodos: `register`, `login`, `getProfile`, `listProducts`, `createProduct`, `uploadSizeChart`, `getAnalyticsSummary`, `getBillingStatus`, `createCheckoutSession`, `createPortalSession`

---

## Página Dashboard (`/dashboard`)

**Componentes:**
- `StatCard` — totais recomendações
- `QualityChart` — distribuição EXACT/PARTIAL/CLOSEST/NO_MATCH (Recharts)
- `TopProductsTable` — produtos mais recomendados

**Data:** SWR → `GET /api/dashboard/v1/analytics/summary`

---

## Página Products (`/products`)

**Funcionalidades:**
- Listar/criar/editar/apagar produtos
- Associar marca (tenant ou global)
- Upload CSV/XLSX ou entrada manual de size chart
- Tratamento **402** `PLAN_LIMIT_REACHED` ao criar produto

**Componentes:** `ProductForm`, `SizeChartUpload`, `SizeChartTable`

---

## Página Settings (`/settings`)

Secções:
1. **Perfil** — nome, platform, flag `storeBodyData` (consentimento GDPR analytics)
2. **API Keys** — visualizar/regenerar public key
3. **Billing** — `BillingSection`, `PlanComparisonTable`, `UsageBar`
4. **Widget embed** — snippet HTML com CDN e keys

**Billing hook:** `hooks/useBilling.ts` — SWR refresh 60s

---

## Billing UI

| Componente | Ficheiro |
|------------|----------|
| BillingSection | `components/dashboard/BillingSection.tsx` |
| PlanComparisonTable | `components/dashboard/PlanComparisonTable.tsx` |
| UsageBar | `components/dashboard/UsageBar.tsx` |

Fluxo upgrade → `POST /billing/checkout` → redirect Stripe URL

---

## Sidebar store

**Ficheiro:** `components/app/Sidebar.tsx`

Links: Dashboard, Products, Settings — drawer mobile

---

## Scripts npm

```bash
cd dashboard
npm install
npm run dev      # :3000
npm run build
npm run start
npm run lint
```

---

## Docker

`dashboard/Dockerfile` — multi-stage, `node server.js`, ARG `NEXT_PUBLIC_API_URL`

Incluído em `docker-compose.yml` serviço `fitvision-dashboard` porta 3000.

---

## Divergências

| Item | Estado |
|------|--------|
| Testes E2E dashboard | **Não encontrados** |
| CI lint/test dashboard | Workflow só `npm run build` |
| i18n PT completo | Parcial (widget tem locale; dashboard EN) |
| Refresh token automático | **Não implementado** |
