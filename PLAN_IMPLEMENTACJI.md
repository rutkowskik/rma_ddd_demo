# Plan implementacji systemu RMA (neopak.pl) - DDD + TDD

## Context

Stworzenie od zera dedykowanego systemu RMA dla neopak.pl. System automatyzuje cały łańcuch zwrotów:
**Klient → Magazyn → ERP (Subiekt) → Księgowość → Zwrot środków**

Stack: Java 21 + Spring Boot 3.x + PostgreSQL + Maven multi-module

---

## 1. Mapa Bounded Contexts

### Core Domain
- **ReturnManagement** - cykl życia zgłoszenia zwrotu: od złożenia przez klienta, przez obsługę magazynu, do decyzji i zakończenia. Tu koncentruje się unikalna logika biznesowa neopak.pl.

### Supporting Domains (Custom)
- **FinancialSettlement** - rejestr należnych zwrotów, eksport Elixir-0, automatyczne refundy
- **SourceAggregator** - normalizacja zwrotów z Allegro, eMag, Temu, BaseLinker, formularza neopak.pl

### Supporting Domains (Generic)
- **CourierIntegration** - ACL nad DPD, GLS, InPost, Orlen, Geis; generowanie etykiet, kalkulacja kosztów
- **PaymentIntegration** - ACL nad PayU / Przelewy24; sesje płatności, webhooki, zwroty
- **ERPIntegration** - ACL nad Subiekt/Sfera; wyszukiwanie FS/PA/ZK, generowanie KFS/ZK, flagi
- **IdentityAndAccess** - autentykacja JWT, role, logi audytu, RODO
- **Notifications** - reaktywne wysyłanie e-maili po zdarzeniach domenowych
- **Reporting** - projekcje CQRS dla statystyk i raportów

---

## 2. Kluczowe Agregaty

### ReturnManagement — agregat `ReturnRequest` (root)

**Encje wewnątrz agregatu:**
- `ReturnLineItem` - jeden wiersz per produkt; trzyma `ProductId`, `Quantity`, `ReturnReason`, `ConditionAssessment`
- `Shipment` - jedna lub wiele per zwrot (multi-package); trzyma `TrackingNumber`, `CourierId`, `LabelUrl`, `WeightKg`, `DimensionsCm`
- `Photo` - zdjęcia dołączone do zgłoszenia lub konkretnego `ReturnLineItem`

**Value Objects:**
- `RmaNumber` - format `ZWR-NNNNN`, generacja enkapsulowana w VO
- `ReturnStatus` - enum z logiką guard transition
- `ReturnReason` - enum: `DAMAGED`, `WRONG_ITEM`, `CHANGED_MIND`, `INCOMPLETE`, ...
- `ConditionAssessment` - enum: `NEW`, `DAMAGED`, `INCOMPLETE`, `FOR_RESALE`, `FOR_WRITEOFF`
- `OrderReference` - `orderId` + `sourceSystem` (NEOPAK, ALLEGRO, EMAG, TEMU, BASELINKER)
- `CustomerInfo` - email, name (pseudonimizowalne pod RODO)
- `PackageDimensions` - length/width/height/weight z logiką eligibility kuriera
- `ShippingCostSplit` - koszt całkowity, udział klienta (50%), udział sklepu
- `RefundDecision` - enum: `REFUND_AND_RETURN`, `REFUND_AND_DISPOSE`, `REJECTION`
- `SlaDeadline` - wraps `LocalDate` z metodą `isBreached()`

**Maszyna stanów `ReturnStatus`:**
```
PENDING_SHIPMENT
    └─► IN_TRANSIT
            └─► RECEIVED
                    └─► VERIFICATION
                              └─► DECISION
                                    ├─► REJECTED
                                    ├─► REFUND_AND_DISPOSE ──► COMPLETED
                                    └─► AWAITING_REFUND ──────► COMPLETED

BLIND_RECEIVED (wejście dla ślepych zwrotów)
    └─► VERIFICATION (dalej jak wyżej)
```

**Invarianty agregatu:**
- Etykieta kuriera NIE może być wygenerowana przed potwierdzeniem płatności → `LabelGenerationBeforePaymentException`
- Przejścia statusów wymuszone przez maszynę stanów → `InvalidStatusTransitionException`
- "Ślepe zwroty" startują w stanie `BLIND_RECEIVED`
- Agregat akumuluje `List<DomainEvent>`, czyszczone po publikacji

### Pozostałe agregaty

| Agregat | Context | Klucz | Opis |
|---|---|---|---|
| `PaymentSession` | PaymentIntegration | `PaymentSessionId` | Status INITIATED/PAID/FAILED/REFUNDED, kwota w **groszach** (int, nie float) |
| `ShipmentOrder` | CourierIntegration | `ShipmentOrderId` | Etykiety PDF, tracking, per paczka |
| `RefundSettlement` | FinancialSettlement | `RefundSettlementId` | Rekord Elixir-0, status eksportu |
| `ErpDocument` | ERPIntegration | `ErpDocumentId` | Synchronizacja z Subiektem, numer korekty |
| `User` | IdentityAndAccess | `UserId` | Role, audit log z IP |
| `NotificationLog` | Notifications | `NotificationId` | Idempotencja wysyłki, audit |

---

## 3. Kluczowe Zdarzenia Domenowe

### ReturnManagement emituje:
| Zdarzenie | Wyzwalacz |
|---|---|
| `ReturnRequestCreated` | złożenie zgłoszenia |
| `ReturnLabelPaymentRequested` | inicjacja płatności za etykietę |
| `ReturnLabelGenerated` | po PaymentConfirmed + wygenerowanie etykiety |
| `ReturnShipmentReceived` | przyjęcie paczki przez magazyn |
| `ReturnConditionAssessed` | ocena stanu towaru |
| `RefundDecisionMade` | decyzja BOK / kierownika |
| `ReturnRejected` | odrzucenie zwrotu |
| `ReturnCompleted` | zakończenie procesu |
| `SlaBreachWarningTriggered` | flagowanie 14-dniowe |
| `BlindReturnRegistered` | rejestracja ślepego zwrotu |

