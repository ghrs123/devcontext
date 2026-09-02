# 14 — Configuração e Variáveis de Ambiente

[← Execução local](./13-execucao-local.md) | [Índice](./README.md) | [Próximo: Testes →](./15-testes.md)

---

## Ficheiros de referência

| Ficheiro | Scope |
|----------|-------|
| `.env.example` | Docker Compose raiz |
| `shopify-app/.env.example` | Shopify app |
| `dashboard/.env.production` | Build prod dashboard |
| `src/main/resources/application.yml` | Backend dev |
| `src/main/resources/application-prod.yml` | Backend prod |
| `src/test/resources/application-test.yml` | Testes |

---

## Raiz — `.env.example`

| Variável | Obrigatória | Descrição | Consumidor |
|----------|-------------|-----------|------------|
| `POSTGRES_DB` | Sim (compose) | Nome BD | fitvision-db |
| `POSTGRES_USER` | Sim | User Postgres | fitvision-db |
| `POSTGRES_PASSWORD` | Sim | Password Postgres | fitvision-db |
| `DB_URL` | Sim | JDBC URL | backend |
| `DB_USERNAME` | Sim | User JDBC | backend |
| `DB_PASSWORD` | Sim | Password JDBC | backend |
| `FITVISION_JWT_SECRET` | Sim prod | Secret JWT (≥32 bytes) | backend dev compose |
| `FITVISION_JWT_EXPIRATION_HOURS` | Não | Default 24 | backend compose |
| `NEXT_PUBLIC_API_URL` | Não | URL API dashboard | dashboard compose |
| `SHOPIFY_ENCRYPTION_KEY` | Sim prod | Base64 32 bytes AES | backend |
| `FITVISION_SHOPIFY_SHARED_SECRET` | Sim | Secret Shopify connect | backend + shopify-app |

---

## Backend — `application.yml` (profile `dev`)

| Propriedade | Env var | Default dev |
|-------------|---------|-------------|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/fitvision` |
| `spring.datasource.username` | `DB_USERNAME` | `fitvision` |
| `spring.datasource.password` | `DB_PASSWORD` | `fitvision` |
| `server.port` | — | `8080` |
| `fitvision.jwt.secret` | inline dev | string dev 32+ chars |
| `fitvision.jwt.expiration-hours` | — | `24` |
| `fitvision.shopify.encryption-key` | `SHOPIFY_ENCRYPTION_KEY` | placeholder base64 |
| `fitvision.shopify.shared-secret` | `FITVISION_SHOPIFY_SHARED_SECRET` | dev secret |
| `fitvision.dashboard.url` | `FITVISION_DASHBOARD_URL` | `http://localhost:3000` |
| `fitvision.admin.bootstrap-token` | `ADMIN_BOOTSTRAP_TOKEN` | vazio (desativado) |
| `stripe.secret-key` | `STRIPE_SECRET_KEY` | vazio |
| `stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | vazio |
| `stripe.prices.starter` | `STRIPE_PRICE_STARTER` | vazio |
| `stripe.prices.pro` | `STRIPE_PRICE_PRO` | vazio |
| `stripe.prices.team` | `STRIPE_PRICE_TEAM` | vazio |

**Multipart max:** 2MB

---

## Backend — `application-prod.yml`

| Propriedade | Env var | Notas |
|-------------|---------|-------|
| `spring.datasource.url` | `DATABASE_URL` \| `DB_URL` | Neon; `postgresql://`/`postgres://` convertido para `jdbc:` no arranque |
| `server.port` | `PORT` | Default 8080 |
| `fitvision.jwt.secret` | `JWT_SECRET` \| `FITVISION_JWT_SECRET` | ≥ 32 bytes; fail-fast no boot |
| `fitvision.shopify.encryption-key` | `SHOPIFY_ENCRYPTION_KEY` | Base64 → 32 bytes exatos; fail-fast no boot |
| `fitvision.shopify.shared-secret` | `SHOPIFY_SHARED_SECRET` \| `FITVISION_SHOPIFY_SHARED_SECRET` | tem de igualar o `shopify-app` |
| `fitvision.health.db.down-threshold-ms` | `DB_HEALTH_DOWN_MS` | default 2000; limiar do `/actuator/health` |
| `fitvision.health.db.slow-threshold-ms` | `DB_HEALTH_SLOW_MS` | default 100 |
| `fitvision.dashboard.url` | (valor fixo prod no ficheiro) | `https://app.fitvision.io` |
| `fitvision.admin.bootstrap-token` | `ADMIN_BOOTSTRAP_TOKEN` | obrigatório para usar `/api/admin/seed` |
| `stripe.secret-key` / `webhook-secret` | `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | sem default — obrigatório no boot |
| `stripe.prices.*` | `STRIPE_PRICE_STARTER` / `_PRO` / `_TEAM` | 3 valores não-vazios e **distintos** ou o contexto não arranca |
| `sentry.dsn` | `SENTRY_DSN` | chave obrigatória; valor vazio desliga o Sentry |
| `sentry.release` | `APP_VERSION` | |
| `logging.level.com.fitvision` | — | INFO |

---

## Dashboard

| Variável | Ficheiro | Valor prod |
|----------|----------|------------|
| `NEXT_PUBLIC_API_URL` | `.env.production` | `https://api.fitvision.io` |
| `NEXT_PUBLIC_WIDGET_CDN` | `.env.production` | `https://cdn.fitvision.io/widget/fitvision-widget.min.js` |

