# Stan implementacji systemu RMA — neopak.pl

## Wyniki testow

| Modul | Testy | Status |
|---|---|---|
| `rma-domain` | **104** | PASS |
| `rma-application` | **39** | PASS |
| **Lacznie** | **143** | **BUILD SUCCESS** |

---

## Historia commitow (24 commits)

```
9e825ac fix(payment): move @EnableConfigurationProperties to RmaApplication, add @Autowired to disambiguate constructor
cc08e3a test(payment): add PayURestAdapterTest with WireMock (session, signature, refund, OAuth cache)
acf8198 chore(config): add payu credentials and base-url to application.yml
a0d8730 feat(payment): add PayURestAdapter with OAuth token cache and MD5 webhook validation
cc6109a feat(payment): add PaymentGateway output port to domain
a3b781c test(security): add JwtTokenProviderTest and JwtAuthFilterTest
41c14fe chore(config): add jwt.secret and jwt.expiration-ms to application.yml
0920420 feat(config): add SecurityConfig with stateless JWT-based filter chain
a9d3aa7 feat(security): add JwtUser, JwtTokenProvider and JwtAuthFilter
49ab799 feat(persistence): add JPA adapter for ReturnRequest with Testcontainers integration test
fa31be6 fix(db): renumber identity_access migrations to V10-V12 to avoid Flyway version conflicts
cb058e0 feat(domain): add reconstruct factory methods to ReturnRequest, Shipment and ReturnLineItem
15e861d feat(db): add Flyway migrations for return_management and identity_access schemas
bec2ac1 feat(app): implement AssessConditionService and MakeRefundDecisionService
753d38d feat(app): implement ReceiveShipmentService with blind return fallback
35bf045 feat(app): implement ConfirmPaymentService and GenerateLabelService (multi-package support)
e2c4d75 feat(app): implement CreateReturnRequestService with duplicate detection
91bf2c2 feat(domain): add ReturnRequest aggregate with Shipment and ReturnLineItem entities
7eeed34 feat(domain): add hexagonal ports (use cases, output ports) and remaining domain exceptions
2161787 feat(domain): add domain events (ReturnRequestCreated, ReturnLabelGenerated, RefundDecisionMade, etc.)
d0d3095 feat(domain): add core value objects (RmaNumber, PackageDimensions, ShippingCostSplit, SlaDeadline, CustomerInfo)
513711e feat(domain): add ReturnStatus state machine with InvalidStatusTransitionException
0fcedfb docs: add project plan, RMA lifecycle diagrams and agent instructions
75873ec chore: initialize Maven multi-module project (rma-domain, rma-application)
```

---

## Zrealizowane fragmenty planu

### Faza 0 — Szkielet (DONE)
- Maven multi-module: `rma-system` > `rma-domain` + `rma-application`
- `docker-compose.yml` z PostgreSQL 16
- `application.yml` + `application-test.yml` (Testcontainers JDBC)
- `RmaApplication.java` (Spring Boot entry point)

### Faza 1 — Domain Model (DONE)

**Fragment 1.1 — ReturnStatus**
- Enum z pelna maszyna stanow (10 statusow)
- `canTransitionTo()` / `transitionTo()` z `InvalidStatusTransitionException`
- Testy: 19 scenariuszy (wszystkie poprawne i niepoprawne przejscia)

**Fragment 1.2–1.6 — Value Objects**
- `RmaNumber` (format ZWR-NNNNN, walidacja regex)
- `PackageDimensions` + `CourierCode` (limity per kurier: InPost, DPD, GLS, Orlen, Geis)
- `ShippingCostSplit` (podzial kosztow w groszach, zaokraglenie ceil)
- `SlaDeadline` (14 dni od przyjecia, `isBreached()`, `daysRemaining()`)
- `CustomerInfo` (walidacja email, `pseudonymize()` RODO)
- `OrderReference` / `SourceSystem` / `ConditionAssessment` / `RefundDecision` / `ReturnReason`

**Fragment 1.7 — Zdarzenia domenowe** (10 eventow jako Java records)
- `ReturnRequestCreated`, `ReturnLabelPaymentRequested`, `ReturnLabelGenerated`
- `ReturnShipmentReceived`, `ReturnConditionAssessed`, `RefundDecisionMade`
- `ReturnRejected`, `ReturnCompleted`, `SlaBreachWarningTriggered`, `BlindReturnRegistered`

**Fragment 1.8 — Porty (interfejsy)**
- Porty wejsciowe (use cases): `CreateReturnRequestUseCase`, `ConfirmPaymentUseCase`, `GenerateLabelUseCase`, `ReceiveShipmentUseCase`, `AssessConditionUseCase`, `MakeRefundDecisionUseCase`, `RegisterBlindReturnUseCase`, `QueryReturnRequestUseCase`
- Porty wyjsciowe: `ReturnRequestRepository`, `RmaNumberGenerator`, `DomainEventPublisher`, `PhotoStoragePort`, `SlaClockPort`, `CourierGateway`, `PaymentGateway`, `ShipmentTrackingRepository`

**Fragment 1.9 — Agregat ReturnRequest**
- Klasy: `ReturnRequest` (aggregate root), `ReturnLineItem`, `Shipment`, `ReturnRequestId`
- Pelna maszyna stanow zintegrowana ze statusami
- Blokada generowania etykiety przed platnoscia (`LabelGenerationBeforePaymentException`)
- Sciezka slepego zwrotu (`BLIND_RECEIVED`)
- Wielopaczkowosc (N paczek = N etykiet)
- Factory methods `reconstruct()` dla persystencji (bez refleksji)
- Testy: 18 scenariuszy

