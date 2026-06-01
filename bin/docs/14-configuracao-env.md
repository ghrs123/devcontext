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
| `spring.datasource.url` | `DATABASE_URL` | Railway Postgres |
| `server.port` | `PORT` | Default 8080 |
| `fitvision.jwt.secret` | `JWT_SECRET` | **Nome diferente do .env.example** |
| `fitvision.shopify.encryption-key` | `SHOPIFY_ENCRYPTION_KEY` | |
| `fitvision.shopify.shared-secret` | `SHOPIFY_SHARED_SECRET` | **Nome diferente: sem prefixo FITVISION_** |
| `stripe.*` | `STRIPE_*` | Obrigatório billing prod |
| `sentry.dsn` | `SENTRY_DSN` | |
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
| JWT secret | `FITVISION_JWT_SECRET` | `JWT_SECRET` |
| Shopify shared | `FITVISION_SHOPIFY_SHARED_SECRET` | `SHOPIFY_SHARED_SECRET` |
| Database | `DB_URL` | `DATABASE_URL` |

**Risco:** deploy incorrecto se nomes não mapeados na plataforma (Railway env vars).

---

## Billing URLs hardcoded

**Ficheiro:** `BillingController.java`

```java
String successUrl = "http://localhost:3000/settings?billing=success";
String cancelUrl  = "http://localhost:3000/settings?billing=cancelled";
String returnUrl  = "http://localhost:3000/settings";
```

**Status:** Pendente — deveria usar env `FITVISION_DASHBOARD_URL` ou similar (**não encontrado**)

---

## Variáveis não encontradas no código

| Variável | Spec/roadmap |
|----------|--------------|
| `REDIS_URL` | Cache/sessions |
| `SMTP_*` | Email |
| `FITVISION_DASHBOARD_URL` | Stripe redirects prod |

Ver [AUDIT.md](./AUDIT.md).
