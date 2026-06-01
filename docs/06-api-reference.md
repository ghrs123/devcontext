# 06 — Referência da API

[← Modelo de dados](./05-modelo-de-dados.md) | [Índice](./README.md) | [Próximo: Widget →](./07-widget-integration.md)

---

## Convenções

- **Base URL dev:** `http://localhost:8080`
- **Base URL prod:** `https://api.fitvision.io` (dashboard `.env.production`)
- **Envelope:** todas as respostas usam `ApiResponse<T>` (`shared/response/ApiResponse.java`)
- **Swagger:** `/swagger-ui.html` (sem auth)
- **Versionamento widget:** path `/api/widget/v1/` — breaking changes exigem nova versão

### Envelope de sucesso

```json
{
  "success": true,
  "data": { },
  "error": null,
  "meta": {
    "requestId": "a1b2c3d4-...",
    "timestamp": "2026-05-31T12:00:00Z"
  }
}
```

### Envelope de erro

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "heightCm must be at least 50",
    "field": "heightCm"
  },
  "meta": { "requestId": "...", "timestamp": "..." }
}
```

### Códigos HTTP por `ErrorCode`

| HTTP | Códigos |
|------|---------|
| 400 | `VALIDATION_ERROR`, `INVALID_BODY_MEASUREMENTS`, `UNSUPPORTED_FILE_FORMAT`, `SIZE_CHART_PARSE_ERROR` |
| 401 | `UNAUTHORIZED`, `INVALID_API_KEY`, `INVALID_SECRET_KEY`, `INVALID_CREDENTIALS` |
| 402 | `PLAN_LIMIT_REACHED` (dashboard; **não** widget) |
| 404 | `*_NOT_FOUND` |
| 409 | `STORE_ALREADY_EXISTS`, `ADMIN_ALREADY_EXISTS` |
| 500 | `INTERNAL_ERROR` |
| 502 | `STRIPE_ERROR` |

---

## Widget (`/api/widget/v1`)

**Auth:** header `X-FitVision-Key: {api_key_public}`

### POST `/size-recommendation`

**Request** (`SizeRecommendationRequest`):

```json
{
  "externalProductId": "shopify-product-123",
  "heightCm": 175.0,
  "weightKg": 70.0,
  "gender": "MALE",
  "age": 30,
  "storeBodyData": false
}
```

| Campo | Obrigatório | Validação |
|-------|-------------|-----------|
| externalProductId | Sim | @NotBlank |
| heightCm | Sim | 50–250 |
| weightKg | Sim | 20–300 |
| gender | Não | MALE/FEMALE/UNISEX; default UNISEX |
| age | Não | 10–120 |
| storeBodyData | Não | default false |

**Response 200** (`SizeRecommendationResponse`):

```json
{
  "success": true,
  "data": {
    "recommendedSize": "M",
    "confidenceScore": 0.85,
    "quality": "EXACT",
    "productName": "Camisola Básica",
    "hasSizeChart": true,
    "confidenceLabel": "High",
    "message": "Based on your measurements, we recommend size M."
  }
}
```

**Fallback limite de plano** (sempre 200):

```json
{
  "data": {
    "recommendedSize": null,
    "confidenceScore": 0.0,
    "quality": "NO_MATCH",
    "hasSizeChart": false,
    "confidenceLabel": "Low",
    "message": "Size recommendations are temporarily unavailable for this store."
  }
}
```

**401:** API key inválida → `INVALID_API_KEY`

---

## Dashboard — Auth (`/api/dashboard/v1/auth`)

**Auth:** nenhuma

### POST `/register`

```json
{
  "name": "Minha Loja",
  "email": "loja@example.com",
  "password": "senha1234",
  "platform": "MANUAL"
}
```

**Response:** `AuthResponse` — `accessToken`, `tokenType` ("Bearer"), `expiresIn` (segundos), `apiKeyPublic`

### POST `/login`

```json
{ "email": "loja@example.com", "password": "senha1234" }
```

**401:** `INVALID_CREDENTIALS` | **409 register:** `STORE_ALREADY_EXISTS`

---

## Dashboard — Store (`/api/dashboard/v1/store`)

**Auth:** `Authorization: Bearer {jwt}`

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/profile` | Perfil da loja |
| PATCH | `/profile` | Actualizar nome, platform, storeBodyData |
| GET | `/api-keys` | Chaves public/secret (mascarada) |
| POST | `/api-keys/regenerate` | Regenera par de keys |

**PATCH body** (`UpdateStoreProfileRequest`): `name`, `platform`, `storeBodyData` (boolean)

---

## Dashboard — Products (`/api/dashboard/v1/products`)

**Auth:** JWT store

| Método | Path | Body | Notas |
|--------|------|------|-------|
| GET | `/` | — | Lista produtos do tenant |
| POST | `/` | `ProductRequest` | 402 se limite plano |
| GET | `/{productId}` | — | |
| PUT | `/{productId}` | `ProductRequest` | |
| DELETE | `/{productId}` | — | Soft delete |