### Faza 2 — Application Services (DONE)

| Serwis | Testy | Opis |
|---|---|---|
| `CreateReturnRequestService` | 3 | happy path, duplikat, zwracany numer RMA |
| `ConfirmPaymentService` | 3 | potwierdzenie platnosci, idempotencja |
| `GenerateLabelService` | 4 | brak platnosci throws, single, multi-package |
| `ReceiveShipmentService` | 3 | przyjecie, nieznany tracking -> blind return, partial |
| `RegisterBlindReturnService` | — | serwis pomocniczy |
| `AssessConditionService` | 3 | ocena stanu, not found, wiele pozycji |
| `MakeRefundDecisionService` | 4 | REFUND_AND_RETURN, REFUND_AND_DISPOSE, REJECTION, not found |

### Faza 3 — Infrastruktura (DONE: 3.1–3.4)

**Fragment 3.1 — Migracje SQL Flyway**
- Schemat `return_management`: V1–V4 (schema, return_requests, line_items, shipments)
- Schemat `identity_access`: V10–V12 (schema, users, audit_log)
- Optymistic locking (`version BIGINT`), UUID klucze, CHECK constraints na enumach, indeksy

**Fragment 3.2 — JPA Persistence Adapter**
- `ReturnRequestJpaEntity` + `ReturnLineItemJpaEntity` + `ShipmentJpaEntity`
- `ReturnRequestMapper` (mapowanie domain <-> JPA bez refleksji)
- `ReturnRequestPersistenceAdapter` implementujacy port `ReturnRequestRepository`
- `TestStubsConfig` — no-op stuby dla testow integracyjnych
- Testy: 6 scenariuszy Testcontainers (PostgreSQL 16): save/find, shipments, state changes, duplikaty, findByStatuses

**Fragment 3.3 — SecurityConfig + JWT**
- `JwtTokenProvider` — HS256, konfiguracja przez `jwt.secret` / `jwt.expiration-ms`
- `JwtAuthFilter` — `OncePerRequestFilter`, ustawia `ROLE_<rola>` w SecurityContextHolder
- `JwtUser` — record pomocniczy
- `SecurityConfig` — STATELESS, `@EnableMethodSecurity`, publiczne: `/api/v1/auth/**`, `/swagger-ui/**`, `/api-docs/**`
- Testy: 5x `JwtTokenProviderTest` (czyste jednostkowe) + 3x `JwtAuthFilterTest` (MockHttpServlet)

**Fragment 3.4 — PayURestAdapter**
- `PaymentGateway` — port domenowy (`createPaymentSession`, `validateWebhookSignature`, `refund`)
- `PayURestAdapter` — RestClient, OAuth client_credentials z cache tokenu, MD5 sygnatura webhooka
- `PayUProperties` — `@ConfigurationProperties(prefix = "payu")`
- Testy: 5 scenariuszy WireMock (tworzenie sesji, podpis valid/invalid, refund, idempotencja OAuth)

---

## Struktura plikow (produkcja)

```
rma-domain/src/main/java/pl/neopak/rma/returnmanagement/
├── domain/
│   ├── event/         (11 plikow — zdarzenia domenowe)
│   ├── exception/     (3 wyjatki domenowe)
│   └── model/         (16 klas — agregat, VO, enums)
└── port/
    ├── in/            (9 interfejsow — use cases + command)
    └── out/           (8 interfejsow — repozytoria i bramki)

rma-application/src/main/java/pl/neopak/rma/
├── RmaApplication.java
├── config/
│   └── SecurityConfig.java
├── security/
│   ├── JwtAuthFilter.java
│   ├── JwtTokenProvider.java
│   └── JwtUser.java
├── payment/adapter/out/
│   ├── PayUProperties.java
│   └── PayURestAdapter.java
└── returnmanagement/
    ├── adapter/out/persistence/
    │   ├── ReturnLineItemJpaEntity.java
    │   ├── ReturnRequestJpaEntity.java
    │   ├── ReturnRequestJpaRepository.java
    │   ├── ReturnRequestMapper.java
    │   ├── ReturnRequestPersistenceAdapter.java
    │   └── ShipmentJpaEntity.java
    └── service/
        ├── AssessConditionService.java
        ├── ConfirmPaymentService.java
        ├── CreateReturnRequestService.java
        ├── DuplicateReturnException.java
        ├── GenerateLabelService.java
        ├── MakeRefundDecisionService.java
        ├── ReceiveShipmentService.java
        └── RegisterBlindReturnService.java
```

---

## Co pozostalo do zrobienia (zgodnie z planem)

### Faza 3 — Infrastruktura
- **3.5** — `InPostRestAdapter` + `DpdRestAdapter` z WireMock

### Faza 4 — Web Layer
- **4.1** — `CustomerPortalController` (POST /returns, GET /returns/{rma}, POST payment, POST photos)
- **4.2** — `WarehouseController` (receive, condition, decision, lista z filtrowaniem)
- **4.3** — `ReturnFlowIntegrationTest` — E2E: Testcontainers PostgreSQL + WireMock PayU + WireMock InPost

### Poza zakresem MVP (faza 2/3)
- Allegro, eMag, Temu, BaseLinker adaptery
- Eksport Elixir-0 (zwroty bankowe)
- Korekty w Subiekcie ERP (KFS/ZK)
- GLS, Orlen, Geis adaptery kurierskie
- Kafka (zamiana ApplicationEventPublisher)
- Redis (cache rate card, idempotencja webhookow)
- Raportowanie (CQRS projekcje)
