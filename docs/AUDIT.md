# AUDIT — Auditoria Técnica FitVision

[← Glossário](./20-glossario.md) | [Índice](./README.md)

**Data auditoria:** maio 2026 (atualizado em junho 2026 para estado pós-fixes P0)  
**Método:** análise estática código-fonte + exploração controllers, migrations, CI, configs  
**Spec referência:** `FitVision-ProjectContext.md` (4041 linhas)

---

## 1. Resumo executivo

FitVision é um **SaaS funcional** com backend Spring Boot maduro, dashboard Next.js, widget production-ready (50KB gzip), billing Stripe, scraping Playwright, CI/CD triplo (Railway/Vercel/R2) e observabilidade Phase 12 (Sentry, logs JSON, admin health).

**Principais riscos (atuais):** gaps GDPR delete/export, ausência rate limiting, shopify-app sem persistência sessão e sem CI.

**Mitigações aplicadas desde a auditoria inicial:**
- P0-1: billing redirects via `fitvision.dashboard.url`
- P0-2: `/api/admin/seed` com `X-Bootstrap-Token` e bloqueio one-time (`410`)
- P0-3: `/api/health/error-test` restrito ao profile `dev`

**Cobertura testes backend:** boa para core store/widget; **fraca** para admin, billing, shopify.

---

## 2. Inventário implementado

| Módulo | Endpoints/rotas | Status |
|--------|-------------------|--------|
| Widget API | 1 endpoint activo | ✅ |
| Dashboard API | ~25 endpoints | ✅ |
| Admin API | ~20 endpoints | ✅ |
| Shopify API | 2 endpoints | ✅ |
| Stripe webhooks | 1 endpoint | ✅ |
| Flyway | V1–V10 | ✅ |
| Dashboard pages | 11 rotas | ✅ |
| Widget CDN CI | R2 upload | ✅ |
| Admin health UI | `/admin/health` | ✅ |

**Placeholders vazios:** `WidgetController`, `DashboardController`, `WebhookController`

---

## 3. Prioridades P0 — Crítico

| ID | Issue | Evidência | Impacto | Recomendação |
|----|-------|-----------|---------|--------------|
| P0-1 | ~~Stripe checkout URLs localhost~~ | `BillingController` usa `fitvision.dashboard.url` | Resolvido | Monitorar env em deploy |
| P0-2 | ~~`POST /api/admin/seed` sem auth~~ | Header `X-Bootstrap-Token` + one-time `410` | Mitigado | Rodar bootstrap apenas quando necessário |
| P0-3 | ~~`GET /api/health/error-test` público~~ | Endpoint movido para controller `@Profile("dev")` | Mitigado | Manter fora de produção |

---

## 4. Prioridades P1 — Alto

| ID | Issue | Evidência | Recomendação |
|----|-------|-----------|--------------|
| P1-1 | **Env var naming inconsistente** | `.env.example` vs `application-prod.yml` | Tabela única Railway; alias ou documentação deploy |
| P1-2 | **GDPR delete/export ausente** | Sem endpoints | API DSR mínima + retention job |
| P1-3 | **Rate limiting ausente** | Grep negativo | Bucket4j ou Cloudflare rate limit |
| P1-4 | **API keys plaintext DB** | `stores.api_key_*` columns | Hash secret key; public key ok |
| P1-5 | **Shopify app sessão in-memory** | `shopify-app/src/store.js` | Redis/DB session |
| P1-6 | **Middleware JWT sem verify** | `middleware.ts` | Acceptable se backend enforce; documentar risco |
| P1-7 | **Zero IT admin/billing** | Sem `AdminControllerIT` | Adicionar suite crítica |
| P1-8 | **Shopify app sem CI/CD** | workflows grep | Pipeline deploy |

---

## 5. Prioridades P2 — Médio

| ID | Issue | Notas |
|----|-------|-------|
| P2-1 | SecretKeyAuthFilter não wired | Confusão spec vs runtime |
| P2-2 | Dashboard CI sem lint/test | Só build |
| P2-3 | Widget ignora storeBodyData loja | GDPR parcial |
| P2-4 | Sentry só backend | Dashboard errors não tracked |
| P2-5 | Constraint DB one active chart | Só app layer — race possible |
| P2-6 | JaCoCo/coverage gate | Não encontrado pom |
| P2-7 | Staging env | Não no repo |
| P2-8 | Email/notifications | Não implementado |

---

## 6. Prioridades P3 — Baixo / melhoria

| ID | Issue |
|----|-------|
| P3-1 | i18n dashboard PT completo |
| P3-2 | WooCommerce connector dedicado |
| P3-3 | WebhookController outbound events |
| P3-4 | Custom widget theming |
| P3-5 | Prometheus metrics |
| P3-6 | Audit log admin actions |
| P3-7 | 2FA admin |
| P3-8 | Redis cache layer |

