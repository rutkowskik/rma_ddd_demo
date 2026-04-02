# Diagram cyklu zycia reklamacji RMA

## 1a. Maszyna stanow — pelny cykl zycia

```mermaid
stateDiagram-v2
    direction TB

    [*] --> PENDING_SHIPMENT : Klient sklada zgloszenie
    [*] --> BLIND_RECEIVED : Slepa paczka bez etykiety RMA

    PENDING_SHIPMENT --> IN_TRANSIT : Platnosc SUCCESS\nEtykieta wygenerowana

    IN_TRANSIT --> RECEIVED : Magazyn skanuje paczke

    BLIND_RECEIVED --> VERIFICATION : Magazynier laczy\nz zamowieniem w ERP

    RECEIVED --> VERIFICATION : Rozpakowanie\ni weryfikacja stanu

    VERIFICATION --> DECISION : Ocena towaru\n+ zdjecia uszkodzen

    DECISION --> AWAITING_REFUND : Zwrot towaru\n+ zwrot srodkow
    DECISION --> REFUND_AND_DISPOSE : Utylizacja\n(prewencja kosztu kuriera)
    DECISION --> REJECTED : Odrzucenie\n(brak podstaw)

    PENDING_SHIPMENT --> DECISION : Skrot BOK\npo analizie zdjec klienta

    AWAITING_REFUND --> COMPLETED : Przelew Elixir-0\nlub PayU API
    REFUND_AND_DISPOSE --> COMPLETED : Automatyczny\nzwrot srodkow

    REJECTED --> [*]
    COMPLETED --> [*]
```

---

## 1b. Podzial stanow na dzialy

```mermaid
flowchart TD
    subgraph K["KLIENT — Portal neopak.pl"]
        PS([PENDING_SHIPMENT])
        IT([IN_TRANSIT])
    end

    subgraph M["MAGAZYN — Obsluga fizyczna"]
        BR([BLIND_RECEIVED])
        RC([RECEIVED])
        VF([VERIFICATION])
    end

    subgraph B["BOK i KSIEGOWOSC — Decyzja i rozliczenie"]
        DC([DECISION])
        AR([AWAITING_REFUND])
        RD([REFUND_AND_DISPOSE])
        RJ([REJECTED])
        CO([COMPLETED])
    end

    START(( )) -->|Zgloszenie + platnosc| PS
    PS -->|Etykieta wygenerowana| IT
    IT -->|Skanowanie w magazynie| RC
    START -->|Slepa paczka| BR
    BR -->|Identyfikacja w ERP| VF
    RC -->|Weryfikacja stanu| VF
    VF -->|Ocena + zdjecia| DC
    PS -.->|Skrot BOK\nanaliza zdjec| DC
    DC -->|Zwrot towaru| AR
    DC -->|Utylizacja| RD
    DC -->|Odrzucenie| RJ
    AR -->|Przelew zrealizowany| CO
    RD -->|Automatyczny zwrot| CO
    RJ --> STOP(( ))
    CO --> STOP

    style K fill:#dce8ff,stroke:#4a7ece
    style M fill:#dcffd8,stroke:#3a9e34
    style B fill:#fff3dc,stroke:#ce8e2a
    style START fill:#333,color:#fff
    style STOP fill:#333,color:#fff
```

---

## 2. Sciezka slepego zwrotu

```mermaid
stateDiagram-v2
    direction LR

    [*] --> BLIND_RECEIVED : Magazyn odbiera paczke\nbez etykiety RMA.\nReczne utworzenie karty.

    BLIND_RECEIVED --> VERIFICATION : Magazynier identyfikuje\nzamowienie w archiwum ERP\ni laczy z karta RMA

    VERIFICATION --> DECISION : Ocena stanu towaru\n+ zdjecia

    DECISION --> AWAITING_REFUND : Zwrot srodkow\n+ odebranie towaru
    DECISION --> REFUND_AND_DISPOSE : Zwrot srodkow\n+ utylizacja
    DECISION --> REJECTED : Odrzucenie

    AWAITING_REFUND --> COMPLETED : Przelew zrealizowany
    REFUND_AND_DISPOSE --> COMPLETED : Przelew zrealizowany
    REJECTED --> [*]
    COMPLETED --> [*]
```

---

## 3. Pelny przeplyw — diagram sekwencji

