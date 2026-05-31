# FitVision — Documentação Técnica

Documentação profissional do monorepo FitVision, gerada a partir de **análise do código-fonte real** (Spring Boot, Next.js, widget Vite, Shopify app, Docker, CI/CD).

**Última revisão:** maio de 2026  
**Versão backend:** `0.0.1-SNAPSHOT` (`pom.xml`)  
**Versão widget:** `1.0.0` (`widget/package.json`)

---

## Índice

| # | Documento | Descrição |
|---|-----------|-----------|
| 01 | [Visão geral](./01-visao-geral.md) | Produto, módulos, stack, personas |
| 02 | [Documentação funcional](./02-documentacao-funcional.md) | Casos de uso, regras de negócio |
| 03 | [Arquitetura técnica](./03-arquitetura-tecnica.md) | Camadas, componentes, dependências |
| 04 | [Fluxos da aplicação](./04-fluxos-da-aplicacao.md) | Sequências: auth, recomendação, billing, scrape |
| 05 | [Modelo de dados](./05-modelo-de-dados.md) | ERD, tabelas Flyway V1–V10 |
| 06 | [Referência da API](./06-api-reference.md) | Todos os endpoints com auth e exemplos |
| 07 | [Integração do widget](./07-widget-integration.md) | Embed, contrato, CDN |
| 08 | [Dashboard (loja)](./08-dashboard-store.md) | Rotas store owner, billing, produtos |
| 09 | [Área admin](./09-admin-area.md) | Painel operacional, health, scrape |
| 10 | [Shopify App](./10-shopify-app.md) | OAuth, webhooks, sync |
| 11 | [Segurança e multitenancy](./11-seguranca-e-multitenancy.md) | Filtros, JWT, API key, isolamento |
| 12 | [GDPR e privacidade](./12-gdpr-e-privacidade.md) | Consentimento, retenção de dados |
| 13 | [Execução local](./13-execucao-local.md) | Docker, comandos, portas |
| 14 | [Configuração e env](./14-configuracao-env.md) | Variáveis por ambiente |
| 15 | [Testes](./15-testes.md) | Unit, integração, CI |
| 16 | [Deploy](./16-deploy.md) | Railway, Vercel, Cloudflare R2 |
| 17 | [Observabilidade](./17-observabilidade-operacoes.md) | Sentry, logs, health |
| 18 | [Roadmap e pendências](./18-roadmap-pendencias.md) | Status honesto vs spec |
| 19 | [Guia para novo developer](./19-guia-para-novo-developer.md) | Onboarding passo a passo |
| 20 | [Glossário](./20-glossario.md) | Termos do domínio |
| — | [AUDIT](./AUDIT.md) | Auditoria P0–P3 e divergências |

---

## Módulos do repositório

```
devcontext/
├── src/                    # Backend Spring Boot 3.3 / Java 21
├── dashboard/              # Next.js 14 (store + admin)
├── widget/                 # Vite IIFE embeddable
├── shopify-app/            # Express OAuth + webhooks
├── docker-compose.yml
├── Dockerfile / Dockerfile.dev
├── railway.toml
└── .github/workflows/      # backend, dashboard, widget CI/CD
```

---

## Links rápidos (dev local)

| Serviço | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator health | http://localhost:8080/actuator/health |
| Dashboard | http://localhost:3000 |
| Widget dev | `cd widget && npm run dev` |
| Shopify app | http://localhost:3001 |

---

## Convenções desta documentação

- **Status:** `Implementado` / `Parcial` / `Pendente` / `Não implementado` / `Confirmar no código`
- Caminhos citados são relativos à raiz do repositório, salvo indicação contrária
- Quando algo não existe no código: **"Não encontrado no código atual"**
- Divergências entre `FitVision-ProjectContext.md` e implementação estão em cada doc (secção *Divergências*) e consolidadas em [AUDIT.md](./AUDIT.md)

---

## Como contribuir para a documentação

1. Altere o código
2. Atualize o doc correspondente (índice acima)
3. Registe divergências em [AUDIT.md](./AUDIT.md) se aplicável
4. Mantenha exemplos alinhados com DTOs reais em `src/main/java/com/fitvision/api/`
