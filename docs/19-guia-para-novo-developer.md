# 19 — Guia para Novo Developer

[← Roadmap](./18-roadmap-pendencias.md) | [Índice](./README.md) | [Próximo: Glossário →](./20-glossario.md)

---

## Bem-vindo

Este guia orienta onboarding no monorepo FitVision. Tempo estimado setup: **30–60 minutos** com Docker.

---

## 1. Clone e estrutura

```
devcontext/
├── src/           → Backend (começar aqui para API)
├── dashboard/     → UI store + admin
├── widget/        → JS embed
├── shopify-app/   → Integração Shopify
├── docs/          → Esta documentação
├── CLAUDE.md      → Referência rápida AI/dev
└── docker-compose.yml
```

Leia primeiro: [01-visao-geral.md](./01-visao-geral.md), [03-arquitetura-tecnica.md](./03-arquitetura-tecnica.md)

---

## 2. Setup rápido (Docker)

```bash
cp .env.example .env
# Editar FITVISION_JWT_SECRET (openssl rand -base64 32)

docker compose up --build -d
```

Verificar:
- http://localhost:8080/actuator/health → UP
- http://localhost:3000 → login

---

## 3. Criar utilizadores

**Loja (UI):** http://localhost:3000/register

**Admin (CLI):**

```bash
./scripts/create-admin.sh admin@fitvision.io MinhaSenhaSegura8
```

Login admin → redirect automático `/admin/dashboard`

---

## 4. Fluxo dev típico — nova feature API

1. Entidade/migration se needed → `src/main/resources/db/migration/V11__*.sql`
2. Repository → `infrastructure/persistence/`
3. Service/domain logic
4. Controller → `api/dashboard/` ou `api/admin/`
5. **Sempre** filtrar por `TenantContext.get()` em dados tenant
6. IT test → `*IT.java` extends `AbstractIntegrationTest`
7. Documentar endpoint em [06-api-reference.md](./06-api-reference.md)

---

## 5. Fluxo dev — dashboard

```bash
cd dashboard
npm install
npm run dev
```

- Rotas: `app/(app)/` store, `app/(admin)/admin/` admin
- API client: `lib/api.ts` — adicionar método + tipo em `lib/types.ts`
- Auth: token em localStorage + cookie para middleware

---

## 6. Fluxo dev — widget

```bash
cd widget
npm run dev
```

**Regra crítica:** bundle gzip **≤ 50 KB** — `npm run build` falha se exceder.

Testar contra backend local com `data-fitvision-api-url="http://localhost:8080"`.

---

## 7. Comandos essenciais

| Tarefa | Comando |
|--------|---------|
| Testes backend | `mvn verify` |
| Teste unitário | `mvn test -Dtest=NomeTest` |
| Build backend | `mvn clean package -DskipTests` |
| Lint dashboard | `cd dashboard && npm run lint` |
| Build widget | `cd widget && npm run build` |
| Logs backend docker | `docker logs devcontext-fitvision-backend-1 --tail 50` |

---

## 8. Onde debugar problemas comuns

| Sintoma | Verificar |
|---------|-----------|
| 401 dashboard | Token expirado, store INACTIVE |
| 401 widget | API key wrong, header name |
| 402 create product | Plano FREE limite 2 produtos |
| 403 admin | JWT sem role ADMIN |
| CORS | SecurityConfig origins |
| Flyway error | Logs startup, migration SQL |
| Scrape fail | `scrape_jobs.error_message`, Chromium no container |

---

## 9. Convenções código

### Backend Java
- Controllers finos — lógica em services/engine
- `ApiResponse.ok(data)` / exceptions → `GlobalExceptionHandler`
- Size labels **UPPERCASE**
- Engine classes **sem** DB access

### Dashboard TS
- SWR para fetch
- Componentes em `components/app/` vs `components/admin/`
- Formulários: react-hook-form + Zod

### Git
- Não commitar `.env`
- IT e unit naming: `*Test` vs `*IT`

---

## 10. Documentação e spec

| Documento | Uso |
|-----------|-----|
| `docs/` | Documentação oficial (esta pasta) |
| `CLAUDE.md` | Cheat sheet monorepo |
| `FitVision-ProjectContext.md` | Spec histórica — **validar vs código** |
| `AUDIT.md` | Divergências e riscos |

**Regra:** código > ProjectContext quando conflito.

---

## 11. PR checklist

- [ ] `mvn verify` passa
- [ ] Tenant isolation respeitado
- [ ] GDPR: não logar PII
- [ ] Widget size check se touched `widget/`
- [ ] Migration forward-only se schema change
- [ ] Actualizar doc API se novo endpoint

---

## 12. Contactos e recursos

- Swagger local: http://localhost:8080/swagger-ui.html
- Docs índice: [README.md](./README.md)
- n8n-as-code: ver `AGENTS.md` se trabalhar workflows n8n no workspace

---

## Divergências onboarding

| Item | Nota |
|------|------|
| README raiz | Confirmar se existe — usar `docs/` + `CLAUDE.md` |
| shopify-app no compose | Arrancar manualmente |