```mermaid
sequenceDiagram
    actor Klient
    participant Portal as Portal neopak.pl
    participant RMA as System RMA
    participant PayU as PayU
    participant Kurier as Kurier (InPost / DPD)
    actor Magazynier
    actor BOK
    actor Ksiegowosc

    rect rgb(220, 235, 255)
        Note over Klient,Kurier: FAZA 1 — Zgloszenie i generowanie etykiety
        Klient->>Portal: Logowanie (nr zamowienia + email)
        Portal->>RMA: Pobierz produkty zamowienia
        RMA-->>Portal: Lista produktow z wymiarami i waga
        Klient->>Portal: Wybiera produkty, powod,\nwgrywam zdjecia uszkodzen
        Portal->>RMA: POST /api/v1/returns
        RMA-->>Portal: { rmaNumber: ZWR-00001, status: PENDING_SHIPMENT }
        Portal->>RMA: POST /api/v1/returns/ZWR-00001/payment
        RMA->>PayU: Inicjuj sesje platnosci (50% kosztu kuriera)
        PayU-->>RMA: { redirectUrl }
        RMA-->>Portal: { redirectUrl }
        Klient->>PayU: Oplacenie kosztu zwrotu
        PayU->>RMA: Webhook: ORDER_STATUS SUCCESS
        RMA->>Kurier: Generuj etykiete(y) PDF
        Note over RMA,Kurier: Algorytm wielopaczkowosci:\n5 paczek po 30kg = 5 osobnych PDF
        Kurier-->>RMA: { labelUrl[], trackingNumber[] }
        RMA-->>Klient: E-mail: etykieta gotowa (ZWR-00001)\n[status zmieniony na IN_TRANSIT]
        Klient->>Kurier: Nadaje paczke z wydrukiem etykiety\n(kod RMA w polu Reference)
    end

    rect rgb(220, 255, 220)
        Note over Magazynier,RMA: FAZA 2 — Przyjecie w magazynie
        Kurier->>Magazynier: Dostarcza paczke do magazynu
        Magazynier->>RMA: Skanuje numer listu kuriera\nPOST /api/v1/warehouse/shipments/receive
        RMA-->>Magazynier: Karta zwrotu ZWR-00001\n[status: RECEIVED, SLA: 14 dni]
        RMA-->>Klient: E-mail: paczka dotarla do magazynu
        Magazynier->>RMA: Ocena stanu towaru + zdjecia\nPUT /api/v1/warehouse/returns/ZWR-00001/condition\n{ condition: DAMAGED }
        RMA-->>Magazynier: [status: DECISION]
    end

    rect rgb(255, 240, 220)
        Note over BOK,Ksiegowosc: FAZA 3 — Decyzja i zwrot srodkow
        BOK->>RMA: Przeglad karty + zdjec\nPUT /api/v1/warehouse/returns/ZWR-00001/decision\n{ decision: REFUND_AND_RETURN }
        RMA->>RMA: Tworzy RefundSettlement\nGeneruje korekte KFS w Subiekcie
        RMA-->>Klient: E-mail: decyzja o zwrocie srodkow\n[status: AWAITING_REFUND]
        Ksiegowosc->>RMA: Pobiera liste do zwrotu\nGET /api/v1/accounting/settlements
        Ksiegowosc->>RMA: Eksport paczki przelewow\nPOST /api/v1/accounting/export/elixir
        RMA-->>Ksiegowosc: Plik Elixir-0 (Santander / Pekao S.A.)
        Ksiegowosc->>Klient: Przelew bankowy
        Ksiegowosc->>RMA: Potwierdzenie realizacji\nPUT /api/v1/accounting/settlements/{id}/confirm
        RMA-->>Klient: E-mail: srodki zostaly zwrocone\n[status: COMPLETED]
    end
```

---

## 4. Saga: generowanie etykiety (choreography)

```mermaid
sequenceDiagram
    participant RM as ReturnManagement
    participant PI as PaymentIntegration
    participant CI as CourierIntegration
    participant N as Notifications

    Note over RM,N: MVP: Spring ApplicationEventPublisher (in-process)\nFaza 2: Kafka topics

    RM->>PI: [event] ReturnLabelPaymentRequested\n{ rmaNumber, amountGrosze }
    PI-->>RM: { redirectUrl } dla klienta (sync)

    Note over PI: Klient placi w PayU
    PI->>PI: Odbiera webhook PayU SUCCESS\n(walidacja sygnatury MD5)
    PI->>RM: [event] PaymentConfirmed\n{ rmaNumber, sessionId, paidAt }

    loop Dla kazdej paczki (multi-package)
        RM->>CI: createShipment(ParcelSpecification) [sync]
        CI-->>RM: ShipmentOrderId + labelUrl
    end

    RM->>RM: [event] ReturnLabelGenerated\n{ rmaNumber, labelUrls[] }
    RM->>N: [event] ReturnLabelGenerated
    N-->>Klient: E-mail z N linkami do PDF etykiet
```

