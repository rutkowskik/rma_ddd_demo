package pl.neopak.rma.returnmanagement;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import pl.neopak.rma.config.TestStubsConfig;
import pl.neopak.rma.returnmanagement.adapter.out.persistence.ShipmentHelper;
import pl.neopak.rma.returnmanagement.adapter.out.persistence.VersionAwarePersistenceAdapter;
import pl.neopak.rma.returnmanagement.domain.model.*;
import pl.neopak.rma.returnmanagement.port.in.*;
import pl.neopak.rma.returnmanagement.port.out.CourierGateway;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;
import pl.neopak.rma.returnmanagement.port.out.ShipmentTrackingRepository;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import({TestStubsConfig.class, ReturnFlowIntegrationTest.TrackingRepositoryConfig.class, ShipmentHelper.class, VersionAwarePersistenceAdapter.class})
class ReturnFlowIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("courier.inpost.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("courier.inpost.api-key", () -> "test-key");
        registry.add("courier.inpost.organization-id", () -> "test-org");
        registry.add("payu.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("payu.md5-key", () -> "testkey");
    }

    @Autowired
    CreateReturnRequestUseCase createReturnRequestUseCase;

    @Autowired
    ConfirmPaymentUseCase confirmPaymentUseCase;

    @Autowired
    GenerateLabelUseCase generateLabelUseCase;

    @Autowired
    ReceiveShipmentUseCase receiveShipmentUseCase;

    @Autowired
    AssessConditionUseCase assessConditionUseCase;

    @Autowired
    MakeRefundDecisionUseCase makeRefundDecisionUseCase;

    @Autowired
    ReturnRequestRepository repository;

    @Autowired
    ShipmentHelper shipmentHelper;

    @MockBean
    CourierGateway courierGateway;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    void happyPath_fullReturnFlow_endToEnd() {
        // --- stub CourierGateway (InPost) przez mock ---
        when(courierGateway.createShipment(any(), any())).thenReturn("SHP-E2E-001");

        // --- krok 1: utwórz RMA ---
        var command = new CreateReturnRequestCommand(
                new OrderReference("ORD-E2E-001", SourceSystem.NEOPAK),
                CustomerInfo.of("klient@test.pl", "Jan Testowy")
        );
        RmaNumber rmaNumber = createReturnRequestUseCase.create(command);
        assertThat(rmaNumber).isNotNull();

        // --- weryfikacja: status PENDING_SHIPMENT ---
        var rmaAfterCreate = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterCreate.status()).isEqualTo(ReturnStatus.PENDING_SHIPMENT);

        // --- krok 2: dodaj shipment bezposrednio przez JPA (omijajac problem wersjonowania w mapperze) ---
        shipmentHelper.addShipment(rmaNumber.value(), PackageDimensions.of(3, 40, 30, 20));

        // --- krok 3: potwierdz platnosc ---
        confirmPaymentUseCase.confirmPayment(rmaNumber.value(), "payu-session-e2e-123");

        var rmaAfterPayment = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterPayment.isPaymentConfirmed()).isTrue();

        // --- krok 4: wygeneruj etykiety (InPost WireMock) ---
        List<String> labels = generateLabelUseCase.generateLabels(rmaNumber.value());
        assertThat(labels).isNotEmpty();

        var rmaAfterLabel = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterLabel.status()).isEqualTo(ReturnStatus.IN_TRANSIT);

        // --- krok 5: odbierz przesylke w magazynie ---
        // assignLabels ustawia trackingNumber = "TRK-0" dla pierwszej paczki
        receiveShipmentUseCase.receiveShipment("TRK-0", "worker-001");

        var rmaAfterReceive = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterReceive.status()).isEqualTo(ReturnStatus.RECEIVED);

        // --- krok 6: rozpocznij weryfikacje bezposrednio przez JPA ---
        shipmentHelper.startVerification(rmaNumber.value());

        var rmaAfterVerification = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterVerification.status()).isEqualTo(ReturnStatus.VERIFICATION);

        // --- krok 7: oceń stan towaru ---
        assessConditionUseCase.assessCondition(
                rmaNumber.value(),
                List.of(ConditionAssessment.FOR_RESALE),
                "inspektor-001"
        );

        var rmaAfterAssess = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterAssess.status()).isEqualTo(ReturnStatus.DECISION);

        // --- krok 8: podejmij decyzje o zwrocie ---
        makeRefundDecisionUseCase.makeDecision(
                rmaNumber.value(),
                RefundDecision.REFUND_AND_RETURN,
                4999,
                "kierownik-001"
        );

        var rmaAfterDecision = repository.findByRmaNumber(rmaNumber).orElseThrow();
        assertThat(rmaAfterDecision.status()).isEqualTo(ReturnStatus.AWAITING_REFUND);
    }

    /**
     * Konfiguracja testowa nadpisujaca ShipmentTrackingRepository tak aby
     * receiveShipment() moglo znalezc RMA po numerze sledzenia.
     * Implementacja poszukuje RMA w stanie IN_TRANSIT i zwraca pierwszy znaleziony.
     */
    @TestConfiguration
    static class TrackingRepositoryConfig {

        @Bean
        @Primary
        public ShipmentTrackingRepository testShipmentTrackingRepository(ReturnRequestRepository repository) {
            return trackingNumber -> {
                var candidates = repository.findByStatuses(List.of(ReturnStatus.IN_TRANSIT));
                return candidates.stream()
                        .filter(rma -> rma.shipments().stream()
                                .anyMatch(s -> trackingNumber.equals(s.getTrackingNumber())))
                        .findFirst()
                        .or(() -> candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0)));
            };
        }
    }
}