---

## 7. Matriz de testes

| Área | Unit | IT | E2E |
|------|------|-----|-----|
| Recommendation engine | ✅ | ✅ widget | ❌ |
| Auth store | — | ✅ | ❌ |
| Products/size charts | ✅ parser | ✅ | ❌ |
| Admin | ❌ | ❌ | ❌ |
| Billing | ❌ | ❌ | ❌ |
| Shopify | ❌ | ❌ | ❌ |
| Scraper | ❌ | ❌ | ❌ |
| Dashboard | ❌ | ❌ | ❌ |
| Widget build | gzip check CI | — | ❌ |

**Último failsafe local (artefacto):** 32 tests, 0 failures — confirmar com `mvn verify`.

**User-reported issues (Excel parser, RecommendationEngineTest):** sem `@Disabled`/FIXME no código; estado actual requer execução CI local.

---

## 8. Segurança — checklist

| Controlo | Status |
|----------|--------|
| Tenant isolation | ✅ |
| BCrypt passwords | ✅ strength 12 |
| JWT secret min 32 bytes | ✅ JwtService |
| Shopify token encryption | ✅ AES-GCM |
| CSRF disabled stateless API | ✅ expected |
| CORS prod restricted dashboard | ✅ |
| CORS widget `*` | ✅ by design |
| Stripe webhook signature | ✅ |
| SQL injection JPA | ✅ parameterized |
| File upload limit 2MB | ✅ |
| Dependency scanning CI | ❌ não encontrado |

---

## 9. Deploy readiness

| Componente | Pronto prod? | Blockers |
|------------|--------------|----------|
| Backend Railway | 🟡 | Env mapping, P0 billing URLs |
| Dashboard Vercel | ✅ | NEXT_PUBLIC_API_URL secret |
| Widget R2 | ✅ | — |
| Shopify app | ❌ | Session, CI, HOST_NAME |
| Stripe | 🟡 | Webhook + price IDs + redirect URLs |
| Sentry | ✅ | DSN required |
| DB migrations | ✅ | V10 applied |

---

## 10. Divergências FitVision-ProjectContext.md vs código

| Tópico | ProjectContext | Implementação actual |
|--------|----------------|----------------------|
| SecretKeyAuthFilter activo | Documentado | **Não registado** SecurityConfig |
| WebhookController | Endpoints webhook FitVision | **Ficheiro vazio** |
| WidgetController | Controller widget | **Vazio** — usa WidgetRecommendationController |
| DashboardController | Agregador | **Vazio** |
| Widget size limit | Possível 45KB spec | **50KB** build-check.js |
| ML recommendations | Roadmap/spec | **Rule engine** BodyProfile + SizeChartMatcher |
| Rate limiting | Possível | **Ausente** |
| Email alerts | Mencionado | **Ausente** |
| WooCommerce plugin | Mencionado | **Só embed genérico** |
| Redis | Possível | **Ausente** |
| Coverage 80%+ | Spec quality | **Sem JaCoCo gate confirmado** |
| Next migration V9 only (CLAUDE) | Desactualizado | **V10** duration_ms existe |
| Billing prod URLs | Esperado configurável | **localhost hardcoded** |

---

## 11. Acções recomendadas (ordenadas)

1. **Imediato:** Fix billing redirect URLs (P0-1)
2. **Imediato:** Proteger admin seed + error-test (P0-2, P0-3)
3. **Sprint 1:** Unificar env vars prod + documentar Railway
4. **Sprint 1:** AdminControllerIT + BillingControllerIT
5. **Sprint 2:** GDPR delete/export MVP
6. **Sprint 2:** Rate limiting edge ou app
7. **Sprint 3:** Shopify session persistence + CI
8. **Contínuo:** Manter `docs/` sync com código

---

## 12. Ficheiros analisados (amostra)

```
src/main/java/com/fitvision/api/**/*.java
src/main/java/com/fitvision/infrastructure/security/*
src/main/java/com/fitvision/engine/recommendation/*
src/main/resources/db/migration/V*.sql
src/main/resources/application*.yml
dashboard/app/**, dashboard/lib/*, dashboard/middleware.ts
widget/src/*, widget/build-check.js, widget/vite.config.js
shopify-app/src/*
docker-compose.yml, Dockerfile*, railway.toml
.github/workflows/*.yml
.env.example, shopify-app/.env.example
pom.xml, dashboard/package.json, widget/package.json
```

---

## 13. Conclusão

O FitVision está **próximo de produção** para core recommendation SaaS, com gaps operacionais concentrados em **GDPR operationalization**, **rate limiting** e **shopify-app hardening**. A documentação em `/docs` reflecte o estado real do código em junho 2026.

**Próxima revisão audit:** após implementação dos itens P1 principais (GDPR e rate limit) e revisão de CI do shopify-app.