---

## 5. Mapa aktorow i uprawnien

```mermaid
graph TD
    subgraph Zewnetrzni["Zewnetrzni"]
        K[Klient]
        MP["Marketplace'y\nAllegro / eMag\nTemu / BaseLinker"]
    end

    subgraph RMA["System RMA"]
        CP["Portal Klienta\n/api/v1/returns\nrola: CUSTOMER"]
        WH["Panel Magazynu\n/api/v1/warehouse\nrola: WAREHOUSE_WORKER\nWAREHOUSE_MANAGER / BOK"]
        AC["Panel Ksiegowosci\n/api/v1/accounting\nrola: ACCOUNTING"]
        ADM["Panel Admina\n/api/v1/admin\nrola: ADMIN"]
    end

    subgraph Zewnetrzne["Systemy zewnetrzne"]
        PAYU["PayU / Przelewy24"]
        KUR["Kurierzy\nInPost DPD GLS\nOrlen Geis"]
        ERP["Subiekt ERP\nSfera / REST"]
        BANK["Bank\nElixir-0"]
    end

    K -->|"nr zamowienia + email → JWT"| CP
    MP -->|"API polling / scheduler"| CP

    CP <-->|platnosc i etykiety| PAYU
    CP <-->|generowanie etykiet PDF| KUR
    WH <-->|korekty KFS/ZK, flagi| ERP
    AC -->|export paczki przelewow| BANK
    AC <-->|automatyczny zwrot API| PAYU
```

---

## 6. Zrodla zwrotow — SourceAggregator

```mermaid
flowchart LR
    subgraph Zrodla["Zrodla zwrotow"]
        F1["Formularz neopak.pl\nFaza 1 MVP"]
        F6["Zwroty reczne\ni slepe\nFaza 1 MVP"]
        F2["BaseLinker API\nFaza 2"]
        F3["Allegro API\nFaza 3"]
        F4["eMag API\nFaza 3"]
        F5["Temu CSV\nFaza 3"]
    end

    subgraph ACL["Anti-Corruption Layer"]
        N1[NeopakFormAdapter]
        N6[ManualReturnController]
        N2[BaseLinkReturnAdapter]
        N3[AllegroReturnAdapter]
        N4[EMagReturnAdapter]
        N5[TemuCsvImportAdapter]
    end

    NORM["ReturnRequestNormalizer\nNormalizedReturnData"]
    EVENT[["event:\nExternalReturnRequestNormalized"]]
    RM["ReturnManagement\nCore Domain"]

    F1 --> N1 --> NORM
    F6 --> N6 --> NORM
    F2 --> N2 --> NORM
    F3 --> N3 --> NORM
    F4 --> N4 --> NORM
    F5 --> N5 --> NORM
    NORM --> EVENT --> RM
```

---

## 7. Legenda statusow

| Status | Wlasciciel | Opis |
|---|---|---|
| `PENDING_SHIPMENT` | System | Zgloszenie zlozoone, oczekiwanie na platnosc za etykiete |
| `IN_TRANSIT` | System | Etykieta oplacona i wygenerowana, paczka w drodze |
| `RECEIVED` | Magazynier | Paczka fizycznie przyjeta w magazynie, SLA 14 dni aktywne |
| `VERIFICATION` | Magazynier | Weryfikacja stanu towaru, upload zdjec |
| `DECISION` | BOK | Towar zweryfikowany, oczekiwanie na decyzje BOK |
| `AWAITING_REFUND` | Ksiegowosc | Decyzja pozytywna, oczekiwanie na realizacje przelewu |
| `REFUND_AND_DISPOSE` | System | Zwrot bez odsylania towaru — utylizacja na miejscu |
| `REJECTED` | BOK | Zgloszenie odrzucone, brak podstaw do zwrotu |
| `COMPLETED` | Ksiegowosc | Przelew zrealizowany, sprawa zamknieta |
| `BLIND_RECEIVED` | Magazynier | Paczka bez etykiety RMA, wymaga recznej identyfikacji |
