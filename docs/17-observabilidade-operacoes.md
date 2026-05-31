# 17 — Observabilidade e Operações

[← Deploy](./16-deploy.md) | [Índice](./README.md) | [Próximo: Roadmap →](./18-roadmap-pendencias.md)

---

## Visão geral — Phase 12

**Status:** Implementado (prod profile)

Componentes:
- **Sentry** — erros não tratados
- **Logging estruturado JSON** — prod via Logstash encoder
- **Request ID** — correlação requests
- **Health checks** — Actuator + admin panel
- **Métricas recomendação** — `duration_ms` (V10)

---

## Sentry

**Dependência:** `sentry-spring-boot-starter-jakarta` 7.6.0 (`pom.xml`)

**Config prod** (`application-prod.yml`):

```yaml
sentry:
  dsn: ${SENTRY_DSN}
  traces-sample-rate: 0.2
  environment: production
  release: ${APP_VERSION:unknown}
```

**Captura manual:** `GlobalExceptionHandler.captureUnexpectedException` — tag `requestId`, `tenantId` no scope Sentry.

**Dev:** Sentry **não** configurado em `application.yml` dev.

---

## Logging estruturado

**Ficheiro:** `src/main/resources/logback-spring.xml`

| Profile | Formato |
|---------|---------|
| prod | JSON (`LogstashEncoder`) |
| não-prod | Console pattern legível |

**MDC keys:**
- `requestId` — `RequestIdFilter`
- `tenantId` — `TenantContext`

**Eventos engine:**
- `recommendation_completed` — INFO
- `slow_recommendation` — WARN se >500ms

**Níveis prod:** `com.fitvision=INFO`, `root=WARN`

---

## Request ID

**Ficheiro:** `shared/response/RequestIdFilter.java`

1. Lê header `X-Request-Id` ou gera UUID
2. Coloca em MDC
3. Echo no response header
4. Incluído em `ApiResponse.meta.requestId`

---

## Health checks

### Actuator

| Endpoint | Auth | Detalhe |
|----------|------|---------|
| `GET /actuator/health` | Público | `show-details: never` |
| `GET /actuator/info` | Exposed | |

**Railway healthcheck:** `/actuator/health` (`railway.toml`)

### DatabaseHealthIndicator

**Ficheiro:** `infrastructure/health/DatabaseHealthIndicator.java`

- `SELECT 1`
- UP se latency ≤200ms
- DOWN se erro ou >200ms
- Warning log se >100ms

### Admin operational health

**Endpoint:** `GET /api/admin/v1/health` (JWT admin)  
**Service:** `AdminHealthService`

Métricas:
- DB status + latency ms
- Recommendation p50/p95 (`duration_ms`)
- Failed scrapes 7 dias
- Active stores 24h
- Per-brand scrape status

**UI:** `/admin/health` — `SystemHealthCards`, `RecommendationStatsPanel`, `ScrapePipelineStatus`

### Health público diagnóstico

| Endpoint | Uso |
|----------|-----|
| `GET /api/health` | Ping simples |
| `GET /api/health/error-test` | **Teste Sentry** — lança excepção |

**Cuidado:** `/error-test` exposto publicamente — desactivar ou proteger em prod.

---

## Operações scrape

| Acção | Como |
|-------|------|
| Scheduler weekly | Segunda 02:00 UTC cron |
| Manual marca | Admin UI ou `POST .../brands/{id}/scrape` |
| Batch | `POST /api/admin/v1/scrape-jobs/trigger-all` |
| Monitor | `/admin/health` + `GET /scrape-jobs` |

**Playwright:** Chromium no container prod (`Dockerfile`)

---

## Alertas recomendados (não automatizados no código)

| Alerta | Fonte | Status |
|--------|-------|--------|
| Error rate 5xx | Sentry | Configurar no Sentry |
| DB down | Actuator/Railway | Plataforma |
| Slow recommendations p95 | Admin health | Manual |
| Scrape failures spike | Admin health | Manual |
| Stripe webhook failures | Stripe dashboard | Externo |

**Status:** **Não encontrado** PagerDuty/Opsgenie integration no código.

---

## Dashboard observabilidade

**Status:** Confirmar no código — Sentry Next.js **não encontrado** em `dashboard/package.json` grep.

Backend Sentry only no código analisado.

---

## Runbook incidentes (resumo)

1. **API down** — check Railway logs, `/actuator/health`
2. **Widget 401** — verificar API keys store ACTIVE
3. **Recomendações fallback** — limite plano ou Stripe inactive
4. **Scrape stuck RUNNING** — investigar `scrape_jobs`, restart pod se needed
5. **DB migration fail** — Flyway logs startup, fix forward migration

---

## Divergências

| Item | Spec | Código |
|------|------|--------|
| Prometheus metrics | Roadmap | **Não encontrado** |
| Grafana dashboards | Roadmap | **Não no repo** |
| Distributed tracing full | Sentry 20% traces | Parcial |
| `/api/health/error-test` prod | — | Risco exposto |

Ver [AUDIT.md](./AUDIT.md).