**ProductRequest:**

```json
{
  "externalProductId": "ext-001",
  "name": "Vestido Verão",
  "category": "dresses",
  "genderTarget": "FEMALE",
  "brandId": "uuid-opcional"
}
```

---

## Dashboard — Brands (`/api/dashboard/v1/brands`)

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/` | Marcas tenant + globais |
| POST | `/` | Criar marca tenant |
| DELETE | `/{id}` | Soft delete |

---

## Dashboard — Size Charts (`/api/dashboard/v1/size-charts`)

| Método | Path | Content-Type |
|--------|------|--------------|
| POST | `/{productId}/upload` | multipart (`file`) |
| POST | `/{productId}/manual` | JSON entries |
| GET | `/{productId}/active` | — |
| DELETE | `/{productId}/active` | — |

---

## Dashboard — Analytics (`/api/dashboard/v1/analytics`)

| Método | Path | Query |
|--------|------|-------|
| GET | `/summary` | — |
| GET | `/recommendations` | `page`, `size` |

---

## Dashboard — Billing (`/api/dashboard/v1/billing`)

| Método | Path | Body |
|--------|------|------|
| GET | `/status` | — |
| POST | `/checkout` | `{ "plan": "PRO" }` ou `{ "priceId": "price_..." }` |
| POST | `/portal` | — |

**GET /status response** (`BillingStatusResponse`):

```json
{
  "plan": "FREE",
  "subscriptionStatus": "inactive",
  "currentPeriodEnd": null,
  "productsUsed": 1,
  "productsLimit": 2,
  "recommendationsUsed": 42,
  "recommendationsLimit": 100
}
```

**POST /checkout response:** `{ "checkoutUrl": "https://checkout.stripe.com/..." }`

---

## Health público

| Método | Path | Auth |
|--------|------|------|
| GET | `/api/health` | Nenhuma |
| GET | `/actuator/health` | Nenhuma |

`/api/health/error-test` existe apenas no profile `dev` (`DevErrorTestController`) para testes locais de erro.

---

## Admin (`/api/admin/v1`)

**Auth:** JWT com claim `role=ADMIN`

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/health` | Saúde operacional |
| GET | `/metrics` | KPIs plataforma |
| GET | `/recommendations/stats` | Stats motor recomendação |
| POST | `/scrape-jobs/trigger-all` | Batch scrape async |
| GET | `/stores` | `page`, `size`, `status`, `search` |
| GET | `/stores/{storeId}` | Detalhe |
| PATCH | `/stores/{storeId}/status` | `{ "status": "ACTIVE" }` |
| PATCH | `/stores/{storeId}/plan` | `{ "plan": "PRO" }` |
| GET | `/brands` | Marcas globais |
| POST | `/brands` | Criar global |
| PUT | `/brands/{id}` | Actualizar |
| DELETE | `/brands/{id}` | Soft delete |
| POST | `/brands/{brandId}/size-charts/upload` | multipart |
| GET | `/brands/{brandId}/size-charts` | Versões |
| DELETE | `/brands/{brandId}/size-charts/active` | |
| POST | `/brands/{brandId}/scrape` | Trigger scrape |
| GET | `/brands/{brandId}/scrape-jobs` | Histórico |
| GET | `/scrape-jobs` | `status`, `page`, `size` |
| GET | `/recommendations` | `tenantId`, `productId`, `quality`, `page`, `size` |

### POST `/api/admin/seed` (bootstrap)

**Path:** `/api/admin/seed` (fora de `/v1`)  
**Auth:** sem JWT, mas exige header `X-Bootstrap-Token`  
**Body:** `{ "email": "admin@fitvision.io", "password": "..." }`  
**401:** token ausente/inválido  
**410:** bootstrap desativado ou já utilizado  
**409:** `ADMIN_ALREADY_EXISTS`

---

## Shopify (`/api/shopify`)

| Método | Path | Auth |
|--------|------|------|
| POST | `/connect` | Header `X-FitVision-Shopify-Secret` |
| GET | `/status?shop=` | Nenhuma |

**POST /connect** (`ShopifyConnectRequest`): `shop`, `accessToken`, `email`, `storeName`

**Response:** `ShopifyConnectResponse` — `storeId`, `jwt`, `apiKeyPublic`

---

## Billing webhooks (`/api/billing`)

### POST `/webhooks`

**Auth:** header `Stripe-Signature`  
**Eventos tratados:** `customer.subscription.created/updated/deleted`, `invoice.payment_failed`

---

## Placeholders (sem endpoints activos)

| Ficheiro | Status |
|----------|--------|
| `api/widget/WidgetController.java` | Não implementado |
| `api/dashboard/DashboardController.java` | Não implementado |
| `api/webhook/WebhookController.java` | Não implementado |

---

## Divergências

- `SecretKeyAuthFilter` + header `X-FitVision-Secret`: **não activo** na cadeia de segurança
- Documentação ProjectContext pode listar endpoints removidos ou renomeados — esta lista reflecte grep `@*Mapping` em controllers activos (maio 2026)