### PaymentIntegration emituje:
`PaymentSessionCreated`, `PaymentConfirmed`, `PaymentFailed`, `RefundInitiated`, `RefundConfirmed`

### CourierIntegration emituje:
`ShipmentLabelCreated`, `ShipmentStatusUpdated`

### SourceAggregator emituje:
`ExternalReturnRequestNormalized`

### ERPIntegration emituje:
`ErpDocumentFound`, `ErpCorrectionGenerated`

### FinancialSettlement emituje:
`RefundSettlementCreated`, `ElixirBatchExported`

---

## 4. Anti-Corruption Layers

Każdy system zewnętrzny ma własny adapter implementujący port (interface domenowy):

```
CourierGateway (port)
    ├── DpdRestAdapter
    ├── GlsRestAdapter
    ├── InPostRestAdapter
    ├── OrlenRestAdapter
    └── GeisRestAdapter

PaymentGateway (port)
    ├── PayURestAdapter
    └── Przelewy24RestAdapter

ExternalReturnSource (port)
    ├── AllegroReturnAdapter
    ├── EMagReturnAdapter
    ├── TemuCsvImportAdapter
    ├── BaseLinkReturnAdapter
    └── NeopakFormAdapter

ErpGateway (port)
    ├── SubiektSferaAdapter     (COM/Sfera - bezpośrednia integracja)
    └── SubiektRestAdapter      (jeśli wewnętrzny programista wystawi REST)
```

---

## 5. Architektura modułowa (Hexagonal + Maven multi-module)

```
rma-system/
│
├── pom.xml   (parent POM)
│
├── rma-domain/                    ← czysta Java 21, ZERO Spring, ZERO JPA
│   └── src/main/java/pl/neopak/rma/
│       ├── returnmanagement/
│       │   ├── domain/
│       │   │   ├── model/         ReturnRequest, ReturnLineItem, Shipment, Photo
│       │   │   │                  + wszystkie Value Objects
│       │   │   ├── event/         ReturnRequestCreated, ReturnLabelGenerated, ...
│       │   │   └── exception/     InvalidStatusTransitionException, ...
│       │   ├── port/
│       │   │   ├── in/            CreateReturnRequestUseCase, ReceiveShipmentUseCase, ...
│       │   │   └── out/           ReturnRequestRepository, DomainEventPublisher, ...
│       │   └── service/           ReturnRequestService, SlaEnforcementService
│       │
│       ├── courier/domain/ + port/
│       ├── payment/domain/ + port/
│       ├── financial/domain/ + port/
│       ├── identity/domain/ + port/
│       └── notification/domain/ + port/
│
└── rma-application/               ← Spring Boot 3.x, wires everything
    └── src/main/java/pl/neopak/rma/
        ├── RmaApplication.java
        ├── config/
        │   ├── SecurityConfig.java
        │   ├── KafkaConfig.java       (faza 2; MVP: ApplicationEventPublisher)
        │   ├── RedisConfig.java       (faza 2)
        │   └── OpenApiConfig.java
        │
        ├── returnmanagement/adapter/
        │   ├── in/
        │   │   ├── web/
        │   │   │   ├── CustomerPortalController.java
        │   │   │   ├── WarehouseController.java
        │   │   │   └── dto/           (request/response DTOs - oddzielne od domeny)
        │   │   └── messaging/
        │   │       └── ExternalReturnRequestListener.java
        │   └── out/
        │       ├── persistence/
        │       │   ├── ReturnRequestJpaRepository.java
        │       │   ├── ReturnRequestPersistenceAdapter.java
        │       │   ├── ReturnRequestMapper.java    ← KLUCZOWE: domain ↔ JPA entity
        │       │   └── entity/                    (JPA entities - ODDZIELONE od domeny)
        │       ├── messaging/
        │       │   └── SpringApplicationEventPublisher.java  (MVP)
        │       └── storage/
        │           └── S3PhotoStorageAdapter.java
        │
        ├── courier/adapter/out/
        │   ├── DpdRestAdapter.java
        │   ├── GlsRestAdapter.java
        │   ├── InPostRestAdapter.java
        │   ├── OrlenRestAdapter.java
        │   └── GeisRestAdapter.java
        │
        ├── payment/adapter/
        │   ├── in/web/PaymentWebhookController.java
        │   └── out/
        │       ├── PayURestAdapter.java
        │       └── Przelewy24RestAdapter.java
        │
        ├── sourceaggregator/adapter/
        │   ├── in/scheduler/ExternalSourcePollingScheduler.java
        │   └── out/
        │       ├── AllegroReturnAdapter.java
        │       ├── EMagReturnAdapter.java
        │       ├── TemuCsvImportAdapter.java
        │       └── BaseLinkReturnAdapter.java
        │
        ├── erp/adapter/out/
        │   ├── SubiektSferaAdapter.java
        │   └── SubiektRestAdapter.java
        │
        └── financial/adapter/
            ├── in/web/AccountingController.java
            └── out/ElixirFileExportAdapter.java
```

**Kluczowa zasada:** Encje JPA (`entity/`) są ODDZIELONE od modelu domenowego. `ReturnRequestMapper` tłumaczy w obie strony. Domenowy `ReturnRequest` nigdy nie ma adnotacji `@Entity`.

---

## 6. Strategia bazy danych

**Jeden PostgreSQL, osobny schemat per bounded context:**

```
Database: rma_db
├── Schema: return_management      ← return_requests, return_line_items, shipments, photos
├── Schema: courier_integration    ← shipment_orders, courier_rate_cards
├── Schema: payment_integration    ← payment_sessions, payment_webhooks_log (idempotencja)
├── Schema: financial_settlement   ← refund_settlements, elixir_batch_exports
├── Schema: erp_integration        ← erp_document_sync_log
├── Schema: identity_access        ← users, audit_log
├── Schema: notifications          ← notification_log
└── Schema: reporting              ← denormalizowane projekcje CQRS
```

