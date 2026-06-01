# 12 — GDPR e Privacidade

[← Segurança](./11-seguranca-e-multitenancy.md) | [Índice](./README.md) | [Próximo: Execução local →](./13-execucao-local.md)

---

## Princípios implementados

FitVision trata dados de medidas corporais de compradores finais. No código actual:

1. **Consentimento explícito** via flag `storeBodyData`
2. **Minimização** — widget envia `storeBodyData: false` por defeito
3. **Separação analytics** — flag `body_measurements_stored` na entidade
4. **Logging** — medidas não devem aparecer em INFO (regra CLAUDE.md)

**Status:** Implementado (backend + widget); **Parcial** (políticas legais/documentos jurídicos)

---

## Flag `storeBodyData`

### Widget

**Ficheiro:** `widget/src/main.js`  
Sempre envia `storeBodyData: false` nas recomendações públicas.

### API request

**DTO:** `SizeRecommendationRequest.java`

```java
private boolean storeBodyData = false;
```

### Persistência

**Ficheiro:** `RecommendationEngine.java` — método `persistAnalytics`

Quando `storeBodyData == false`:
- `height_cm` e `weight_kg` gravados como **0**
- `body_measurements_stored = false`

Quando `true` (consentimento loja + comprador):
- Valores reais persistidos
- `body_measurements_stored = true`

---

## Configuração loja

**Dashboard Settings:** toggle `storeBodyData` no perfil da loja (`UpdateStoreProfileRequest`)

Define preferência default da loja para analytics — widget actual **ignora** e força false.

**Status:** Parcial — preferência loja existe; widget não a propaga ainda

---

## Dados armazenados em `recommendation_requests`

| Campo | Sempre | Condicional |
|-------|--------|-------------|
| tenant_id, product_id | ✅ | |
| recommended_size, confidence_score | ✅ | |
| gender, age | ✅ (se fornecidos) | |
| height_cm, weight_kg | Zeros se GDPR | Reais se consent |
| duration_ms | ✅ (V10) | |
| created_at | ✅ | |

**Sem cascade delete** — histórico preservado (V1 comentário migration).

---

## Direito ao apagamento

| Mecanismo | Estado |
|-----------|--------|
| API delete buyer data | **Não encontrado no código atual** |
| Anonymização batch | **Não implementado** |
| Retention policy automática | **Não implementado** |

**Pendente:** endpoint ou job para cumprir pedidos de eliminação.

---

## Logging e observabilidade

**Regras (CLAUDE.md / código):**
- Não logar height/weight em nível INFO
- Engine loga `recommendation_completed` com metadados agregados
- Sentry: tags `requestId`, `tenantId` — evitar PII nos extras

**Logback prod:** JSON estruturado (`logback-spring.xml`) — facilita filtragem PII em SIEM.

---

## Transferências internacionais

**Status:** Confirmar no código — infra prod (Railway, Vercel, R2) implica regiões cloud; não documentado em código.

---

## Shopify

Tokens OAuth encriptados at rest (`shopify_access_token_encrypted`).  
Webhook uninstall desactiva loja — não apaga histórico recomendações.

---

## Checklist compliance (estado honesto)

| Requisito | Status |
|-----------|--------|
| Consent flag técnico | ✅ Implementado |
| Widget default sem PII | ✅ Implementado |
| Privacy policy UI | ❌ Não encontrado |
| Cookie banner dashboard | ❌ Não encontrado |
| DPA / subprocessors doc | ❌ Não no repo |
| Export dados comprador | ❌ Não implementado |
| Delete dados comprador | ❌ Não implementado |

---

## Divergências

| Tópico | ProjectContext | Código |
|--------|----------------|--------|
| Widget respeita storeBodyData loja | Possível expectativa | Widget força `false` |
| Zero PII em logs | Regra documentada | Confirmar audit manual logs |
| Retention 90 dias | Roadmap spec | **Sem job no código** |

Ver [AUDIT.md](./AUDIT.md) item GDPR.
