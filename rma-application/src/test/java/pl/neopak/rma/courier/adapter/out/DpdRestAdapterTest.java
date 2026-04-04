package pl.neopak.rma.courier.adapter.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class DpdRestAdapterTest {

    private static WireMockServer wireMock;
    private DpdRestAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        DpdProperties properties = new DpdProperties(
                "http://localhost:" + wireMock.port(),
                "neopak-login",
                "secret-pass",
                "5678"
        );
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        adapter = new DpdRestAdapter(properties, restClient);
    }

    @Test
    void createShipment_returnsShipmentId() {
        wireMock.stubFor(post(urlEqualTo("/services/shipment"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"shipmentId": "DPD-00042", "parcelNumber": "09876543210987654321"}
                                """)));

        PackageDimensions dims = PackageDimensions.of(5, 50, 40, 30);
        String shipmentId = adapter.createShipment(dims, "ZWR-00003");

        assertThat(shipmentId).isEqualTo("DPD-00042");
        wireMock.verify(postRequestedFor(urlEqualTo("/services/shipment"))
                .withRequestBody(matchingJsonPath("$.shipment.reference", equalTo("ZWR-00003")))
                .withRequestBody(matchingJsonPath("$.login", equalTo("neopak-login")))
                .withRequestBody(matchingJsonPath("$.shipment.services.type", equalTo("RETURN"))));
    }

    @Test
    void getLabel_returnsPdfBytes() {
        byte[] fakePdf = "%PDF-1.4 dpd label content".getBytes();
        wireMock.stubFor(get(urlEqualTo("/services/label/DPD-00042"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(fakePdf)));

        byte[] label = adapter.getLabel("DPD-00042");

        assertThat(label).isEqualTo(fakePdf);
    }

    @Test
    void createShipment_throwsWhenNoShipmentIdReturned() {
        wireMock.stubFor(post(urlEqualTo("/services/shipment"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        PackageDimensions dims = PackageDimensions.of(2, 30, 20, 15);

        assertThatThrownBy(() -> adapter.createShipment(dims, "ZWR-00004"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DPD did not return shipment id");
    }

    @Test
    void getLabel_throwsWhenEmptyBody() {
        wireMock.stubFor(get(urlEqualTo("/services/label/DPD-MISSING"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(new byte[0])));

        assertThatThrownBy(() -> adapter.getLabel("DPD-MISSING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty label");
    }
}