**Zasady:**
- Brak FK constraints między schematami - spójność przez zdarzenia domenowe
- `rma_number` (VARCHAR) jako jedyny klucz korelacji cross-schema (nie FK, tylko string)
- Optymistyczne blokowanie: kolumna `version BIGINT` + `@Version` na każdym aggregate root JPA entity
- Flyway per schemat: `src/main/resources/db/migration/return_management/V1__create_return_requests.sql`
- Każdy schemat ma własną tabelę historii: `{schema}.flyway_schema_history`

**Redis (faza 2):**
- `CourierRateCard` cache (TTL 1h) - unikanie powtarzanych odczytów podczas sesji portalu klienta
- Klucze idempotencji webhooków płatności (TTL 24h)

---

## 7. Strategia integracji cross-context

| Od | Do | Mechanizm | Uzasadnienie |
|---|---|---|---|
| ReturnManagement | PaymentIntegration | Async `ReturnLabelPaymentRequested` | Płatność jest async z natury |
| PaymentIntegration | ReturnManagement | Async `PaymentConfirmed` | Webhook-driven, nie może blokować |
| ReturnManagement | CourierIntegration | Sync (faza 1), Async (faza 2) | Etykieta potrzebna inline dla klienta |
| ReturnManagement | Notifications | Async, fire-and-forget | Błąd e-mail nie może failować zwrot |
| ReturnManagement | ERPIntegration | Sync przez port | Wyszukiwanie dokumentu wymaga odpowiedzi |
| SourceAggregator | ReturnManagement | Async `ExternalReturnRequestNormalized` | Ingestion jest batch/scheduled |
| ReturnManagement | FinancialSettlement | Async `RefundDecisionMade` | Settlement to proces tła |

**MVP uproszczenie:** `ApplicationEventPublisher` (Spring, in-process) zamiast Kafka. Port `DomainEventPublisher` izoluje tę decyzję - zamiana na Kafka w fazie 2 dotyka TYLKO adaptera infrastrukturalnego, nie domeny.

### Saga: generowanie etykiety (choreography-based)

```
1. ReturnManagement emituje: ReturnLabelPaymentRequested
2. PaymentIntegration: tworzy sesję PayU → zwraca redirect URL klientowi
3. Klient płaci w PayU
4. PayU wywołuje webhook → PaymentIntegration waliduje sygnaturę
5. PaymentIntegration emituje: PaymentConfirmed
6. ReturnManagement odbiera PaymentConfirmed → wywołuje CourierIntegration (sync)
7. CourierIntegration generuje N etykiet PDF (multi-package)
8. ReturnManagement emituje: ReturnLabelGenerated
9. Notifications odbiera → wysyła e-mail do klienta z linkami do PDF
```

---

## 8. Scope MVP (Faza 1)

### W zakresie MVP (8-12 tygodni):
- [ ] Portal klienta: logowanie po nr zamówienia + email, wybór produktów z powodem, upload zdjęć
- [ ] Kalkulacja gabarytów i eligibility kurierów (InPost + DPD)
- [ ] Multi-package: algorytm podziału na N paczek → N etykiet PDF
- [ ] Integracja PayU: płatność 50% przez klienta, etykieta generowana dopiero po SUCCESS
- [ ] Pełna maszyna stanów `ReturnRequest`
- [ ] Panel magazyniera (desktop-first): przyjęcie, ocena stanu, decyzja + upload zdjęć z telefonu
- [ ] Integracja Subiekt: wyszukiwanie FS/PA/ZK (bez generowania korekt)
- [ ] Alert 14-dniowy: scheduler flagujący zbliżające się terminy
- [ ] E-maile na kluczowe statusy: zgłoszenie, przyjęcie, decyzja
- [ ] Role RBAC: CUSTOMER, WAREHOUSE_WORKER, WAREHOUSE_MANAGER, BOK, ACCOUNTING, ADMIN
- [ ] Audit log: userId + IP + timestamp per każda akcja
- [ ] OpenAPI dokumentacja (`/swagger-ui.html`)
- [ ] Flyway migracje

### Poza MVP - Faza 2 (4-6 tygodni):
- [ ] Ślepe zwroty (`BLIND_RECEIVED`)
- [ ] Multi-package od strony magazynu (jeden RMA w kilku transzach)
- [ ] Eksport Elixir-0 dla Santander / Pekao S.A.
- [ ] Korekty KFS/ZK w Subiekcie (w tym korekty częściowe)
- [ ] GLS, Orlen, Geis (palety)
- [ ] Przelewy24
- [ ] BaseLinker API
- [ ] Responsywny UI magazyniera (RWD, tablety, kolektory Android + skanery BT/USB)
- [ ] Redis cache
- [ ] Zamiana `ApplicationEventPublisher` → Kafka

### Faza 3 (4-6 tygodni):
- [ ] Allegro API
- [ ] eMag API
- [ ] Temu CSV import
- [ ] Moduł raportów (CQRS read model, projekcje Kafka)
- [ ] Automatyczne zwroty API PayU / Przelewy24
- [ ] RODO: pseudonimizacja `CustomerInfo`
- [ ] BI-ready reporting endpoints

---

## 9. Strategia testowania - podział Spock / JUnit 5

| Warstwa | Framework | Uzasadnienie |
|---|---|---|
| Value Objects (VO) | **Spock** | `where:` tables eliminują powtarzalny boilerplate przy wielu kombinacjach wejsc |
| Agregat `ReturnRequest` | **Spock** | `given/when/then` mapuje 1:1 na język DDD i ubiquitous language |
| Use Cases (Sprint 1) | **Spock** | Wbudowane `Mock()` / `Stub()` zastępują Mockito, `thrown()` jest czytelniejszy |
| Domain Services | **Spock** | Jak wyżej |
| Persistence (Testcontainers) | **JUnit 5** | Brak przewagi Spocka, standardowa integracja Spring |
| Web layer (MockMvc) | **JUnit 5** | Adnotacje Spring (`@WebMvcTest`) działają bez dodatkowej konfiguracji |
| Full E2E integration | **JUnit 5** | Łatwe do utrzymania, znajome dla każdego Java dewelopera |

