# Instrukcja: jak uzywac agentow i dzielic prace

## Dostepni agenci

| Agent | Komenda | Uzycie |
|---|---|---|
| `ddd-value-object` | `/ddd-value-object` | Nowy Value Object + Spock spec |
| `ddd-aggregate` | `/ddd-aggregate` | Agregat root + Spock spec |
| `ddd-use-case` | `/ddd-use-case` | Application Service + Spock spec |
| `flyway-schema` | `/flyway-schema` | Migracje SQL dla schematu |
| `jpa-persistence-adapter` | `/jpa-persistence-adapter` | JPA entity + mapper + adapter + test |
| `rest-controller` | `/rest-controller` | Kontroler REST + DTOs + test MockMvc |

---

## Zasada kazdej sesji

Kazda sesja = jeden fragment = zielone testy na koncu.
Nigdy nie zaczynam nastepnego fragmentu jesli poprzedni ma czerwone testy.

---

## Kolejnosc fragmentow i komendy

### Faza 0 — Szkielet (zrob recznie raz)

```
Zadanie:
  Stworz Maven multi-module projekt z modulami rma-domain i rma-application.
  Dodaj pom.xml zgodnie z PLAN_IMPLEMENTACJI.md sekcja 11.
  Dodaj docker-compose.yml z PostgreSQL 16 na porcie 5432.
  Dodaj src/main/resources/application.yml z datasource i flyway.

Weryfikacja: ./mvnw compile
```

---

### Faza 1 — Domain Model (jeden VO na sesje)

**Fragment 1.1 — ReturnStatus**
```
Przeczytaj PLAN_IMPLEMENTACJI.md sekcje 2 (maszyna stanow).
/ddd-value-object
Nazwa: ReturnStatus
Typ: enum z metoda canTransitionTo(ReturnStatus) i transitionTo(ReturnStatus)
Invariant: nieprawidlowe przejscie rzuca InvalidStatusTransitionException
```

**Fragment 1.2 — RmaNumber**
```
/ddd-value-object
Nazwa: RmaNumber
Format: ZWR-NNNNN (regex: ZWR-\d{5})
Invariant: nieprawidlowy format rzuca IllegalArgumentException
```

**Fragment 1.3 — PackageDimensions + CourierCode**
```
/ddd-value-object
Nazwy: CourierCode (enum: INPOST, DPD, GLS, ORLEN, GEIS), PackageDimensions
Pola PackageDimensions: weightKg (int), lengthCm, widthCm, heightCm
Metoda: isSuitableForCourier(CourierCode) - limity z PLAN_IMPLEMENTACJI.md
```

**Fragment 1.4 — ShippingCostSplit**
```
/ddd-value-object
Nazwa: ShippingCostSplit
Pola: totalGrosze (int), customerSharePercent (int)
Metody: customerShare(), storeShare() - zaokraglenie ceil po stronie klienta
Invariant: kwoty w groszach, nie float
```

**Fragment 1.5 — SlaDeadline**
```
/ddd-value-object
Nazwa: SlaDeadline
Pole: deadline (LocalDate), obliczane jako receivedAt + 14 dni
Metody: isBreached(Clock), daysRemaining(Clock), of(LocalDate)
```

**Fragment 1.6 — CustomerInfo + OrderReference + SourceSystem**
```
/ddd-value-object
Nazwy: SourceSystem (enum: NEOPAK, ALLEGRO, EMAG, TEMU, BASELINKER, MANUAL)
       OrderReference (orderId: String, sourceSystem: SourceSystem)
       CustomerInfo (email: String, name: String) z pseudonymize()
```

**Fragment 1.7 — Zdarzenia domenowe**
```
Stworz wszystkie zdarzenia jako Java records w pakiecie domain/event/:
ReturnRequestCreated, ReturnLabelPaymentRequested, ReturnLabelGenerated,
ReturnShipmentReceived, ReturnConditionAssessed, RefundDecisionMade,
ReturnRejected, ReturnCompleted, SlaBreachWarningTriggered, BlindReturnRegistered.
Wzor: public record ReturnRequestCreated(RmaNumber rmaNumber, String customerEmail, Instant occurredAt) {}
Brak testow dla prostych rekordow.
```

**Fragment 1.8 — Porty (interfejsy)**
```
Stworz interfejsy portow z PLAN_IMPLEMENTACJI.md sekcja 10 (lista plikow):
- port/in/: CreateReturnRequestUseCase, ConfirmPaymentUseCase, GenerateLabelUseCase,
            ReceiveShipmentUseCase, AssessConditionUseCase, MakeRefundDecisionUseCase,
            RegisterBlindReturnUseCase, QueryReturnRequestUseCase
- port/out/: ReturnRequestRepository, RmaNumberGenerator,
             DomainEventPublisher, PhotoStoragePort, SlaClockPort
Brak testow dla czystych interfejsow.
```

**Fragment 1.9 — Agregat ReturnRequest** *(wymaga 1.1–1.8 gotowych)*
```
Przeczytaj wszystkie pliki z src/main/java/pl/neopak/rma/returnmanagement/domain/
/ddd-aggregate
Agregat: ReturnRequest z encjami ReturnLineItem, Shipment, Photo
Maszyna stanow z PLAN_IMPLEMENTACJI.md sekcja 2
```

**Fragment 1.10 — SlaEnforcementService**
```
/ddd-use-case
Nazwa: SlaEnforcementService (domain service, nie application service)
Metody: findBreachedReturns(), findWarningReturns() (<=3 dni)
Zaleznosci: ReturnRequestRepository, SlaClockPort
```

