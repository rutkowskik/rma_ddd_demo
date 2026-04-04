package pl.neopak.rma.courier.adapter.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.neopak.rma.returnmanagement.domain.model.PackageDimensions;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class InPostRestAdapterTest {

    private static WireMockServer wireMock;
    private InPostRestAdapter adapter;

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
        InPostProperties properties = new InPostProperties(
                "http://localhost:" + wireMock.port(),
                "test-api-key",
                "org-123"
        );
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
        adapter = new InPostRestAdapter(properties, restClient);
    }

    @Test
    void createShipment_returnsShipmentId() {
        wireMock.stubFor(post(urlEqualTo("/v1/organizations/org-123/shipments"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "SHP-INPOST-001", "status": "created", "trackingNumber": "000099999000000000"}
                                """)));

        PackageDimensions dims = PackageDimensions.of(3, 40, 30, 20);
        String shipmentId = adapter.createShipment(dims, "ZWR-00001");

        assertThat(shipmentId).isEqualTo("SHP-INPOST-001");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/organizations/org-123/shipments"))
                .withHeader("Authorization", equalTo("Bearer test-api-key"))
                .withRequestBody(matchingJsonPath("$.reference", equalTo("ZWR-00001")))
                .withRequestBody(matchingJsonPath("$.service", equalTo("inpost_courier_return"))));
    }

    @Test
    void getLabel_returnsPdfBytes() {
        byte[] fakePdf = "%PDF-1.4 fake label content".getBytes();
        wireMock.stubFor(get(urlEqualTo("/v1/shipments/SHP-INPOST-001/label"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(fakePdf)));

        byte[] label = adapter.getLabel("SHP-INPOST-001");

        assertThat(label).isEqualTo(fakePdf);
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/shipments/SHP-INPOST-001/label"))
                .withHeader("Authorization", equalTo("Bearer test-api-key")));
    }

    @Test
    void createShipment_throwsWhenNoIdReturned() {
        wireMock.stubFor(post(urlEqualTo("/v1/organizations/org-123/shipments"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        PackageDimensions dims = PackageDimensions.of(1, 20, 15, 10);

        assertThatThrownBy(() -> adapter.createShipment(dims, "ZWR-00002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("InPost did not return shipment id");
    }

    @Test
    void getLabel_throwsWhenEmptyBody() {
        wireMock.stubFor(get(urlEqualTo("/v1/shipments/SHP-MISSING/label"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(new byte[0])));

        assertThatThrownBy(() -> adapter.getLabel("SHP-MISSING"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty label");
    }
}
