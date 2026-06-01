# 20 — Glossário

[← Guia dev](./19-guia-para-novo-developer.md) | [Índice](./README.md) | [AUDIT →](./AUDIT.md)

---

## Termos de domínio

| Termo | Definição |
|-------|-----------|
| **Tenant** | Loja (`Store`) isolada multi-tenant; identificada por UUID |
| **Store owner** | Utilizador com `role=STORE` que gere produtos e settings |
| **Admin** | Operador FitVision com `role=ADMIN` |
| **API key (public)** | Chave embeddable no widget (`X-FitVision-Key`) |
| **API key (secret)** | Chave servidor — regenerável; não expor no frontend |
| **External product ID** | ID do produto na plataforma e-commerce (Shopify, etc.) |
| **Size chart** | Tabela de tamanhos com versões; uma activa por produto |
| **Size entry** | Linha da tabela: label (M, L) + ranges chest/waist/hip/height |
| **Body profile** | Perfil calculado: BMI, body fat, estimativas corporais |
| **Recommendation** | Resultado match tamanho + confidence + quality |
| **Match quality** | EXACT, PARTIAL, CLOSEST, NO_MATCH |
| **Global brand** | Marca FitVision (`brands.tenant_id IS NULL`) |
| **Scrape job** | Execução scraping Playwright por marca |
| **Plan** | FREE, STARTER, PRO, TEAM — limites produtos/recomendações |

---

## Termos técnicos

| Termo | Definição |
|-------|-----------|
| **TenantContext** | ThreadLocal UUID tenant + MDC logging |
| **ApiResponse** | Envelope JSON padrão `{ success, data, error, meta }` |
| **Flyway** | Migrações SQL versionadas V1–V10 |
| **IIFE** | Immediately Invoked Function Expression — formato bundle widget |
| **JWT** | JSON Web Token auth dashboard/admin |
| **Testcontainers** | PostgreSQL ephemeral para IT tests |
| **ScriptTag** | Shopify injecção script widget CDN |
| **Stripe Checkout** | Hosted payment page upgrade plano |
| **Billing portal** | Stripe self-service gestão subscrição |
| **MDC** | Mapped Diagnostic Context — logging correlation |
| **Sentry** | Error tracking produção |
| **R2** | Cloudflare object storage — CDN widget |

---

## Siglas

| Sigla | Significado |
|-------|-------------|
| GDPR | General Data Protection Regulation |
| SaaS | Software as a Service |
| CRUD | Create, Read, Update, Delete |
| IT | Integration Test (suffix `*IT.java`) |
| CI/CD | Continuous Integration / Deployment |
| CDN | Content Delivery Network |
| OAuth | Open Authorization (Shopify) |
| HMAC | Hash-based message authentication (webhooks) |
| BMI | Body Mass Index |
| PII | Personally Identifiable Information |

---

## Códigos erro frequentes

Ver [06-api-reference.md](./06-api-reference.md) — enum `ErrorCode`:

| Código | Significado típico |
|--------|-------------------|
| `INVALID_API_KEY` | Widget key inválida |
| `PLAN_LIMIT_REACHED` | Limite plano (dashboard) |
| `PRODUCT_NOT_FOUND` | Produto inexistente tenant |
| `SIZE_CHART_NOT_FOUND` | Sem tabela activa |
| `VALIDATION_ERROR` | Bean validation fail |
| `STORE_ALREADY_EXISTS` | Email registado |
| `STRIPE_ERROR` | Stripe API/config |

---

## Paths API versionados

| Prefixo | Versão | Notas |
|---------|--------|-------|
| `/api/widget/v1/` | v1 | Contrato widget — breaking → v2 |
| `/api/dashboard/v1/` | v1 | Store dashboard |
| `/api/admin/v1/` | v1 | Admin ops |
| `/api/shopify/` | unversioned | Integração app |
| `/api/billing/` | unversioned | Webhooks Stripe |

---

## Ficheiros “fonte de verdade”

| Conceito | Ficheiro |
|----------|----------|
| Planos limites | `domain/billing/Plan.java` |
| Error codes | `shared/exception/ErrorCode.java` |
| Security chain | `infrastructure/security/SecurityConfig.java` |
| Engine orchestration | `engine/recommendation/RecommendationEngine.java` |
| Schema BD | `db/migration/V*.sql` |