**Konfiguracja Spock w `rma-domain/pom.xml`:**
```xml
<dependency>
    <groupId>org.spockframework</groupId>
    <artifactId>spock-core</artifactId>
    <version>2.4-M4-groovy-4.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.x</version>
    <scope>test</scope>
</dependency>
```

**Konfiguracja Spock w `rma-application/pom.xml`** (dla use case tests):
```xml
<!-- spock-core jak wyżej, plus: -->
<dependency>
    <groupId>org.spockframework</groupId>
    <artifactId>spock-spring</artifactId>
    <version>2.4-M4-groovy-4.0</version>
    <scope>test</scope>
</dependency>
```

**Plugin Maven kompilujący Groovy razem z Javą:**
```xml
<plugin>
    <groupId>org.codehaus.gmavenplus</groupId>
    <artifactId>gmavenplus-plugin</artifactId>
    <version>3.0.2</version>
    <executions>
        <execution>
            <goals><goal>addTestSources</goal><goal>compileTests</goal></goals>
        </execution>
    </executions>
</plugin>
```

Pliki Spock umieszczamy w `src/test/groovy/` (obok `src/test/java/`). Maven Surefire 3.x wykrywa oba automatycznie.

---

## 10. Kolejność implementacji TDD

### Sprint 0 - Pure Domain (Spock, ZERO Spring, ZERO zewnętrznych zależności)

**Krok 1: Value Objects** — `where:` bloki Spocka eliminują 20 osobnych metod testowych:

```groovy
// ReturnStatusSpec.groovy
class ReturnStatusSpec extends Specification {

    def "valid and invalid status transitions"() {
        expect:
        from.canTransitionTo(to) == allowed

        where:
        from                 | to                  || allowed
        PENDING_SHIPMENT     | IN_TRANSIT          || true
        PENDING_SHIPMENT     | RECEIVED            || false
        PENDING_SHIPMENT     | COMPLETED           || false
        IN_TRANSIT           | RECEIVED            || true
        IN_TRANSIT           | PENDING_SHIPMENT    || false
        RECEIVED             | VERIFICATION        || true
        RECEIVED             | COMPLETED           || false
        VERIFICATION         | DECISION            || true
        VERIFICATION         | RECEIVED            || false
        DECISION             | AWAITING_REFUND     || true
        DECISION             | REJECTED            || true
        DECISION             | REFUND_AND_DISPOSE  || true
        DECISION             | COMPLETED           || false
        AWAITING_REFUND      | COMPLETED           || true
        AWAITING_REFUND      | DECISION            || false
        BLIND_RECEIVED       | VERIFICATION        || true
        BLIND_RECEIVED       | RECEIVED            || false
    }

    def "invalid transition throws InvalidStatusTransitionException"() {
        when:
        PENDING_SHIPMENT.transitionTo(COMPLETED)

        then:
        thrown(InvalidStatusTransitionException)
    }
}
```

```groovy
// PackageDimensionsSpec.groovy
class PackageDimensionsSpec extends Specification {

    def "courier eligibility based on weight and dimensions"() {
        expect:
        new PackageDimensions(weightKg, lengthCm, widthCm, heightCm)
            .isSuitableForCourier(courier) == eligible

        where:
        weightKg | lengthCm | widthCm | heightCm | courier || eligible
        20       | 60       | 40      | 40       | INPOST  || true
        25       | 60       | 40      | 40       | INPOST  || true   // granica
        26       | 60       | 40      | 40       | INPOST  || false  // przekroczony max 25kg
        30       | 100      | 60      | 60       | DPD     || true
        31       | 100      | 60      | 60       | DPD     || false  // max 30kg DPD
        150      | 120      | 80      | 80       | GEIS    || true   // paleta
        150      | 120      | 80      | 80       | DPD     || false  // zbyt ciężka dla DPD
        150      | 120      | 80      | 80       | INPOST  || false
        10       | 40       | 30      | 20       | INPOST  || true
        10       | 40       | 30      | 20       | DPD     || true
    }

    def "dimensions with zero weight are invalid"() {
        when:
        new PackageDimensions(0, 60, 40, 40)

        then:
        thrown(IllegalArgumentException)
    }
}
```

```groovy
// ShippingCostSplitSpec.groovy
class ShippingCostSplitSpec extends Specification {

    def "50% split rounding always favours the store (customer pays ceiling)"() {
        expect:
        new ShippingCostSplit(totalGrosze, 50).customerShare() == expectedCustomerGrosze

        where:
        totalGrosze || expectedCustomerGrosze
        2000        || 1000   // równy podział
        1999        || 1000   // nieparzysty — klient płaci ceil
        1001        || 501
        1           || 1      // minimum 1 grosz
        0           || 0      // darmowy zwrot (błąd sklepu)
    }

    def "store share is total minus customer share"() {
        given:
        def split = new ShippingCostSplit(1999, 50)

        expect:
        split.storeShare() == 999
        split.customerShare() + split.storeShare() == 1999
    }
}
```

```groovy
// SlaDeadlineSpec.groovy
class SlaDeadlineSpec extends Specification {

    def "isBreached returns correct result relative to fixed clock"() {
        given:
        def deadline = SlaDeadline.of(LocalDate.of(2026, 4, 15))

        expect:
        deadline.isBreached(fixedClock) == breached

        where:
        fixedClock                                           || breached
        clockAt(LocalDate.of(2026, 4, 14))                  || false  // dzień przed
        clockAt(LocalDate.of(2026, 4, 15))                  || false  // dokładnie deadline
        clockAt(LocalDate.of(2026, 4, 16))                  || true   // dzień po
        clockAt(LocalDate.of(2026, 5, 1))                   || true   // tydzień po
    }

    def "daysRemaining at boundary values"() {
        given:
        def deadline = SlaDeadline.of(LocalDate.of(2026, 4, 15))

        expect:
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 12))) == 3
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 15))) == 0
        deadline.daysRemaining(clockAt(LocalDate.of(2026, 4, 16))) == -1

    }

    private static Clock clockAt(LocalDate date) {
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    }
}
```