---

### Faza 2 — Application Services (jeden use case na sesje)

**Fragment 2.1 — CreateReturnRequestService**
```
Przeczytaj: ReturnRequest.java, CreateReturnRequestUseCase.java, ReturnRequestRepository.java
/ddd-use-case
Nazwa: CreateReturnRequestService
Scenariusze z PLAN_IMPLEMENTACJI.md sekcja 10 krok 4
```

**Fragment 2.2 — ConfirmPaymentService + GenerateLabelService**
```
Przeczytaj: ReturnRequest.java, porty payment i courier
/ddd-use-case
Razem bo to choreography saga: platnosc odblokowuje generacje etykiety
Wielopaczkowosc: N paczek -> N wywolan CourierGateway -> N PDF
```

**Fragment 2.3 — ReceiveShipmentService**
```
/ddd-use-case
Scenariusz: nieznany numer listu -> wywolaj RegisterBlindReturnUseCase
Scenariusz: partial receipt (2 z 3 paczek) -> nie zmieniaj statusu na RECEIVED
```

**Fragment 2.4 — AssessConditionService + MakeRefundDecisionService**
```
/ddd-use-case
AssessCondition: autoryzacja rola WAREHOUSE_WORKER lub WAREHOUSE_MANAGER
MakeRefundDecision: REFUND_AND_DISPOSE nie tworzy ShipmentOrder
```

---

### Faza 3 — Infrastruktura (jeden adapter na sesje)

**Fragment 3.1 — SQL migracje**
```
Przeczytaj: ReturnRequest.java i wszystkie encje wewnetrzne
/flyway-schema
Schemat: return_management
Tabele: return_requests, return_line_items, shipments, photos
```

**Fragment 3.2 — JPA adapter ReturnRequest**
```
Przeczytaj: ReturnRequest.java + V1-V4 migracje SQL z kroku 3.1
/jpa-persistence-adapter
Agregat: ReturnRequest
```

**Fragment 3.3 — SecurityConfig + JWT**
```
Stworz SecurityConfig.java, JwtTokenProvider.java, JwtAuthFilter.java.
Role: CUSTOMER, WAREHOUSE_WORKER, WAREHOUSE_MANAGER, BOK, ACCOUNTING, ADMIN.
Endpointy /api/v1/auth/** sa publiczne, reszta wymaga JWT.
```

**Fragment 3.4 — PayURestAdapter**
```
Stworz PayURestAdapter implementujacy PaymentGateway.
Test z WireMock: tworzenie sesji, walidacja sygnatury MD5 webhooka, refund, idempotencja.
```

**Fragment 3.5 — Adaptery kurierow**
```
Stworz InPostRestAdapter i DpdRestAdapter implementujace CourierGateway.
Test z WireMock per adapter: generowanie etykiety, pobieranie PDF.
```

---

### Faza 4 — Web Layer (jeden kontroler na sesje)

**Fragment 4.1 — CustomerPortalController**
```
Przeczytaj: CreateReturnRequestUseCase.java, QueryReturnRequestUseCase.java
/rest-controller
Endpoints: POST /api/v1/returns, GET /api/v1/returns/{rma},
           POST /api/v1/returns/{rma}/payment,
           POST /api/v1/returns/{rma}/photos (multipart)
Rola: CUSTOMER (autentykacja przez nr zamowienia + email -> JWT)
```

**Fragment 4.2 — WarehouseController**
```
Przeczytaj: ReceiveShipmentUseCase.java, AssessConditionUseCase.java, MakeRefundDecisionUseCase.java
/rest-controller
Endpoints: POST /api/v1/warehouse/shipments/receive,
           PUT  /api/v1/warehouse/returns/{rma}/condition,
           PUT  /api/v1/warehouse/returns/{rma}/decision,
           GET  /api/v1/warehouse/returns (filtrowanie, paginacja)
Role: WAREHOUSE_WORKER, WAREHOUSE_MANAGER, BOK
```

**Fragment 4.3 — Full E2E test**
```
Stworz ReturnFlowIntegrationTest.java.
Testcontainers: PostgreSQL.
WireMock: PayU, InPost.
Scenariusz: POST /returns -> platnosc -> webhook -> receive -> condition -> decision -> AWAITING_REFUND.
Sprawdz audit_log po kazdym kroku.
```

---

## Wzorzec promptu na poczatek sesji

Kopiuj i uzupelnij przed kazdym fragmentem:

```
Pracujemy nad systemem RMA dla neopak.pl (DDD + TDD, Java 21, Spring Boot 3).

Przeczytaj najpierw:
- PLAN_IMPLEMENTACJI.md sekcje [N]
- [lista konkretnych plikow zaleznosci]

Nastepnie wygeneruj fragment [X.Y]:
[tresc z listy powyzej]

Na koncu uruchom testy i napraw bledy jesli wystapily.
```

---

## Co robic gdy test nie przechodzi

1. Nie zmieniaj testu zeby passowal do implementacji
2. Przeczytaj komunikat bledu — czesto to literowka lub brakujacy import
3. Jesli blad w logice domenowej — popraw implementacje, nie test
4. Jesli blad w konfiguracji (Testcontainers, WireMock) — sprawdz application-test.yml
5. Dopiero gdy nie mozesz naprawic — zapytaj z pelnym stack trace
