# 18 — Roadmap e Pendências

[← Observabilidade](./17-observabilidade-operacoes.md) | [Índice](./README.md) | [Próximo: Guia dev →](./19-guia-para-novo-developer.md)

---

## Legenda de status

| Marcador | Significado |
|----------|-------------|
| ✅ | Implementado e verificado no código |
| 🟡 | Parcial |
| ⏳ | Pendente / planeado |
| ❌ | Não encontrado no código actual |

---

## Fases concluídas (código)

| Fase | Conteúdo | Status |
|------|----------|--------|
| Core API | Auth, products, size charts, widget recommend | ✅ |
| Multi-tenant | TenantContext, API key, JWT | ✅ |
| Admin | Stores, brands, recommendations, metrics | ✅ |
| Shopify | OAuth app, connect, sync, webhooks | 🟡 (sessão in-memory) |
| Scraping | 4 brands, scheduler, admin UI | ✅ |
| Billing | Stripe checkout, portal, webhooks, limits | ✅ |
| CI/CD | Railway, Vercel, R2 | ✅ |
| Observability | Sentry, JSON logs, admin health, duration_ms | ✅ |

---

## Pendências P0–P1 (ver AUDIT)

| Item | Prioridade | Status |
|------|------------|--------|
| Billing redirect URLs prod | P0 | ✅ resolvido (`fitvision.dashboard.url`) |
| `/api/admin/seed` público | P0 | ✅ mitigado (bootstrap token + one-time 410) |
| Env var naming dev vs prod | P1 | ⏳ documentado em 14 |
| GDPR delete/export API | P1 | ❌ |
| Rate limiting API | P1 | ❌ |

---

## Funcionalidades roadmap vs código

| Feature | ProjectContext / expectativa | Código |
|---------|------------------------------|--------|
| Email transacional | Mencionado | ❌ |
| WooCommerce plugin | Mencionado | ❌ (embed genérico only) |
| Redis cache | Possível | ❌ |
| WebhookController outbound | Mencionado | ❌ placeholder |
| SecretKeyAuthFilter | Documentado | ❌ não wired |
| Refresh tokens | Nice-to-have | ❌ |
| WooCommerce native | Roadmap | ❌ |
| Multi-language dashboard | Roadmap | 🟡 widget pt/en |
| Prometheus/Grafana | Roadmap | ❌ |
| Shopify app CI/CD | Esperado prod | ❌ |
| Testes admin/billing IT | Qualidade | ❌ |
| JaCoCo coverage gate | Spec | ❌ confirmar pom |
| Staging environment | Ops | ❌ |
| Audit log admin actions | Compliance | ❌ |
| 2FA admin | Security | ❌ |
| API key hashing at rest | Security | ❌ |
| Retention job GDPR | Compliance | ❌ |

---

## Melhorias técnicas sugeridas

### Curto prazo
1. IT tests AdminController + BillingController
2. CI dashboard: adicionar `npm run lint`
3. Mapear env vars Railway com tabela única
4. Endpoints GDPR export/delete
5. Rate limiting API

### Médio prazo
1. Shopify app session store (Redis)
2. Rate limiting (bucket4j ou edge)
3. GDPR export/delete endpoints
4. Sentry no dashboard Next.js
5. Deploy pipeline shopify-app

### Longo prazo
1. WooCommerce / Magento connectors
2. ML size prediction (spec) vs rule engine actual
3. Multi-region deployment
4. Enterprise SSO

---

## Widget roadmap

| Item | Status |
|------|--------|
| v2 API breaking changes | ⏳ quando necessário |
| Respeitar storeBodyData loja | ⏳ |
| Custom theming CSS vars | ❌ |
| A/B test hooks | ❌ |

---

## Divergências consolidadas

Secção completa em [AUDIT.md](./AUDIT.md) secção 10.

**Regra:** quando `FitVision-ProjectContext.md` descreve feature ausente no código, marcar **"Não encontrado no código actual"** — não assumir implementação futura como presente.

---

## Próximo marco sugerido (V11+)

1. Migration V11 se novos campos billing/GDPR
2. Unificar nomenclatura de env vars dev/prod
3. Admin IT suite
4. GDPR MVP endpoints