```groovy
// RmaNumberSpec.groovy
class RmaNumberSpec extends Specification {

    def "RmaNumber matches expected format ZWR-NNNNN"() {
        expect:
        RmaNumber.of("ZWR-00001").value() == "ZWR-00001"
        RmaNumber.of("ZWR-99999").value() == "ZWR-99999"
    }

    def "invalid formats are rejected"() {
        when:
        RmaNumber.of(invalid)

        then:
        thrown(IllegalArgumentException)

        where:
        invalid << ["ZWR-1", "ZWR-000001", "RMA-00001", "", null, "ZWR-ABCDE"]
    }

    def "two RmaNumbers with same value are equal"() {
        expect:
        RmaNumber.of("ZWR-00001") == RmaNumber.of("ZWR-00001")
        RmaNumber.of("ZWR-00001") != RmaNumber.of("ZWR-00002")
    }
}
```

**Krok 2: Agregat `ReturnRequest`** — `given/when/then` odzwierciedla język domenowy:

```groovy
// ReturnRequestSpec.groovy
class ReturnRequestSpec extends Specification {

    def "creating a return request emits ReturnRequestCreated event"() {
        given:
        def orderRef = new OrderReference("ORDER-001", SourceSystem.NEOPAK)
        def customer = CustomerInfo.of("jan@example.com", "Jan Kowalski")

        when:
        def rma = ReturnRequest.create(RmaNumber.of("ZWR-00001"), orderRef, customer)

        then:
        def events = rma.pullEvents()
        events.size() == 1
        events[0] instanceof ReturnRequestCreated
        (events[0] as ReturnRequestCreated).rmaNumber() == RmaNumber.of("ZWR-00001")
        (events[0] as ReturnRequestCreated).customerEmail() == "jan@example.com"
    }

    def "label generation before payment confirmation is forbidden"() {
        given:
        def rma = aReturnRequest()   // helper method, status = PENDING_SHIPMENT

        when:
        rma.generateLabel()

        then:
        thrown(LabelGenerationBeforePaymentException)
    }

    def "confirming payment transitions status and enables label generation"() {
        given:
        def rma = aReturnRequest()

        when:
        rma.confirmPayment(PaymentSessionId.of("PAY-123"))

        then:
        rma.status() == ReturnStatus.IN_TRANSIT
        rma.pullEvents().any { it instanceof ReturnLabelPaymentRequested }
    }

    def "receiving a shipment records warehouse arrival and starts SLA clock"() {
        given:
        def rma = aReturnRequestInTransit()
        def receivedAt = Instant.parse("2026-04-01T10:00:00Z")

        when:
        rma.receiveShipment(TrackingNumber.of("DPD-XYZ-001"), receivedAt)

        then:
        rma.status() == ReturnStatus.RECEIVED
        rma.receivedAt() == receivedAt
        rma.slaDeadline() == SlaDeadline.of(LocalDate.of(2026, 4, 15))  // +14 dni
    }

    def "condition can only be assessed in VERIFICATION status"() {
        given:
        def rma = aReturnRequestInStatus(status)

        when:
        rma.assessCondition(ConditionAssessment.DAMAGED)

        then:
        if (shouldSucceed) noExceptionThrown()
        else thrown(InvalidStatusTransitionException)

        where:
        status              || shouldSucceed
        VERIFICATION        || true
        RECEIVED            || false
        DECISION            || false
        PENDING_SHIPMENT    || false
    }

    def "REFUND_AND_DISPOSE decision completes without requiring return courier"() {
        given:
        def rma = aReturnRequestInStatus(DECISION)

        when:
        rma.makeRefundDecision(RefundDecision.REFUND_AND_DISPOSE, 4999)

        then:
        rma.status() == ReturnStatus.COMPLETED
        def events = rma.pullEvents()
        events.any { it instanceof RefundDecisionMade }
        !(events.any { it instanceof ShipmentOrderRequested })
    }

    def "pullEvents clears the internal event list"() {
        given:
        def rma = aReturnRequest()

        when:
        def first = rma.pullEvents()
        def second = rma.pullEvents()

        then:
        first.size() == 1
        second.isEmpty()
    }

    def "blind return starts in BLIND_RECEIVED status"() {
        when:
        def rma = ReturnRequest.registerBlind(RmaNumber.of("ZWR-00002"), "Nieznana paczka, etykieta: DPD-ABC")

        then:
        rma.status() == ReturnStatus.BLIND_RECEIVED
        rma.pullEvents().any { it instanceof BlindReturnRegistered }
    }

    // --- helpers ---
    private ReturnRequest aReturnRequest() {
        ReturnRequest.create(
            RmaNumber.of("ZWR-00001"),
            new OrderReference("ORDER-001", SourceSystem.NEOPAK),
            CustomerInfo.of("jan@example.com", "Jan Kowalski")
        ).tap { pullEvents() }  // czyscimy zdarzenia z tworzenia
    }
}
```

**Krok 3: Domain Services:**

```groovy
// SlaEnforcementServiceSpec.groovy
class SlaEnforcementServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    Clock clock = Clock.fixed(Instant.parse("2026-04-16T08:00:00Z"), ZoneOffset.UTC)
    SlaEnforcementService service = new SlaEnforcementService(repository, clock)

    def "findBreachedReturns returns only returns past their 14-day deadline"() {
        given:
        repository.findByStatuses([RECEIVED, VERIFICATION, DECISION]) >> [
            aReturnWithDeadline(LocalDate.of(2026, 4, 15)),  // wczoraj — przeterminowany
            aReturnWithDeadline(LocalDate.of(2026, 4, 16)),  // dzisiaj — jeszcze ok
            aReturnWithDeadline(LocalDate.of(2026, 4, 20)),  // w przyszlosci
        ]

        when:
        def breached = service.findBreachedReturns()

        then:
        breached.size() == 1
        breached[0].slaDeadline().asLocalDate() == LocalDate.of(2026, 4, 15)
    }

    def "findWarningReturns returns returns with 3 or fewer days remaining"() {
        given:
        repository.findByStatuses(_) >> [
            aReturnWithDeadline(LocalDate.of(2026, 4, 16)),  // 0 dni — warning
            aReturnWithDeadline(LocalDate.of(2026, 4, 19)),  // 3 dni — warning (granica)
            aReturnWithDeadline(LocalDate.of(2026, 4, 20)),  // 4 dni — poza zakresem
        ]

        when:
        def warnings = service.findWarningReturns()

        then:
        warnings.size() == 2
    }
}
```

