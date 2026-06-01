# 15 — Testes

[← Config env](./14-configuracao-env.md) | [Índice](./README.md) | [Próximo: Deploy →](./16-deploy.md)

---

## Estratégia

| Tipo | Padrão ficheiro | Runner Maven | Contexto Spring |
|------|-----------------|--------------|-----------------|
| Unit | `*Test.java` | Surefire (`mvn test`) | Não |
| Integração | `*IT.java` | Failsafe (`mvn verify`) | Sim + Testcontainers |

Documentado em `CLAUDE.md` e `pom.xml`.

---

## Infraestrutura de integração

**Ficheiro:** `src/test/java/com/fitvision/AbstractIntegrationTest.java`

- `@SpringBootTest(RANDOM_PORT)`
- `@ActiveProfiles("test")`
- Singleton `PostgreSQLContainer` postgres:16
- DB: `fitvision_test` / user `fitvision` / pass `fitvision`
- Flyway corre no startup
- Subclasses gerem isolamento (`@BeforeEach`/`@AfterEach`)

**Profile test:** `src/test/resources/application-test.yml` — JWT test secret, Stripe placeholders

---

## Testes unitários (8 classes)

| Classe | Área |
|--------|------|
| `BodyProfileCalculatorTest` | Engine — BMI, medidas |
| `RecommendationEngineTest` | Engine — orquestração mock repos |
| `SizeChartMatcherTest` | Engine — scoring match |
| `CsvSizeChartParserTest` | Parser CSV |
| `ExcelSizeChartParserTest` | Parser Excel |
| `SizeChartServiceTest` | Serviço size charts |

**RecommendationEngineTest:** Mockito puro, sem Spring — valida persistência GDPR, NO_MATCH sem chart, etc.

---

## Testes integração (6 classes)

| Classe | Endpoints |
|--------|-----------|
| `StoreAuthControllerIT` | register, login |
| `StoreControllerIT` | profile, api-keys |
| `ProductControllerIT` | CRUD products |
| `SizeChartControllerIT` | upload, manual |
| `AnalyticsControllerIT` | summary |
| `WidgetRecommendationControllerIT` | size-recommendation |

Todos extendem `AbstractIntegrationTest`.

---

## Comandos

```bash
# Todos unit
mvn test

# Unit + IT
mvn verify

# Classe específica
mvn test -Dtest=SizeChartMatcherTest
mvn verify -Dit.test=WidgetRecommendationControllerIT

# Skip tests build
mvn clean package -DskipTests
```

---

## CI backend

**Workflow:** `.github/workflows/backend.yml`

- Postgres 16 service container
- `mvn -B verify`
- Deploy Railway após sucesso (`mvn package -DskipTests`)

---

## Dashboard / Widget CI

| Workflow | Testes |
|----------|--------|
| `dashboard.yml` | **Apenas build** — sem lint/test |
| `widget.yml` | **Build + gzip check** via `build-check.js` |

**Status:** Parcial cobertura frontend

---

## Utilitários teste

**Ficheiro:** `src/test/java/com/fitvision/testutil/TestDataBuilder.java` — fixtures

---

## Issues conhecidos (auditoria)

| Item | Estado no código |
|------|------------------|
| `@Disabled` tests | **Nenhum encontrado** |
| Comentários FIXME test | **Nenhum** |
| ExcelSizeChartParserTest | Existe — correr `mvn test` para estado actual |
| RecommendationEngineTest | Existe — unit com mocks |
| failsafe-summary.xml local | Artefacto reporta 32 tests, 0 failures (snapshot workspace) |

**Nota user query:** mencionava issues Excel/RecommendationEngine — **confirmar no código** executando `mvn verify` no ambiente local; sem `@Disabled` ou TODO explícitos.

---

## Cobertura gaps (honesto)

| Área | Cobertura IT |
|------|--------------|
| Admin endpoints | **Não encontrado** *AdminControllerIT* |
| Billing Stripe | **Não encontrado** *BillingControllerIT* |
| Shopify connect | **Não encontrado** |
| ScraperService | **Não encontrado** |
| Dashboard E2E | **Não encontrado** |

---

## Boas práticas novos testes

1. IT → extend `AbstractIntegrationTest`
2. Usar `TestDataBuilder` para stores/products
3. Limpar dados por teste — evitar dependência ordem
4. Widget IT → header `X-FitVision-Key`
5. Admin IT → JWT com role ADMIN

---

## Divergências

ProjectContext pode referir cobertura >80% — **sem plugin JaCoCo encontrado** no `pom.xml` lido (confirmar no código).