**Docker build ARG:** `NEXT_PUBLIC_API_URL` em `dashboard/Dockerfile`

**CI secret:** `NEXT_PUBLIC_API_URL` em `.github/workflows/dashboard.yml`

---

## Shopify app — `.env.example`

| Variável | Descrição |
|----------|-----------|
| `SHOPIFY_API_KEY` | App Shopify |
| `SHOPIFY_API_SECRET` | App secret |
| `HOST_NAME` | URL pública (ngrok dev) |
| `FITVISION_API_URL` | Backend URL |
| `FITVISION_SHOPIFY_SHARED_SECRET` | Deve match backend |
| `FITVISION_ADMIN_EMAIL` | Admin para uninstall |
| `FITVISION_ADMIN_PASSWORD` | |
| `SESSION_SECRET` | Express session |
| `PORT` | Default 3001 |

---

## CI/CD secrets (GitHub)

| Secret | Workflow |
|--------|----------|
| `RAILWAY_TOKEN` | backend.yml |
| `NEXT_PUBLIC_API_URL` | dashboard.yml |
| `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID` | dashboard.yml |
| `CLOUDFLARE_API_TOKEN` | widget.yml |

---

## Docker production — `Dockerfile`

| Env | Uso |
|-----|-----|
| `PLAYWRIGHT_BROWSERS_PATH` | Chromium scraping |
| `CHROMIUM_PATH` | Path browser |
| `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` | Build optim |
| `spring.profiles.active=prod` | startCommand railway |

---

## Mapa de divergências de nomes

| Conceito | Dev (.env.example) | Prod (application-prod.yml) |
|----------|-------------------|-------------------------------|
| JWT secret | `FITVISION_JWT_SECRET` | `JWT_SECRET`, fallback `FITVISION_JWT_SECRET` |
| Shopify shared | `FITVISION_SHOPIFY_SHARED_SECRET` | `SHOPIFY_SHARED_SECRET`, fallback `FITVISION_SHOPIFY_SHARED_SECRET` |
| Database | `DB_URL` | `DATABASE_URL`, fallback `DB_URL` |

Desde o deploy Railway+Neon, o profile `prod` aceita **ambos** os nomes (placeholder com fallback),
por isso podes usar o conjunto `FITVISION_*` / `DB_URL` — igual ao `.env.example` e ao `shopify-app` —
em todos os serviços. O `shopify-app` (Node) só conhece `FITVISION_SHOPIFY_SHARED_SECRET`; o valor tem
de ser idêntico ao do backend.

---

## Billing URLs

**Ficheiro:** `BillingController.java`

```java
@Value("${fitvision.dashboard.url}")
private String dashboardUrl;

String successUrl = dashboardUrl + "/settings?billing=success";
String cancelUrl  = dashboardUrl + "/settings?billing=cancelled";
String returnUrl  = dashboardUrl + "/settings";
```

**Status:** Implementado

---

## Variáveis não encontradas no código

| Variável | Spec/roadmap |
|----------|--------------|
| `REDIS_URL` | Cache/sessions |
| `SMTP_*` | Email |

Ver [AUDIT.md](./AUDIT.md).