### Sprint 1 - Application Layer (Spock z wbudowanym Mock(), mock wszystkich portów)

**Krok 4: Use Case implementacje:**

```groovy
// CreateReturnRequestServiceSpec.groovy
class CreateReturnRequestServiceSpec extends Specification {

    ReturnRequestRepository repository = Mock()
    RmaNumberGenerator numberGenerator = Stub { generate() >> RmaNumber.of("ZWR-00001") }
    DomainEventPublisher eventPublisher = Mock()
    CreateReturnRequestService service = new CreateReturnRequestService(
        repository, numberGenerator, eventPublisher
    )

    def "happy path: valid request is saved and event is published"() {
        given:
        def command = new CreateReturnRequestCommand("ORDER-001", SourceSystem.NEOPAK, "jan@example.com")
        repository.existsByOrderReference(_) >> false

        when:
        def rmaNumber = service.create(command)

        then:
        rmaNumber == RmaNumber.of("ZWR-00001")
        1 * repository.save(_ as ReturnRequest)
        1 * eventPublisher.publish(_ as ReturnRequestCreated)
    }

    def "duplicate order reference on the same day throws DuplicateReturnException"() {
        given:
        repository.existsByOrderReference(_) >> true

        when:
        service.create(new CreateReturnRequestCommand("ORDER-001", SourceSystem.NEOPAK, "jan@example.com"))

        then:
        thrown(DuplicateReturnException)
        0 * repository.save(_)
        0 * eventPublisher.publish(_)
    }
}
```

```groovy
// GenerateLabelServiceSpec.groovy
class GenerateLabelServiceSpec extends Specification {

    CourierGateway courierGateway = Mock()
    ReturnRequestRepository repository = Mock()
    DomainEventPublisher eventPublisher = Mock()
    GenerateLabelService service = new GenerateLabelService(courierGateway, repository, eventPublisher)

    def "generates N labels for N packages"() {
        given:
        def rma = aReturnRequestWithPackages(packageCount)
        repository.findByRmaNumber(_) >> Optional.of(rma)
        courierGateway.createShipment(_) >> ShipmentOrderId.generate()

        when:
        def labels = service.generateLabels(RmaNumber.of("ZWR-00001"))

        then:
        labels.size() == packageCount
        packageCount * courierGateway.createShipment(_)

        where:
        packageCount << [1, 3, 5]
    }

    def "throws when payment not confirmed"() {
        given:
        def rma = aReturnRequestWithoutPayment()
        repository.findByRmaNumber(_) >> Optional.of(rma)

        when:
        service.generateLabels(RmaNumber.of("ZWR-00001"))

        then:
        thrown(LabelGenerationBeforePaymentException)
        0 * courierGateway.createShipment(_)
    }
}
```

### Sprint 2 - Infrastructure (JUnit 5 + Testcontainers)

**Krok 5: Persistence adapters** (real PostgreSQL via Testcontainers):

```java
// ReturnRequestPersistenceAdapterTest.java  ← JUnit 5
@Testcontainers
@SpringBootTest(classes = PersistenceTestConfig.class)
class ReturnRequestPersistenceAdapterTest {
    // save, findById, findByRmaNumber, optimistic locking
}
```

**Krok 6: Payment adapter** (WireMock):

```java
// PayURestAdapterTest.java  ← JUnit 5 + WireMock
@ExtendWith(WireMockExtension.class)
class PayURestAdapterTest {
    // initiateSession, webhook signature validation, refund, idempotency
}
```

**Krok 7: Courier adapters** (WireMock):

```java
// InPostRestAdapterTest.java  ← JUnit 5 + WireMock
// DpdRestAdapterTest.java     ← JUnit 5 + WireMock
```

**Krok 8: Full E2E integration test** (PostgreSQL + WireMock PayU + WireMock InPost):

```java
// ReturnFlowIntegrationTest.java  ← JUnit 5
// POST /api/v1/returns → ... → status AWAITING_REFUND → audit_log populated
```

### Sprint 3 - Web Layer (JUnit 5 + MockMvc)

**Krok 9: Controller tests:**

```java
// CustomerPortalControllerTest.java  ← JUnit 5 + @WebMvcTest
// WarehouseControllerTest.java       ← JUnit 5 + @WebMvcTest
// Security: 401/403 dla nieautoryzowanych
```

### Sprint 2 - Infrastructure (Testcontainers)

**Krok 5: Persistence adapters** (real PostgreSQL via Testcontainers):

```java
// @Testcontainers + @SpringBootTest(classes = PersistenceTestConfig.class)
ReturnRequestPersistenceAdapterTest
  ✓ save() + findById() → round-trip bez utraty danych
  ✓ findByRmaNumber("ZWR-00001") → Optional.of(...)
  ✓ findByRmaNumber("ZWR-99999") → Optional.empty()
  ✓ optimistic locking: dwa równoczesne save() → ObjectOptimisticLockingFailureException
  ✓ mapper poprawnie tłumaczy wszystkie VO na kolumny i z powrotem
```

**Krok 6: Payment adapter** (WireMock):

```java
PayURestAdapterTest
  ✓ initiateSession() → POST /api/v2_1/order stub → zwraca redirectUrl
  ✓ handleWebhook() z poprawną sygnaturą MD5 → PaymentConfirmed
  ✓ handleWebhook() z błędną sygnaturą → InvalidWebhookSignatureException
  ✓ requestRefund() → PUT /api/v2_1/orders/{id}/refund stub → sukces
  ✓ ten sam orderId w webhooku 2x → idempotencja (tylko raz zapis)
```

**Krok 7: Courier adapters** (WireMock):

```java
InPostRestAdapterTest
  ✓ createShipment() → POST /v1/shipments stub → zwraca ShipmentOrderId
  ✓ getLabel() → GET /v1/shipments/{id}/label → zwraca byte[] PDF
  ✓ paczka > 25kg → CourierCapacityExceededException

DpdRestAdapterTest
  ✓ createShipment() z referencją RMA w polu "reference"
  ✓ getLabel() → zwraca byte[] ZPL/PDF
```

**Krok 8: Full E2E integration test** (PostgreSQL + WireMock PayU + WireMock InPost):

```java
ReturnFlowIntegrationTest
  ✓ POST /api/v1/returns → 201, body zawiera rmaNumber ZWR-*
  ✓ POST /api/v1/returns/{rma}/payment → 200, body zawiera redirectUrl
  ✓ POST /api/v1/payment/webhook z PayU SUCCESS payload → status IN_TRANSIT
  ✓ GET /api/v1/returns/{rma} → status IN_TRANSIT, labelUrls non-empty
  ✓ POST /api/v1/warehouse/shipments/receive → status RECEIVED
  ✓ PUT /api/v1/warehouse/returns/{rma}/condition → status DECISION
  ✓ PUT /api/v1/warehouse/returns/{rma}/decision REFUND_AND_RETURN → status AWAITING_REFUND
  ✓ audit_log zawiera wszystkie zmiany statusu z userId i IP
```

### Sprint 3 - Web Layer (MockMvc / WebTestClient)

**Krok 9: Controller tests:**

```java
CustomerPortalControllerTest
  ✓ POST /api/v1/auth/identify z orderNumber + email → 200 + JWT
  ✓ POST /api/v1/auth/identify z błędnym email → 401
  ✓ POST /api/v1/returns (z JWT) → 201
  ✓ POST /api/v1/returns bez JWT → 401
  ✓ POST /api/v1/returns/{rma}/photos multipart/form-data → 200
  ✓ GET /api/v1/returns/{rma} z JWT innego klienta → 403

WarehouseControllerTest
  ✓ POST /api/v1/warehouse/shipments/receive z JWT WAREHOUSE_WORKER → 200
  ✓ POST /api/v1/warehouse/shipments/receive z JWT CUSTOMER → 403
  ✓ PUT /api/v1/warehouse/returns/{rma}/condition z JWT WAREHOUSE_WORKER → 200
  ✓ PUT /api/v1/warehouse/returns/{rma}/decision z JWT BOK → 200
  ✓ GET /api/v1/warehouse/returns?status=VERIFICATION → lista z paginacją
  ✓ GET /api/v1/warehouse/returns?slaBreaching=true → tylko flagowane na czerwono
```

---

## 10. Pliki krytyczne do stworzenia

### Rdzeń domeny (`rma-domain`)
```
pl/neopak/rma/returnmanagement/domain/model/
    ReturnRequest.java                  ← aggregate root
    ReturnRequestId.java                ← value object (UUID wrapper)
    ReturnLineItem.java                 ← entity
    Shipment.java                       ← entity (multi-package)
    Photo.java                          ← entity
    RmaNumber.java                      ← value object
    ReturnStatus.java                   ← enum z transition guard
    ReturnReason.java                   ← enum
    ConditionAssessment.java            ← enum
    OrderReference.java                 ← value object
    CustomerInfo.java                   ← value object (RODO)
    PackageDimensions.java              ← value object z courier eligibility
    ShippingCostSplit.java              ← value object
    RefundDecision.java                 ← enum
    SlaDeadline.java                    ← value object

pl/neopak/rma/returnmanagement/domain/event/
    ReturnRequestCreated.java
    ReturnLabelPaymentRequested.java
    ReturnLabelGenerated.java
    ReturnShipmentReceived.java
    ReturnConditionAssessed.java
    RefundDecisionMade.java
    ReturnRejected.java
    ReturnCompleted.java
    SlaBreachWarningTriggered.java
    BlindReturnRegistered.java

pl/neopak/rma/returnmanagement/domain/exception/
    InvalidStatusTransitionException.java
    LabelGenerationBeforePaymentException.java
    RmaNotFoundException.java

pl/neopak/rma/returnmanagement/port/in/
    CreateReturnRequestUseCase.java
    ConfirmPaymentUseCase.java
    GenerateLabelUseCase.java
    ReceiveShipmentUseCase.java
    AssessConditionUseCase.java
    MakeRefundDecisionUseCase.java
    RegisterBlindReturnUseCase.java
    QueryReturnRequestUseCase.java

pl/neopak/rma/returnmanagement/port/out/
    ReturnRequestRepository.java
    RmaNumberGenerator.java
    DomainEventPublisher.java           ← kluczowy port, MVP: ApplicationEventPublisher
    PhotoStoragePort.java
    SlaClockPort.java                   ← abstrakcja nad Clock dla testowalności

pl/neopak/rma/returnmanagement/service/
    ReturnRequestService.java           ← implementuje use case interfaces
    SlaEnforcementService.java
```

### Infrastruktura (`rma-application`)
```
pl/neopak/rma/config/
    SecurityConfig.java
    OpenApiConfig.java

pl/neopak/rma/returnmanagement/adapter/in/web/
    CustomerPortalController.java
    WarehouseController.java
    dto/CreateReturnRequestRequest.java
    dto/ReturnRequestResponse.java
    dto/AssessConditionRequest.java
    dto/MakeDecisionRequest.java

pl/neopak/rma/returnmanagement/adapter/out/persistence/
    ReturnRequestJpaRepository.java
    ReturnRequestPersistenceAdapter.java
    ReturnRequestMapper.java
    entity/ReturnRequestEntity.java
    entity/ReturnLineItemEntity.java
    entity/ShipmentEntity.java

pl/neopak/rma/returnmanagement/adapter/out/messaging/
    SpringApplicationEventPublisher.java    (MVP)

pl/neopak/rma/returnmanagement/scheduler/
    SlaEnforcementScheduler.java            (@Scheduled, cron)

src/main/resources/
    application.yml
    db/migration/return_management/V1__create_return_requests.sql
    db/migration/return_management/V2__create_return_line_items.sql
    db/migration/return_management/V3__create_shipments.sql
    db/migration/identity_access/V1__create_users.sql
    db/migration/identity_access/V2__create_audit_log.sql
```

### Testy
```
rma-domain/src/test/groovy/pl/neopak/rma/returnmanagement/   ← Spock (.groovy)
    domain/model/ReturnRequestSpec.groovy
    domain/model/ReturnStatusSpec.groovy
    domain/model/PackageDimensionsSpec.groovy
    domain/model/ShippingCostSplitSpec.groovy
    domain/model/SlaDeadlineSpec.groovy
    domain/model/RmaNumberSpec.groovy
    service/SlaEnforcementServiceSpec.groovy

rma-application/src/test/groovy/pl/neopak/rma/               ← Spock (.groovy)
    returnmanagement/service/CreateReturnRequestServiceSpec.groovy
    returnmanagement/service/GenerateLabelServiceSpec.groovy
    returnmanagement/service/ReceiveShipmentServiceSpec.groovy
    returnmanagement/service/MakeRefundDecisionServiceSpec.groovy

rma-application/src/test/java/pl/neopak/rma/                 ← JUnit 5 (.java)
    returnmanagement/adapter/out/persistence/ReturnRequestPersistenceAdapterTest.java
    returnmanagement/adapter/out/payment/PayURestAdapterTest.java
    returnmanagement/adapter/out/courier/InPostRestAdapterTest.java
    returnmanagement/adapter/in/web/CustomerPortalControllerTest.java
    returnmanagement/adapter/in/web/WarehouseControllerTest.java
    ReturnFlowIntegrationTest.java         ← full E2E
```

---

## 11. Zależności Maven (kluczowe)

```xml
<!-- ============================================================ -->
<!-- rma-domain/pom.xml - ZERO frameworków produkcyjnych          -->
<!-- ============================================================ -->
<dependencies>
    <!-- Spock: testy domenowe (VO, agregaty, domain services) -->
    <dependency>
        <groupId>org.spockframework</groupId>
        <artifactId>spock-core</artifactId>
        <version>2.4-M4-groovy-4.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.groovy</groupId>
        <artifactId>groovy</artifactId>
        <version>4.0.21</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- kompilacja Groovy (src/test/groovy) razem z Javą -->
        <plugin>
            <groupId>org.codehaus.gmavenplus</groupId>
            <artifactId>gmavenplus-plugin</artifactId>
            <version>3.0.2</version>
            <executions>
                <execution>
                    <goals>
                        <goal>addTestSources</goal>
                        <goal>compileTests</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>

<!-- ============================================================ -->
<!-- rma-application/pom.xml                                      -->
<!-- ============================================================ -->
<dependencies>
    <!-- produkcja -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.6.0</version></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><version>0.12.6</version></dependency>

    <!-- Spock: testy use case (Sprint 1) -->
    <dependency>
        <groupId>org.spockframework</groupId>
        <artifactId>spock-core</artifactId>
        <version>2.4-M4-groovy-4.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.spockframework</groupId>
        <artifactId>spock-spring</artifactId>
        <version>2.4-M4-groovy-4.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.groovy</groupId>
        <artifactId>groovy</artifactId>
        <version>4.0.21</version>
        <scope>test</scope>
    </dependency>

    <!-- JUnit 5: testy infrastruktury i web (Sprint 2-3) -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>spock</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.wiremock</groupId><artifactId>wiremock-standalone</artifactId><version>3.9.1</version><scope>test</scope></dependency>
</dependencies>
```

> `spring-boot-starter-test` wciaga JUnit 5 + AssertJ + Mockito. Mockito jest dostepny ale uzywanym **tylko** w testach warstwy web i infrastruktury — w domenowych i use-case testach zastepuja go wbudowane Spock `Mock()` / `Stub()`.

---

## 12. Weryfikacja end-to-end

**Uruchomienie lokalne:**
```bash
docker-compose up -d          # PostgreSQL (port 5432)
./mvnw spring-boot:run        # aplikacja na localhost:8080
```

**Testy jednostkowe domeny (bez Spring, < 1s):**
```bash
./mvnw test -pl rma-domain
```

**Testy integracyjne (Testcontainers autostaruje PG):**
```bash
./mvnw verify -pl rma-application
```

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

**Scenariusz happy path (curl/Postman):**
```
1. POST /api/v1/auth/identify         { orderNumber, email }          → JWT token
2. POST /api/v1/returns               { lineItems, courier }          → { rmaNumber: "ZWR-00001" }
3. POST /api/v1/returns/ZWR-00001/payment                             → { redirectUrl }
4. [symulacja webhooka PayU SUCCESS]  POST /api/v1/payment/webhook
5. GET  /api/v1/returns/ZWR-00001                                     → status: IN_TRANSIT, labelUrls: [...]
6. POST /api/v1/warehouse/shipments/receive  { trackingNumber }       → status: RECEIVED
7. PUT  /api/v1/warehouse/returns/ZWR-00001/condition  { condition }  → status: DECISION
8. PUT  /api/v1/warehouse/returns/ZWR-00001/decision   { decision: REFUND_AND_RETURN } → status: AWAITING_REFUND
```

**Weryfikacja audit logu:**
```sql
SELECT * FROM identity_access.audit_log WHERE rma_number = 'ZWR-00001' ORDER BY created_at;
-- powinno zawierać 6 wierszy ze zmianami statusu, każdy z user_id i ip_address
```
